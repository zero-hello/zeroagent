package me.rerere.zeroagent.data.security

import com.google.crypto.tink.Aead
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Base64

/** Keystore-backed authenticated encryption for device-local credentials. */
internal class CredentialCipher(
    private val encryptionKey: () -> Aead,
    private val decryptionKey: () -> Aead,
) {
    fun encrypt(value: String, scope: String): String = cryptoOperation {
        Base64.getEncoder().encodeToString(
            encryptionKey().encrypt(value.encodeToByteArray(), scope.encodeToByteArray())
        )
    }

    fun decrypt(value: String, scope: String): String = cryptoOperation {
        decryptionKey().decrypt(Base64.getDecoder().decode(value), scope.encodeToByteArray()).decodeToString()
    }

    private inline fun <T> cryptoOperation(operation: () -> T): T = try {
        operation()
    } catch (_: GeneralSecurityException) {
        throw IOException("The device credential store could not protect or read this credential")
    } catch (_: IllegalArgumentException) {
        throw IOException("The protected credential data is invalid")
    }

    companion object {
        private const val KEY_URI = "android-keystore://zeroagent_device_credentials_v1"

        val device by lazy {
            CredentialCipher(
                encryptionKey = { AndroidKeystoreKmsClient.getOrGenerateNewAeadKey(KEY_URI) },
                decryptionKey = { AndroidKeystoreKmsClient().getAead(KEY_URI) },
            )
        }
    }
}
