package com.example.model

import com.example.BuildConfig

data class GymExerciseDetail(
    val explanation: String,
    val exerciseImageUrl: String,
    val muscleImageUrl: String,
    val muscleGroup: String = ""
)

class AIStudyRepository {
    suspend fun getExerciseDetails(exerciseName: String): GymExerciseDetail {
        return try {
            val prompt = "Provide a professional, concise guide/description for the exercise: '$exerciseName' in a simple, parsable structure.\n" +
                    "Always output exactly three sections separated by the marker '---SECTION---':\n\n" +
                    "Section 1: Complete guide including Target Muscles, Proper Form/Execution (2-3 quick bullets), and a major mistake to avoid. Keep this under 100 words.\n" +
                    "---SECTION---\n" +
                    "Section 2: Provide a valid, high-quality, realistic gym photo URL of this specific exercise from Unsplash that we can directly render. " +
                    "CRITICAL MANDATE: The photo must represent the name of this specific exercise ('$exerciseName'), NOT a general picture of random gym equipment, group exercises, or multi-person setups. If you do not know a highly specific Unsplash ID for '$exerciseName', just write 'FALLBACK' or 'DEFAULT'.\n" +
                    "---SECTION---\n" +
                    "Section 3: The single most specific primary muscle group of this exercise from this exact list: chest, biceps, triceps, shoulders, abs, calves, glutes, traps, quads, lats, hamstrings.\n\n" +
                    "Avoid any headers like 'Section 1' or markdown decorators; just write the values separated by '---SECTION---'."

            val apiKey = BuildConfig.GEMINI_API_KEY
            val request = com.example.api.GenerateContentRequest(
                contents = listOf(
                    com.example.api.Content(
                        parts = listOf(com.example.api.Part(text = prompt))
                    )
                ),
                systemInstruction = com.example.api.Content(
                    parts = listOf(com.example.api.Part(text = "You are an elite kinesiologist and personal trainer. You output strict delimiter format. No introductory or concluding chit-chat. Always choose an extremely specific individual exercise photo for the requested exercise name."))
                )
            )
            val response = generateContentResilient(apiKey, request)
            val rawText = extractResponseText(response)
            
            val sections = rawText.split("---SECTION---")
            val explanation = sections.getOrNull(0)?.trim() ?: "No description available for $exerciseName."
            
            val rawUrl = sections.getOrNull(1)?.trim() ?: ""
            val fallbackUrl = getFallbackExerciseImage(exerciseName)
            val exerciseImageUrl = if (rawUrl.startsWith("http")) {
                val rawLower = rawUrl.lowercase()
                // If it's a known generic/placeholder background or if we have an exceptionally specific matched fallback, use the specific fallback to guarantee correctness!
                if (rawLower.contains("photo-1517838277536-f5f99be501cd") || 
                    rawLower.contains("gym") || 
                    rawLower.contains("workout") || 
                    fallbackUrl != "https://images.unsplash.com/photo-1541534741688-6078c6bfb5c5?auto=format&fit=crop&w=500&q=80") {
                    fallbackUrl
                } else {
                    rawUrl
                }
            } else {
                fallbackUrl
            }

            val muscleGroup = sections.getOrNull(2)?.trim()?.lowercase() ?: ""
            val muscleImageUrl = mapMuscleGroupToWgerUrl(muscleGroup)

            GymExerciseDetail(explanation, exerciseImageUrl, muscleImageUrl, muscleGroup)
        } catch (e: Exception) {
            GymExerciseDetail(
                explanation = "Hmm, I couldn't reach the Gym AI. Make sure you are connected. Error: ${e.message}",
                exerciseImageUrl = getFallbackExerciseImage(exerciseName),
                muscleImageUrl = "",
                muscleGroup = ""
            )
        }
    }

    private fun getFallbackExerciseImage(name: String): String {
        val n = name.lowercase().trim()
        return when {
            // 1. Bench Press / Chest Presses
            n.contains("bench") && (n.contains("press") || n.contains("barbell") || n.contains("dumbbell") || n.contains("db")) -> 
                "https://images.unsplash.com/photo-1541534741688-6078c6bfb5c5?auto=format&fit=crop&w=500&q=80" // Real barbell bench press action
            // Chest / pushups
            n.contains("pushup") || n.contains("push-up") || n.contains("push up") || (n.contains("chest") && n.contains("fly")) -> 
                "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?auto=format&fit=crop&w=500&q=80" // Floor press & pushups
            n.contains("press") && (n.contains("chest") || n.contains("pec")) -> 
                "https://images.unsplash.com/photo-1541534741688-6078c6bfb5c5?auto=format&fit=crop&w=500&q=80"
                
            // 2. Squats
            n.contains("squat") || n.contains("squats") || n.contains("hack squat") || n.contains("front squat") || n.contains("back squat") || n.contains("goblet squat") -> 
                "https://images.unsplash.com/photo-1574680096145-d05b474e2155?auto=format&fit=crop&w=500&q=80" // Athlete squatting on rack
                
            // 3. Deadlifts
            n.contains("deadlift") || n.contains("deadlifts") || n.contains("rdl") || n.contains("romanian deadlift") || n.contains("sumo dead") -> 
                "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?auto=format&fit=crop&w=500&q=80" // Barbell heavy lift pose
                
            // 4. Biceps
            n.contains("bicep") || n.contains("biceps") || n.contains("curl") || n.contains("curls") || n.contains("hammer") || n.contains("preacher") -> 
                "https://images.unsplash.com/photo-1581009146145-b5ef050c2e1e?auto=format&fit=crop&w=500&q=80" // Dedicated close-up of a dumbbell bicep curl
                
            // 5. Triceps
            n.contains("tricep") || n.contains("triceps") || n.contains("pushdown") || n.contains("pushdowns") || n.contains("skull crusher") || n.contains("skullcrusher") || n.contains("kickback") || n.contains("kickbacks") -> 
                "https://images.unsplash.com/photo-1530822847156-5df684ec5ee1?auto=format&fit=crop&w=500&q=80" // Triceps press-down / bodyweight dip
                
            // 6. Pullups / Chinups
            n.contains("pullup") || n.contains("pull up") || n.contains("pullups") || n.contains("chin up") || n.contains("chinup") || n.contains("chin-up") -> 
                "https://images.unsplash.com/photo-1598971639058-fab3c3109a00?auto=format&fit=crop&w=500&q=80" // Athlete doing pullups on pullup bar
                
            // 7. Lat pulldown / back rows
            n.contains("pulldown") || n.contains("lat pull") || n.contains("row") || n.contains("rows") || n.contains("t-bar") || n.contains("back development") -> 
                "https://images.unsplash.com/photo-1605296867304-46d5465a25f1?auto=format&fit=crop&w=500&q=80" // Row / pulldown training
                
            // 8. Shoulder exercises
            n.contains("shoulder") || n.contains("shoulders") || (n.contains("press") && (n.contains("military") || n.contains("overhead") || n.contains("deltoid"))) || n.contains("raise") || n.contains("deltoid") || n.contains("shrug") || n.contains("shrugs") -> 
                "https://images.unsplash.com/photo-1532089001514-241fa7d174e5?auto=format&fit=crop&w=500&q=80" // Standing dumbbell shoulder work
                
            // 9. Leg Press / Calf raise / Lunges
            n.contains("leg press") || n.contains("legpress") -> 
                "https://images.unsplash.com/photo-1434608519344-49d77a699e1d?auto=format&fit=crop&w=500&q=80" // Heavy leg press machine movement
            n.contains("lunge") || n.contains("lunges") || n.contains("calf") || n.contains("calves") || n.contains("leg extension") || n.contains("quad") || n.contains("hamstring") -> 
                "https://images.unsplash.com/photo-1583454110551-21f2fa2afe61?auto=format&fit=crop&w=500&q=80" // Leg / thigh / calf focus training
                
            // 10. Plank / Abs / Core
            n.contains("plank") || n.contains("planks") || n.contains("abs") || n.contains("abdominal") || n.contains("abdominals") || n.contains("situp") || n.contains("situps") || n.contains("sit-up") || n.contains("crunch") || n.contains("crunches") || n.contains("core") -> 
                "https://images.unsplash.com/photo-1566241477600-ac026ad43874?auto=format&fit=crop&w=500&q=80" // Dedicated low plank pose on standard gym floor
                
            // 11. Cardio / Running
            n.contains("run") || n.contains("running") || n.contains("treadmill") || n.contains("cardio") || n.contains("jog") || n.contains("jogging") -> 
                "https://images.unsplash.com/photo-1571008887538-b36bb32f4571?auto=format&fit=crop&w=500&q=80" // Running on track/treadmill
                
            else -> 
                "https://images.unsplash.com/photo-1541534741688-6078c6bfb5c5?auto=format&fit=crop&w=500&q=80" // Default to bench press (a highly iconic exercise) rather than generic 'exercises' image
        }
    }

    private fun mapMuscleGroupToWgerUrl(muscleGroup: String): String {
        val m = muscleGroup.lowercase()
        return when {
            m.contains("biceps") || m.contains("bicep") -> "https://wger.de/static/images/muscles/main/muscle-1.svg"
            m.contains("shoulders") || m.contains("shoulder") || m.contains("deltoids") || m.contains("deltoid") -> "https://wger.de/static/images/muscles/main/muscle-2.svg"
            m.contains("chest") || m.contains("pectoralis") || m.contains("pecs") -> "https://wger.de/static/images/muscles/main/muscle-4.svg"
            m.contains("triceps") || m.contains("tricep") -> "https://wger.de/static/images/muscles/main/muscle-5.svg"
            m.contains("abs") || m.contains("abdominals") || m.contains("core") -> "https://wger.de/static/images/muscles/main/muscle-6.svg"
            m.contains("calves") || m.contains("gastrocnemius") || m.contains("calf") -> "https://wger.de/static/images/muscles/main/muscle-7.svg"
            m.contains("glutes") || m.contains("gluteus") -> "https://wger.de/static/images/muscles/main/muscle-8.svg"
            m.contains("traps") || m.contains("trapezius") -> "https://wger.de/static/images/muscles/main/muscle-9.svg"
            m.contains("quads") || m.contains("quadriceps") -> "https://wger.de/static/images/muscles/main/muscle-10.svg"
            m.contains("lats") || m.contains("latissimus") || m.contains("back") -> "https://wger.de/static/images/muscles/main/muscle-11.svg"
            m.contains("hamstrings") || m.contains("hamstring") || m.contains("biceps femoris") -> "https://wger.de/static/images/muscles/main/muscle-15.svg"
            else -> ""
        }
    }

    suspend fun getExerciseDescription(exerciseName: String): String {
        return getExerciseDetails(exerciseName).explanation
    }

    suspend fun getMuscleStudyInfo(muscleName: String, topic: String): String {
        return try {
            val prompt = "Provide professional, scientifically accurate gym study information about $muscleName, specifically focusing on $topic (e.g. growth, recovery, exercises)."
            val apiKey = BuildConfig.GEMINI_API_KEY
            val request = com.example.api.GenerateContentRequest(
                contents = listOf(
                    com.example.api.Content(
                        parts = listOf(com.example.api.Part(text = prompt))
                    )
                ),
                systemInstruction = com.example.api.Content(
                    parts = listOf(com.example.api.Part(text = "You are an expert AI personal trainer and sports scientist. Provide detailed, factual insights into muscle anatomy, growth, and recovery. Provide clear formatting."))
                )
            )
            val response = generateContentResilient(apiKey, request)
            extractResponseText(response).ifBlank { "No insights available." }
        } catch (e: Exception) {
            "Hmm, I couldn't reach the AI Coach right now. Please test your network or check API key. Error: ${e.message}"
        }
    }

    data class ScannedWorkoutProgram(
        val name: String,
        val notes: String,
        val exercises: List<com.example.data.ProfileExercise>
    )

    private fun hasValidGeminiKey(): String? {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (
            apiKey.isEmpty() ||
            apiKey.equals("GEMINI_API_KEY", ignoreCase = true) ||
            apiKey.equals("MY_GEMINI_API_KEY", ignoreCase = true) ||
            apiKey.startsWith("YOUR_", ignoreCase = true)
        ) {
            return null
        }
        return apiKey
    }

    companion object {
        @Volatile
        private var lastWorkingModel: String? = null

        fun extractResponseText(response: com.example.api.GenerateContentResponse): String {
            val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
            val nonThought = parts.filter { it.thought != true }.mapNotNull { it.text?.takeIf { t -> t.isNotBlank() } }
            if (nonThought.isNotEmpty()) return nonThought.joinToString("\n")
            return parts.mapNotNull { it.text?.takeIf { t -> t.isNotBlank() } }.joinToString("\n")
        }

        private fun extractHttpErrorMessage(e: retrofit2.HttpException): String {
            return try {
                val errBody = e.response()?.errorBody()?.string().orEmpty()
                val msg = if (errBody.isNotBlank()) {
                    org.json.JSONObject(errBody).optJSONObject("error")?.optString("message").orEmpty()
                } else ""
                msg.ifBlank { "HTTP ${e.code()}: ${e.message()}" }
            } catch (_: Exception) {
                "HTTP ${e.code()}"
            }
        }

        private fun requestWithoutSystemInstruction(
            request: com.example.api.GenerateContentRequest
        ): com.example.api.GenerateContentRequest {
            val sysText = request.systemInstruction?.parts
                ?.mapNotNull { it.text?.trim() }
                ?.filter { it.isNotEmpty() }
                ?.joinToString("\n")
                .orEmpty()
            if (sysText.isBlank()) return request.copy(systemInstruction = null)
            val firstContent = request.contents.firstOrNull() ?: return request.copy(systemInstruction = null)
            val newParts = listOf(com.example.api.Part(text = "Instructions: $sysText")) + firstContent.parts
            val newContents = listOf(firstContent.copy(parts = newParts)) + request.contents.drop(1)
            return com.example.api.GenerateContentRequest(contents = newContents, systemInstruction = null)
        }

        suspend fun generateContentResilient(
            apiKey: String,
            request: com.example.api.GenerateContentRequest
        ): com.example.api.GenerateContentResponse {
            val staticModels = buildList {
                lastWorkingModel?.let { add(it) }
                add("gemini-2.5-flash")
                add("gemini-flash-latest")
                add("gemini-2.5-flash-lite")
                add("gemini-2.5-pro")
                add("gemini-2.0-flash")
                add("gemini-2.0-flash-lite")
            }.distinct()
            val noSysRequest = requestWithoutSystemInstruction(request)
            val tried = mutableSetOf<String>()
            var meaningfulError: String? = null
            var lastErrorMsg: String? = null

            suspend fun tryModel(rawModelName: String): com.example.api.GenerateContentResponse? {
                val model = rawModelName.removePrefix("models/").trim()
                if (model.isBlank() || !tried.add(model)) return null
                try {
                    val resp = com.example.api.RetrofitClient.service.generateContentWithModel(model, apiKey, request)
                    if (extractResponseText(resp).isNotBlank()) {
                        lastWorkingModel = model
                        return resp
                    }
                } catch (e: retrofit2.HttpException) {
                    val code = e.code()
                    val msg = extractHttpErrorMessage(e)
                    lastErrorMsg = "$model: $msg"
                    if (code != 404 && meaningfulError == null) {
                        meaningfulError = "$model: $msg"
                    }
                    // If auth key is explicitly invalid (401/403), fail fast with clear error
                    if (code == 401 || code == 403) {
                        throw IllegalStateException("Gemini API key error ($msg)")
                    }
                    // Retry without systemInstruction if 400 (some models/endpoints reject systemInstruction)
                    if (code == 400) {
                        try {
                            val retryNoSys = com.example.api.RetrofitClient.service.generateContentWithModel(model, apiKey, noSysRequest)
                            if (extractResponseText(retryNoSys).isNotBlank()) {
                                lastWorkingModel = model
                                return retryNoSys
                            }
                        } catch (_: Exception) {
                        }
                    }
                    // Also try stable v1 endpoint without systemInstruction when v1beta returns 404 or 400
                    if (code == 404 || code == 400) {
                        try {
                            val v1Resp = com.example.api.RetrofitClient.service.generateContentV1WithModel(model, apiKey, noSysRequest)
                            if (extractResponseText(v1Resp).isNotBlank()) {
                                lastWorkingModel = model
                                return v1Resp
                            }
                        } catch (_: Exception) {
                        }
                    }
                } catch (e: Exception) {
                    val msg = e.localizedMessage ?: e.javaClass.simpleName
                    lastErrorMsg = msg
                    if (meaningfulError == null) meaningfulError = msg
                }
                return null
            }

            // 1. Try current active Gemini models first
            for (model in staticModels) {
                tryModel(model)?.let { return it }
            }

            // 2. Dynamically discover available models from the user's Gemini API key via ListModels (v1beta & v1)
            val discoveredModels = mutableListOf<com.example.api.GeminiModelInfo>()
            try {
                discoveredModels += com.example.api.RetrofitClient.service.listModels(apiKey, 100).models
            } catch (e: retrofit2.HttpException) {
                val msg = extractHttpErrorMessage(e)
                if (e.code() == 401 || e.code() == 403) {
                    throw IllegalStateException("Gemini API key error ($msg)")
                }
                if (meaningfulError == null) meaningfulError = "ListModels: $msg"
            } catch (_: Exception) {
            }
            if (discoveredModels.isEmpty()) {
                try {
                    discoveredModels += com.example.api.RetrofitClient.service.listModelsV1(apiKey, 100).models
                } catch (_: Exception) {
                }
            }

            val discovered = discoveredModels
                .filter { info ->
                    info.supportedGenerationMethods.any { it.equals("generateContent", ignoreCase = true) }
                }
                .map { it.name.removePrefix("models/").trim() }
                .distinct()
                .filter { m ->
                    val lower = m.lowercase()
                    m.isNotBlank() &&
                        m !in tried &&
                        !lower.contains("1.0") &&
                        !lower.contains("1.5") &&
                        !lower.contains("embedding") &&
                        !lower.contains("imagen") &&
                        !lower.contains("veo") &&
                        !lower.contains("tts") &&
                        !lower.contains("aqa") &&
                        !lower.contains("audio") &&
                        !lower.contains("gemma") &&
                        !lower.contains("robotics") &&
                        !lower.contains("computer-use")
                }
                .sortedWith(
                    compareByDescending<String> { it.contains("2.5") || it.contains("3") }
                        .thenByDescending { it.contains("flash", ignoreCase = true) }
                        .thenByDescending { !it.contains("preview", ignoreCase = true) && !it.contains("exp", ignoreCase = true) }
                )
            for (model in discovered.take(8)) {
                tryModel(model)?.let { return it }
            }

            throw IllegalStateException(meaningfulError ?: lastErrorMsg ?: "Gemini API request failed.")
        }
    }

    private suspend fun callGeminiWithFallback(
        apiKey: String,
        request: com.example.api.GenerateContentRequest
    ): com.example.api.GenerateContentResponse = generateContentResilient(apiKey, request)

    private fun prepareBytesForGemini(bytes: ByteArray, mimeType: String): Pair<ByteArray, String> {
        if (!mimeType.startsWith("image/", ignoreCase = true)) {
            return bytes to mimeType
        }
        return try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return bytes to mimeType
            }
            val maxDim = 1600
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxDim || bounds.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            if (sampleSize == 1 && bytes.size <= 1_500_000 && mimeType == "image/jpeg") {
                return bytes to mimeType
            }
            val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                ?: return bytes to mimeType
            val out = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 86, out)
            bmp.recycle()
            out.toByteArray() to "image/jpeg"
        } catch (_: Exception) {
            bytes to mimeType
        }
    }

    private fun extractOfficeDocumentText(bytes: ByteArray): String? {
        // DOCX / XLSX / ODT files are ZIP archives starting with PK\x03\x04
        if (bytes.size < 4 ||
            bytes[0] != 0x50.toByte() ||
            bytes[1] != 0x4B.toByte() ||
            bytes[2] != 0x03.toByte() ||
            bytes[3] != 0x04.toByte()
        ) {
            return null
        }
        return try {
            val entries = mutableMapOf<String, String>()
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if (!entry.isDirectory &&
                        (name == "word/document.xml" ||
                            name == "content.xml" ||
                            name == "xl/sharedstrings.xml" ||
                            (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")))
                    ) {
                        val content = zis.readBytes().toString(Charsets.UTF_8)
                        entries[name] = content
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            fun decodeXmlEntities(raw: String): String = raw
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")

            // DOCX (word/document.xml)
            entries["word/document.xml"]?.let { xml ->
                val text = decodeXmlEntities(
                    xml.replace(Regex("</w:p>"), "\n")
                        .replace(Regex("</w:tr>"), "\n")
                        .replace(Regex("</w:tc>"), "  ")
                        .replace(Regex("<w:tab[^>]*/>"), "  ")
                        .replace(Regex("<w:br[^>]*/>"), "\n")
                        .replace(Regex("<[^>]+>"), "")
                )
                return text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n").takeIf { it.isNotBlank() }
            }

            // ODT (content.xml)
            entries["content.xml"]?.let { xml ->
                val text = decodeXmlEntities(
                    xml.replace(Regex("</text:p>"), "\n")
                        .replace(Regex("</table:table-row>"), "\n")
                        .replace(Regex("</table:table-cell>"), "  ")
                        .replace(Regex("<[^>]+>"), "")
                )
                return text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n").takeIf { it.isNotBlank() }
            }

            // XLSX (xl/sharedStrings.xml + xl/worksheets/sheet*.xml)
            val sheetKeys = entries.keys.filter { it.startsWith("xl/worksheets/sheet") }.sorted()
            if (sheetKeys.isNotEmpty()) {
                val sharedStrings = mutableListOf<String>()
                entries["xl/sharedstrings.xml"]?.let { ssXml ->
                    Regex("<si[^>]*>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(ssXml).forEach { m ->
                        val cellText = decodeXmlEntities(m.groupValues[1].replace(Regex("<[^>]+>"), "")).trim()
                        sharedStrings.add(cellText)
                    }
                }
                val rowsOut = mutableListOf<String>()
                for (sheetKey in sheetKeys) {
                    val sheetXml = entries[sheetKey].orEmpty()
                    Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(sheetXml).forEach { rowMatch ->
                        val cells = mutableListOf<String>()
                        Regex("<c([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL).findAll(rowMatch.groupValues[1]).forEach { cMatch ->
                            val attrs = cMatch.groupValues[1]
                            val body = cMatch.groupValues[2]
                            val isShared = attrs.contains("t=\"s\"")
                            val vVal = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)?.trim()
                            val inlineVal = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)?.trim()
                            val resolved = when {
                                isShared && vVal != null -> vVal.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
                                inlineVal != null -> decodeXmlEntities(inlineVal)
                                vVal != null -> decodeXmlEntities(vVal)
                                else -> ""
                            }
                            if (resolved.isNotBlank()) cells.add(resolved)
                        }
                        if (cells.isNotEmpty()) rowsOut.add(cells.joinToString(" "))
                    }
                }
                return rowsOut.joinToString("\n").takeIf { it.isNotBlank() }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractReadablePdfTextFallback(bytes: ByteArray): String {
        return try {
            val sb = StringBuilder()
            val raw = bytes.toString(Charsets.ISO_8859_1)
            val parenRegex = Regex("""\(([^()\\]{2,140})\)""")

            fun collectPdfTextStrings(source: String) {
                parenRegex.findAll(source).forEach { m ->
                    val s = m.groupValues[1]
                        .replace("\\(", "(")
                        .replace("\\)", ")")
                        .trim()
                    if (s.count { it.isLetterOrDigit() } >= 2) {
                        sb.append(s).append('\n')
                    }
                }
            }

            // 1. Decompress FlateDecode streams inside PDF
            val streamMarker = "stream"
            val endStreamMarker = "endstream"
            var pos = 0
            while (pos < raw.length) {
                val startIdx = raw.indexOf(streamMarker, pos)
                if (startIdx < 0) break
                var dataStart = startIdx + streamMarker.length
                if (dataStart < raw.length && raw[dataStart] == '\r') dataStart++
                if (dataStart < raw.length && raw[dataStart] == '\n') dataStart++
                val endIdx = raw.indexOf(endStreamMarker, dataStart)
                if (endIdx < 0) break
                pos = endIdx + endStreamMarker.length
                val len = (endIdx - dataStart).coerceAtMost(2_000_000)
                if (len > 4) {
                    try {
                        val inflater = java.util.zip.Inflater()
                        inflater.setInput(bytes, dataStart, len)
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(4096)
                        while (!inflater.finished() && out.size() < 2_000_000) {
                            val count = inflater.inflate(buf)
                            if (count <= 0) break
                            out.write(buf, 0, count)
                        }
                        inflater.end()
                        if (out.size() > 0) {
                            collectPdfTextStrings(out.toByteArray().toString(Charsets.ISO_8859_1))
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            // 2. Also scan uncompressed content
            if (sb.isEmpty()) {
                collectPdfTextStrings(raw)
            }
            sb.toString().trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun sniffMimeType(bytes: ByteArray, rawMime: String): String {
        val cleaned = rawMime.substringBefore(';').trim().lowercase()
        if (bytes.size >= 4 &&
            bytes[0] == '%'.code.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'D'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()
        ) {
            return "application/pdf"
        }
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }
        if (bytes.size >= 4 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte()
        ) {
            return "image/webp"
        }
        return cleaned.ifBlank { "image/jpeg" }
    }

    suspend fun parseWorkoutProgramFromBytes(
        bytes: ByteArray,
        mimeType: String
    ): Result<List<ScannedWorkoutProgram>> {
        val normalizedMime = sniffMimeType(bytes, mimeType)
        return try {
            // Support Word (.docx), Excel (.xlsx), and OpenDocument (.odt) files directly
            val officeText = extractOfficeDocumentText(bytes)
            if (!officeText.isNullOrBlank()) {
                return parseWorkoutProgramFromText(officeText)
            }

            val isTextOrJson = normalizedMime.startsWith("text/") ||
                normalizedMime.contains("json") ||
                normalizedMime.contains("csv")
            if (isTextOrJson) {
                return parseWorkoutProgramFromText(bytes.toString(Charsets.UTF_8))
            }

            val apiKey = hasValidGeminiKey()
            if (apiKey == null) {
                if (normalizedMime == "application/pdf") {
                    val extracted = extractReadablePdfTextFallback(bytes)
                    if (extracted.isNotBlank()) {
                        val localFromPdf = parseWorkoutProgramLocally(extracted)
                        if (localFromPdf.isNotEmpty()) {
                            return Result.success(localFromPdf)
                        }
                    }
                }
                return Result.failure(
                    IllegalStateException("Gemini API key is not set in AI Studio Secrets, so image/PDF AI scanning cannot run yet. Use 'Paste Text' below or upload a .txt/.csv/.json/.docx/.xlsx program file.")
                )
            }

            val (preparedBytes, finalMime) = prepareBytesForGemini(bytes, normalizedMime)
            val base64 = android.util.Base64.encodeToString(preparedBytes, android.util.Base64.NO_WRAP)
            val prompt = buildProgramExtractionPrompt(null)
            val request = com.example.api.GenerateContentRequest(
                contents = listOf(
                    com.example.api.Content(
                        parts = listOf(
                            com.example.api.Part(text = prompt),
                            com.example.api.Part(
                                inlineData = com.example.api.InlineData(
                                    mimeType = finalMime,
                                    data = base64
                                )
                            )
                        )
                    )
                ),
                systemInstruction = com.example.api.Content(
                    parts = listOf(
                        com.example.api.Part(
                            text = "You extract structured workout routines from images, screenshots, tables, and PDF documents. Return ONLY valid JSON with no markdown fences or commentary."
                        )
                    )
                )
            )
            val response = callGeminiWithFallback(apiKey, request)
            val raw = extractResponseText(response)
            val programs = parseProgramsJsonResponse(raw)
            if (programs.isEmpty()) {
                if (normalizedMime == "application/pdf") {
                    val extracted = extractReadablePdfTextFallback(bytes)
                    val localFromPdf = if (extracted.isNotBlank()) parseWorkoutProgramLocally(extracted) else emptyList()
                    if (localFromPdf.isNotEmpty()) {
                        return Result.success(localFromPdf)
                    }
                }
                Result.failure(IllegalStateException("Could not detect exercises in the uploaded file. Try a clearer photo or paste the program text below."))
            } else {
                Result.success(programs)
            }
        } catch (e: retrofit2.HttpException) {
            if (normalizedMime == "application/pdf") {
                val extracted = extractReadablePdfTextFallback(bytes)
                val localFromPdf = if (extracted.isNotBlank()) parseWorkoutProgramLocally(extracted) else emptyList()
                if (localFromPdf.isNotEmpty()) {
                    return Result.success(localFromPdf)
                }
            }
            val errBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            val readable = if (!errBody.isNullOrBlank()) {
                try {
                    org.json.JSONObject(errBody).optJSONObject("error")?.optString("message").orEmpty()
                } catch (_: Exception) { "" }
            } else ""
            val suffix = readable.ifBlank { "HTTP ${e.code()}" }
            Result.failure(IllegalStateException("Scan failed ($suffix). Check GEMINI_API_KEY in Secrets or use 'Paste Text' below."))
        } catch (e: Exception) {
            if (normalizedMime == "application/pdf") {
                val extracted = extractReadablePdfTextFallback(bytes)
                val localFromPdf = if (extracted.isNotBlank()) parseWorkoutProgramLocally(extracted) else emptyList()
                if (localFromPdf.isNotEmpty()) {
                    return Result.success(localFromPdf)
                }
            }
            Result.failure(IllegalStateException("Scan failed (${e.localizedMessage ?: "API error"}). Check GEMINI_API_KEY in Secrets or use 'Paste Text' below."))
        }
    }

    suspend fun parseWorkoutProgramFromText(rawText: String): Result<List<ScannedWorkoutProgram>> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("The uploaded text or document is empty."))
        }

        // Fast-path JSON import if user uploaded a JSON program export
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val direct = parseProgramsJsonResponse(trimmed)
            if (direct.isNotEmpty()) {
                return Result.success(direct)
            }
        }

        val apiKey = hasValidGeminiKey()
        if (apiKey != null) {
            try {
                val prompt = buildProgramExtractionPrompt(trimmed)
                val request = com.example.api.GenerateContentRequest(
                    contents = listOf(
                        com.example.api.Content(
                            parts = listOf(com.example.api.Part(text = prompt))
                        )
                    ),
                    systemInstruction = com.example.api.Content(
                        parts = listOf(
                            com.example.api.Part(
                                text = "You extract structured workout routines from text, CSV, or notes. Return ONLY valid JSON with no markdown fences or commentary."
                            )
                        )
                    )
                )
                val response = callGeminiWithFallback(apiKey, request)
                val raw = extractResponseText(response)
                val aiPrograms = parseProgramsJsonResponse(raw)
                if (aiPrograms.isNotEmpty()) {
                    return Result.success(aiPrograms)
                }
            } catch (_: Exception) {
                // Fall back to deterministic offline line parser below
            }
        }

        val localParsed = parseWorkoutProgramLocally(trimmed)
        return if (localParsed.isNotEmpty()) {
            Result.success(localParsed)
        } else {
            Result.failure(IllegalStateException("Could not find any exercises in the text. Format lines like 'Bench Press 4x10 80kg'."))
        }
    }

    private fun buildProgramExtractionPrompt(textContent: String?): String {
        val base = """
            Analyze the workout program and extract all routine days/profiles into strict JSON format.
            Output ONLY a JSON object with this exact schema:
            {
              "programs": [
                {
                  "name": "Routine or Day Name (e.g., Monday: Push Day)",
                  "notes": "Brief goal or focus notes",
                  "exercises": [
                    {
                      "name": "Exercise Name",
                      "category": "One of: Chest, Back, Legs, Arms, Shoulders, Forearms, Calves, Other, Warm-Up, Cool-Down",
                      "targetSets": 4,
                      "targetReps": 10,
                      "targetWeightKg": 0.0,
                      "isSuperSet": false
                    }
                  ]
                }
              ]
            }
            Rules:
            - Extract every exercise shown, preserving order.
            - If reps are a range like 8-12, use the upper number (12) or average integer.
            - If weight is listed in lbs, convert approximately to kg; if not listed, use 0.0.
            - If sets/reps are missing for an exercise, default to targetSets=3, targetReps=10.
        """.trimIndent()
        return if (textContent != null) {
            "$base\n\nProgram text:\n$textContent"
        } else {
            base
        }
    }

    private fun normalizeCategory(raw: String, exerciseName: String): String {
        val r = raw.lowercase().trim()
        val n = exerciseName.lowercase()
        val allowed = listOf("Chest", "Back", "Legs", "Arms", "Shoulders", "Forearms", "Calves", "Other", "Warm-Up", "Cool-Down")
        allowed.firstOrNull { it.equals(r, ignoreCase = true) }?.let { return it }
        return when {
            r.contains("chest") || r.contains("pec") || n.contains("bench") || n.contains("chest") || n.contains("fly") || n.contains("pushup") || n.contains("push-up") -> "Chest"
            r.contains("back") || r.contains("lat") || n.contains("pulldown") || n.contains("pullup") || n.contains("pull-up") || n.contains("chin") || n.contains("row") || n.contains("deadlift") -> "Back"
            r.contains("calf") || n.contains("calf") || n.contains("calves") -> "Calves"
            r.contains("leg") || r.contains("quad") || r.contains("ham") || r.contains("glute") || n.contains("squat") || n.contains("lunge") || n.contains("leg") || n.contains("rdl") -> "Legs"
            r.contains("forearm") || n.contains("wrist") -> "Forearms"
            r.contains("arm") || r.contains("bicep") || r.contains("tricep") || n.contains("curl") || n.contains("tricep") || n.contains("pushdown") || n.contains("dip") || n.contains("skull") -> "Arms"
            r.contains("shoulder") || r.contains("delt") || n.contains("shoulder") || n.contains("lateral") || n.contains("overhead") || n.contains("arnold") || n.contains("ohp") -> "Shoulders"
            r.contains("warm") -> "Warm-Up"
            r.contains("cool") || r.contains("stretch") -> "Cool-Down"
            else -> "Other"
        }
    }

    private fun parseProgramsJsonResponse(rawResponse: String): List<ScannedWorkoutProgram> {
        var cleaned = rawResponse.trim()
        if (cleaned.contains("```")) {
            cleaned = cleaned.replace(Regex("```json", RegexOption.IGNORE_CASE), "")
                .replace("```", "")
                .trim()
        }
        val jsonCandidate = when {
            cleaned.indexOf('{') in 0 until cleaned.length -> {
                val start = cleaned.indexOf('{')
                val end = cleaned.lastIndexOf('}')
                if (end > start) cleaned.substring(start, end + 1) else cleaned
            }
            cleaned.indexOf('[') in 0 until cleaned.length -> {
                val start = cleaned.indexOf('[')
                val end = cleaned.lastIndexOf(']')
                if (end > start) cleaned.substring(start, end + 1) else cleaned
            }
            else -> cleaned
        }

        val result = mutableListOf<ScannedWorkoutProgram>()
        try {
            val programsArray = if (jsonCandidate.startsWith("[")) {
                org.json.JSONArray(jsonCandidate)
            } else {
                val root = org.json.JSONObject(jsonCandidate)
                when {
                    root.has("programs") -> root.optJSONArray("programs")
                    root.has("routines") -> root.optJSONArray("routines")
                    root.has("exercises") -> org.json.JSONArray().put(root)
                    else -> null
                }
            } ?: return emptyList()

            for (i in 0 until programsArray.length()) {
                val obj = programsArray.optJSONObject(i) ?: continue
                val progName = obj.optString("name", "").ifBlank {
                    obj.optString("title", "Scanned Program ${i + 1}")
                }.trim()
                val notes = obj.optString("notes", "").ifBlank {
                    obj.optString("description", "Imported program")
                }.trim()
                val exArr = obj.optJSONArray("exercises") ?: continue
                val exercises = mutableListOf<com.example.data.ProfileExercise>()
                for (j in 0 until exArr.length()) {
                    val exObj = exArr.optJSONObject(j) ?: continue
                    val exName = exObj.optString("name", "").trim()
                    if (exName.isEmpty()) continue
                    val cat = normalizeCategory(exObj.optString("category", ""), exName)
                    val targetSets = exObj.optInt("targetSets", exObj.optInt("sets", 4)).coerceIn(1, 20)
                    val targetReps = exObj.optInt("targetReps", exObj.optInt("reps", 10)).coerceIn(1, 300)
                    val weight = exObj.optDouble("targetWeightKg", exObj.optDouble("weightKg", 0.0)).coerceAtLeast(0.0)
                    val superSet = exObj.optBoolean("isSuperSet", false)
                    exercises.add(
                        com.example.data.ProfileExercise(
                            name = exName,
                            category = cat,
                            targetSets = targetSets,
                            targetReps = targetReps,
                            targetWeightKg = weight,
                            isCompleted = false,
                            isSuperSet = superSet
                        )
                    )
                }
                if (exercises.isNotEmpty()) {
                    result.add(ScannedWorkoutProgram(progName, notes, exercises))
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun parseWorkoutProgramLocally(rawText: String): List<ScannedWorkoutProgram> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val programs = mutableListOf<ScannedWorkoutProgram>()
        var currentTitle = "Imported Workout Program"
        var hasExplicitHeader = false
        var currentExercises = mutableListOf<com.example.data.ProfileExercise>()

        val setsRepsRangeRegex = Regex("""(\d+)\s*[xX×]\s*(\d+)\s*[-–]\s*(\d+)""")
        val setsRepsRegex = Regex("""(\d+)\s*[xX×]\s*(\d+)""")
        val setsWordRegex = Regex("""(\d+)\s*sets?(?:\s+of)?""", RegexOption.IGNORE_CASE)
        val repsRangeWordRegex = Regex("""(\d+)\s*[-–]\s*(\d+)\s*reps?""", RegexOption.IGNORE_CASE)
        val repsWordRegex = Regex("""(\d+)\s*reps?""", RegexOption.IGNORE_CASE)
        val weightKgRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:kg|kgs)""", RegexOption.IGNORE_CASE)
        val weightLbsRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:lb|lbs|pounds?)""", RegexOption.IGNORE_CASE)
        val dayHeaderRegex = Regex("""^(day\s*\d+|week\s*\d+|monday|tuesday|wednesday|thursday|friday|saturday|sunday|push|pull|legs|upper|lower|full\s*body|arms|chest|back|shoulders|core)\b.*""", RegexOption.IGNORE_CASE)

        for ((idx, line) in lines.withIndex()) {
            // Skip CSV header rows
            if (line.startsWith("Exercise,", ignoreCase = true) || line.startsWith("Program,", ignoreCase = true)) {
                continue
            }

            // Handle CSV style: "Bench Press,4,10,80"
            if (line.contains(',') && !line.contains(" x ", ignoreCase = true)) {
                val cols = line.split(',').map { it.trim().trim('"') }
                if (cols.size >= 3) {
                    val name = cols[0]
                    val setsVal = cols[1].toIntOrNull()
                    val repsVal = cols[2].toIntOrNull()
                    val weightVal = cols.getOrNull(3)?.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && (setsVal != null || repsVal != null)) {
                        currentExercises.add(
                            com.example.data.ProfileExercise(
                                name = name,
                                category = normalizeCategory("", name),
                                targetSets = (setsVal ?: 3).coerceIn(1, 20),
                                targetReps = (repsVal ?: 10).coerceIn(1, 300),
                                targetWeightKg = weightVal.coerceAtLeast(0.0)
                            )
                        )
                        continue
                    }
                }
            }

            val cleanLine = line.removePrefix("-").removePrefix("*").removePrefix("•").trim()
            val hasNumbers = setsRepsRegex.containsMatchIn(cleanLine) ||
                setsRepsRangeRegex.containsMatchIn(cleanLine) ||
                setsWordRegex.containsMatchIn(cleanLine) ||
                repsWordRegex.containsMatchIn(cleanLine) ||
                repsRangeWordRegex.containsMatchIn(cleanLine)

            if (!hasNumbers && (dayHeaderRegex.matches(cleanLine.trimEnd(':')) || cleanLine.endsWith(":"))) {
                if (currentExercises.isNotEmpty()) {
                    programs.add(ScannedWorkoutProgram(currentTitle, "Imported routine", currentExercises.toList()))
                    currentExercises = mutableListOf()
                }
                currentTitle = cleanLine.trimEnd(':')
                hasExplicitHeader = true
                continue
            }

            var targetSets = 3
            var targetReps = 10
            var targetWeight = 0.0
            var namePart = cleanLine

            val sxrr = setsRepsRangeRegex.find(cleanLine)
            val sxr = setsRepsRegex.find(cleanLine)
            if (sxrr != null) {
                targetSets = sxrr.groupValues[1].toIntOrNull()?.coerceIn(1, 20) ?: 3
                targetReps = sxrr.groupValues[3].toIntOrNull()?.coerceIn(1, 300) ?: 10
                namePart = cleanLine.substring(0, sxrr.range.first).trim()
            } else if (sxr != null) {
                targetSets = sxr.groupValues[1].toIntOrNull()?.coerceIn(1, 20) ?: 3
                targetReps = sxr.groupValues[2].toIntOrNull()?.coerceIn(1, 300) ?: 10
                namePart = cleanLine.substring(0, sxr.range.first).trim()
            } else {
                val sm = setsWordRegex.find(cleanLine)
                val rrm = repsRangeWordRegex.find(cleanLine)
                val rm = repsWordRegex.find(cleanLine)
                if (sm != null) {
                    targetSets = sm.groupValues[1].toIntOrNull()?.coerceIn(1, 20) ?: 3
                    namePart = namePart.replace(sm.value, "")
                }
                if (rrm != null) {
                    targetReps = rrm.groupValues[2].toIntOrNull()?.coerceIn(1, 300) ?: 10
                    namePart = namePart.replace(rrm.value, "")
                } else if (rm != null) {
                    targetReps = rm.groupValues[1].toIntOrNull()?.coerceIn(1, 300) ?: 10
                    namePart = namePart.replace(rm.value, "")
                }
            }

            val wmKg = weightKgRegex.find(cleanLine)
            val wmLbs = weightLbsRegex.find(cleanLine)
            if (wmKg != null) {
                targetWeight = wmKg.groupValues[1].toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                namePart = namePart.replace(wmKg.value, "")
            } else if (wmLbs != null) {
                val lbs = wmLbs.groupValues[1].toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                targetWeight = kotlin.math.round(lbs * 0.453592 * 2.0) / 2.0
                namePart = namePart.replace(wmLbs.value, "")
            }

            val finalName = namePart
                .replace(Regex("""^\d+[.)]\s*"""), "")
                .trim(' ', '-', ':', '@', ',', '(', ')')
            if (finalName.length >= 2) {
                val treatFirstLineAsTitle = !hasNumbers && !hasExplicitHeader && idx == 0 && lines.size > 1 && wmKg == null && wmLbs == null
                if (treatFirstLineAsTitle) {
                    currentTitle = finalName
                    hasExplicitHeader = true
                } else if (hasNumbers || wmKg != null || wmLbs != null || hasExplicitHeader || currentExercises.isNotEmpty() || lines.size == 1) {
                    currentExercises.add(
                        com.example.data.ProfileExercise(
                            name = finalName,
                            category = normalizeCategory("", finalName),
                            targetSets = targetSets,
                            targetReps = targetReps,
                            targetWeightKg = targetWeight
                        )
                    )
                }
            }
        }

        if (currentExercises.isNotEmpty()) {
            programs.add(ScannedWorkoutProgram(currentTitle, "Imported routine", currentExercises.toList()))
        }
        return programs
    }
}
