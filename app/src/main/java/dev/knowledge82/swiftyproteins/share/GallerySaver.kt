package dev.knowledge82.swiftyproteins.share

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/**
 * Saves an image into the phone's GALLERY, album "Swifty Proteins" (the mandatory "save it to your photo album" of
 * the evaluation, document 18).
 *
 * Two ways, because Android changed the rules in version 10 (API 29), "scoped storage":
 * - **Android 10+:** through MediaStore, the system's media database. No permission at all: an app may always ADD
 *   its own pictures. The file is first inserted as PENDING (invisible to other apps), written, then published:
 *   the gallery never shows a half-written image.
 * - **Android 7-9:** a plain file in Pictures/Swifty Proteins, which needs WRITE_EXTERNAL_STORAGE (declared with
 *   maxSdkVersion="28" in the manifest and asked at run time by the screen), then the media scanner is told about
 *   it so the gallery shows it at once.
 */
object GallerySaver {

    /** @return where it went, for the status line */
    fun savePng(context: Context, bitmap: Bitmap, displayName: String): String {
        val relative = "${Environment.DIRECTORY_PICTURES}/${GalleryName.ALBUM}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: throw IOException("MediaStore refused the new image")
            try {
                val out = resolver.openOutputStream(uri) ?: throw IOException("cannot open $uri")
                out.use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("PNG compression failed") }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)   // never leave an empty or broken entry in the gallery
                throw if (e is IOException) e else IOException("saving to the gallery failed", e)
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), GalleryName.ALBUM)
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
            val file = File(dir, displayName)
            try {
                file.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("PNG compression failed") }
            } catch (e: IOException) {
                file.delete()
                throw e
            }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
        }
        return relative
    }
}
