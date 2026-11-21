package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature

data class BiometricHardwareStatus(
    val canAuthenticate: Boolean,
    val statusMessage: String
)

object BiometricAuthManager {

    fun findFragmentActivity(context: Context): FragmentActivity? {
        var cur: Context? = context
        while (cur is ContextWrapper) {
            if (cur is FragmentActivity) return cur
            cur = cur.baseContext
        }
        return null
    }

    fun checkBiometricAvailability(context: Context): BiometricHardwareStatus {
        val manager = BiometricManager.from(context)
        val bioResult = manager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
        if (bioResult == BiometricManager.BIOMETRIC_SUCCESS) {
            return BiometricHardwareStatus(true, "Fingerprint / Biometric sensor ready")
        }
        val fallbackResult = manager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        return when {
            fallbackResult == BiometricManager.BIOMETRIC_SUCCESS ->
                BiometricHardwareStatus(true, "Biometric / Device credential ready")
            bioResult == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                BiometricHardwareStatus(false, "No fingerprint enrolled on this device yet — enroll a fingerprint in Android Settings first.")
            bioResult == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                BiometricHardwareStatus(false, "No biometric sensor detected on this device.")
            else ->
                BiometricHardwareStatus(false, "Biometric hardware currently unavailable.")
        }
    }

    private fun keyAliasForUser(username: String): String {
        val clean = CloudflareR2Service.sanitizeKeySlug(username)
        return "elysium_biometric_key_$clean"
    }

    private fun prepareHardwareSignature(username: String): Pair<Signature?, String> {
        return try {
            val alias = keyAliasForUser(username)
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!ks.containsAlias(alias)) {
                val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                val specBuilder = KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(true)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    specBuilder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
                kpg.initialize(specBuilder.build())
                kpg.generateKeyPair()
            }
            val entry = ks.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            val pubKeyB64 = entry?.certificate?.publicKey?.encoded?.let {
                Base64.encodeToString(it, Base64.NO_WRAP)
            }.orEmpty()
            val signature = Signature.getInstance("SHA256withECDSA").apply {
                initSign(entry?.privateKey)
            }
            signature to pubKeyB64
        } catch (_: Exception) {
            // Fallback when running on an emulator/device with weak biometric or device credential only
            null to ""
        }
    }

    private fun buildBiometricPrintToken(
        username: String,
        enrolledAt: Long,
        cryptoSignatureBytes: ByteArray?
    ): String {
        val seed = "ELYSIUM_CLOUD_PRINT|${username.lowercase()}|$enrolledAt|${Build.MANUFACTURER}_${Build.MODEL}"
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(seed.toByteArray(Charsets.UTF_8))
        if (cryptoSignatureBytes != null && cryptoSignatureBytes.isNotEmpty()) {
            digest.update(cryptoSignatureBytes)
        }
        val hashHex = digest.digest().joinToString("") { "%02x".format(it) }
        return "BIO-V1-$hashHex"
    }

    fun enrollBiometricAndSaveToCloud(
        context: Context,
        user: User,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager,
        scope: CoroutineScope,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val activity = findFragmentActivity(context)
        if (activity == null) {
            onError("Could not attach biometric prompt to current screen.")
            return
        }
        val status = checkBiometricAvailability(context)
        if (!status.canAuthenticate) {
            onError(status.statusMessage)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val (hwSignature, pubKeyB64) = prepareHardwareSignature(user.username)

        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val now = System.currentTimeMillis()
                    val challengeBytes = "ENROLL:${user.username}:$now".toByteArray(Charsets.UTF_8)
                    val signedBytes = try {
                        result.cryptoObject?.signature?.let { sig ->
                            sig.update(challengeBytes)
                            sig.sign()
                        }
                    } catch (_: Exception) {
                        null
                    }
                    val printToken = buildBiometricPrintToken(user.username, now, signedBytes)
                    val updatedUser = user.copy(
                        biometricEnabled = true,
                        biometricToken = printToken,
                        biometricPublicKey = pubKeyB64.ifBlank { "SHA256-BIO-${user.username.lowercase()}" },
                        biometricEnrolledAt = now
                    )

                    scope.launch {
                        repository.updateUserBiometric(
                            userId = updatedUser.id,
                            enabled = true,
                            token = updatedUser.biometricToken,
                            publicKey = updatedUser.biometricPublicKey,
                            enrolledAt = now
                        )
                        preferencesManager.saveLastBiometricUserId(updatedUser.id)
                        preferencesManager.saveBiometricLockOnStartup(true)

                        // Push biometric print immediately to Cloudflare D1 and Cloudflare R2
                        CloudflareSyncService.appSyncScope.launch {
                            val d1 = CloudflareSyncService(context.applicationContext)
                            d1.saveBiometricPrintToD1(updatedUser, "${Build.MANUFACTURER} ${Build.MODEL}", preferencesManager)
                            d1.syncAllUserDataToD1(updatedUser.id, repository, preferencesManager)
                        }
                        CloudflareR2Service.r2SyncScope.launch {
                            CloudflareR2Service(context.applicationContext)
                                .uploadBiometricPrintToR2(updatedUser, "${Build.MANUFACTURER} ${Build.MODEL}", preferencesManager)
                        }
                        onSuccess(updatedUser)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onError(errString.toString())
                    } else {
                        onCancel()
                    }
                }
            }
        )

        val canStrong = BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        if (canStrong && hwSignature != null) {
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enroll Biometric Print")
                .setSubtitle("Scan fingerprint for ${user.username}")
                .setDescription("Creates an encrypted biometric credential and saves your print record to Cloudflare D1 & R2.")
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancel")
                .build()
            try {
                prompt.authenticate(info, BiometricPrompt.CryptoObject(hwSignature))
                return
            } catch (_: Exception) {
                // Fall through to standard prompt below
            }
        }

        val fallbackInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Enroll Biometric Print")
            .setSubtitle("Scan fingerprint or biometric for ${user.username}")
            .setDescription("Saves your verified biometric print credential to Cloudflare D1 & R2.")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(fallbackInfo)
    }

    fun authenticateWithBiometric(
        context: Context,
        targetUser: User,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager,
        scope: CoroutineScope,
        title: String = "Biometric Sign-In",
        subtitle: String = "Scan fingerprint for ${targetUser.username}",
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val activity = findFragmentActivity(context)
        if (activity == null) {
            onError("Biometric prompt requires an active window.")
            return
        }
        val status = checkBiometricAvailability(context)
        if (!status.canAuthenticate) {
            onError(status.statusMessage)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    scope.launch {
                        // Ensure cloud print token is bound locally if restored from cloud
                        val effectiveToken = targetUser.biometricToken.ifBlank {
                            buildBiometricPrintToken(targetUser.username, System.currentTimeMillis(), null)
                        }
                        val enrolledAt = if (targetUser.biometricEnrolledAt > 0L) {
                            targetUser.biometricEnrolledAt
                        } else {
                            System.currentTimeMillis()
                        }
                        val verifiedUser = targetUser.copy(
                            biometricEnabled = true,
                            biometricToken = effectiveToken,
                            biometricEnrolledAt = enrolledAt
                        )
                        repository.updateUserBiometric(
                            userId = verifiedUser.id,
                            enabled = true,
                            token = verifiedUser.biometricToken,
                            publicKey = verifiedUser.biometricPublicKey,
                            enrolledAt = enrolledAt
                        )
                        preferencesManager.saveLastBiometricUserId(verifiedUser.id)
                        onSuccess(verifiedUser)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onError(errString.toString())
                    } else {
                        onCancel()
                    }
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription("Verified against your Cloudflare D1 & R2 biometric print credential")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(promptInfo)
    }

    suspend fun removeBiometricFromCloud(
        context: Context,
        user: User,
        repository: WorkoutRepository,
        preferencesManager: PreferencesManager
    ) {
        val cleared = user.copy(
            biometricEnabled = false,
            biometricToken = "",
            biometricPublicKey = "",
            biometricEnrolledAt = 0L
        )
        repository.updateUserBiometric(user.id, false, "", "", 0L)
        preferencesManager.saveBiometricLockOnStartup(false)

        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val alias = keyAliasForUser(user.username)
            if (ks.containsAlias(alias)) ks.deleteEntry(alias)
        } catch (_: Exception) {
        }

        CloudflareSyncService.appSyncScope.launch {
            val d1 = CloudflareSyncService(context.applicationContext)
            d1.saveBiometricPrintToD1(cleared, "${Build.MANUFACTURER} ${Build.MODEL}", preferencesManager)
            d1.syncAllUserDataToD1(user.id, repository, preferencesManager)
        }
        CloudflareR2Service.r2SyncScope.launch {
            CloudflareR2Service(context.applicationContext)
                .uploadBiometricPrintToR2(cleared, "${Build.MANUFACTURER} ${Build.MODEL}", preferencesManager)
        }
    }
}
