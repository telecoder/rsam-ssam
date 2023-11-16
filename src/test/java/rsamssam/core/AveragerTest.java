package rsamssam.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;

import rsamssam.query.Metadata;
import rsamssam.query.Query;

import java.util.Arrays;

/**
 *
 * @author Julian Peña.
 */
public class AveragerTest {

    Query query;
    Averager averager;

    final int sps = 16;
    final int windowSize = 16;
    final int cutoff = 8;

    // a little more than two days of data
    final int secondsOfData = 86400 * 2 + 160;

    final double totalBins = Math.ceil(1d * secondsOfData * sps / windowSize);

    public AveragerTest() {
    }

    @BeforeEach
    public void setQuery() {
        query = new Query("JULI", "HHZ", "CM", "00");
        query
                .setWindowSize(windowSize)
                .setCutoffFrequency(cutoff)
                .setFrom(0)
                .setTo(secondsOfData);
        
        query.setMetadata(new Metadata(sps, 0));

        averager = new Averager(query, sps);
    }

    @Test
    public void resultsQuantityIsCorrect() throws InterruptedException {

        double[] spectra = new double[windowSize];

        for (int i = 0; i < totalBins; i++) {
            averager.addResult(new Result(1, spectra, null));
        }
        averager.complete();

        long bins = 0;
        while (averager.hasResults()) {
            averager.getResult();
            bins++;
        }

        assertEquals(totalBins, bins, "wrong number of results after average");
    }

    @Test
    public void averagesAreCorrectlyCalculated() throws InterruptedException {

        double[] spectra = {2, 1, 2, 0, 4, 2, 2, 0, 18, 1, 2, 0, 12, 1, 2, 3};

        // since cutoff frequency is 8, then the expected average spectra length is 8
        double[] expectedSpectra = {2, 1, 2, 0, 4, 2, 2, 0};

        // dominant frequency at index 4 with a power of 4 (because the upper half of the spectra is discarded because of nyquist).
        double freqResolution = 1d * sps / windowSize;
        double freq = freqResolution * 4;
        double freqPower = 4;
        double[] maxFreq = {freq, freqPower};

        for (int i = 0; i < totalBins; i++) {
            averager.addResult(new Result(1, spectra, maxFreq));
        }
        averager.complete();

        Result result = averager.getResult();

        assertEquals(1d, result.rsam(), "wrong rsam value");
        assertArrayEquals(expectedSpectra, result.spectra(), "wrong average spectra");

        // average ssam is a normalized value using the maximum power
        double[] avgSsam = {7.0710678118654755, 2.6591479484724942, 7.0710678118654755, 1.0, 50.0, 7.0710678118654755, 7.0710678118654755, 1.0};
        assertArrayEquals(avgSsam, averager.getAverageSsam(), "wrong average ssam");

        assertArrayEquals(maxFreq, result.maxFreq(), "wrong max frequency");
    }

}
