package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.CloudflareSyncService
import com.example.data.PreferencesManager
import com.example.data.User
import com.example.data.WorkoutRepository
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    repository: WorkoutRepository,
    preferences: PreferencesManager,
    onLoginSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var whatsappPhone by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var infoMessage by remember { mutableStateOf("") }

    var showResetDialog by remember { mutableStateOf(false) }
    var savedUsers by remember { mutableStateOf<List<User>>(emptyList()) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isSyncingAuth by remember { mutableStateOf(false) }
    val lastBiometricUserId by preferences.lastBiometricUserIdFlow.collectAsState(initial = -1)
    val biometricStatus = remember(context) {
        com.example.data.BiometricAuthManager.checkBiometricAvailability(context)
    }

    LaunchedEffect(Unit) {
        val syncService = CloudflareSyncService(context.applicationContext)
        if (syncService.hasCloudflareCredentials(preferences) && syncService.isNetworkAvailable()) {
            syncService.syncRegisteredUsersWithD1(repository, preferences)
        }
        savedUsers = repository.getAllUsers()
            .filter { it.username.isNotBlank() && !it.username.equals("guest_tester", ignoreCase = true) }
    }

    var forcePasswordLogin by remember { mutableStateOf(false) }

    val selectedSavedUser = remember(savedUsers, username) {
        val clean = username.trim()
        if (clean.isEmpty()) null else savedUsers.firstOrNull { it.username.equals(clean, ignoreCase = true) }
    }
    val showBiometricOnlyMode = !isSignUp &&
        !forcePasswordLogin &&
        selectedSavedUser != null &&
        selectedSavedUser.biometricEnabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AsyncImage(
            model = com.example.R.drawable.elysium_logo_1779444589575,
            contentDescription = "Elysium Logo",
            modifier = Modifier
                .size(132.dp)
                .padding(bottom = 12.dp),
            contentScale = ContentScale.Fit
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Elysium Training",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "ELITE PERFORMANCE SYSTEM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
        }

        if (savedUsers.isNotEmpty() && !isSignUp) {
            Text(
                text = "Saved Accounts",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                savedUsers.forEach { saved ->
                    val isChipSelected = saved.username.equals(username.trim(), ignoreCase = true)
                    FilterChip(
                        selected = isChipSelected,
                        onClick = {
                            if (isChipSelected) {
                                username = ""
                                email = ""
                                whatsappPhone = ""
                                forcePasswordLogin = false
                            } else {
                                username = saved.username
                                email = saved.email
                                whatsappPhone = saved.phoneNumber
                                forcePasswordLogin = false
                            }
                            errorMessage = ""
                            infoMessage = ""
                        },
                        label = {
                            Text(if (saved.biometricEnabled) "${saved.username} 🔐" else saved.username)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (saved.biometricEnabled) Icons.Filled.Fingerprint else Icons.Filled.Person,
                                contentDescription = if (saved.biometricEnabled) "Biometric enrolled account" else null,
                                tint = if (saved.biometricEnabled) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }

        if (showBiometricOnlyMode && selectedSavedUser != null) {
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("biometric_only_signin_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = "Fingerprint Sign-In",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = selectedSavedUser.username,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Fingerprint / Biometric registered",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (errorMessage.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        enabled = !isSyncingAuth,
                        onClick = {
                            errorMessage = ""
                            infoMessage = ""
                            if (!biometricStatus.canAuthenticate) {
                                errorMessage = biometricStatus.statusMessage
                                return@Button
                            }
                            isSyncingAuth = true
                            com.example.data.BiometricAuthManager.authenticateWithBiometric(
                                context = context,
                                targetUser = selectedSavedUser,
                                repository = repository,
                                preferencesManager = preferences,
                                scope = scope,
                                title = "Biometric Sign-In",
                                subtitle = "Sign in as ${selectedSavedUser.username}",
                                onSuccess = { verifiedUser ->
                                    scope.launch {
                                        onLoginSuccess()
                                        val syncService = CloudflareSyncService(context.applicationContext)
                                        if (syncService.hasCloudflareCredentials(preferences)) {
                                            CloudflareSyncService.appSyncScope.launch {
                                                syncService.syncOnAppOpenOrReconnect(verifiedUser.id, repository, preferences)
                                            }
                                        }
                                        preferences.saveUserId(verifiedUser.id)
                                        isSyncingAuth = false
                                    }
                                },
                                onError = { err ->
                                    isSyncingAuth = false
                                    errorMessage = err
                                },
                                onCancel = {
                                    isSyncingAuth = false
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("biometric_login_button")
                    ) {
                        if (isSyncingAuth) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Sign In with Fingerprint 🔐", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = {
                    username = ""
                    password = ""
                    errorMessage = ""
                    infoMessage = ""
                }
            ) {
                Text("Switch Account")
            }
            TextButton(
                onClick = {
                    forcePasswordLogin = true
                    errorMessage = ""
                }
            ) {
                Text("Sign in with password instead", color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; forcePasswordLogin = false; errorMessage = "" },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_username_input")
            )

            if (isSignUp) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = "" },
                    label = { Text("Email Address (for password reset)") },
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_email_input")
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = whatsappPhone,
                    onValueChange = { whatsappPhone = it; errorMessage = "" },
                    label = { Text("WhatsApp Phone Number (e.g. +2782...)") },
                    leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_whatsapp_input")
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = "" },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_password_input")
            )

            Spacer(Modifier.height(20.dp))

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (infoMessage.isNotEmpty()) {
                Text(
                    text = infoMessage,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Button(
                enabled = !isSyncingAuth,
                onClick = {
                    val cleanUser = username.trim()
                    val cleanEmail = email.trim()
                    val cleanPhone = whatsappPhone.trim()
                    if (cleanUser.isBlank() || password.isBlank()) {
                        errorMessage = "Please enter both username and password"
                        return@Button
                    }
                    if (isSignUp && cleanEmail.isBlank() && cleanPhone.isBlank()) {
                        errorMessage = "Please enter an email or WhatsApp phone number for password recovery"
                        return@Button
                    }
                    isSyncingAuth = true
                    errorMessage = ""
                    infoMessage = ""
                    scope.launch {
                        try {
                            val syncService = CloudflareSyncService(context.applicationContext)
                            if (isSignUp) {
                                val localExisting = repository.getUserByUsername(cleanUser)
                                val remoteExisting = if (localExisting == null && syncService.hasCloudflareCredentials(preferences) && syncService.isNetworkAvailable()) {
                                    syncService.fetchUserRecordFromD1(cleanUser, preferences)
                                } else null

                                if (localExisting != null || remoteExisting != null) {
                                    errorMessage = "User already exists. Please log in or reset password."
                                } else {
                                    val newUser = User(
                                        username = cleanUser,
                                        passwordHash = password,
                                        email = cleanEmail,
                                        phoneNumber = cleanPhone
                                    )
                                    val newUserId = repository.insertUser(newUser).toInt()
                                    savedUsers = repository.getAllUsers()
                                        .filter { it.username.isNotBlank() && !it.username.equals("guest_tester", ignoreCase = true) }

                                    if (syncService.hasCloudflareCredentials(preferences)) {
                                        val pushed = syncService.registerUserInD1(
                                            username = cleanUser,
                                            passwordHash = password,
                                            localUserId = newUserId,
                                            preferencesManager = preferences,
                                            email = cleanEmail,
                                            phoneNumber = cleanPhone
                                        )
                                        if (!pushed) {
                                            preferences.setPendingD1Upload(true)
                                        }
                                        CloudflareSyncService.appSyncScope.launch {
                                            syncService.syncAllUserDataToD1(newUserId, repository, preferences)
                                        }
                                    }
                                    onLoginSuccess()
                                    preferences.saveUserId(newUserId)
                                }
                            } else {
                                val localUser = repository.getUserByUsername(cleanUser)
                                if (localUser != null && localUser.passwordHash == password) {
                                    if (syncService.hasCloudflareCredentials(preferences)) {
                                        CloudflareSyncService.appSyncScope.launch {
                                            syncService.registerUserInD1(
                                                username = localUser.username,
                                                passwordHash = localUser.passwordHash,
                                                localUserId = localUser.id,
                                                preferencesManager = preferences,
                                                email = localUser.email,
                                                phoneNumber = localUser.phoneNumber
                                            )
                                            syncService.syncOnAppOpenOrReconnect(localUser.id, repository, preferences)
                                        }
                                    }
                                    onLoginSuccess()
                                    preferences.saveUserId(localUser.id)
                                } else if (syncService.hasCloudflareCredentials(preferences) && syncService.isNetworkAvailable()) {
                                    val remoteValid = syncService.authenticateUserFromD1(cleanUser, password, preferences)
                                    if (remoteValid) {
                                        val remoteUser = syncService.fetchUserRecordFromD1(cleanUser, preferences)
                                        val resolvedId = if (localUser != null) {
                                            repository.updateUserPassword(localUser.id, password)
                                            localUser.id
                                        } else {
                                            repository.insertUser(
                                                User(
                                                    username = remoteUser?.username ?: cleanUser,
                                                    passwordHash = password,
                                                    email = remoteUser?.email.orEmpty(),
                                                    phoneNumber = remoteUser?.phoneNumber.orEmpty()
                                                )
                                            ).toInt()
                                        }
                                        CloudflareSyncService.appSyncScope.launch {
                                            syncService.syncOnAppOpenOrReconnect(resolvedId, repository, preferences)
                                        }
                                        onLoginSuccess()
                                        preferences.saveUserId(resolvedId)
                                    } else {
                                        errorMessage = "Invalid username or password"
                                    }
                                } else {
                                    errorMessage = "Invalid username or password"
                                }
                            }
                        } finally {
                            isSyncingAuth = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("login_submit_button")
            ) {
                if (isSyncingAuth) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isSignUp) "Sign Up" else "Log In")
            }

            if (!isSignUp) {
                val biometricTargetUser = remember(savedUsers, username, lastBiometricUserId) {
                    val cleanInput = username.trim()
                    savedUsers.firstOrNull { it.username.equals(cleanInput, ignoreCase = true) && it.biometricEnabled }
                        ?: savedUsers.firstOrNull { it.id == lastBiometricUserId && it.biometricEnabled }
                        ?: savedUsers.firstOrNull { it.biometricEnabled }
                        ?: savedUsers.firstOrNull { it.username.equals(cleanInput, ignoreCase = true) }
                        ?: savedUsers.firstOrNull { it.id == lastBiometricUserId }
                        ?: savedUsers.firstOrNull()
                }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    enabled = !isSyncingAuth,
                    onClick = {
                        errorMessage = ""
                        infoMessage = ""
                        if (biometricTargetUser == null) {
                            errorMessage = "Sign up or log in with a username first, then tap Enroll Biometric in Settings."
                            return@OutlinedButton
                        }
                        if (!biometricTargetUser.biometricEnabled) {
                            errorMessage = "No biometric print enrolled yet for '${biometricTargetUser.username}'. Log in with password once and enroll in Settings → Biometric Security."
                            return@OutlinedButton
                        }
                        if (!biometricStatus.canAuthenticate) {
                            errorMessage = biometricStatus.statusMessage
                            return@OutlinedButton
                        }
                        isSyncingAuth = true
                        com.example.data.BiometricAuthManager.authenticateWithBiometric(
                            context = context,
                            targetUser = biometricTargetUser,
                            repository = repository,
                            preferencesManager = preferences,
                            scope = scope,
                            title = "Biometric Sign-In",
                            subtitle = "Sign in as ${biometricTargetUser.username}",
                            onSuccess = { verifiedUser ->
                                scope.launch {
                                    onLoginSuccess()
                                    val syncService = CloudflareSyncService(context.applicationContext)
                                    if (syncService.hasCloudflareCredentials(preferences)) {
                                        CloudflareSyncService.appSyncScope.launch {
                                            syncService.syncOnAppOpenOrReconnect(verifiedUser.id, repository, preferences)
                                        }
                                    }
                                    preferences.saveUserId(verifiedUser.id)
                                    isSyncingAuth = false
                                }
                            },
                            onError = { err ->
                                isSyncingAuth = false
                                errorMessage = err
                            },
                            onCancel = {
                                isSyncingAuth = false
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("biometric_login_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = "Biometric Sign-In",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (biometricTargetUser != null && biometricTargetUser.biometricEnabled) {
                            "Sign In with Biometric (${biometricTargetUser.username})"
                        } else {
                            "Sign In with Biometrics 🔐"
                        }
                    )
                }
                Text(
                    text = if (!biometricStatus.canAuthenticate) {
                        biometricStatus.statusMessage
                    } else if (biometricTargetUser != null && biometricTargetUser.biometricEnabled) {
                        "Fingerprint / Device Biometric ready for ${biometricTargetUser.username}"
                    } else {
                        "Enroll your fingerprint in Settings → Biometric Security for 1-tap login"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = {
                    isSignUp = !isSignUp
                    forcePasswordLogin = false
                    errorMessage = ""
                    infoMessage = ""
                },
                modifier = Modifier.testTag("toggle_auth_mode_button")
            ) {
                Text(if (isSignUp) "Already have an account? Log In" else "Don't have an account? Sign Up")
            }

            TextButton(
                onClick = {
                    errorMessage = ""
                    showResetDialog = true
                },
                modifier = Modifier.testTag("forgot_password_button")
            ) {
                Icon(Icons.Filled.LockReset, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Forgot / Reset Password (Email or WhatsApp)")
            }

            Spacer(Modifier.height(4.dp))

            TextButton(onClick = {
                scope.launch {
                    val guestUser = repository.getUserByUsername("guest_tester")
                    onLoginSuccess()
                    if (guestUser == null) {
                        val id = repository.insertUser(User(username = "guest_tester", passwordHash = ""))
                        preferences.saveUserId(id.toInt())
                    } else {
                        preferences.saveUserId(guestUser.id)
                    }
                }
            }) {
                Text("Skip Login (Test Mode)", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }

    if (showResetDialog) {
        var resetIdentifier by remember(showResetDialog) {
            mutableStateOf(
                username.trim().ifBlank { email.trim().ifBlank { whatsappPhone.trim() } }
            )
        }
        var resetChannel by remember(showResetDialog) {
            mutableStateOf(if (whatsappPhone.isNotBlank() && email.isBlank()) "WhatsApp" else "Email")
        }
        var destinationContact by remember(showResetDialog) {
            mutableStateOf(
                if (resetChannel == "WhatsApp") whatsappPhone.trim() else email.trim()
            )
        }
        var newResetPassword by remember(showResetDialog) { mutableStateOf("") }
        var resetStatusError by remember { mutableStateOf("") }
        var isResetting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isResetting) showResetDialog = false },
            title = {
                Text(
                    text = "Reset Password",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Recover or reset your password via Email or WhatsApp. Changes save directly to your account & Cloudflare D1.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = resetIdentifier,
                        onValueChange = { resetIdentifier = it; resetStatusError = "" },
                        label = { Text("Username, Email, or Phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Send reset password via:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isEmail = resetChannel == "Email"
                        FilterChip(
                            selected = isEmail,
                            onClick = {
                                resetChannel = "Email"
                                if (destinationContact.isBlank() && email.isNotBlank()) {
                                    destinationContact = email.trim()
                                }
                            },
                            label = { Text("Email") },
                            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !isEmail,
                            onClick = {
                                resetChannel = "WhatsApp"
                                if (destinationContact.isBlank() && whatsappPhone.isNotBlank()) {
                                    destinationContact = whatsappPhone.trim()
                                }
                            },
                            label = { Text("WhatsApp") },
                            leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = destinationContact,
                        onValueChange = { destinationContact = it; resetStatusError = "" },
                        label = {
                            Text(
                                if (resetChannel == "WhatsApp") "WhatsApp Phone Number" else "Recipient Email Address"
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (resetChannel == "WhatsApp") KeyboardType.Phone else KeyboardType.Email
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newResetPassword,
                        onValueChange = { newResetPassword = it; resetStatusError = "" },
                        label = { Text("New Password (leave blank to auto-generate)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (resetStatusError.isNotEmpty()) {
                        Text(
                            text = resetStatusError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isResetting,
                    onClick = {
                        val idQuery = resetIdentifier.trim().ifBlank { destinationContact.trim() }
                        if (idQuery.isBlank()) {
                            resetStatusError = "Please enter your username, email, or WhatsApp phone number."
                            return@Button
                        }
                        isResetting = true
                        resetStatusError = ""
                        scope.launch {
                            try {
                                val syncService = CloudflareSyncService(context.applicationContext)
                                var targetUser = repository.findUserByIdentifier(idQuery)
                                if (targetUser == null && syncService.hasCloudflareCredentials(preferences) && syncService.isNetworkAvailable()) {
                                    val remote = syncService.fetchUserRecordFromD1(idQuery, preferences)
                                    if (remote != null) {
                                        val insertedId = repository.insertUser(remote).toInt()
                                        targetUser = remote.copy(id = insertedId)
                                    }
                                }

                                if (targetUser == null) {
                                    resetStatusError = "No saved account matched \"$idQuery\". Check your username, email, or WhatsApp number."
                                    isResetting = false
                                    return@launch
                                }

                                val finalNewPassword = newResetPassword.trim().ifBlank {
                                    "ELYS-" + (1000..9999).random() + ('A'..'Z').random()
                                }

                                val targetEmail = if (resetChannel == "Email") {
                                    destinationContact.trim().ifBlank { targetUser.email }
                                } else {
                                    targetUser.email
                                }
                                val targetPhone = if (resetChannel == "WhatsApp") {
                                    destinationContact.trim().ifBlank { targetUser.phoneNumber }
                                } else {
                                    targetUser.phoneNumber
                                }

                                val sendTarget = if (resetChannel == "WhatsApp") targetPhone else targetEmail
                                if (sendTarget.isBlank()) {
                                    resetStatusError = if (resetChannel == "WhatsApp") {
                                        "Please enter a WhatsApp phone number to send the password to."
                                    } else {
                                        "Please enter an email address to send the password to."
                                    }
                                    isResetting = false
                                    return@launch
                                }

                                val updatedUser = targetUser.copy(
                                    passwordHash = finalNewPassword,
                                    email = targetEmail.ifBlank { targetUser.email },
                                    phoneNumber = targetPhone.ifBlank { targetUser.phoneNumber }
                                )
                                repository.insertUser(updatedUser)

                                if (syncService.hasCloudflareCredentials(preferences)) {
                                    syncService.resetUserPasswordInD1(
                                        username = updatedUser.username,
                                        newPassword = finalNewPassword,
                                        email = updatedUser.email,
                                        phoneNumber = updatedUser.phoneNumber,
                                        resetChannel = resetChannel,
                                        contactValue = sendTarget,
                                        localUserId = updatedUser.id,
                                        preferencesManager = preferences
                                    )
                                }

                                savedUsers = repository.getAllUsers()
                                    .filter { it.username.isNotBlank() && !it.username.equals("guest_tester", ignoreCase = true) }

                                username = updatedUser.username
                                password = finalNewPassword
                                email = updatedUser.email
                                whatsappPhone = updatedUser.phoneNumber
                                infoMessage = "Password reset saved for ${updatedUser.username} (also saved to D1)."
                                showResetDialog = false

                                val resetMessage =
                                    "Elysium Training Password Reset\nUsername: ${updatedUser.username}\nNew Password: $finalNewPassword"

                                if (resetChannel == "WhatsApp") {
                                    val digits = sendTarget.replace(Regex("[^0-9]"), "")
                                    val waUri = Uri.parse("https://wa.me/$digits?text=${Uri.encode(resetMessage)}")
                                    val waIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                                        setPackage("com.whatsapp")
                                    }
                                    try {
                                        context.startActivity(waIntent)
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, waUri))
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "New password saved to D1: $finalNewPassword", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    val mailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:$sendTarget")
                                        putExtra(Intent.EXTRA_SUBJECT, "Elysium Training - Password Reset")
                                        putExtra(Intent.EXTRA_TEXT, resetMessage)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(mailIntent, "Send Password via Email"))
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "New password saved to D1: $finalNewPassword", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } finally {
                                isResetting = false
                            }
                        }
                    }
                ) {
                    if (isResetting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Reset & Send")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isResetting,
                    onClick = { showResetDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
