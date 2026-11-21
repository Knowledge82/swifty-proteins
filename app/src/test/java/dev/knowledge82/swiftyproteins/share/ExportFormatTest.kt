package dev.knowledge82.swiftyproteins.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFormatTest {

    @Test
    fun everyFormatCanBeStoredForSharing() {
        // The shared folder refuses unknown extensions: a format it would refuse could never be shared.
        for (f in ExportFormat.entries) assertTrue(f.name, f.extension in ShareImageStore.EXTENSIONS)
    }

    @Test
    fun imagesKeepTheirTypeAndFilesGoAsGenericData() {
        assertEquals("image/png", ExportFormat.PNG.intentType)
        assertEquals("image/jpeg", ExportFormat.JPEG.intentType)
        for (f in listOf(ExportFormat.CIF, ExportFormat.MOL, ExportFormat.GLB)) {
            assertFalse(f.isImage)
            assertEquals("application/octet-stream", f.intentType)
        }
        assertEquals("model/gltf-binary", ExportFormat.GLB.mimeType)   // the registered type, kept for reference
    }

    @Test
    fun theMenuHasTheEightChoicesInOrder() {
        assertEquals(listOf("PNG", "JPEG", "GALLERY", "CIF", "MOL", "GLB", "VIDEO", "VIDEO_GALLERY"), ExportFormat.entries.map { it.name })
    }

    @Test
    fun videosAreMp4AndGoAsVideo() {
        // Document 22: unlike the chemical files, every phone has apps that claim video/mp4.
        for (f in listOf(ExportFormat.VIDEO, ExportFormat.VIDEO_GALLERY)) {
            assertTrue(f.isVideo); assertFalse(f.isImage)
            assertEquals("mp4", f.extension)
            assertEquals("video/mp4", f.intentType)
        }
        assertEquals(listOf(ExportFormat.GALLERY, ExportFormat.VIDEO_GALLERY), ExportFormat.entries.filter { it.toGallery })
        assertTrue(ExportFormat.entries.none { it.isImage && it.isVideo })
    }

    @Test
    fun aVideoInTheGalleryGetsItsOwnExtension() {
        assertTrue(GalleryName.of("ATP", 0L, "mp4").endsWith(".mp4"))
        assertTrue(GalleryName.of("ATP", 0L).endsWith(".png"))
    }

    @Test
    fun galleryNamesSayTheLigandAndTheMomentInUtc() {
        // 2026-11-21 16:24:00 UTC
        assertEquals("SwiftyProteins_ATP_20261121_162400.png", GalleryName.of("ATP", 1_795_278_240_000L))
    }

    @Test
    fun twoSavesASecondApartNeverShareAName() {
        assertNotEquals(GalleryName.of("ATP", 1_795_278_240_000L), GalleryName.of("ATP", 1_795_278_241_000L))
    }

    @Test
    fun aStrangeIdCannotMakeAPath() {
        val name = GalleryName.of("../x", 0L)
        assertFalse(name.contains('/'))
        assertTrue(name.startsWith("SwiftyProteins_x_19700101_000000"))
        assertEquals("SwiftyProteins_ligand_19700101_000000.png", GalleryName.of("Å/", 0L))
    }
}
