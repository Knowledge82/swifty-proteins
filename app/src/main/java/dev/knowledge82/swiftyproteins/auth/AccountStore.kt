package dev.knowledge82.swiftyproteins.auth

import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Where the accounts are kept. An interface: the app uses the encrypted file, the tests a list in memory. */
interface AccountStore {
    /** @throws AccountFormatException if what is stored cannot be read; a missing file is [AccountsSnapshot.EMPTY]. */
    fun load(): AccountsSnapshot

    /** @throws IOException if it cannot be written. */
    fun save(snapshot: AccountsSnapshot)
}

/** Encrypts and authenticates bytes (document 05, D41). */
interface Aead {
    fun encrypt(plain: ByteArray): ByteArray

    /** @throws AccountFormatException if the data was changed, cut, or encrypted with another key. */
    fun decrypt(blob: ByteArray): ByteArray
}

/**
 * AES-256-GCM with a key that comes from outside: on the phone, a key of the Android Keystore that never leaves the
 * secure hardware; in the tests, an ordinary software key. Pure Java crypto (javax.crypto), the same on both.
 *
 * Blob: 1 byte version, 12 bytes IV, ciphertext + 16-byte tag.
 * The IV is chosen by the CIPHER, not by us: the Android Keystore refuses an IV given by the app (it would allow the
 * same IV twice, which breaks GCM), and generating it inside is also what a software key does.
 */
class AesGcmAead(private val key: () -> SecretKey) : Aead {

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "unexpected IV size ${iv.size}" }
        return byteArrayOf(VERSION) + iv + cipher.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        if (blob.size < 1 + IV_BYTES + TAG_BITS / 8 || blob[0] != VERSION) throw AccountFormatException("not an accounts file")
        return try {
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob, 1, IV_BYTES))
                doFinal(blob, 1 + IV_BYTES, blob.size - 1 - IV_BYTES)
            }
        } catch (e: GeneralSecurityException) {
            // A wrong tag (changed or foreign file) or a key that does not open it: unreadable, never half-read.
            throw AccountFormatException("cannot decrypt: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/**
 * The accounts file: [AccountCodec] text, encrypted with [aead], written atomically (document 05, D41).
 * Pure Java file code: tested on the JVM with a temporary folder and a software key.
 */
class EncryptedAccountStore(private val file: File, private val aead: Aead) : AccountStore {

    override fun load(): AccountsSnapshot {
        if (!file.exists()) return AccountsSnapshot.EMPTY
        val bytes = try {
            file.readBytes()
        } catch (e: IOException) {
            throw AccountFormatException("cannot read: ${e.message}")
        }
        val text = try {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(aead.decrypt(bytes))).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            throw AccountFormatException("not UTF-8")
        }
        return AccountCodec.decode(text)
    }

    override fun save(snapshot: AccountsSnapshot) {
        val blob = aead.encrypt(AccountCodec.encode(snapshot).toByteArray(Charsets.UTF_8))
        // Write a new file, then RENAME it over the old one: a crash in the middle leaves the old file or the new one,
        // never half of each (the same pattern as the share image, document 09).
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(blob)
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("cannot replace ${file.name}")
        }
    }
}
