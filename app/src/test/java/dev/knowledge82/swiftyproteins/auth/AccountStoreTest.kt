package dev.knowledge82.swiftyproteins.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AccountStoreTest {

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val key = newKey()
    private val dir: File = Files.createTempDirectory("accounts").toFile()
    private val file = File(dir, "accounts.bin")
    private val store = EncryptedAccountStore(file, AesGcmAead { key })

    private val snapshot = AccountsSnapshot(
        listOf(Account("adept42", PasswordHash(HashParams(19_456, 2, 1), byteArrayOf(1, 2), byteArrayOf(3, 4)))),
        "adept42",
    )

    @Test
    fun aMissingFileMeansNoAccounts() {
        assertEquals(AccountsSnapshot.EMPTY, store.load())
    }

    @Test
    fun whatIsSavedIsLoadedBack() {
        store.save(snapshot)
        assertEquals(AccountCodec.encode(snapshot), AccountCodec.encode(store.load()))
    }

    @Test
    fun theFileOnDiskIsNotReadableText() {
        store.save(snapshot)
        val raw = String(file.readBytes(), Charsets.ISO_8859_1)
        assertFalse(raw.contains("adept42"))
        assertFalse(raw.contains("SWIFTY-ACCOUNTS"))
    }

    @Test
    fun twoSavesOfTheSameDataGiveDifferentBytes() {
        store.save(snapshot)
        val first = file.readBytes()
        store.save(snapshot)
        assertFalse(first.contentEquals(file.readBytes()))   // a fresh IV every time
    }

    @Test
    fun aChangedByteIsDetected() {
        store.save(snapshot)
        val bytes = file.readBytes()
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertThrows(AccountFormatException::class.java) { store.load() }
    }

    @Test
    fun aFileFromAnotherKeyIsUnreadable() {
        store.save(snapshot)
        val other = EncryptedAccountStore(file, AesGcmAead { newKey() })
        assertThrows(AccountFormatException::class.java) { other.load() }
    }

    @Test
    fun aCutOrForeignFileIsUnreadable() {
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertThrows(AccountFormatException::class.java) { store.load() }
        file.writeBytes(ByteArray(64) { 9 })
        assertThrows(AccountFormatException::class.java) { store.load() }
    }

    @Test
    fun noTemporaryFileIsLeftBehind() {
        store.save(snapshot)
        assertEquals(listOf("accounts.bin"), dir.list()!!.toList())
    }

    @Test
    fun theCipherItselfRefusesAChangedBlob() {
        val aead = AesGcmAead { key }
        val blob = aead.encrypt("SWIFTY".toByteArray())
        assertEquals("SWIFTY", String(aead.decrypt(blob)))
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()
        assertThrows(AccountFormatException::class.java) { aead.decrypt(blob) }
    }
}
