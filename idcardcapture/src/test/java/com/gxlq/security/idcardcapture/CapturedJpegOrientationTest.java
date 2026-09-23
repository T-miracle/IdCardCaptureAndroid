package io.github.uniidcardcapture;

import android.media.ExifInterface;

import org.junit.Test;

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
        // Rotate only the selected crop; rotating the full bitmap first picks the opposite side.
        assertEquals(180, CapturedJpegOrientation.afterCropRotation(0, 180));
        assertEquals(180, CapturedJpegOrientation.afterCropRotation(180, 0));
        assertEquals(0, CapturedJpegOrientation.afterCropRotation(90, 90));
        assertEquals(0, CapturedJpegOrientation.afterCropRotation(270, 270));
    }
}
