package io.github.uniidcardcapture;

import android.media.ExifInterface;

/**
 * The legacy Camera API may rotate JPEG pixels or leave them in sensor order with EXIF metadata.
 * Only the EXIF value describes the rotation still needed after BitmapFactory decodes the pixels.
 */
final class CapturedJpegOrientation {
    private CapturedJpegOrientation() {
    }

    static int degrees(int exifOrientation) {
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
            case ExifInterface.ORIENTATION_TRANSPOSE:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
            case ExifInterface.ORIENTATION_TRANSVERSE:
                return 270;
            default:
                return 0;
        }
    }

    static boolean mirrored(int exifOrientation) {
        return exifOrientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL
            || exifOrientation == ExifInterface.ORIENTATION_FLIP_VERTICAL
            || exifOrientation == ExifInterface.ORIENTATION_TRANSPOSE
            || exifOrientation == ExifInterface.ORIENTATION_TRANSVERSE;
    }

    static int afterCropRotation(int displayOrientation, int jpegRotation) {
        return (displayOrientation - jpegRotation + 360) % 360;
    }
}
