package io.github.uniidcardcapture;

/** Prefers a still-photo aspect that can be mapped to the preview without a large crop. */
final class CaptureSizeSelector {
    private static final float MAX_ASPECT_ERROR = 0.01f;

    private CaptureSizeSelector() {
    }

    static int pictureIndex(int previewWidth, int previewHeight, int[] widths, int[] heights) {
        int largest = 0;
        int largestMatching = -1;
        float previewRatio = previewWidth > 0 && previewHeight > 0
            ? previewWidth / (float) previewHeight : 0f;
        for (int i = 0; i < widths.length; i++) {
            if ((long) widths[i] * heights[i] > (long) widths[largest] * heights[largest]) {
                largest = i;
            }
            if (previewRatio > 0f && heights[i] > 0
                && Math.abs(widths[i] / (float) heights[i] - previewRatio) / previewRatio
                    <= MAX_ASPECT_ERROR
                && (largestMatching < 0
                    || (long) widths[i] * heights[i]
                        > (long) widths[largestMatching] * heights[largestMatching])) {
                largestMatching = i;
            }
        }
        return largestMatching >= 0 ? largestMatching : largest;
    }
}
