package dev.knowledge82.swiftyproteins.share

/**
 * What the export menu offers (bonus VII.5 "multiple export formats", and the mandatory "save it to your photo
 * album", document 18). Pure: the Android code maps each one to a file and an intent.
 */
enum class ExportFormat(val label: String, val detail: String, val extension: String, val mimeType: String) {
    PNG("Share image", "PNG · lossless", "png", "image/png"),
    JPEG("Share image", "JPEG · smaller", "jpg", "image/jpeg"),
    GALLERY("Save to gallery", "Pictures/Swifty Proteins", "png", "image/png"),
    CIF("Share ligand file", ".cif · RCSB original", "cif", "chemical/x-cif"),
    MOL("Share chemical model", ".mol · MDL V2000", "mol", "chemical/x-mdl-molfile"),
    GLB("Share 3D model", ".glb · glTF 2.0", "glb", "model/gltf-binary"),
    VIDEO("Share video", "MP4 · one full turn · 6 s", "mp4", "video/mp4"),
    VIDEO_GALLERY("Save video to gallery", "Movies/Swifty Proteins", "mp4", "video/mp4"),
    ;

    /** The captured image (with its caption) or a file made from the molecule. */
    val isImage: Boolean get() = this == PNG || this == JPEG || this == GALLERY

    /** A recording of the molecule turning (document 22). */
    val isVideo: Boolean get() = this == VIDEO || this == VIDEO_GALLERY

    /** Goes to the phone's gallery (Pictures or Movies) instead of the share sheet. */
    val toGallery: Boolean get() = this == GALLERY || this == VIDEO_GALLERY

    /**
     * The MIME type to put on the share intent. The chemical types are registered names, but almost no app on a phone
     * claims them, and an intent nobody claims shows an empty share sheet: those files go as generic data
     * ("application/octet-stream"), and the file name (".mol", ".glb") tells the receiving app what it is.
     */
    val intentType: String get() = if (isImage || isVideo) mimeType else "application/octet-stream"
}

/** Names of the images saved to the gallery (document 18). Pure, tested. */
object GalleryName {
    /** The album: the folder under Pictures (images) and under Movies (videos) where the gallery groups them. */
    const val ALBUM = "Swifty Proteins"

    /**
     * "SwiftyProteins_ATP_20261121_172400.png": the ligand and the moment, so two saves never overwrite each other
     * and the gallery sorts them by name and by time alike. [utc] is a moment in UTC milliseconds.
     */
    fun of(ligandId: String, utc: Long, extension: String = "png"): String {
        val safe = ligandId.filter { it.isLetterOrDigit() && it.code < 128 }.ifEmpty { "ligand" }
        val f = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        return "SwiftyProteins_${safe}_${f.format(java.util.Date(utc))}.$extension"
    }
}
