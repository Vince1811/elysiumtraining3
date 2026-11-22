package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class R2ObjectMeta(
    val key: String,
    val etag: String,
    val size: Long,
    val lastModified: String = ""
)

data class R2Credentials(
    val accountId: String,
    val bucketName: String,
    val accessKeyId: String,
    val secretAccessKey: String
) {
    val isComplete: Boolean
        get() = accountId.isNotBlank() &&
            bucketName.isNotBlank() &&
            accessKeyId.isNotBlank() &&
            secretAccessKey.isNotBlank()
}

data class R2TestResult(
    val success: Boolean,
    val bucketName: String = "",
    val totalObjects: Int = 0,
    val progressPhotosCount: Int = 0,
    val avatarsCount: Int = 0,
    val exerciseGuidesCount: Int = 0,
    val readWriteVerified: Boolean = false,
    val latencyMs: Long = 0L,
    val statusMessage: String = ""
)

class CloudflareR2Service(val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private val r2Mutex = Mutex()
        val r2SyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private const val REGION = "auto"
        private const val SERVICE = "s3"
        private const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        fun sanitizeKeySlug(raw: String): String {
            return raw.lowercase(Locale.US)
                .trim()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "item" }
        }

        fun getPicturesCacheDir(context: Context): File {
            val dir = File(context.filesDir, "r2_pictures")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        fun getLocalGuidePictureFile(context: Context, guideId: String): File? {
            val dir = getPicturesCacheDir(context)
            val file = File(dir, "guide_${sanitizeKeySlug(guideId)}.img")
            return if (file.exists() && file.length() > 0L) file else null
        }

        fun getLocalExercisePictureFile(context: Context, exerciseName: String): File? {
            val dir = getPicturesCacheDir(context)
            val file = File(dir, "exercise_${sanitizeKeySlug(exerciseName)}.img")
            return if (file.exists() && file.length() > 0L) file else null
        }

        /**
         * Matches a filename, description, or R2 object key to its canonical exercise guide ID.
         */
        fun matchGuideIdFromDescription(rawText: String): String? {
            val trimmed = rawText.trim()
            if (trimmed.isBlank()) return null
            if (trimmed.startsWith("avatars/", ignoreCase = true)) return null

            val base = trimmed.substringAfterLast('/').substringBeforeLast('.')
            val norm = base.lowercase(Locale.US)
                .replace('_', ' ')
                .replace('-', ' ')
                .replace(Regex("[^a-z0-9 ]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            // Direct ID/slug match first
            val slug = sanitizeKeySlug(base)
            val knownGuideIds = setOf(
                "incline_bench_barbell", "incline_bench",
                "decline_bench_barbell", "decline_bench_dumbbell",
                "barbell_bench_press", "bench_press",
                "chest_fly_dumbbell", "pec_deck", "cable_crossover", "cable_crossover_high_to_low", "cable_fly",
                "chest_dips", "weighted_chest_dips", "landmine_press",
                "pushup", "wide_grip_pushup", "incline_pushup", "decline_pushup",
                "bicep_curl", "bicep_21s", "hammer_curl", "concentration_curl",
                "lat_pulldown", "cable_row", "bent_over_barbell_row", "overhead_press", "lateral_raise",
                "tricep_pushdown", "tricep_rope_pushdown", "tricep_dip", "squat", "deadlift", "lunge",
                "leg_press", "leg_extension", "hamstring_curl", "calf_raise", "knee_raise", "plank",
                "pullup", "bodyweight_squat", "arnold_press",
                "mountain_climber", "bicycle_crunch",
                "shadow_boxing_1_2", "shadow_boxing_1_2_3",
                "shadow_boxing_slip", "shadow_boxing_footwork", "shadow_boxing_burnout"
            )
            if (slug in knownGuideIds) return slug

            val has = { token: String -> norm.contains(token) }
            val hasWord = { word: String -> Regex("\\b$word\\b").containsMatchIn(norm) }

            return when {
                has("21s") || (hasWord("21") && (has("bicep") || has("curl") || has("finisher") || has("barbell") || has("ez"))) -> "bicep_21s"
                has("concentration") && has("curl") -> "concentration_curl"
                has("hammer") && has("curl") -> "hammer_curl"
                (has("bicep") && has("curl") && !has("dumbbell") && !has("cable") && !has("preacher") && !has("spider") && !has("reverse") && !has("ez")) ||
                    (has("barbell") && has("curl") && !has("reverse") && !has("wrist") && !has("drag") && !has("cheat") && !has("spider")) -> "bicep_curl"
                has("incline") && has("bench") && has("barbell") -> "incline_bench_barbell"
                has("incline") && (has("dumbbell") || has("bench") || has("press")) && !has("fly") && !has("curl") && !has("row") && !has("leg") && !has("shoulder") -> "incline_bench"
                has("decline") && (has("dumbbell") || hasWord("db")) && has("bench") -> "decline_bench_dumbbell"
                has("decline") && has("bench") -> "decline_bench_barbell"
                has("barbell") && has("bench") -> "barbell_bench_press"
                has("flat") && has("dumbbell") && (has("bench") || has("press")) -> "bench_press"
                has("bench") && has("press") && has("dumbbell") -> "bench_press"
                has("bench") && has("press") -> "barbell_bench_press"
                has("cable") && (has("crossover") || has("fly") || has("flies")) && has("high to low") && !has("rear") && !has("reverse") -> "cable_crossover_high_to_low"
                has("cable") && has("crossover") && !has("rear") && !has("reverse") -> "cable_crossover"
                has("cable") && (has("fly") || has("flies")) && !has("rear") && !has("reverse") -> "cable_fly"
                (has("chest") || has("dumbbell") || has("floor") || has("flat")) && !has("rear") && !has("reverse") && !has("cable") && (has("fly") || has("flys") || has("flyes") || has("flies")) -> "chest_fly_dumbbell"
                has("pec") && has("deck") -> "pec_deck"
                has("weighted") && has("chest") && has("dip") -> "weighted_chest_dips"
                has("chest") && has("dip") -> "chest_dips"
                has("landmine") -> "landmine_press"
                has("wide") && (has("push up") || has("pushup") || has("push ups")) -> "wide_grip_pushup"
                has("decline") && has("push") -> "decline_pushup"
                has("incline") && has("push") -> "incline_pushup"
                has("push up") || has("pushup") || has("push ups") -> "pushup"
                has("pull up") || has("pullup") || has("pull ups") -> "pullup"
                has("lat") && (has("pulldown") || has("pull down")) -> "lat_pulldown"
                ((has("bent over") || has("barbell")) && has("row") && !has("dumbbell") && !has("cable") && !has("upright") && !has("inverted") && !has("band") && !has("smith") && !has("chest supported") && !has("rear delt")) -> "bent_over_barbell_row"
                has("cable") && has("row") -> "cable_row"
                has("arnold") -> "arnold_press"
                has("overhead") && has("press") -> "overhead_press"
                has("lateral") && has("raise") -> "lateral_raise"
                has("rope") && (has("pushdown") || has("push down") || has("pressdown") || has("press down")) -> "tricep_rope_pushdown"
                (has("tricep") && (has("pushdown") || has("push down") || has("pressdown"))) || has("pushdown") -> "tricep_pushdown"
                has("tricep") && has("dip") -> "tricep_dip"
                has("bodyweight") && has("squat") -> "bodyweight_squat"
                has("squat") -> "squat"
                has("deadlift") || has("rdl") -> "deadlift"
                has("lunge") -> "lunge"
                has("leg") && has("press") && !has("calf") -> "leg_press"
                (has("leg") && has("extension")) || (has("quad") && has("extension")) -> "leg_extension"
                (has("hamstring") || has("leg")) && has("curl") -> "hamstring_curl"
                has("calf") && has("raise") -> "calf_raise"
                has("knee") && has("raise") -> "knee_raise"
                has("plank") -> "plank"
                has("mountain") && has("climber") -> "mountain_climber"
                has("bicycle") && has("crunch") -> "bicycle_crunch"
                else -> null
            }
        }

        private val SHARED_FALLBACK_ONLY_GUIDES = setOf(
            "incline_bench_barbell",
            "decline_bench_barbell",
            "decline_bench_dumbbell",
            "barbell_bench_press",
            "bench_press",
            "chest_fly_dumbbell",
            "pec_deck",
            "cable_crossover",
            "cable_crossover_high_to_low",
            "cable_fly",
            "chest_dips",
            "weighted_chest_dips",
            "landmine_press",
            "wide_grip_pushup",
            "incline_pushup",
            "decline_pushup",
            "concentration_curl",
            "bicep_curl",
            "bicep_21s",
            "tricep_rope_pushdown",
            "bent_over_barbell_row",
            "leg_extension",
            "hamstring_curl",
            "shadow_boxing_1_2",
            "shadow_boxing_1_2_3",
            "shadow_boxing_slip",
            "shadow_boxing_footwork",
            "shadow_boxing_burnout"
        )

        fun hasUniqueBundledGuide(guideId: String?, drawableResId: Int): Boolean {
            return !guideId.isNullOrBlank() && drawableResId != 0 && guideId !in SHARED_FALLBACK_ONLY_GUIDES
        }
    }

    fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveSecret(input: String, buildConfigVal: String, placeholder: String): String {
        val t1 = input.trim()
        if (t1.isNotEmpty() && t1 != placeholder) return t1
        val t2 = buildConfigVal.trim()
        return if (t2.isNotEmpty() && t2 != placeholder) t2 else ""
    }

    suspend fun getCredentials(preferencesManager: PreferencesManager): R2Credentials {
        val acc = resolveSecret(
            preferencesManager.cfAccountFlow.first(),
            BuildConfig.CLOUDFLARE_ACCOUNT_ID,
            "CLOUDFLARE_ACCOUNT_ID"
        )
        val bucket = resolveSecret(
            preferencesManager.cfR2BucketFlow.first(),
            BuildConfig.CLOUDFLARE_R2_BUCKET_NAME,
            "CLOUDFLARE_R2_BUCKET_NAME"
        )
        val accessKey = resolveSecret(
            preferencesManager.cfR2AccessKeyFlow.first(),
            BuildConfig.CLOUDFLARE_R2_ACCESS_KEY_ID,
            "CLOUDFLARE_R2_ACCESS_KEY_ID"
        )
        val secretKey = resolveSecret(
            preferencesManager.cfR2SecretKeyFlow.first(),
            BuildConfig.CLOUDFLARE_R2_SECRET_ACCESS_KEY,
            "CLOUDFLARE_R2_SECRET_ACCESS_KEY"
        )
        return R2Credentials(acc, bucket, accessKey, secretKey)
    }

    suspend fun hasR2Credentials(preferencesManager: PreferencesManager): Boolean {
        return getCredentials(preferencesManager).isComplete
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun hmacSha256Hex(key: ByteArray, data: String): String {
        return hmacSha256(key, data).joinToString("") { "%02x".format(it) }
    }

    private fun encodeRfc3986(value: String, keepSlash: Boolean = false): String {
        val encoded = URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
        return if (keepSlash) encoded.replace("%2F", "/") else encoded
    }

    private fun buildSignedRequest(
        creds: R2Credentials,
        method: String,
        objectKey: String = "",
        queryParams: List<Pair<String, String>> = emptyList(),
        payloadBytes: ByteArray? = null,
        contentType: String? = null
    ): Request {
        val host = "${creds.accountId}.r2.cloudflarestorage.com"
        val now = Date()
        val amzDateFmt = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dateStampFmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val amzDate = amzDateFmt.format(now)
        val dateStamp = dateStampFmt.format(now)

        val cleanKey = objectKey.trimStart('/')
        val canonicalPath = if (cleanKey.isBlank()) {
            "/${encodeRfc3986(creds.bucketName)}"
        } else {
            "/${encodeRfc3986(creds.bucketName)}/${encodeRfc3986(cleanKey, keepSlash = true)}"
        }

        val sortedQuery = queryParams
            .map { encodeRfc3986(it.first) to encodeRfc3986(it.second) }
            .sortedBy { it.first }
        val canonicalQuery = sortedQuery.joinToString("&") { "${it.first}=${it.second}" }

        val payloadHash = if (payloadBytes != null) sha256Hex(payloadBytes) else EMPTY_SHA256

        val headerPairs = mutableListOf(
            "host" to host,
            "x-amz-content-sha256" to payloadHash,
            "x-amz-date" to amzDate
        )
        if (!contentType.isNullOrBlank()) {
            headerPairs.add("content-type" to contentType.trim())
        }
        headerPairs.sortBy { it.first }

        val canonicalHeaders = headerPairs.joinToString("\n") { "${it.first}:${it.second}" } + "\n"
        val signedHeaders = headerPairs.joinToString(";") { it.first }

        val canonicalRequest = listOf(
            method.uppercase(Locale.US),
            canonicalPath,
            canonicalQuery,
            canonicalHeaders,
            signedHeaders,
            payloadHash
        ).joinToString("\n")

        val credentialScope = "$dateStamp/$REGION/$SERVICE/aws4_request"
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzDate,
            credentialScope,
            sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))
        ).joinToString("\n")

        val kDate = hmacSha256(("AWS4" + creds.secretAccessKey).toByteArray(Charsets.UTF_8), dateStamp)
        val kRegion = hmacSha256(kDate, REGION)
        val kService = hmacSha256(kRegion, SERVICE)
        val kSigning = hmacSha256(kService, "aws4_request")
        val signature = hmacSha256Hex(kSigning, stringToSign)

        val authorizationHeader =
            "AWS4-HMAC-SHA256 Credential=${creds.accessKeyId}/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        val url = buildString {
            append("https://")
            append(host)
            append(canonicalPath)
            if (canonicalQuery.isNotEmpty()) {
                append("?")
                append(canonicalQuery)
            }
        }

        val builder = Request.Builder()
            .url(url)
            .addHeader("Host", host)
            .addHeader("x-amz-date", amzDate)
            .addHeader("x-amz-content-sha256", payloadHash)
            .addHeader("Authorization", authorizationHeader)

        if (!contentType.isNullOrBlank()) {
            builder.addHeader("Content-Type", contentType)
        }

        when (method.uppercase(Locale.US)) {
            "PUT" -> {
                val media = (contentType ?: "application/octet-stream").toMediaType()
                val body = (payloadBytes ?: ByteArray(0)).toRequestBody(media)
                builder.put(body)
            }
            "DELETE" -> builder.delete()
            else -> builder.get()
        }
        return builder.build()
    }

    private fun putObject(
        creds: R2Credentials,
        objectKey: String,
        bytes: ByteArray,
        contentType: String
    ): Pair<Boolean, String?> {
        return try {
            val req = buildSignedRequest(
                creds = creds,
                method = "PUT",
                objectKey = objectKey,
                payloadBytes = bytes,
                contentType = contentType
            )
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val etag = resp.header("ETag")?.trim('"')
                    true to etag
                } else {
                    val err = resp.body?.string()?.take(200)
                    val msg = if (resp.code == 403) {
                        "R2 403 Forbidden: Verify Cloudflare R2 Bucket Name and S3 Access/Secret Keys in AI Studio Secrets."
                    } else {
                        "HTTP ${resp.code}: ${err ?: resp.message}"
                    }
                    false to msg
                }
            }
        } catch (e: Exception) {
            false to (e.localizedMessage ?: "R2 upload failed")
        }
    }

    private fun getObject(creds: R2Credentials, objectKey: String): Triple<Boolean, ByteArray?, String?> {
        return try {
            val req = buildSignedRequest(
                creds = creds,
                method = "GET",
                objectKey = objectKey
            )
            client.newCall(req).execute().use { resp ->
                when {
                    resp.code == 404 -> Triple(true, null, null)
                    resp.isSuccessful -> {
                        val bytes = resp.body?.bytes()
                        val etag = resp.header("ETag")?.trim('"')
                        Triple(true, bytes, etag)
                    }
                    else -> {
                        val msg = if (resp.code == 403) {
                            "R2 HTTP 403 Forbidden: Check Account ID, R2 Bucket Name, and S3 Access/Secret Keys in AI Studio Secrets."
                        } else {
                            "HTTP ${resp.code}: ${resp.message}"
                        }
                        Triple(false, null, msg)
                    }
                }
            }
        } catch (e: Exception) {
            Triple(false, null, e.localizedMessage ?: "R2 download failed")
        }
    }

    private fun deleteObject(creds: R2Credentials, objectKey: String): Boolean {
        return try {
            val req = buildSignedRequest(
                creds = creds,
                method = "DELETE",
                objectKey = objectKey
            )
            client.newCall(req).execute().use { resp ->
                resp.isSuccessful || resp.code == 204 || resp.code == 404
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun listObjects(creds: R2Credentials, prefix: String = ""): Pair<Boolean, List<R2ObjectMeta>> {
        return try {
            val qp = mutableListOf("list-type" to "2")
            if (prefix.isNotBlank()) {
                qp.add("prefix" to prefix)
            }
            val req = buildSignedRequest(
                creds = creds,
                method = "GET",
                queryParams = qp
            )
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false to emptyList()
                val xml = resp.body?.string().orEmpty()
                if (xml.isBlank()) return true to emptyList()
                true to parseListObjectsXml(xml)
            }
        } catch (_: Exception) {
            false to emptyList()
        }
    }

    private fun parseListObjectsXml(xml: String): List<R2ObjectMeta> {
        val result = mutableListOf<R2ObjectMeta>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            var inContents = false
            var currentTag = ""
            var key = ""
            var etag = ""
            var size = 0L
            var lastModified = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        if (currentTag == "Contents") {
                            inContents = true
                            key = ""
                            etag = ""
                            size = 0L
                            lastModified = ""
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inContents) {
                            val txt = parser.text.orEmpty()
                            when (currentTag) {
                                "Key" -> key += txt
                                "ETag" -> etag += txt
                                "Size" -> size = txt.trim().toLongOrNull() ?: 0L
                                "LastModified" -> lastModified += txt
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "Contents") {
                            if (key.isNotBlank()) {
                                result.add(R2ObjectMeta(key.trim(), etag.trim().trim('"'), size, lastModified.trim()))
                            }
                            inContents = false
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun saveCachedImage(destFile: File, bytes: ByteArray, etag: String?) {
        destFile.parentFile?.mkdirs()
        destFile.writeBytes(bytes)
        if (!etag.isNullOrBlank()) {
            File(destFile.absolutePath + ".etag").writeText(etag)
        }
    }

    private fun cachedEtag(destFile: File): String {
        val f = File(destFile.absolutePath + ".etag")
        return if (f.exists()) f.readText().trim() else ""
    }

    private fun avatarR2Key(userId: Int, username: String): String {
        val slug = if (username.isNotBlank()) sanitizeKeySlug(username) else "user_$userId"
        return "avatars/$slug.jpg"
    }

    suspend fun uploadUserProfilePicture(
        userId: Int,
        username: String,
        localFile: File,
        preferencesManager: PreferencesManager
    ): Boolean = withContext(Dispatchers.IO) {
        if (!localFile.exists() || localFile.length() == 0L) return@withContext false
        val creds = getCredentials(preferencesManager)
        if (!creds.isComplete) {
            preferencesManager.saveLastR2Error("R2 API keys not configured")
            preferencesManager.setPendingR2Upload(true)
            return@withContext false
        }
        if (!isNetworkAvailable()) {
            preferencesManager.saveLastR2Error("Offline — profile picture queued for R2")
            preferencesManager.setPendingR2Upload(true)
            return@withContext false
        }

        val key = avatarR2Key(userId, username)
        val (ok, errOrEtag) = putObject(creds, key, localFile.readBytes(), "image/jpeg")
        if (ok) {
            if (!errOrEtag.isNullOrBlank()) {
                File(localFile.absolutePath + ".etag").writeText(errOrEtag)
            }
            preferencesManager.setPendingR2Upload(false)
            preferencesManager.saveLastR2SyncTime(System.currentTimeMillis())
            preferencesManager.saveLastR2Error("")
            true
        } else {
            preferencesManager.setPendingR2Upload(true)
            preferencesManager.saveLastR2Error(errOrEtag ?: "R2 avatar upload failed")
            false
        }
    }

    suspend fun deleteUserProfilePicture(
        userId: Int,
        username: String,
        preferencesManager: PreferencesManager
    ): Boolean = withContext(Dispatchers.IO) {
        val creds = getCredentials(preferencesManager)
        if (!creds.isComplete || !isNetworkAvailable()) return@withContext false
        val key = avatarR2Key(userId, username)
        deleteObject(creds, key)
    }

    private fun progressPhotoR2Key(userId: Int, username: String, timestamp: Long, existingKey: String = ""): String {
        if (existingKey.isNotBlank()) return existingKey
        val slug = if (username.isNotBlank()) sanitizeKeySlug(username) else "user_$userId"
        return "progress_photos/$slug/$timestamp.jpg"
    }

    private fun extractBasicFileDetails(file: File): String {
        return try {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
            val dims = if (opts.outWidth > 0 && opts.outHeight > 0) "${opts.outWidth}×${opts.outHeight} px" else ""
            val kb = (file.length() / 1024L).coerceAtLeast(1L)
            val sizeStr = if (kb >= 1024L) String.format(Locale.US, "%.1f MB", kb / 1024.0) else "$kb KB"
            var cameraLabel = ""
            try {
                val exif = android.media.ExifInterface(file.absolutePath)
                val make = exif.getAttribute(android.media.ExifInterface.TAG_MAKE)?.trim().orEmpty()
                val model = exif.getAttribute(android.media.ExifInterface.TAG_MODEL)?.trim().orEmpty()
                cameraLabel = when {
                    model.isNotBlank() && make.isNotBlank() && !model.contains(make, ignoreCase = true) -> "$make $model"
                    model.isNotBlank() -> model
                    make.isNotBlank() -> make
                    else -> ""
                }
            } catch (_: Exception) {
            }
            listOf(dims, sizeStr, cameraLabel).filter { it.isNotBlank() }.joinToString(" • ")
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun uploadProgressPhoto(
        photo: ProgressPhoto,
        preferencesManager: PreferencesManager,
        username: String = ""
    ): String? = withContext(Dispatchers.IO) {
        val file = File(photo.photoPath)
        if (!file.exists() || file.length() == 0L) return@withContext null
        val creds = getCredentials(preferencesManager)
        if (!creds.isComplete) {
            preferencesManager.setPendingR2Upload(true)
            preferencesManager.saveLastR2Error("R2 optional: add R2 bucket keys in Secrets for cloud backup")
            return@withContext null
        }
        if (!isNetworkAvailable()) {
            preferencesManager.setPendingR2Upload(true)
            preferencesManager.saveLastR2Error("Offline — photo queued for R2")
            return@withContext null
        }

        val r2Key = progressPhotoR2Key(photo.userId, username, photo.timestamp, photo.r2Key)
        val (ok, errOrEtag) = putObject(creds, r2Key, file.readBytes(), "image/jpeg")
        if (ok) {
            if (!errOrEtag.isNullOrBlank()) {
                File(file.absolutePath + ".etag").writeText(errOrEtag)
            }
            preferencesManager.setPendingR2Upload(false)
            preferencesManager.saveLastR2SyncTime(System.currentTimeMillis())
            preferencesManager.saveLastR2Error("")
            r2Key
        } else {
            preferencesManager.setPendingR2Upload(true)
            preferencesManager.saveLastR2Error(errOrEtag ?: "R2 progress photo upload failed")
            null
        }
    }

    suspend fun deleteProgressPhoto(
        photo: ProgressPhoto,
        preferencesManager: PreferencesManager,
        username: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val creds = getCredentials(preferencesManager)
        if (!creds.isComplete || !isNetworkAvailable()) return@withContext false
        val r2Key = progressPhotoR2Key(photo.userId, username, photo.timestamp, photo.r2Key)
        deleteObject(creds, r2Key)
    }

    suspend fun deduplicateLocalProgressPhotos(repository: WorkoutRepository): Int = withContext(Dispatchers.IO) {
        var removed = 0
        try {
            val all = repository.getAllProgressPhotos()
                .sortedWith(
                    compareByDescending<ProgressPhoto> { it.r2Key.isNotBlank() }
                        .thenByDescending { it.aiFeedback.isNotBlank() }
                        .thenByDescending { it.id }
                )
            val seenR2Keys = mutableSetOf<String>()
            val seenUserHashes = mutableSetOf<String>()
            val keptPhotos = mutableListOf<ProgressPhoto>()

            for (p in all) {
                val r2Dup = p.r2Key.isNotBlank() && !seenR2Keys.add("${p.userId}|${p.r2Key}")
                val tsDup = keptPhotos.any { it.userId == p.userId && kotlin.math.abs(it.timestamp - p.timestamp) < 2000L }
                val file = if (p.photoPath.isNotBlank()) File(p.photoPath) else null
                val fileHash = if (file != null && file.exists() && file.length() > 0L) {
                    try { sha256Hex(file.readBytes()) } catch (_: Exception) { null }
                } else null
                val hashDup = fileHash != null && !seenUserHashes.add("${p.userId}|$fileHash")

                if (r2Dup || tsDup || hashDup) {
                    repository.deleteProgressPhotoById(p.id)
                    if (file != null && file.exists() && keptPhotos.none { it.photoPath == file.absolutePath }) {
                        try { file.delete() } catch (_: Exception) {}
                    }
                    removed++
                } else {
                    keptPhotos.add(p)
                }
            }
        } catch (_: Exception) {
        }
        removed
    }

    suspend fun syncProgressPhotosForUsers(
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager
    ) = withContext(Dispatchers.IO) {
        try {
            // 1. Clean local duplicates before syncing
            deduplicateLocalProgressPhotos(repository)

            val creds = getCredentials(preferencesManager)
            if (!creds.isComplete || !isNetworkAvailable()) {
                val hasUnuploaded = repository.getAllProgressPhotos().any {
                    it.r2Key.isBlank() && it.photoPath.isNotBlank() && File(it.photoPath).exists()
                }
                if (hasUnuploaded) {
                    preferencesManager.setPendingR2Upload(true)
                }
                return@withContext
            }

            val users = repository.getAllUsers()
            val (listOk, rawObjects) = listObjects(creds, "progress_photos/")
            val remoteByKey: Map<String, R2ObjectMeta> = if (listOk) {
                val validObjects = rawObjects
                    .filter { it.size > 0L && !it.key.endsWith(".etag", ignoreCase = true) }
                    .sortedByDescending { it.lastModified }

                // 2. Deduplicate identical progress photos in R2 per user folder by ETag
                val seenFolderEtag = mutableSetOf<String>()
                val deduplicatedObjects = mutableListOf<R2ObjectMeta>()
                for (meta in validObjects) {
                    val folder = meta.key.removePrefix("progress_photos/").substringBefore('/', "")
                    val etagKey = if (meta.etag.isNotBlank() && folder.isNotBlank()) "$folder|${meta.etag}" else ""
                    if (etagKey.isNotBlank() && !seenFolderEtag.add(etagKey)) {
                        deleteObject(creds, meta.key)
                    } else {
                        deduplicatedObjects.add(meta)
                    }
                }

                val usersByFolder = mutableMapOf<String, User>()
                users.forEach { u ->
                    if (u.username.isNotBlank()) {
                        usersByFolder[sanitizeKeySlug(u.username)] = u
                    }
                    usersByFolder[u.id.toString()] = u
                    usersByFolder["user_${u.id}"] = u
                }

                val map = deduplicatedObjects.associateBy { it.key }
                val localPhotos = repository.getAllProgressPhotos()

                // 3. Download missing progress photos from R2 into local database per user
                for ((key, meta) in map) {
                    val parts = key.removePrefix("progress_photos/").split("/")
                    if (parts.size < 2) continue
                    val folder = parts[0]
                    val fileName = parts[1]
                    val timestamp = fileName.substringBeforeLast('.').toLongOrNull() ?: continue

                    val resolvedUser = usersByFolder[folder.lowercase(Locale.US)]
                        ?: usersByFolder[folder]
                    val targetUserId = resolvedUser?.id ?: folder.toIntOrNull() ?: continue

                    val localPhoto = localPhotos.find {
                        it.r2Key == key || (it.userId == targetUserId && kotlin.math.abs(it.timestamp - timestamp) < 2000L)
                    }
                    val localFile = if (localPhoto != null) File(localPhoto.photoPath) else null

                    if (localPhoto == null || localFile == null || !localFile.exists() || localFile.length() == 0L) {
                        val (getOk, bytes, etag) = getObject(creds, key)
                        if (getOk && bytes != null && bytes.isNotEmpty()) {
                            val destFile = File(context.filesDir, "progress_${targetUserId}_$timestamp.jpg")
                            saveCachedImage(destFile, bytes, etag ?: meta.etag)
                            val fileDetails = extractBasicFileDetails(destFile)
                            if (localPhoto != null) {
                                repository.insertProgressPhoto(
                                    localPhoto.copy(
                                        photoPath = destFile.absolutePath,
                                        r2Key = key,
                                        photoDetails = localPhoto.photoDetails.ifBlank { fileDetails }
                                    )
                                )
                            } else {
                                repository.insertProgressPhoto(
                                    ProgressPhoto(
                                        userId = targetUserId,
                                        photoPath = destFile.absolutePath,
                                        caption = "",
                                        timestamp = timestamp,
                                        r2Key = key,
                                        photoDetails = fileDetails
                                    )
                                )
                            }
                        }
                    } else if (localPhoto.r2Key.isBlank()) {
                        repository.updateProgressPhotoR2Key(localPhoto.id, key)
                    }
                }
                map
            } else {
                emptyMap()
            }

            // 4. Always upload any local progress photos that aren't saved on R2 yet
            val usersById = users.associateBy { it.id }
            var anyUploadFailed = false
            for (local in repository.getAllProgressPhotos()) {
                val ownerUsername = usersById[local.userId]?.username.orEmpty()
                val expectedKey = progressPhotoR2Key(local.userId, ownerUsername, local.timestamp, local.r2Key)
                val remoteMeta = remoteByKey[expectedKey]
                val needsUpload = local.r2Key.isBlank() || (listOk && remoteMeta == null)
                val localFile = File(local.photoPath)
                if (needsUpload && localFile.exists() && localFile.length() > 0L) {
                    val uploadedKey = uploadProgressPhoto(local, preferencesManager, ownerUsername)
                    if (uploadedKey != null) {
                        if (local.r2Key != uploadedKey) {
                            repository.updateProgressPhotoR2Key(local.id, uploadedKey)
                        }
                    } else {
                        anyUploadFailed = true
                    }
                }
            }
            preferencesManager.setPendingR2Upload(anyUploadFailed)

            // 5. Final duplicate check locally
            deduplicateLocalProgressPhotos(repository)
        } catch (_: Exception) {}
    }

    suspend fun testR2Connection(
        preferencesManager: PreferencesManager
    ): R2TestResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val creds = getCredentials(preferencesManager)
            if (!creds.isComplete) {
                val missing = buildList {
                    if (creds.accountId.isBlank()) add("CLOUDFLARE_ACCOUNT_ID")
                    if (creds.bucketName.isBlank()) add("CLOUDFLARE_R2_BUCKET_NAME")
                    if (creds.accessKeyId.isBlank()) add("CLOUDFLARE_R2_ACCESS_KEY_ID")
                    if (creds.secretAccessKey.isBlank()) add("CLOUDFLARE_R2_SECRET_ACCESS_KEY")
                }.joinToString(", ")
                val msg = "Missing R2 Secrets: $missing"
                preferencesManager.saveLastR2Error(msg)
                return@withContext R2TestResult(success = false, statusMessage = msg)
            }
            if (!isNetworkAvailable()) {
                val msg = "No active internet connection to reach Cloudflare R2."
                preferencesManager.saveLastR2Error(msg)
                return@withContext R2TestResult(success = false, bucketName = creds.bucketName, statusMessage = msg)
            }

            // 1. Test ListObjectsV2
            val (listOk, objects) = listObjects(creds, "")
            if (!listOk) {
                val msg = "Failed listing R2 bucket '${creds.bucketName}'. Verify Account ID, Bucket Name, and S3 Access/Secret Keys."
                preferencesManager.saveLastR2Error(msg)
                return@withContext R2TestResult(success = false, bucketName = creds.bucketName, statusMessage = msg)
            }

            val nonDiag = objects.filter { !it.key.startsWith("_diagnostics/") && !it.key.endsWith(".etag", ignoreCase = true) }
            val progressCount = nonDiag.count { it.key.startsWith("progress_photos/") }
            val avatarCount = nonDiag.count { it.key.startsWith("avatars/") }
            val guideCount = nonDiag.count { it.key.startsWith("exercise_guides/") || it.key.startsWith("exercises/") }

            // 2. Test live Put + Get + Delete round-trip on diagnostic key
            val diagKey = "_diagnostics/healthcheck_${System.currentTimeMillis()}.txt"
            val payload = "elysium-r2-ok-${System.currentTimeMillis()}".toByteArray(Charsets.UTF_8)
            val (putOk, putErr) = putObject(creds, diagKey, payload, "text/plain")
            if (!putOk) {
                val msg = "R2 List OK (${nonDiag.size} files), but Write test failed: ${putErr ?: "Unknown error"}"
                preferencesManager.saveLastR2Error(msg)
                return@withContext R2TestResult(
                    success = false,
                    bucketName = creds.bucketName,
                    totalObjects = nonDiag.size,
                    progressPhotosCount = progressCount,
                    avatarsCount = avatarCount,
                    exerciseGuidesCount = guideCount,
                    readWriteVerified = false,
                    latencyMs = System.currentTimeMillis() - startMs,
                    statusMessage = msg
                )
            }

            val (getOk, fetchedBytes, _) = getObject(creds, diagKey)
            deleteObject(creds, diagKey)
            val readMatch = getOk && fetchedBytes != null && fetchedBytes.contentEquals(payload)
            val elapsed = System.currentTimeMillis() - startMs

            if (!readMatch) {
                val msg = "R2 Write succeeded, but Read verification failed for bucket '${creds.bucketName}'."
                preferencesManager.saveLastR2Error(msg)
                return@withContext R2TestResult(
                    success = false,
                    bucketName = creds.bucketName,
                    totalObjects = nonDiag.size,
                    progressPhotosCount = progressCount,
                    avatarsCount = avatarCount,
                    exerciseGuidesCount = guideCount,
                    readWriteVerified = false,
                    latencyMs = elapsed,
                    statusMessage = msg
                )
            }

            preferencesManager.saveLastR2Error("")
            preferencesManager.saveLastR2SyncTime(System.currentTimeMillis())
            R2TestResult(
                success = true,
                bucketName = creds.bucketName,
                totalObjects = nonDiag.size,
                progressPhotosCount = progressCount,
                avatarsCount = avatarCount,
                exerciseGuidesCount = guideCount,
                readWriteVerified = true,
                latencyMs = elapsed,
                statusMessage = "R2 Bucket '${creds.bucketName}' ONLINE (${elapsed}ms) • Read/Write/Delete PASSED ✓\nStored files: ${nonDiag.size} total ($progressCount progress photos, $avatarCount avatars, $guideCount exercise images)"
            )
        } catch (e: Exception) {
            val msg = "R2 test exception: ${e.localizedMessage ?: e.javaClass.simpleName}"
            preferencesManager.saveLastR2Error(msg)
            R2TestResult(success = false, latencyMs = System.currentTimeMillis() - startMs, statusMessage = msg)
        }
    }

    suspend fun uploadExerciseGuidePicture(
        guideId: String,
        exerciseName: String,
        bytes: ByteArray,
        mimeType: String,
        preferencesManager: PreferencesManager
    ): File? = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) return@withContext null
        val dir = getPicturesCacheDir(context)
        val guideFile = File(dir, "guide_${sanitizeKeySlug(guideId)}.img")
        val exFile = File(dir, "exercise_${sanitizeKeySlug(exerciseName)}.img")
        saveCachedImage(guideFile, bytes, null)
        saveCachedImage(exFile, bytes, null)

        val creds = getCredentials(preferencesManager)
        if (!creds.isComplete) {
            preferencesManager.saveLastR2Error("R2 API keys not configured")
            preferencesManager.setPendingR2Upload(true)
            return@withContext guideFile
        }
        if (!isNetworkAvailable()) {
            preferencesManager.saveLastR2Error("Offline — exercise picture queued for R2")
            preferencesManager.setPendingR2Upload(true)
            return@withContext guideFile
        }

        val ext = if (mimeType.contains("png", ignoreCase = true)) "png" else "jpg"
        val r2Key = "exercise_guides/${sanitizeKeySlug(guideId)}.$ext"
        val (ok, errOrEtag) = putObject(creds, r2Key, bytes, mimeType.ifBlank { "image/jpeg" })
        if (ok) {
            if (!errOrEtag.isNullOrBlank()) {
                File(guideFile.absolutePath + ".etag").writeText(errOrEtag)
                File(exFile.absolutePath + ".etag").writeText(errOrEtag)
            }
            preferencesManager.setPendingR2Upload(false)
            preferencesManager.saveLastR2SyncTime(System.currentTimeMillis())
            preferencesManager.saveLastR2Error("")
        } else {
            preferencesManager.saveLastR2Error(errOrEtag ?: "R2 upload failed")
        }
        guideFile
    }

    suspend fun uploadDescribedExercisePicture(
        displayNameOrDescription: String,
        bytes: ByteArray,
        mimeType: String,
        preferencesManager: PreferencesManager
    ): Pair<String?, File?> = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) return@withContext null to null
        val matchedGuideId = matchGuideIdFromDescription(displayNameOrDescription)
        val effectiveSlug = matchedGuideId ?: sanitizeKeySlug(displayNameOrDescription.substringBeforeLast('.'))
        val dir = getPicturesCacheDir(context)
        val guideFile = File(dir, "guide_$effectiveSlug.img")
        saveCachedImage(guideFile, bytes, null)

        val creds = getCredentials(preferencesManager)
        if (!creds.isComplete) {
            preferencesManager.saveLastR2Error("R2 API keys not configured")
            preferencesManager.setPendingR2Upload(true)
            return@withContext matchedGuideId to guideFile
        }
        if (!isNetworkAvailable()) {
            preferencesManager.saveLastR2Error("Offline — picture queued for R2")
            preferencesManager.setPendingR2Upload(true)
            return@withContext matchedGuideId to guideFile
        }

        val ext = if (mimeType.contains("png", ignoreCase = true) || displayNameOrDescription.endsWith(".png", ignoreCase = true)) "png" else "jpg"
        val r2Key = if (matchedGuideId != null) {
            "exercise_guides/$matchedGuideId.$ext"
        } else {
            "exercises/$effectiveSlug.$ext"
        }
        val (ok, errOrEtag) = putObject(creds, r2Key, bytes, mimeType.ifBlank { if (ext == "png") "image/png" else "image/jpeg" })
        if (ok) {
            if (!errOrEtag.isNullOrBlank()) {
                File(guideFile.absolutePath + ".etag").writeText(errOrEtag)
            }
            preferencesManager.setPendingR2Upload(false)
            preferencesManager.saveLastR2SyncTime(System.currentTimeMillis())
            preferencesManager.saveLastR2Error("")
        } else {
            preferencesManager.saveLastR2Error(errOrEtag ?: "R2 upload failed")
        }
        matchedGuideId to guideFile
    }

    suspend fun isExercisePictureAvailable(
        guideId: String?,
        exerciseName: String,
        publicFallbackUrl: String = "",
        hasUniqueBundledGuide: Boolean = false,
        preferencesManager: PreferencesManager
    ): Boolean = withContext(Dispatchers.IO) {
        if (hasUniqueBundledGuide || publicFallbackUrl.isNotBlank()) return@withContext true
        if (!guideId.isNullOrBlank() && getLocalGuidePictureFile(context, guideId) != null) return@withContext true
        if (getLocalExercisePictureFile(context, exerciseName) != null) return@withContext true

        val creds = getCredentials(preferencesManager)
        if (!creds.isComplete || !isNetworkAvailable()) return@withContext false

        try {
            val guideSlug = guideId?.let { sanitizeKeySlug(it) }.orEmpty()
            val exSlug = sanitizeKeySlug(exerciseName)
            val (listOk, allObjects) = listObjects(creds, "")
            if (!listOk) return@withContext false

            allObjects.any { meta ->
                !meta.key.startsWith("avatars/", ignoreCase = true) &&
                    meta.size > 0L &&
                    run {
                        val keyNoExt = meta.key.substringAfterLast('/').substringBeforeLast('.')
                        val matchedId = matchGuideIdFromDescription(meta.key)
                        (guideId != null && matchedId == guideId) ||
                            (guideSlug.isNotBlank() && meta.key.startsWith("exercise_guides/$guideSlug", ignoreCase = true)) ||
                            meta.key.startsWith("exercises/$exSlug", ignoreCase = true) ||
                            (guideSlug.isNotBlank() && sanitizeKeySlug(keyNoExt) == guideSlug) ||
                            sanitizeKeySlug(keyNoExt) == exSlug
                    }
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun retrievePictureForExerciseInfo(
        guideId: String,
        exerciseName: String,
        defaultDrawableResId: Int,
        preferencesManager: PreferencesManager,
        publicFallbackUrl: String = ""
    ): File? = withContext(Dispatchers.IO) {
        val localExisting = getLocalGuidePictureFile(context, guideId)
            ?: getLocalExercisePictureFile(context, exerciseName)

        val guideSlug = sanitizeKeySlug(guideId)
        val exSlug = sanitizeKeySlug(exerciseName)
        val creds = getCredentials(preferencesManager)

        if (creds.isComplete && isNetworkAvailable()) {
            try {
                val (listOk, allObjects) = listObjects(creds, "")
                val targetMeta = if (listOk) {
                    allObjects
                        .filter { !it.key.startsWith("avatars/", ignoreCase = true) && it.size > 0L }
                        .filter { meta ->
                            val keyNoExt = meta.key.substringAfterLast('/').substringBeforeLast('.')
                            val matchedId = matchGuideIdFromDescription(meta.key)
                            matchedId == guideId ||
                                meta.key.startsWith("exercise_guides/$guideSlug", ignoreCase = true) ||
                                meta.key.startsWith("exercises/$exSlug", ignoreCase = true) ||
                                sanitizeKeySlug(keyNoExt) == guideSlug ||
                                sanitizeKeySlug(keyNoExt) == exSlug
                        }
                        .maxByOrNull { it.lastModified }
                } else {
                    null
                }

                if (targetMeta != null) {
                    val destFile = File(getPicturesCacheDir(context), "guide_$guideSlug.img")
                    val existingEtag = cachedEtag(destFile)
                    if (!destFile.exists() || existingEtag != targetMeta.etag) {
                        val (getOk, bytes, etag) = getObject(creds, targetMeta.key)
                        if (getOk && bytes != null && bytes.isNotEmpty()) {
                            saveCachedImage(destFile, bytes, etag ?: targetMeta.etag)
                            val exFile = File(getPicturesCacheDir(context), "exercise_$exSlug.img")
                            if (exFile.exists()) {
                                saveCachedImage(exFile, bytes, etag ?: targetMeta.etag)
                            }
                            preferencesManager.saveLastR2SyncTime(System.currentTimeMillis())
                            preferencesManager.saveLastR2Error("")
                            return@withContext destFile
                        }
                    } else {
                        return@withContext destFile
                    }
                } else if (publicFallbackUrl.isBlank() && defaultDrawableResId != 0 && guideId !in SHARED_FALLBACK_ONLY_GUIDES) {
                    // Back up unique bundled drawable to R2 if not present yet
                    try {
                        val bytes = context.resources.openRawResource(defaultDrawableResId).use { it.readBytes() }
                        if (bytes.isNotEmpty()) {
                            val isJpg = bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
                            val ext = if (isJpg) "jpg" else "png"
                            val contentType = if (isJpg) "image/jpeg" else "image/png"
                            putObject(creds, "exercise_guides/$guideSlug.$ext", bytes, contentType)
                        }
                    } catch (_: Exception) {
                    }
                }
            } catch (e: Exception) {
                preferencesManager.saveLastR2Error(e.localizedMessage ?: "R2 retrieval error")
            }
        }

        // Download from public R2 URL if provided and not cached locally yet
        if (localExisting == null && publicFallbackUrl.isNotBlank() && isNetworkAvailable()) {
            try {
                val req = Request.Builder().url(publicFallbackUrl).get().build()
                client.newCall(req).execute().use { resp ->
                    val bytes = if (resp.isSuccessful) resp.body?.bytes() else null
                    if (bytes != null && bytes.isNotEmpty()) {
                        val destFile = File(getPicturesCacheDir(context), "guide_$guideSlug.img")
                        val etag = resp.header("ETag")?.replace("\"", "")
                        saveCachedImage(destFile, bytes, etag)
                        return@withContext destFile
                    }
                }
            } catch (_: Exception) {
            }
        }

        localExisting
    }

    suspend fun syncAllPicturesOnStartup(
        userId: Int,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager,
        bundledGuides: List<Pair<String, Int>>
    ): Boolean = r2Mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val creds = getCredentials(preferencesManager)
                if (!creds.isComplete) {
                    preferencesManager.saveLastR2Error("R2 API keys required in Secrets")
                    return@withContext false
                }
                if (!isNetworkAvailable()) {
                    preferencesManager.saveLastR2Error("Offline — R2 pictures cached locally")
                    return@withContext false
                }

                val allUsers = repository.getAllUsers()

                // 1. List all objects currently stored in R2
                val (listOk, allObjects) = listObjects(creds, "")
                if (!listOk) {
                    preferencesManager.saveLastR2Error("Could not connect to Cloudflare R2 bucket '${creds.bucketName}'")
                    return@withContext false
                }
                val objectsByKey = allObjects.associateBy { it.key }

                // Clean up any leftover diagnostic objects on R2
                allObjects.filter { it.key.startsWith("_diagnostics/") }.forEach { stale ->
                    deleteObject(creds, stale.key)
                }

                // 2. Sync profile pictures <-> R2 for ALL registered users (and active userId)
                val hasPending = preferencesManager.pendingR2UploadFlow.first()
                val syncUserTargets = buildList {
                    allUsers.forEach { add(it.id to it.username) }
                    if (userId != -1 && allUsers.none { it.id == userId }) {
                        add(userId to repository.getUserById(userId)?.username.orEmpty())
                    }
                }
                syncUserTargets.forEach { (uid, uname) ->
                    val expectedAvatarKey = avatarR2Key(uid, uname)
                    val remoteAvatar = objectsByKey[expectedAvatarKey]
                    val localAvatarPath = preferencesManager.profilePicturePathFlow(uid).first()
                    val localAvatarFile = if (localAvatarPath.isNotBlank()) File(localAvatarPath) else null

                    if (localAvatarFile != null && localAvatarFile.exists() && localAvatarFile.length() > 0L && (remoteAvatar == null || hasPending)) {
                        val (putOk, putEtag) = putObject(creds, expectedAvatarKey, localAvatarFile.readBytes(), "image/jpeg")
                        if (putOk && !putEtag.isNullOrBlank()) {
                            File(localAvatarFile.absolutePath + ".etag").writeText(putEtag)
                        }
                    } else if (remoteAvatar != null && (localAvatarFile == null || !localAvatarFile.exists() || cachedEtag(localAvatarFile) != remoteAvatar.etag)) {
                        val (ok, bytes, etag) = getObject(creds, remoteAvatar.key)
                        if (ok && bytes != null && bytes.isNotEmpty()) {
                            val destAvatar = File(context.filesDir, "avatar_${uid}_${System.currentTimeMillis()}.jpg")
                            saveCachedImage(destAvatar, bytes, etag ?: remoteAvatar.etag)
                            if (localAvatarFile != null && localAvatarFile.exists() && localAvatarFile.absolutePath.startsWith(context.filesDir.absolutePath)) {
                                localAvatarFile.delete()
                            }
                            preferencesManager.saveProfilePicturePath(uid, destAvatar.absolutePath)
                        }
                    }
                }

                // 3. Download & match all saved exercise pictures from R2 by guide ID OR description/filename
                val cacheDir = getPicturesCacheDir(context)
                val nonAvatarImages = allObjects.filter {
                    !it.key.startsWith("avatars/", ignoreCase = true) &&
                        !it.key.startsWith("progress_photos/", ignoreCase = true) &&
                        !it.key.startsWith("biometrics/", ignoreCase = true) &&
                        !it.key.startsWith("_diagnostics/", ignoreCase = true) &&
                        it.size > 0L
                }

                // Group objects matching a known guideId, pick newest, and remove duplicate identical copies (same guideId + etag)
                val matchedByGuide = mutableMapOf<String, R2ObjectMeta>()
                val unmatchedExerciseObjects = mutableListOf<R2ObjectMeta>()

                nonAvatarImages.sortedByDescending { it.lastModified }.forEach { meta ->
                    val matchedGuideId = matchGuideIdFromDescription(meta.key)
                    if (matchedGuideId != null) {
                        val currentBest = matchedByGuide[matchedGuideId]
                        if (currentBest == null) {
                            matchedByGuide[matchedGuideId] = meta
                        } else if (meta.etag.isNotBlank() && meta.etag == currentBest.etag && meta.key != currentBest.key) {
                            deleteObject(creds, meta.key)
                        }
                    } else if (meta.key.startsWith("exercise_guides/") || meta.key.startsWith("exercises/")) {
                        unmatchedExerciseObjects.add(meta)
                    }
                }

                matchedByGuide.forEach { (guideId, meta) ->
                    val localFile = File(cacheDir, "guide_${sanitizeKeySlug(guideId)}.img")
                    if (!localFile.exists() || cachedEtag(localFile) != meta.etag) {
                        val (ok, bytes, etag) = getObject(creds, meta.key)
                        if (ok && bytes != null && bytes.isNotEmpty()) {
                            saveCachedImage(localFile, bytes, etag ?: meta.etag)
                        }
                    }
                }

                unmatchedExerciseObjects.forEach { meta ->
                    val fileNameNoExt = meta.key.substringAfterLast('/').substringBeforeLast('.')
                    val localFile = if (meta.key.startsWith("exercises/")) {
                        File(cacheDir, "exercise_${sanitizeKeySlug(fileNameNoExt)}.img")
                    } else {
                        File(cacheDir, "guide_${sanitizeKeySlug(fileNameNoExt)}.img")
                    }
                    if (!localFile.exists() || cachedEtag(localFile) != meta.etag) {
                        val (ok, bytes, etag) = getObject(creds, meta.key)
                        if (ok && bytes != null && bytes.isNotEmpty()) {
                            saveCachedImage(localFile, bytes, etag ?: meta.etag)
                        }
                    }
                }

                // 4. Upload any missing unique bundled exercise guide pictures to R2
                val existingGuidePrefixes = matchedByGuide.keys +
                    allObjects
                        .filter { it.key.startsWith("exercise_guides/") }
                        .map { it.key.substringAfter("exercise_guides/").substringBeforeLast('.') }

                bundledGuides.forEach { (guideId, resId) ->
                    val slug = sanitizeKeySlug(guideId)
                    if (slug !in existingGuidePrefixes && guideId !in SHARED_FALLBACK_ONLY_GUIDES && resId != 0) {
                        try {
                            val bytes = context.resources.openRawResource(resId).use { it.readBytes() }
                            if (bytes.isNotEmpty()) {
                                val isJpg = bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
                                val isPng = bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
                                if (isJpg || isPng) {
                                    val ext = if (isJpg) "jpg" else "png"
                                    val mime = if (isJpg) "image/jpeg" else "image/png"
                                    putObject(creds, "exercise_guides/$slug.$ext", bytes, mime)
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }

                // 5. Sync all progress photos for each user with R2
                syncProgressPhotosForUsers(repository, preferencesManager)

                preferencesManager.setPendingR2Upload(false)
                preferencesManager.saveLastR2SyncTime(System.currentTimeMillis())
                preferencesManager.saveLastR2Error("")
                true
            } catch (e: Exception) {
                preferencesManager.saveLastR2Error(e.localizedMessage ?: "R2 startup sync error")
                false
            }
        }
    }

    suspend fun uploadBiometricPrintToR2(
        user: User,
        deviceInfo: String,
        preferencesManager: PreferencesManager
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val creds = getCredentials(preferencesManager)
            if (!creds.isComplete || !isNetworkAvailable()) return@withContext false
            val key = "biometrics/${sanitizeKeySlug(user.username)}.json"
            if (!user.biometricEnabled) {
                return@withContext deleteObject(creds, key)
            }
            val json = org.json.JSONObject()
                .put("username", user.username)
                .put("biometric_enabled", user.biometricEnabled)
                .put("biometric_token", user.biometricToken)
                .put("biometric_public_key", user.biometricPublicKey)
                .put("device_info", deviceInfo)
                .put("enrolled_at", user.biometricEnrolledAt)
                .toString()
            val (ok, _) = putObject(creds, key, json.toByteArray(Charsets.UTF_8), "application/json")
            ok
        } catch (_: Exception) {
            false
        }
    }
}
