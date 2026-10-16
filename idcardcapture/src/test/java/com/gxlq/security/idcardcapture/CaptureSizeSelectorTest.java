package io.github.uniidcardcapture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CaptureSizeSelectorTest {
    @Test
    public void iqooUsesPictureWithSameAspectAsIts2340By1080Preview() {
        int[] widths = {4080, 4080, 3200, 2400, 2340};
        int[] heights = {3060, 2296, 1440, 1080, 1080};
        assertEquals(4, CaptureSizeSelector.pictureIndex(2340, 1080, widths, heights));
    }

    @Test
    public void matchingPictureWithMostPixelsWins() {
        int[] widths = {4000, 1920, 3264, 4000};
        int[] heights = {3000, 1080, 1836, 2250};
        assertEquals(3, CaptureSizeSelector.pictureIndex(1920, 1080, widths, heights));
    }

    @Test
    public void fallsBackToLargestPictureIfNoAspectMatchExists() {
        int[] widths = {1600, 4000, 3264};
        int[] heights = {1200, 3000, 2448};
        assertEquals(1, CaptureSizeSelector.pictureIndex(1920, 1080, widths, heights));
    }

    @Test
    public void largestPictureRemainsSelectedWhenItsAspectAlreadyMatches() {
        int[] widths = {4000, 3264, 1920};
        int[] heights = {2250, 1836, 1080};
        assertEquals(0, CaptureSizeSelector.pictureIndex(1920, 1080, widths, heights));
    }
}
