package watch

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM encryption. Output layout: 12-byte IV followed by ciphertext+tag. */
class SecretBox(private val key: () -> SecretKey) {
    fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        // Android Keystore keys refuse a caller-supplied IV, so let the cipher choose a random one and read it back.
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        return iv + c.doFinal(plain)
    }

    /** Returns null when the data is corrupt, tampered with, or encrypted with another key. */
    fun decrypt(data: ByteArray): ByteArray? = try {
        require(data.size > 12 + 16)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        c.doFinal(data, 12, data.size - 12)
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Test hook: replaces the Android Keystore key. */
        var override: SecretBox? = null

        private const val ALIAS = "eventerwatch_profile"

        fun android(): SecretBox = override ?: SecretBox {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build(),
                    )
                }.generateKey()
        }
    }
}
