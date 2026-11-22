package com.example.data

import android.content.Context
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

data class GitHubUpdateCheckResult(
    val isUpdateAvailable: Boolean,
    val updateLabel: String = "",
    val statusMessage: String,
    val apkDownloadUrl: String? = null,
    val releasesPageUrl: String? = null,
    val latestCommitSha: String = ""
)

object GitHubUpdateChecker {

    fun parseGitHubRepoSlug(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return null

        var cleaned = trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trim('/')

        cleaned = when {
            cleaned.startsWith("github.com/", ignoreCase = true) ->
                cleaned.substringAfter("github.com/", "")
            cleaned.startsWith("api.github.com/repos/", ignoreCase = true) ->
                cleaned.substringAfter("api.github.com/repos/", "")
            cleaned.startsWith("raw.githubusercontent.com/", ignoreCase = true) ->
                cleaned.substringAfter("raw.githubusercontent.com/", "")
            else -> {
                val firstSegment = cleaned.substringBefore('/')
                if (firstSegment.contains('.') && !firstSegment.equals("github.com", ignoreCase = true)) {
                    return null
                }
                cleaned
            }
        }

        val parts = cleaned.split('/').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val owner = parts[0].trim()
        val repo = parts[1].removeSuffix(".git").trim()
        if (owner.isBlank() || repo.isBlank()) return null
        return "$owner/$repo"
    }

    suspend fun checkGitHubFilesForUpdate(
        context: Context,
        rawSource: String,
        token: String = ""
    ): GitHubUpdateCheckResult = withContext(Dispatchers.IO) {
        val trimmed = rawSource.trim()
        val pkgInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
        val installedVersionName = pkgInfo?.versionName ?: "1.0"
        val installedVersionCode = if (pkgInfo != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
        } else 1L
        val installedUpdateTimeMs = pkgInfo?.lastUpdateTime ?: 0L

        if (trimmed.isBlank()) {
            return@withContext GitHubUpdateCheckResult(
                isUpdateAvailable = false,
                statusMessage = "No GitHub repository configured yet."
            )
        }

        if (trimmed.endsWith(".apk", ignoreCase = true) &&
            (trimmed.startsWith("http://") || trimmed.startsWith("https://"))
        ) {
            return@withContext GitHubUpdateCheckResult(
                isUpdateAvailable = true,
                updateLabel = trimmed.substringAfterLast('/'),
                statusMessage = "Direct APK update link configured.",
                apkDownloadUrl = trimmed
            )
        }

        val repoSlug = parseGitHubRepoSlug(trimmed)
            ?: return@withContext GitHubUpdateCheckResult(
                isUpdateAvailable = false,
                statusMessage = "Enter a valid GitHub repo (owner/repo or https://github.com/owner/repo)."
            )

        try {
            if (token.isBlank()) {
                return@withContext checkViaWebAndRawFiles(
                    repoSlug = repoSlug,
                    installedVersionName = installedVersionName,
                    installedVersionCode = installedVersionCode,
                    installedUpdateTimeMs = installedUpdateTimeMs
                ) ?: GitHubUpdateCheckResult(
                    isUpdateAvailable = false,
                    statusMessage = "Repository '$repoSlug' was not found publicly. Verify owner/repo or add a GitHub Token for private repos."
                )
            }

            // Authenticated GitHub REST API path for private or token-enabled repos
            val (repoCode, repoBody) = githubHttpGet("https://api.github.com/repos/$repoSlug", token)
            if (repoCode == 401 || repoCode == 403) {
                return@withContext checkViaWebAndRawFiles(
                    repoSlug = repoSlug,
                    installedVersionName = installedVersionName,
                    installedVersionCode = installedVersionCode,
                    installedUpdateTimeMs = installedUpdateTimeMs
                ) ?: GitHubUpdateCheckResult(
                    isUpdateAvailable = false,
                    statusMessage = "GitHub Token rejected (HTTP $repoCode). Verify token permissions."
                )
            }
            if (repoCode == 404) {
                return@withContext GitHubUpdateCheckResult(
                    isUpdateAvailable = false,
                    statusMessage = "GitHub repository '$repoSlug' not found (HTTP 404). Check name and token access."
                )
            }
            if (repoCode !in 200..299 || repoBody.isBlank()) {
                return@withContext GitHubUpdateCheckResult(
                    isUpdateAvailable = false,
                    statusMessage = "Could not reach '$repoSlug' (HTTP $repoCode)."
                )
            }

            val repoJson = JSONObject(repoBody)
            val defaultBranch = repoJson.optString("default_branch", "main").ifBlank { "main" }
            val encodedBranch = URLEncoder.encode(defaultBranch, "UTF-8")

            // 1. Inspect GitHub Gradle build files
            var remoteCode: Long? = null
            var remoteName: String? = null
            for (path in listOf("app/build.gradle.kts", "build.gradle.kts")) {
                val (gCode, gBody) = githubHttpGet(
                    "https://api.github.com/repos/$repoSlug/contents/$path?ref=$encodedBranch",
                    token
                )
                if (gCode in 200..299 && gBody.isNotBlank()) {
                    val b64 = JSONObject(gBody).optString("content", "").replace("\n", "")
                    if (b64.isNotBlank()) {
                        val decoded = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                        if (decoded.contains("versionCode") || decoded.contains("versionName")) {
                            val parsed = extractVersionFromGradleContent(decoded)
                            remoteCode = parsed.first
                            remoteName = parsed.second
                            break
                        }
                    }
                }
            }

            // 2. Inspect latest commit on default branch
            var commitSha = ""
            var commitMsg = ""
            var commitTimeMs = 0L
            val (cCode, cBody) = githubHttpGet(
                "https://api.github.com/repos/$repoSlug/commits?sha=$encodedBranch&per_page=1",
                token
            )
            if (cCode in 200..299 && cBody.isNotBlank()) {
                val trimmedBody = cBody.trimStart()
                val first = when {
                    trimmedBody.startsWith("[") -> JSONArray(trimmedBody).optJSONObject(0)
                    trimmedBody.startsWith("{") -> JSONObject(trimmedBody)
                    else -> null
                }
                if (first != null) {
                    commitSha = first.optString("sha", "").take(7)
                    val commitObj = first.optJSONObject("commit")
                    commitMsg = commitObj?.optString("message", "")
                        ?.lineSequence()?.firstOrNull()?.trim().orEmpty()
                    val dateStr = commitObj?.optJSONObject("committer")?.optString("date", "").orEmpty()
                    commitTimeMs = parseIsoMillis(dateStr)
                }
            }

            // 3. Check latest GitHub release & APK assets
            var releaseTag = ""
            var releaseApkUrl = ""
            var releaseTimeMs = 0L
            var releaseObj: JSONObject? = null
            val (relCode, relBody) = githubHttpGet("https://api.github.com/repos/$repoSlug/releases/latest", token)
            if (relCode in 200..299 && relBody.isNotBlank()) {
                releaseObj = JSONObject(relBody)
            } else {
                val (listCode, listBody) = githubHttpGet("https://api.github.com/repos/$repoSlug/releases?per_page=1", token)
                if (listCode in 200..299 && listBody.isNotBlank()) {
                    val arr = JSONArray(listBody)
                    if (arr.length() > 0) releaseObj = arr.optJSONObject(0)
                }
            }
            if (releaseObj != null) {
                releaseTag = releaseObj.optString("tag_name", "").trim()
                releaseTimeMs = parseIsoMillis(releaseObj.optString("published_at", ""))
                val assets = releaseObj.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        val name = a.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            releaseApkUrl = a.optString("browser_download_url", "")
                            break
                        }
                    }
                }
            }

            // 4. Also check root directory files for a committed .apk if no release APK exists
            if (releaseApkUrl.isBlank()) {
                val (treeCode, treeBody) = githubHttpGet(
                    "https://api.github.com/repos/$repoSlug/contents?ref=$encodedBranch",
                    token
                )
                if (treeCode in 200..299 && treeBody.isNotBlank() && treeBody.trimStart().startsWith("[")) {
                    val items = JSONArray(treeBody)
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val name = item.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            releaseApkUrl = item.optString("download_url", "")
                            break
                        }
                    }
                }
            }

            evaluateUpdateResult(
                repoSlug = repoSlug,
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode,
                installedUpdateTimeMs = installedUpdateTimeMs,
                remoteVersionCode = remoteCode,
                remoteVersionName = remoteName,
                commitSha = commitSha,
                commitMsg = commitMsg,
                commitTimeMs = commitTimeMs,
                releaseTag = releaseTag,
                releaseTimeMs = releaseTimeMs,
                apkUrl = releaseApkUrl
            )
        } catch (e: Exception) {
            GitHubUpdateCheckResult(
                isUpdateAvailable = false,
                statusMessage = "Update check failed: ${e.localizedMessage ?: "Network error"}"
            )
        }
    }

    private fun checkViaWebAndRawFiles(
        repoSlug: String,
        installedVersionName: String,
        installedVersionCode: Long,
        installedUpdateTimeMs: Long
    ): GitHubUpdateCheckResult? {
        // 1. Check Releases page redirect + expanded_assets (zero API rate limit)
        var detectedTag = ""
        var detectedApkUrl = ""
        var releaseTimeMs = 0L

        val (latestCode, latestFinalUrl, latestHtml) = webHttpGet("https://github.com/$repoSlug/releases/latest")
        if (latestCode in 200..299) {
            if (latestFinalUrl.contains("/releases/tag/")) {
                detectedTag = latestFinalUrl
                    .substringAfter("/releases/tag/")
                    .substringBefore("?")
                    .substringBefore("#")
                    .trim()
            }
            detectedApkUrl = extractApkLinkFromHtml(latestHtml, repoSlug)
            if (detectedApkUrl.isBlank() && detectedTag.isNotBlank()) {
                val encodedTag = URLEncoder.encode(detectedTag, "UTF-8")
                val (assetsCode, _, assetsHtml) = webHttpGet(
                    "https://github.com/$repoSlug/releases/expanded_assets/$encodedTag"
                )
                if (assetsCode in 200..299) {
                    detectedApkUrl = extractApkLinkFromHtml(assetsHtml, repoSlug)
                }
            }
        }

        val (atomCode, _, atomXml) = webHttpGet("https://github.com/$repoSlug/releases.atom")
        if (atomCode in 200..299 && atomXml.contains("<entry>")) {
            val firstEntry = atomXml.substringAfter("<entry>").substringBefore("</entry>")
            val tagHref = Regex("""href=["'][^"']+/releases/tag/([^"']+)["']""")
                .find(firstEntry)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val titleTag = Regex("""<title[^>]*>([^<]+)</title>""")
                .find(firstEntry)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val updatedStr = Regex("""<updated>([^<]+)</updated>""")
                .find(firstEntry)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            releaseTimeMs = parseIsoMillis(updatedStr)
            if (detectedTag.isBlank()) {
                detectedTag = tagHref.ifBlank { titleTag }
            }
            if (detectedApkUrl.isBlank() && tagHref.isNotBlank()) {
                val encodedTag = URLEncoder.encode(tagHref, "UTF-8")
                val (assetsCode, _, assetsHtml) = webHttpGet(
                    "https://github.com/$repoSlug/releases/expanded_assets/$encodedTag"
                )
                if (assetsCode in 200..299) {
                    detectedApkUrl = extractApkLinkFromHtml(assetsHtml, repoSlug)
                }
            }
        }

        // 2. Check actual GitHub files (app/build.gradle.kts or build.gradle.kts)
        var remoteCode: Long? = null
        var remoteName: String? = null
        var activeBranch = ""
        val candidateGradleUrls = listOf(
            "main" to "https://raw.githubusercontent.com/$repoSlug/main/app/build.gradle.kts",
            "master" to "https://raw.githubusercontent.com/$repoSlug/master/app/build.gradle.kts",
            "main" to "https://raw.githubusercontent.com/$repoSlug/main/build.gradle.kts",
            "master" to "https://raw.githubusercontent.com/$repoSlug/master/build.gradle.kts"
        )
        for ((branch, rawUrl) in candidateGradleUrls) {
            val (rCode, _, rBody) = webHttpGet(rawUrl)
            if (rCode in 200..299 && (rBody.contains("versionCode") || rBody.contains("versionName"))) {
                val parsed = extractVersionFromGradleContent(rBody)
                remoteCode = parsed.first
                remoteName = parsed.second
                activeBranch = branch
                break
            }
        }

        // 3. Check latest commit in commits.atom (exact GitHub push time & commit info)
        var commitSha = ""
        var commitMsg = ""
        var commitTimeMs = 0L
        val commitAtomUrl = if (activeBranch.isNotBlank()) {
            "https://github.com/$repoSlug/commits/$activeBranch.atom"
        } else {
            "https://github.com/$repoSlug/commits.atom"
        }
        val (commitsCode, _, commitsXml) = webHttpGet(commitAtomUrl)
        if (commitsCode in 200..299 && commitsXml.contains("<entry>")) {
            val entry = commitsXml.substringAfter("<entry>").substringBefore("</entry>")
            commitMsg = Regex("""<title[^>]*>([^<]+)</title>""")
                .find(entry)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            commitSha = Regex("""/commit/([a-fA-F0-9]{7,40})""")
                .find(entry)?.groupValues?.getOrNull(1)?.take(7).orEmpty()
            val updatedStr = Regex("""<updated>([^<]+)</updated>""")
                .find(entry)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            commitTimeMs = parseIsoMillis(updatedStr)
        }

        // 4. Also check main repo page HTML for any committed .apk link if none found in releases
        var repoHtmlCode = 0
        if (detectedApkUrl.isBlank()) {
            val (homeCode, _, homeHtml) = webHttpGet("https://github.com/$repoSlug")
            repoHtmlCode = homeCode
            if (homeCode in 200..299) {
                val treeApkRegex = Regex(
                    """href=["']/${Regex.escape(repoSlug)}/blob/([^/"']+/[^"']+\.apk)["']""",
                    RegexOption.IGNORE_CASE
                )
                val blobRel = treeApkRegex.find(homeHtml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                if (blobRel.isNotBlank()) {
                    detectedApkUrl = "https://github.com/$repoSlug/raw/$blobRel"
                }
            }
        }

        val reachable = (latestCode in 200..299) ||
            (atomCode in 200..299) ||
            (commitsCode in 200..299) ||
            (repoHtmlCode in 200..299) ||
            remoteCode != null ||
            !remoteName.isNullOrBlank()

        if (!reachable) return null

        return evaluateUpdateResult(
            repoSlug = repoSlug,
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode,
            installedUpdateTimeMs = installedUpdateTimeMs,
            remoteVersionCode = remoteCode,
            remoteVersionName = remoteName,
            commitSha = commitSha,
            commitMsg = commitMsg,
            commitTimeMs = commitTimeMs,
            releaseTag = detectedTag,
            releaseTimeMs = releaseTimeMs,
            apkUrl = detectedApkUrl
        )
    }

    private fun evaluateUpdateResult(
        repoSlug: String,
        installedVersionName: String,
        installedVersionCode: Long,
        installedUpdateTimeMs: Long,
        remoteVersionCode: Long?,
        remoteVersionName: String?,
        commitSha: String,
        commitMsg: String,
        commitTimeMs: Long,
        releaseTag: String,
        releaseTimeMs: Long,
        apkUrl: String
    ): GitHubUpdateCheckResult {
        val normalizedTag = releaseTag.removePrefix("v").removePrefix("V").trim()
        val hasNewerReleaseTag = normalizedTag.isNotBlank() &&
            isVersionNewer(normalizedTag, installedVersionName)
        val hasNewerGradleVersion = (remoteVersionCode != null && remoteVersionCode > installedVersionCode) ||
            (!remoteVersionName.isNullOrBlank() && isVersionNewer(remoteVersionName, installedVersionName))
        val hasNewerCommitFiles = commitTimeMs > 0L && installedUpdateTimeMs > 0L &&
            commitTimeMs > (installedUpdateTimeMs + 60_000L)
        val hasNewerReleaseTime = releaseTimeMs > 0L && installedUpdateTimeMs > 0L &&
            releaseTimeMs > (installedUpdateTimeMs + 60_000L)

        val updateAvailable = hasNewerReleaseTag || hasNewerGradleVersion || hasNewerCommitFiles || hasNewerReleaseTime

        val label = when {
            hasNewerReleaseTag -> releaseTag
            remoteVersionCode != null && remoteVersionCode > installedVersionCode && !remoteVersionName.isNullOrBlank() ->
                "v$remoteVersionName (build $remoteVersionCode)"
            !remoteVersionName.isNullOrBlank() && isVersionNewer(remoteVersionName, installedVersionName) ->
                "v$remoteVersionName"
            hasNewerCommitFiles && commitSha.isNotBlank() ->
                "Commit $commitSha"
            releaseTag.isNotBlank() -> releaseTag
            !remoteVersionName.isNullOrBlank() -> "v$remoteVersionName"
            commitSha.isNotBlank() -> commitSha
            else -> ""
        }

        val fileVersionSummary = if (!remoteVersionName.isNullOrBlank() || remoteVersionCode != null) {
            val vn = remoteVersionName?.let { "v$it" } ?: "v?"
            val vc = remoteVersionCode?.let { " (build $it)" } ?: ""
            "GitHub build.gradle.kts: $vn$vc."
        } else ""

        val commitSummary = if (commitSha.isNotBlank() || commitMsg.isNotBlank()) {
            "Latest commit ${commitSha.ifBlank { "" }} ${if (commitMsg.isNotBlank()) "(\"${commitMsg.take(55)}\")" else ""}."
        } else ""

        val status = if (updateAvailable) {
            buildString {
                append("Update available on '$repoSlug'")
                if (label.isNotBlank()) append(" ($label)")
                append("! Installed: v$installedVersionName (build $installedVersionCode). ")
                if (fileVersionSummary.isNotBlank()) append("$fileVersionSummary ")
                if (commitSummary.isNotBlank()) append("$commitSummary ")
                if (apkUrl.isNotBlank()) {
                    append("APK download is ready below.")
                } else {
                    append("GitHub files are newer than your installed app — export or build the new APK to install.")
                }
            }.trim()
        } else {
            buildString {
                append("Your app is up to date (installed v$installedVersionName, build $installedVersionCode). ")
                if (fileVersionSummary.isNotBlank()) append("$fileVersionSummary ")
                if (commitSummary.isNotBlank()) append(commitSummary)
            }.trim()
        }

        return GitHubUpdateCheckResult(
            isUpdateAvailable = updateAvailable,
            updateLabel = label,
            statusMessage = status,
            apkDownloadUrl = apkUrl.ifBlank { null },
            releasesPageUrl = "https://github.com/$repoSlug/releases",
            latestCommitSha = commitSha
        )
    }

    private fun isVersionNewer(candidate: String, current: String): Boolean {
        val cClean = candidate.removePrefix("v").removePrefix("V").trim()
        val iClean = current.removePrefix("v").removePrefix("V").trim()
        if (cClean.isBlank() || cClean == iClean) return false

        val cParts = cClean.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        val iParts = iClean.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        if (cParts.isEmpty() || iParts.isEmpty()) return cClean != iClean

        val maxLen = maxOf(cParts.size, iParts.size)
        for (idx in 0 until maxLen) {
            val cVal = cParts.getOrElse(idx) { 0 }
            val iVal = iParts.getOrElse(idx) { 0 }
            if (cVal > iVal) return true
            if (cVal < iVal) return false
        }
        return false
    }

    private fun parseIsoMillis(isoText: String): Long {
        if (isoText.isBlank()) return 0L
        return try {
            Instant.parse(isoText.trim()).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    private fun githubHttpGet(urlString: String, token: String): Pair<Int, String> {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ElysiumTraining-Android/1.0")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (token.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${token.trim()}")
            }
            connectTimeout = 10000
            readTimeout = 10000
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to body
        } finally {
            conn.disconnect()
        }
    }

    private fun webHttpGet(urlString: String): Triple<Int, String, String> {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            connectTimeout = 10000
            readTimeout = 10000
        }
        return try {
            val code = conn.responseCode
            val finalUrl = conn.url?.toString().orEmpty()
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Triple(code, finalUrl, body)
        } finally {
            conn.disconnect()
        }
    }

    private fun extractVersionFromGradleContent(gradleText: String): Pair<Long?, String?> {
        val codeMatch = Regex("""versionCode\s*=?\s*(\d+)""").find(gradleText)
        val nameMatch = Regex("""versionName\s*=?\s*["']([^"']+)["']""").find(gradleText)
        val remoteCode = codeMatch?.groupValues?.getOrNull(1)?.toLongOrNull()
        val remoteName = nameMatch?.groupValues?.getOrNull(1)?.trim()
        return remoteCode to remoteName
    }

    private fun extractApkLinkFromHtml(html: String, repoSlug: String): String {
        if (html.isBlank()) return ""
        val regex = Regex(
            """href=["']((?:https://github\.com)?/${Regex.escape(repoSlug)}/releases/download/[^"']+\.apk)["']""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (match.isNotBlank()) {
            return if (match.startsWith("http")) match else "https://github.com$match"
        }
        val genericRegex = Regex(
            """href=["']((?:https://github\.com)?/[^"']+/releases/download/[^"']+\.apk)["']""",
            RegexOption.IGNORE_CASE
        )
        val genericMatch = genericRegex.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        return when {
            genericMatch.startsWith("http") -> genericMatch
            genericMatch.isNotBlank() -> "https://github.com$genericMatch"
            else -> ""
        }
    }
}
