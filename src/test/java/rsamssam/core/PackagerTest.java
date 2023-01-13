package rsamssam.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Julian Peña.
 */
public class PackagerTest {

    public PackagerTest() {
    }

    @Test
    public void numberOfBinsIsCorrect() throws Exception {

        int windowSize = 16;
        int samples = 153;
        int totalBins = 10;                     // 152/16 = 9.56 -> 10

        Packager packager = new Packager(windowSize);

        for (int i = 0; i < samples; i++) {
            packager.addSample(i);
        }
        packager.addSample(Double.NaN);         // the poison pill is NaN

        int bins = 0;
        while (packager.getBin().length > 0) {
            bins++;
        }

        assertEquals(totalBins, bins, "wrong number of bins after packaging");
    }

    @Test
    public void keepsDataSafe() throws InterruptedException {

        Packager packager = new Packager(5);

        // should form array1
        packager.addSample(0d);
        packager.addSample(1d);
        packager.addSample(2d);
        packager.addSample(3d);
        packager.addSample(4d);

        // should form array2
        packager.addSample(5d);
        packager.addSample(6d);
        packager.addSample(7d);
        packager.addSample(8d);
        packager.addSample(9d);

        // should form array3
        packager.addSample(10d);
        packager.addSample(Double.NaN);         // the poison pill is NaN

        double[] array1 = {0d, 1d, 2d, 3d, 4d};
        assertArrayEquals(array1, packager.getBin(), "samples were corrupted");

        double[] array2 = {5d, 6d, 7d, 8d, 9d};
        assertArrayEquals(array2, packager.getBin(), "samples were corrupted");

        double[] array3 = {10d, 0d, 0d, 0d, 0d};
        assertArrayEquals(array3, packager.getBin(), "samples were corrupted");
    }
}
