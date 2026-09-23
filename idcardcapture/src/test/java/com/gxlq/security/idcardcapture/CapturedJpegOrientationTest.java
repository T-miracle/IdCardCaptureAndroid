package io.github.uniidcardcapture;

import android.media.ExifInterface;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CapturedJpegOrientationTest {
    @Test
    public void physicallyRotatedPixelsAreNotRotatedAgain() {
        // Camera.Parameters.setRotation(180) can rotate the JPEG pixels itself.
        // EXIF is then NORMAL or missing, while the preview guide is on the left.
        assertEquals(0, CapturedJpegOrientation.degrees(ExifInterface.ORIENTATION_NORMAL));
        assertEquals(0, CapturedJpegOrientation.degrees(ExifInterface.ORIENTATION_UNDEFINED));
        assertFalse(CapturedJpegOrientation.mirrored(ExifInterface.ORIENTATION_NORMAL));
    }

    @Test
    public void metadataOnlyRotationIsAppliedAfterDecoding() {
        assertEquals(90, CapturedJpegOrientation.degrees(ExifInterface.ORIENTATION_ROTATE_90));
        assertEquals(180, CapturedJpegOrientation.degrees(ExifInterface.ORIENTATION_ROTATE_180));
        assertEquals(270, CapturedJpegOrientation.degrees(ExifInterface.ORIENTATION_ROTATE_270));
    }

    @Test
    public void mirroredExifOrientationsKeepTheirReflection() {
        assertTrue(CapturedJpegOrientation.mirrored(ExifInterface.ORIENTATION_FLIP_HORIZONTAL));
        assertTrue(CapturedJpegOrientation.mirrored(ExifInterface.ORIENTATION_FLIP_VERTICAL));
        assertTrue(CapturedJpegOrientation.mirrored(ExifInterface.ORIENTATION_TRANSPOSE));
        assertTrue(CapturedJpegOrientation.mirrored(ExifInterface.ORIENTATION_TRANSVERSE));
    }

    @Test
    public void postCropRotationMatchesPreviewForBothLandscapeDirections() {
        // Both landscape directions can leave the normalized JPEG opposite to the preview.
        // The crop rectangle must first be mapped to that JPEG coordinate space.
        assertEquals(180, CapturedJpegOrientation.afterCropRotation(0, 180));
        assertEquals(180, CapturedJpegOrientation.afterCropRotation(180, 0));
        assertEquals(0, CapturedJpegOrientation.afterCropRotation(90, 90));
        assertEquals(0, CapturedJpegOrientation.afterCropRotation(270, 270));
    }

    @Test
    public void guideOnLeftSelectsOppositeJpegCoordinatesBeforeHalfTurn() {
        // A 180-degree JPEG-to-preview rotation reverses both axes. The guide's
        // source area must be remapped before rotating the selected pixels.
        assertArrayEquals(new float[] {0.4f, 0.3f, 0.9f, 0.8f},
            CapturedJpegOrientation.previewRectInCapturedJpeg(0.1f, 0.2f, 0.6f, 0.7f, 180),
            0.0001f);
        assertArrayEquals(new float[] {0.1f, 0.2f, 0.6f, 0.7f},
            CapturedJpegOrientation.previewRectInCapturedJpeg(0.1f, 0.2f, 0.6f, 0.7f, 0),
            0.0001f);
    }
}
