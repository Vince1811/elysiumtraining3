package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.CloudflareR2Service
import com.example.data.CloudflareSyncService
import com.example.data.PreferencesManager
import com.example.data.WorkoutRepository
import com.example.ui.screens.ExerciseGuidesList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TopBarAccountButtonAndMenu(
    userId: Int,
    repository: WorkoutRepository,
    preferences: PreferencesManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pendingD1Upload by preferences.pendingD1UploadFlow.collectAsState(initial = false)
    val lastD1SyncTime by preferences.lastD1SyncTimeFlow.collectAsState(initial = 0L)
    val lastD1Error by preferences.lastD1ErrorFlow.collectAsState(initial = "")
    val pendingR2Upload by preferences.pendingR2UploadFlow.collectAsState(initial = false)
    val lastR2SyncTime by preferences.lastR2SyncTimeFlow.collectAsState(initial = 0L)
    val lastR2Error by preferences.lastR2ErrorFlow.collectAsState(initial = "")
    val profilePicturePath by preferences.profilePicturePathFlow(userId).collectAsState(initial = "")

    var activeUsername by remember { mutableStateOf("") }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showProfilePicDialog by remember { mutableStateOf(false) }
    var pendingAvatarCameraPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        activeUsername = if (userId != -1) {
            repository.getUserById(userId)?.username.orEmpty()
        } else {
            ""
        }
    }

    val avatarGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && userId != -1) {
            scope.launch {
                val savedPath = withContext(Dispatchers.IO) {
                    try {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null && bytes.isNotEmpty()) {
                            val oldPath = profilePicturePath
                            val destFile = File(context.filesDir, "avatar_${userId}_${System.currentTimeMillis()}.jpg")
                            destFile.writeBytes(bytes)
                            if (oldPath.isNotBlank() && oldPath.startsWith(context.filesDir.absolutePath)) {
                                File(oldPath).delete()
                            }
                            destFile.absolutePath
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
                if (savedPath != null) {
                    preferences.saveProfilePicturePath(userId, savedPath)
                    Toast.makeText(context, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                    CloudflareR2Service.r2SyncScope.launch {
                        CloudflareR2Service(context.applicationContext)
                            .uploadUserProfilePicture(userId, activeUsername, File(savedPath), preferences)
                    }
                } else {
                    Toast.makeText(context, "Could not load selected picture.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val avatarCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val tempPath = pendingAvatarCameraPath
        if (success && !tempPath.isNullOrBlank() && userId != -1) {
            scope.launch {
                val savedPath = withContext(Dispatchers.IO) {
                    try {
                        val srcFile = File(tempPath)
                        if (srcFile.exists() && srcFile.length() > 0L) {
                            val oldPath = profilePicturePath
                            val destFile = File(context.filesDir, "avatar_${userId}_${System.currentTimeMillis()}.jpg")
                            srcFile.copyTo(destFile, overwrite = true)
                            srcFile.delete()
                            if (oldPath.isNotBlank() && oldPath.startsWith(context.filesDir.absolutePath)) {
                                File(oldPath).delete()
                            }
                            destFile.absolutePath
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
                if (savedPath != null) {
                    preferences.saveProfilePicturePath(userId, savedPath)
                    Toast.makeText(context, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                    CloudflareR2Service.r2SyncScope.launch {
                        CloudflareR2Service(context.applicationContext)
                            .uploadUserProfilePicture(userId, activeUsername, File(savedPath), preferences)
                    }
                }
            }
        }
    }

    val hasProfilePic = remember(profilePicturePath) {
        profilePicturePath.isNotBlank() && File(profilePicturePath).exists()
    }

    Box(modifier = modifier) {
        FilledTonalButton(
            onClick = { showAccountMenu = true },
            contentPadding = PaddingValues(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .widthIn(max = 196.dp)
                .testTag("top_bar_account_button")
        ) {
            if (hasProfilePic) {
                AsyncImage(
                    model = File(profilePicturePath),
                    contentDescription = "Profile Picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = "Account",
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = activeUsername.ifBlank { "Account" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        DropdownMenu(
            expanded = showAccountMenu,
            onDismissRequest = { showAccountMenu = false }
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = activeUsername.ifBlank { "Active Profile" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                lastD1Error.isNotBlank() -> lastD1Error
                                pendingD1Upload -> "D1 Sync: Queued offline"
                                lastD1SyncTime > 0L -> "D1 Sync: Up to date"
                                else -> "D1 Sync: Ready"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (lastD1Error.isNotBlank() || pendingD1Upload) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                lastR2Error.isNotBlank() -> "R2: $lastR2Error"
                                pendingR2Upload -> "R2 Pictures: Queued offline"
                                lastR2SyncTime > 0L -> "R2 Pictures: Synced"
                                else -> "R2 Pictures: Ready"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (lastR2Error.isNotBlank() || pendingR2Upload) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                leadingIcon = {
                    if (hasProfilePic) {
                        AsyncImage(
                            model = File(profilePicturePath),
                            contentDescription = "Profile Picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                },
                onClick = {
                    showAccountMenu = false
                    showProfilePicDialog = true
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(if (hasProfilePic) "Change Profile Picture" else "Add Profile Picture") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    showAccountMenu = false
                    showProfilePicDialog = true
                },
                modifier = Modifier.testTag("top_bar_profile_picture_item")
            )
            DropdownMenuItem(
                text = { Text("Sync Logs & Pictures (D1 / R2)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null
                    )
                },
                onClick = {
                    showAccountMenu = false
                    CloudflareSyncService.appSyncScope.launch {
                        val syncService = CloudflareSyncService(context.applicationContext)
                        if (syncService.hasCloudflareCredentials(preferences)) {
                            syncService.syncOnAppOpenOrReconnect(userId, repository, preferences)
                        }
                    }
                    CloudflareR2Service.r2SyncScope.launch {
                        CloudflareR2Service(context.applicationContext)
                            .syncAllPicturesOnStartup(
                                userId = userId,
                                repository = repository,
                                preferencesManager = preferences,
                                bundledGuides = ExerciseGuidesList.map { it.id to it.drawableResId }
                            )
                    }
                    Toast.makeText(context, "Syncing logs with D1 & pictures with R2...", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.testTag("top_bar_account_sync_item")
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Log Out",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Log Out",
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    showAccountMenu = false
                    scope.launch {
                        preferences.logout()
                    }
                },
                modifier = Modifier.testTag("top_bar_logout_menu_item")
            )
        }
    }

    if (showProfilePicDialog) {
        val hasExistingAvatar = profilePicturePath.isNotBlank() && File(profilePicturePath).exists()
        AlertDialog(
            onDismissRequest = { showProfilePicDialog = false },
            title = {
                Text(
                    text = "Profile Picture",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (hasExistingAvatar) {
                        AsyncImage(
                            model = File(profilePicturePath),
                            contentDescription = "Current Profile Picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(192.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = "Default Profile Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(168.dp)
                        )
                    }

                    Text(
                        text = "Choose a profile photo to display next to ${activeUsername.ifBlank { "your name" }}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            showProfilePicDialog = false
                            avatarGalleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose from Gallery")
                    }

                    OutlinedButton(
                        onClick = {
                            showProfilePicDialog = false
                            try {
                                val tempFile = File(context.cacheDir, "avatar_cam_${System.currentTimeMillis()}.jpg")
                                pendingAvatarCameraPath = tempFile.absolutePath
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                                avatarCameraLauncher.launch(uri)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open camera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Take Photo")
                    }

                    if (hasExistingAvatar) {
                        TextButton(
                            onClick = {
                                val oldPath = profilePicturePath
                                showProfilePicDialog = false
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        if (oldPath.startsWith(context.filesDir.absolutePath)) {
                                            File(oldPath).delete()
                                        }
                                    }
                                    preferences.saveProfilePicturePath(userId, "")
                                    CloudflareR2Service.r2SyncScope.launch {
                                        CloudflareR2Service(context.applicationContext)
                                            .deleteUserProfilePicture(userId, activeUsername, preferences)
                                    }
                                    Toast.makeText(context, "Profile picture removed", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Remove Picture")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showProfilePicDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
