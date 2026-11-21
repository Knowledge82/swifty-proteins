package dev.knowledge82.swiftyproteins.auth

/** A hasher with no cost, for the account book tests. It counts the checks (to prove the decoy check happens). */
class FakeHasher(override var params: HashParams = HashParams(1, 1, 1)) : PasswordHasher {
    var verifications = 0
        private set
    var hashes = 0
        private set

    override fun hash(password: String): PasswordHash {
        hashes++
        return PasswordHash(params, byteArrayOf(1), password.toByteArray())
    }

    override fun verify(password: String, stored: PasswordHash): Boolean {
        verifications++
        return password.toByteArray().contentEquals(stored.hash)
    }
}
