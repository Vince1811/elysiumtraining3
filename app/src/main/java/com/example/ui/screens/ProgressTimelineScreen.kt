package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.InlineData
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.CloudflareR2Service
import com.example.data.CloudflareSyncService
import com.example.data.PreferencesManager
import com.example.data.ProgressPhoto
import com.example.data.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private data class ExtractedPhotoMetadata(
    val timestampMs: Long,
    val detailsSummary: String,
    val dateSource: String
)

private fun parseExifDateString(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    val patterns = listOf(
        "yyyy:MM:dd HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy:MM:dd HH:mm",
        "yyyy-MM-dd'T'HH:mm:ss"
    )
    for (pattern in patterns) {
        try {
            val parsed = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(raw.trim())
            if (parsed != null && parsed.time > 946684800000L) {
                return parsed.time
            }
        } catch (_: Exception) {
        }
    }
    return null
}

private fun parseTimestampFromFilename(displayName: String?): Long? {
    if (displayName.isNullOrBlank()) return null
    val name = displayName.trim()
    // Match YYYYMMDD_HHmmss or YYYY-MM-DD HH.mm.ss / YYYY-MM-DD at HH.mm.ss
    val dateTimeMatch = Regex("""(20\d{2})[-_]?(\d{2})[-_]?(\d{2})(?:[\sT_-]|at)+(\d{2})[-_.:]?(\d{2})[-_.:]?(\d{2})""", RegexOption.IGNORE_CASE).find(name)
    if (dateTimeMatch != null) {
        try {
            val (y, m, d, hh, mm, ss) = dateTimeMatch.destructured
            val parsed = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { isLenient = false }
                .parse("$y-$m-$d $hh:$mm:$ss")
            if (parsed != null && parsed.time > 946684800000L) return parsed.time
        } catch (_: Exception) {
        }
    }
    val dateOnlyMatch = Regex("""(20\d{2})[-_](\d{2})[-_](\d{2})""").find(name)
        ?: Regex("""(?:IMG|PXL|VID|Screenshot)[_-](20\d{2})(\d{2})(\d{2})""", RegexOption.IGNORE_CASE).find(name)
    if (dateOnlyMatch != null) {
        try {
            val (y, m, d) = dateOnlyMatch.destructured
            val parsed = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { isLenient = false }
                .parse("$y-$m-$d 12:00:00")
            if (parsed != null && parsed.time > 946684800000L) return parsed.time
        } catch (_: Exception) {
        }
    }
    return null
}

private suspend fun extractPhotoMetadata(
    context: Context,
    file: File,
    sourceUri: Uri? = null,
    isCameraCapture: Boolean = false
): ExtractedPhotoMetadata = withContext(Dispatchers.IO) {
    var capturedTs: Long? = null
    var dateSource = if (isCameraCapture) "Camera Capture" else "Upload Date"
    var cameraLabel = ""

    fun readFromExif(exif: ExifInterface) {
        if (capturedTs == null) {
            val exifDateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            val parsed = parseExifDateString(exifDateStr)
            if (parsed != null) {
                capturedTs = parsed
                dateSource = "Photo EXIF"
            }
        }
        if (cameraLabel.isBlank()) {
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
            cameraLabel = when {
                model.isNotBlank() && make.isNotBlank() && !model.contains(make, ignoreCase = true) -> "$make $model"
                model.isNotBlank() -> model
                make.isNotBlank() -> make
                else -> ""
            }
        }
    }

    // 1a. Try EXIF directly from sourceUri InputStream (before any transcoding)
    if (sourceUri != null) {
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                readFromExif(ExifInterface(stream))
            }
        } catch (_: Exception) {
        }
    }

    // 1b. Try EXIF from the saved image file
    try {
        readFromExif(ExifInterface(file.absolutePath))
    } catch (_: Exception) {
    }

    // 2. Query MediaStore date columns & Display Name from sourceUri
    var displayName = ""
    if (sourceUri != null) {
        try {
            context.contentResolver.query(
                sourceUri,
                arrayOf(
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.MediaColumns.DATE_ADDED,
                    android.provider.OpenableColumns.DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx).orEmpty()
                    if (capturedTs == null) {
                        val takenIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                        val modIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                        val addedIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                        val takenVal = if (takenIdx >= 0) cursor.getLong(takenIdx) else 0L
                        val modValSec = if (modIdx >= 0) cursor.getLong(modIdx) else 0L
                        val addedValSec = if (addedIdx >= 0) cursor.getLong(addedIdx) else 0L
                        when {
                            takenVal > 946684800000L -> {
                                capturedTs = takenVal
                                dateSource = "Photo Date Taken"
                            }
                            modValSec > 946684800L -> {
                                capturedTs = modValSec * 1000L
                                dateSource = "File Modified Date"
                            }
                            addedValSec > 946684800L -> {
                                capturedTs = addedValSec * 1000L
                                dateSource = "Media Added Date"
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    // 3. Fallback to camera filename date (e.g., IMG_20250412_143022.jpg / WhatsApp Image ...)
    if (capturedTs == null && displayName.isNotBlank()) {
        val parsedNameTs = parseTimestampFromFilename(displayName)
        if (parsedNameTs != null) {
            capturedTs = parsedNameTs
            dateSource = "Photo Filename Date"
        }
    }

    // 4. Extract dimensions & file size
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
    val dims = if (boundsOpts.outWidth > 0 && boundsOpts.outHeight > 0) {
        "${boundsOpts.outWidth}×${boundsOpts.outHeight} px"
    } else ""
    val kb = (file.length() / 1024L).coerceAtLeast(1L)
    val sizeStr = if (kb >= 1024L) String.format(Locale.US, "%.1f MB", kb / 1024.0) else "$kb KB"

    val details = listOf(dateSource, dims, sizeStr, cameraLabel)
        .filter { it.isNotBlank() }
        .joinToString(" • ")

    ExtractedPhotoMetadata(
        timestampMs = capturedTs ?: System.currentTimeMillis(),
        detailsSummary = details,
        dateSource = dateSource
    )
}

private fun stampExifDateIfMissing(file: File, timestampMs: Long) {
    try {
        val exif = ExifInterface(file.absolutePath)
        val existing = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        if (existing.isNullOrBlank()) {
            val formatted = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(timestampMs))
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, formatted)
            exif.setAttribute(ExifInterface.TAG_DATETIME, formatted)
            exif.saveAttributes()
        }
    } catch (_: Exception) {
    }
}

private suspend fun compressPhotoToBase64(photoPath: String): String? = withContext(Dispatchers.IO) {
    try {
        val file = File(photoPath)
        if (!file.exists() || file.length() == 0L) return@withContext null
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        val maxDim = 1024
        var sampleSize = 1
        while (boundsOpts.outWidth / sampleSize > maxDim || boundsOpts.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return@withContext null
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val bytes = stream.toByteArray()
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }
}

private fun hasConfiguredGeminiKey(): Boolean {
    val key = BuildConfig.GEMINI_API_KEY.trim()
    return key.isNotEmpty() && key != "GEMINI_API_KEY" && key != "MY_GEMINI_API_KEY"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressTimelineScreen(
    repository: WorkoutRepository,
    userId: Int
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { PreferencesManager(context) }
    val r2Service = remember { CloudflareR2Service(context) }

    val photos by repository.getProgressPhotosForUser(userId).collectAsState(initial = emptyList())

    var captionInput by remember { mutableStateOf("") }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var tempCapturedUriPath by remember { mutableStateOf<String?>(null) }
    var tempExtractedMetadata by remember { mutableStateOf<ExtractedPhotoMetadata?>(null) }

    // State for tracking which photo is actively being analyzed by AI or uploaded to R2
    var analyzingPhotoId by remember { mutableStateOf<Int?>(null) }
    var isCloudSyncing by remember { mutableStateOf(false) }
    var uploadingPhotoIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val uploadErrorById = remember { mutableStateMapOf<Int, String>() }
    val attemptedAutoUploadIds = remember { mutableStateListOf<Int>() }

    fun enqueuePhotoUploadToR2(photo: ProgressPhoto, showToast: Boolean = false) {
        if (userId == -1 || photo.photoPath.isBlank()) return
        uploadingPhotoIds = uploadingPhotoIds + photo.id
        CloudflareR2Service.r2SyncScope.launch {
            val resultKey = withContext(Dispatchers.IO) {
                val ownerUsername = repository.getUserById(userId)?.username.orEmpty()
                val uploadedKey = r2Service.uploadProgressPhoto(photo, preferences, ownerUsername)
                if (uploadedKey != null) {
                    repository.updateProgressPhotoR2Key(photo.id, uploadedKey)
                    CloudflareSyncService(context.applicationContext).syncAllUserDataToD1(userId, repository, preferences)
                }
                uploadedKey
            }
            withContext(Dispatchers.Main) {
                uploadingPhotoIds = uploadingPhotoIds - photo.id
                if (resultKey != null) {
                    uploadErrorById.remove(photo.id)
                    if (showToast) {
                        Toast.makeText(context, "Photo saved to Cloudflare R2! ☁️", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val err = withContext(Dispatchers.IO) { preferences.lastR2ErrorFlow.first() }
                        .ifBlank { "R2 upload failed — check R2 credentials or network" }
                    uploadErrorById[photo.id] = err
                    if (showToast) {
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Initial sync of progress photos with R2 cloud for this user & all registered users
    LaunchedEffect(userId) {
        if (userId != -1) {
            isCloudSyncing = true
            withContext(Dispatchers.IO) {
                r2Service.syncProgressPhotosForUsers(repository, preferences)
            }
            isCloudSyncing = false
        }
    }

    // Automatically ensure every local progress timeline photo is uploaded to R2
    LaunchedEffect(photos.map { it.id to it.r2Key }, userId) {
        if (userId != -1) {
            photos.filter { it.r2Key.isBlank() && it.photoPath.isNotBlank() && it.id !in attemptedAutoUploadIds }
                .forEach { pendingPhoto ->
                    val file = File(pendingPhoto.photoPath)
                    if (file.exists() && file.length() > 0L) {
                        attemptedAutoUploadIds.add(pendingPhoto.id)
                        enqueuePhotoUploadToR2(pendingPhoto, showToast = false)
                    }
                }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && userId != -1) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null && bytes.isNotEmpty()) {
                            val tempFile = File(context.filesDir, "progress_tmp_${userId}_${System.currentTimeMillis()}.jpg")
                            tempFile.writeBytes(bytes)
                            val meta = extractPhotoMetadata(context, tempFile, sourceUri = uri, isCameraCapture = false)
                            val finalFile = File(context.filesDir, "progress_${userId}_${meta.timestampMs}.jpg")
                            val isJpg = bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
                            if (isJpg) {
                                if (tempFile.absolutePath != finalFile.absolutePath) {
                                    tempFile.copyTo(finalFile, overwrite = true)
                                    tempFile.delete()
                                }
                            } else {
                                val bmp = BitmapFactory.decodeFile(tempFile.absolutePath)
                                if (bmp != null) {
                                    finalFile.outputStream().use { out ->
                                        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                    }
                                    bmp.recycle()
                                    tempFile.delete()
                                } else {
                                    tempFile.copyTo(finalFile, overwrite = true)
                                    tempFile.delete()
                                }
                            }
                            stampExifDateIfMissing(finalFile, meta.timestampMs)
                            finalFile.absolutePath to meta
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
                if (result != null) {
                    tempCapturedUriPath = result.first
                    tempExtractedMetadata = result.second
                    showAddDialog = true
                } else {
                    Toast.makeText(context, "Could not load selected photo.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val tmpPath = pendingCameraPath
        if (success && !tmpPath.isNullOrBlank() && userId != -1) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val src = File(tmpPath)
                        if (src.exists() && src.length() > 0L) {
                            val meta = extractPhotoMetadata(context, src, sourceUri = null, isCameraCapture = true)
                            val dest = File(context.filesDir, "progress_${userId}_${meta.timestampMs}.jpg")
                            src.copyTo(dest, overwrite = true)
                            src.delete()
                            stampExifDateIfMissing(dest, meta.timestampMs)
                            dest.absolutePath to meta
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
                if (result != null) {
                    tempCapturedUriPath = result.first
                    tempExtractedMetadata = result.second
                    showAddDialog = true
                }
            }
        }
    }

    // Baseline calculation: earliest photo in user's timeline
    val baselinePhoto = remember(photos) {
        photos.minByOrNull { it.timestamp }
    }
    val baselineTimestamp = baselinePhoto?.timestamp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("progress_timeline_screen")
    ) {
        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Progress Timeline 📸",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track your physical results from start to finish",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // Sync with Cloud button
            IconButton(
                onClick = {
                    attemptedAutoUploadIds.clear()
                    isCloudSyncing = true
                    Toast.makeText(context, "Saving & syncing photos with Cloudflare R2...", Toast.LENGTH_SHORT).show()
                    CloudflareR2Service.r2SyncScope.launch {
                        withContext(Dispatchers.IO) {
                            r2Service.syncProgressPhotosForUsers(repository, preferences)
                            if (userId != -1) {
                                CloudflareSyncService(context.applicationContext).syncAllUserDataToD1(userId, repository, preferences)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            isCloudSyncing = false
                            Toast.makeText(context, "R2 Photo Sync finished!", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.testTag("sync_r2_photos_button")
            ) {
                if (isCloudSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Filled.CloudSync,
                        contentDescription = "Sync R2 Photos",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Action Buttons: Upload or Take Photo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("upload_progress_photo_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Upload Photo")
            }

            OutlinedButton(
                onClick = {
                    try {
                        val tmpFile = File(context.cacheDir, "prog_cam_${System.currentTimeMillis()}.jpg")
                        pendingCameraPath = tmpFile.absolutePath
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tmpFile)
                        cameraLauncher.launch(uri)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Camera error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("take_progress_photo_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Take Photo")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "No progress photos uploaded yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Upload or snap your first photo to extract its date & photo data, back it up to Cloudflare R2, and get AI physique feedback!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    val daysSinceBaseline = if (baselineTimestamp != null) {
                        ((photo.timestamp - baselineTimestamp) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
                    } else 0

                    val isBaseline = photo.id == baselinePhoto?.id

                    // Lazily inspect live file details if stored photoDetails is empty
                    var liveDetails by remember(photo.id, photo.photoDetails, photo.photoPath) {
                        mutableStateOf(photo.photoDetails)
                    }
                    LaunchedEffect(photo.id, photo.photoPath) {
                        if (liveDetails.isBlank() && photo.photoPath.isNotBlank()) {
                            val file = File(photo.photoPath)
                            if (file.exists() && file.length() > 0L) {
                                val meta = extractPhotoMetadata(context, file)
                                liveDetails = meta.detailsSummary
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("progress_card_${photo.id}"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Top Row: Milestone Badge & Delete Action
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isBaseline) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = if (isBaseline) "🌱 Baseline • Starting Point" else "📈 Day ${daysSinceBaseline + 1} • Week ${(daysSinceBaseline / 7) + 1} (+${daysSinceBaseline}d)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBaseline) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val ownerUsername = withContext(Dispatchers.IO) {
                                                repository.getUserById(userId)?.username.orEmpty()
                                            }
                                            try {
                                                if (photo.photoPath.isNotBlank()) File(photo.photoPath).delete()
                                            } catch (_: Exception) {}
                                            repository.deleteProgressPhotoById(photo.id)
                                            CloudflareR2Service.r2SyncScope.launch {
                                                r2Service.deleteProgressPhoto(photo, preferences, ownerUsername)
                                            }
                                            CloudflareSyncService.appSyncScope.launch {
                                                CloudflareSyncService(context).syncAllUserDataToD1(userId, repository, preferences)
                                            }
                                            Toast.makeText(context, "Progress photo deleted", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete photo",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Photo Container
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(290.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                val imageFile = remember(photo.photoPath) {
                                    if (photo.photoPath.isNotBlank()) File(photo.photoPath) else null
                                }
                                if (imageFile != null && imageFile.exists() && imageFile.length() > 0L) {
                                    AsyncImage(
                                        model = imageFile,
                                        contentDescription = "Progress Photo taken on ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(photo.timestamp))}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "Loading photo from Cloudflare R2...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // PHOTO DATA SECTION: Extracted Date Taken, Time, Photo EXIF/File Data & Cloudflare R2 Status
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("photo_metadata_card_${photo.id}")
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val dateFormatted = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date(photo.timestamp))
                                    val timeFormatted = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(photo.timestamp))
                                    val isUploadingThis = photo.id in uploadingPhotoIds
                                    val uploadError = uploadErrorById[photo.id]

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.CalendarMonth,
                                                contentDescription = "Photo Date Taken",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = "Date Taken: $dateFormatted",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.AccessTime,
                                                contentDescription = "Photo Time",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = timeFormatted,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Photo EXIF / Resolution / Size / Camera Details
                                    if (liveDetails.isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Info,
                                                contentDescription = "Photo Data",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "Photo Info: $liveDetails",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Cloudflare R2 Storage Status + Manual Upload/Retry Button
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            when {
                                                photo.r2Key.isNotBlank() -> {
                                                    Icon(
                                                        imageVector = Icons.Filled.CloudDone,
                                                        contentDescription = "Uploaded to Cloudflare R2",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = "Saved to Cloudflare R2 (${photo.r2Key})",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                isUploadingThis -> {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(14.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = "Uploading photo to Cloudflare R2...",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                                else -> {
                                                    Icon(
                                                        imageVector = Icons.Filled.CloudUpload,
                                                        contentDescription = "Upload to R2",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = uploadError ?: "Pending upload to Cloudflare R2",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }

                                        if (photo.r2Key.isBlank() && !isUploadingThis) {
                                            TextButton(
                                                onClick = {
                                                    enqueuePhotoUploadToR2(photo, showToast = true)
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.testTag("retry_r2_upload_btn_${photo.id}")
                                            ) {
                                                Text("Upload to R2", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }

                                    if (photo.caption.isNotBlank()) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                        Text(
                                            text = "📝 ${photo.caption}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // AI PHOTO ANALYSIS & FEEDBACK SECTION
                            if (analyzingPhotoId == photo.id) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                        Column {
                                            Text(
                                                text = "Analyzing photo with AI Coach...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                text = "Reviewing physique, posture, symmetry & progress vs baseline",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            } else if (photo.aiFeedback.isNotBlank()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Filled.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "AI Physique & Progress Feedback",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    triggerAiAnalysis(
                                                        photo = photo,
                                                        baselinePhoto = if (baselinePhoto?.id != photo.id) baselinePhoto else null,
                                                        userId = userId,
                                                        context = context,
                                                        scope = scope,
                                                        repository = repository,
                                                        preferences = preferences,
                                                        onStart = { analyzingPhotoId = photo.id },
                                                        onFinish = { analyzingPhotoId = null }
                                                    )
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Refresh,
                                                    contentDescription = "Re-analyze with AI",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(8.dp))

                                        Text(
                                            text = photo.aiFeedback,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                                        )
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        triggerAiAnalysis(
                                            photo = photo,
                                            baselinePhoto = if (baselinePhoto?.id != photo.id) baselinePhoto else null,
                                            userId = userId,
                                            context = context,
                                            scope = scope,
                                            repository = repository,
                                            preferences = preferences,
                                            onStart = { analyzingPhotoId = photo.id },
                                            onFinish = { analyzingPhotoId = null }
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 44.dp)
                                        .testTag("analyze_photo_btn_${photo.id}"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Analyze Photo & Get AI Feedback 🤖",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog for previewing extracted photo date/metadata & adding caption before saving
    if (showAddDialog && tempCapturedUriPath != null) {
        val meta = tempExtractedMetadata
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                tempCapturedUriPath = null
                tempExtractedMetadata = null
                captionInput = ""
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Progress Photo")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = File(tempCapturedUriPath!!),
                            contentDescription = "Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (meta != null) {
                        val dateText = SimpleDateFormat("EEE, MMM d, yyyy • hh:mm a", Locale.getDefault()).format(Date(meta.timestampMs))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "📅 Detected Date: $dateText",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                if (meta.detailsSummary.isNotBlank()) {
                                    Text(
                                        text = meta.detailsSummary,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = captionInput,
                        onValueChange = { captionInput = it },
                        label = { Text("Caption (e.g. Week 4 Check-in, Morning fasting)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val path = tempCapturedUriPath
                        val cap = captionInput.trim()
                        val photoMeta = tempExtractedMetadata
                        showAddDialog = false
                        tempCapturedUriPath = null
                        tempExtractedMetadata = null
                        captionInput = ""
                        if (path != null && userId != -1) {
                            scope.launch {
                                val photoTs = photoMeta?.timestampMs ?: System.currentTimeMillis()
                                val photoDetails = photoMeta?.detailsSummary.orEmpty()
                                val newPhoto = ProgressPhoto(
                                    userId = userId,
                                    photoPath = path,
                                    caption = cap,
                                    timestamp = photoTs,
                                    photoDetails = photoDetails
                                )
                                val insertedId = repository.insertProgressPhoto(newPhoto).toInt()
                                val savedPhoto = newPhoto.copy(id = insertedId)
                                Toast.makeText(context, "Saved to timeline — uploading to Cloudflare R2! 📸", Toast.LENGTH_SHORT).show()

                                // Upload persistently to Cloudflare R2 under user's folder
                                attemptedAutoUploadIds.add(insertedId)
                                enqueuePhotoUploadToR2(savedPhoto, showToast = true)

                                // Auto-analyze photo right after upload if Gemini API key is configured
                                if (hasConfiguredGeminiKey()) {
                                    triggerAiAnalysis(
                                        photo = savedPhoto,
                                        baselinePhoto = if (baselinePhoto?.id != savedPhoto.id) baselinePhoto else null,
                                        userId = userId,
                                        context = context,
                                        scope = scope,
                                        repository = repository,
                                        preferences = preferences,
                                        onStart = { analyzingPhotoId = insertedId },
                                        onFinish = { analyzingPhotoId = null }
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Text("Save & Analyze")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        try {
                            tempCapturedUriPath?.let { File(it).delete() }
                        } catch (_: Exception) {}
                        tempCapturedUriPath = null
                        tempExtractedMetadata = null
                        captionInput = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun triggerAiAnalysis(
    photo: ProgressPhoto,
    baselinePhoto: ProgressPhoto?,
    userId: Int,
    context: Context,
    scope: CoroutineScope,
    repository: WorkoutRepository,
    preferences: PreferencesManager,
    onStart: () -> Unit,
    onFinish: () -> Unit
) {
    if (!hasConfiguredGeminiKey()) {
        Toast.makeText(
            context,
            "Please add your GEMINI_API_KEY in AI Studio Secrets to enable AI photo analysis.",
            Toast.LENGTH_LONG
        ).show()
        return
    }

    onStart()
    scope.launch {
        try {
            val base64Data = compressPhotoToBase64(photo.photoPath)
            if (base64Data.isNullOrBlank()) {
                Toast.makeText(context, "Could not process image file for AI analysis", Toast.LENGTH_SHORT).show()
                onFinish()
                return@launch
            }

            // Load user's saved body stats & measurements for personalized physique coaching
            val statsContext = withContext(Dispatchers.IO) {
                val weight = preferences.userWeightFlow.first()
                val height = preferences.userHeightFlow.first()
                val bmi = preferences.userBmiFlow.first()
                val bodyFat = preferences.userBodyFatFlow.first()
                val bicep = preferences.bodyBicepFlow.first()
                val chest = preferences.bodyChestFlow.first()
                val middle = preferences.bodyMiddleFlow.first()
                val quads = preferences.bodyQuadsFlow.first()
                val calves = preferences.bodyCalvesFlow.first()
                val neck = preferences.bodyNeckFlow.first()

                buildList {
                    if (weight.isNotBlank()) add("Weight: ${weight} kg")
                    if (height.isNotBlank()) add("Height: ${height} cm")
                    if (bmi.isNotBlank()) add("BMI: $bmi")
                    if (bodyFat.isNotBlank()) add("Body Fat: ${bodyFat}%")
                    if (bicep.isNotBlank()) add("Bicep: ${bicep} cm")
                    if (chest.isNotBlank()) add("Chest: ${chest} cm")
                    if (middle.isNotBlank()) add("Middle/Waist: ${middle} cm")
                    if (quads.isNotBlank()) add("Quads: ${quads} cm")
                    if (calves.isNotBlank()) add("Calves: ${calves} cm")
                    if (neck.isNotBlank()) add("Neck: ${neck} cm")
                }.joinToString(", ")
            }

            val baselineBase64 = if (baselinePhoto != null && baselinePhoto.photoPath.isNotBlank()) {
                compressPhotoToBase64(baselinePhoto.photoPath)
            } else null

            val dateLabel = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(photo.timestamp))
            val parts = mutableListOf<Part>()

            val comparisonInstruction = if (baselineBase64 != null) {
                val baseDateLabel = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(baselinePhoto!!.timestamp))
                "The FIRST image is the athlete's BASELINE photo from $baseDateLabel, and the SECOND image is their CURRENT progress photo from $dateLabel. Compare visible physique progress between the two!"
            } else {
                "Analyze this athlete's fitness progress photo taken on $dateLabel."
            }

            val prompt = """
                You are an elite certified strength coach, kinesiologist, and physique specialist.
                $comparisonInstruction
                ${if (photo.caption.isNotBlank()) "User note: \"${photo.caption}\"" else ""}
                ${if (statsContext.isNotBlank()) "Athlete body stats: $statsContext" else ""}

                Provide clear, constructive, encouraging coaching feedback structured cleanly:
                1. Physique, Posture & Balance: Observations on stance, posture alignment, and overall build.
                2. Muscle Definition & Symmetry: Visible strengths across shoulders, chest, arms, core, or legs${if (baselineBase64 != null) " and changes compared to baseline" else ""}.
                3. Next-Phase Training Cues: 2-3 specific exercise/hypertrophy or conditioning recommendations tailored to what you observe.
                4. Encouragement: Short motivating closing remark.
            """.trimIndent()

            parts.add(Part(text = prompt))
            if (baselineBase64 != null) {
                parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = baselineBase64)))
            }
            parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Data)))

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = parts)),
                systemInstruction = Content(
                    parts = listOf(Part(text = "You are an elite bodybuilding and fitness coach. Be objective, specific, positive, and concise."))
                )
            )

            val apiKey = BuildConfig.GEMINI_API_KEY.trim()
            val feedbackText = withContext(Dispatchers.IO) {
                val resp = com.example.model.AIStudyRepository.generateContentResilient(apiKey, request)
                resp.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            }

            if (!feedbackText.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    repository.updateProgressPhotoFeedback(photo.id, feedbackText.trim())
                    CloudflareSyncService(context).syncAllUserDataToD1(userId, repository, preferences)
                }
                Toast.makeText(context, "AI Physique analysis complete! 🎯", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "AI analysis returned an empty response. Please try again.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "AI analysis error: ${e.localizedMessage ?: "Network/API error"}", Toast.LENGTH_LONG).show()
        } finally {
            onFinish()
        }
    }
}
