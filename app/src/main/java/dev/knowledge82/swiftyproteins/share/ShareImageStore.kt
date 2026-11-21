package dev.knowledge82.swiftyproteins.share

import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Writes the file to share (an image, or since document 18 any export: .jpg, .cif, .mol, .glb, .mp4) into ONE folder
 * of the app's cache, and keeps only the last one.
 *
 * Pure Java/Kotlin files (no Android): tested on the JVM with a temporary folder.
 *
 * Why the cache: it is private to the app (no storage permission needed), Android may empty it when space runs
 * out, and it is the folder that `res/xml/file_paths.xml` opens to other apps through the FileProvider.
 *
 * @param directory `<cacheDir>/shared` on the phone; a temporary folder in the tests
 */
class ShareImageStore(private val directory: File) {

    /**
     * Writes a new image for [ligandId] and returns the file.
     *
     * Steps: write to `name.part` first, and only when the writing finished, rename it to `name`. A half-written
     * file (exception, full disk) is never left with the real name, so it can never be shared by mistake.
     * Then every OTHER `.png` of the folder is deleted (the cache keeps one image, not one per tap), and so is any
     * old `.part` left by a capture that was cancelled in the middle of writing.
     *
     * @param write puts the bytes of the image (on the phone: `bitmap.compress(PNG, ...)`)
     * @throws IOException if the folder cannot be created or the file cannot be written or renamed
     */
    fun save(ligandId: String, write: (OutputStream) -> Unit): File = save(ligandId, "png", write)

    /** The same for any [extension] ("jpg", "cif", "mol", "glb", "mp4"). */
    fun save(ligandId: String, extension: String, write: (OutputStream) -> Unit): File {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("cannot create $directory")
        val name = fileNameFor(ligandId, extension)
        val target = File(directory, name)
        val partial = File(directory, "$name.part")
        try {
            partial.outputStream().use(write)
        } catch (e: Exception) {
            partial.delete()
            throw if (e is IOException) e else IOException("writing $name failed", e)
        }
        directory.listFiles()
            ?.filter { it != partial && it.name != name && isOurs(it.name) }
            ?.forEach { it.delete() }
        if (target.exists() && !target.delete()) {
            partial.delete()
            throw IOException("cannot replace $name")
        }
        if (!partial.renameTo(target)) {
            partial.delete()
            throw IOException("cannot rename to $name")
        }
        return target
    }

    companion object {
        /** The folder inside the cache. Must match `res/xml/file_paths.xml`. */
        const val DIRECTORY = "shared"

        /**
         * "001" -> "ligand_001.png". Ids are already validated (1-3 letters or digits), but a file name is the
         * classic place for a path trick ("../x"), so anything that is not a letter, digit, '_' or '-' becomes '_'.
         */
        fun fileNameFor(ligandId: String, extension: String = "png"): String {
            val safe = ligandId.map { if (it.isLetterOrDigit() && it.code < 128 || it == '_' || it == '-') it else '_' }
                .joinToString("")
                .ifEmpty { "unknown" }
            require(extension in EXTENSIONS) { "unknown export extension: $extension" }
            return "ligand_$safe.$extension"
        }

        /** Every extension this folder may hold: a file name never takes its extension from outside. */
        val EXTENSIONS = setOf("png", "jpg", "cif", "mol", "glb", "mp4")

        /** A file this class wrote (any export, or a leftover .part): those are cleaned; anything else is left alone. */
        private fun isOurs(name: String) =
            name.endsWith(".part") || (name.startsWith("ligand_") && EXTENSIONS.any { name.endsWith(".$it") })
    }
}
