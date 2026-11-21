package dev.knowledge82.swiftyproteins.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import dev.knowledge82.swiftyproteins.R
import dev.knowledge82.swiftyproteins.export.GlbWriter
import dev.knowledge82.swiftyproteins.export.MolfileWriter
import dev.knowledge82.swiftyproteins.molecule.Molecule
import dev.knowledge82.swiftyproteins.scene.Representation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/** A capture that failed for a reason the user should read (see [CaptureFailure.userMessage]). */
class CaptureException(val failure: CaptureFailure, cause: Throwable? = null) : Exception(failure.name, cause)

/**
 * The export menu (documents 09 and 18): captures the 3D scene with a caption (PNG, JPEG, or into the GALLERY), or
 * makes a file from the molecule (.cif original, .mol, .glb), and opens Android's share sheet.
 *
 * WHY THIS WAY: SceneView draws into a SurfaceView, a separate GPU surface that the normal `View.draw()` cannot see
 * (it would give a black rectangle). `PixelCopy.request(SurfaceView, ...)` (API 24+) is Android's way to copy the
 * last frame of such a surface. SceneView does not hand out its SurfaceView, but it is a real View inside the window,
 * so it is found by walking the view tree ([findSurfaceView]).
 */
object MoleculeSharer {

    /** How long PixelCopy may take before we give up. A copy normally takes a few milliseconds. */
    private const val COPY_TIMEOUT_MILLIS = 3_000L

    /**
     * The whole job. Runs on the main thread except the heavy parts (drawing the caption, writing the PNG), which go
     * to background threads. Cancelled if the screen goes away (the coroutine scope of the screen).
     *
     * @param root any view of the window (Compose's `LocalView.current`)
     * @throws CaptureException with the reason to show
     */
    /**
     * Does [format]. [representation] and [showHydrogens] are what is on screen: the .glb exports exactly that.
     * Saving to the gallery on Android 7-9 needs the storage permission: the SCREEN asks for it before calling this.
     */
    suspend fun export(context: Context, root: View, molecule: Molecule, format: ExportFormat, representation: Representation, showHydrogens: Boolean) {
        val store = ShareImageStore(File(context.cacheDir, ShareImageStore.DIRECTORY))
        when (format) {
            ExportFormat.PNG, ExportFormat.JPEG -> {
                val captioned = capture(context, root, molecule)
                val file = written(captioned) {
                    store.save(molecule.id, format.extension) { out ->
                        val ok = if (format == ExportFormat.PNG) captioned.compress(Bitmap.CompressFormat.PNG, 100, out)
                        else captioned.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                        if (!ok) throw IOException("${format.name} compression failed")
                    }
                }
                openShareSheet(context, file, molecule, format)
            }
            ExportFormat.GALLERY -> {
                val captioned = capture(context, root, molecule)
                written(captioned) { GallerySaver.savePng(context, captioned, GalleryName.of(molecule.id, System.currentTimeMillis())) }
            }
            ExportFormat.CIF -> {
                // The text as it came from RCSB, from the cache (document 13). Copied to the shared folder: the
                // FileProvider opens ONLY that folder, never the cache of ligands itself.
                val original = File(File(context.cacheDir, "ligands"), "${molecule.id}.cif")
                val file = withContext(Dispatchers.IO) {
                    if (!original.isFile) throw CaptureException(CaptureFailure.NO_SOURCE_FILE)
                    try { store.save(molecule.id, "cif") { out -> original.inputStream().use { it.copyTo(out) } } }
                    catch (e: IOException) { throw CaptureException(CaptureFailure.WRITE_FAILED, e) }
                }
                openShareSheet(context, file, molecule, format)
            }
            ExportFormat.MOL, ExportFormat.GLB -> {
                val file = withContext(Dispatchers.Default) {
                    val bytes = try {
                        if (format == ExportFormat.MOL) MolfileWriter.write(molecule).toByteArray(Charsets.US_ASCII)
                        else GlbWriter.write(molecule, representation, showHydrogens)
                    } catch (e: IllegalArgumentException) {
                        throw CaptureException(CaptureFailure.OTHER, e)   // more than 999 atoms for a .mol
                    }
                    withContext(Dispatchers.IO) {
                        try { store.save(molecule.id, format.extension) { it.write(bytes) } }
                        catch (e: IOException) { throw CaptureException(CaptureFailure.WRITE_FAILED, e) }
                    }
                }
                openShareSheet(context, file, molecule, format)
            }
        }
    }

    /** The scene with its caption band (on a background thread for the drawing). */
    private suspend fun capture(context: Context, root: View, molecule: Molecule): Bitmap {
        val surface = findSurfaceView(root.rootView) ?: throw CaptureException(CaptureFailure.NO_SCENE)
        val scene = copy(surface)
        return withContext(Dispatchers.Default) {
            try { addCaption(context, scene, ShareText.caption(molecule)) } finally { scene.recycle() }
        }
    }

    /** Runs [write] on the IO thread, turns a failure into WRITE_FAILED, and always frees the bitmap. */
    private suspend fun <T> written(bitmap: Bitmap, write: () -> T): T = withContext(Dispatchers.IO) {
        try {
            write()
        } catch (e: IOException) {
            throw CaptureException(CaptureFailure.WRITE_FAILED, e)
        } finally {
            bitmap.recycle()
        }
    }

    /** Depth-first search for the first SurfaceView under [view]. On this screen there is exactly one: SceneView's. */
    fun findSurfaceView(view: View): SurfaceView? {
        if (view is SurfaceView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findSurfaceView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    /** PixelCopy turned into a suspend function: it answers through a callback, we wait for it. */
    private suspend fun copy(surface: SurfaceView): Bitmap {
        val width = surface.width
        val height = surface.height
        if (width <= 0 || height <= 0) throw CaptureException(CaptureFailure.NOT_READY)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val result = withTimeoutOrNull(COPY_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                PixelCopy.request(surface, bitmap, { code -> continuation.resume(code) }, Handler(Looper.getMainLooper()))
            }
        }
        if (result != PixelCopy.SUCCESS) {
            // After a TIMEOUT (result == null) PixelCopy may still write into the bitmap later: do not recycle it,
            // let the garbage collector take it. After any real answer it is safe to free it now.
            if (result != null) bitmap.recycle()
            throw CaptureException(
                when (result) {
                    null, PixelCopy.ERROR_TIMEOUT -> CaptureFailure.TIMEOUT
                    PixelCopy.ERROR_SOURCE_NO_DATA -> CaptureFailure.NOT_READY
                    PixelCopy.ERROR_SOURCE_INVALID -> CaptureFailure.NO_SCENE
                    else -> CaptureFailure.OTHER
                }
            )
        }
        return bitmap
    }

    /** A new bitmap: the scene on top, a dark band with the caption below (so the image says which ligand it is). */
    private fun addCaption(context: Context, scene: Bitmap, caption: String): Bitmap {
        val band = (scene.width * 0.09f).toInt().coerceAtLeast(48)
        val out = Bitmap.createBitmap(scene.width, scene.height + band, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(BAND_COLOR)
        canvas.drawBitmap(scene, 0f, 0f, null)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_COLOR
            textSize = band * 0.38f
            // The app's console font (Share Tech Mono); if it cannot be loaded, the system monospace: never a failure.
            typeface = runCatching { ResourcesCompat.getFont(context, R.font.share_tech_mono_regular) }.getOrNull() ?: Typeface.MONOSPACE
        }
        // Shrink the text if the caption would not fit between the margins (it did not, with the wide pixel font tried
        // before; Share Tech Mono is narrower, but a long ligand id or a small capture can still need it).
        val maxWidth = scene.width - band * 0.8f
        val width = paint.measureText(caption)
        if (width > maxWidth) paint.textSize = paint.textSize * maxWidth / width
        val baseline = scene.height + band / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(caption, band * 0.4f, baseline, paint)
        return out
    }

    /**
     * Opens the system share sheet with the PNG.
     *
     * The file is in the app's private cache: other apps cannot read it by path. FileProvider gives a `content://`
     * address, and FLAG_GRANT_READ_URI_PERMISSION lets ONLY the chosen app read it. Setting `clipData` too is what
     * makes the permission reach the app picked in the chooser on recent Android versions.
     */
    private fun openShareSheet(context: Context, file: File, molecule: Molecule, format: ExportFormat) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = format.intentType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, ShareText.message(molecule))
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, ShareText.chooserTitle(molecule))
        try {
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            throw CaptureException(CaptureFailure.NO_RECEIVER, e)
        }
    }

    /** JPEG quality: 92 keeps the thin sticks and the caption crisp at about a third of the PNG's size. */
    private const val JPEG_QUALITY = 92

    private const val BAND_COLOR = 0xFF050806.toInt()   // SwiftyColors.Abyss
    private const val TEXT_COLOR = 0xFF43E07A.toInt()   // SwiftyColors.Phosphor
}
