package rsamssam.core;

import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import rsamssam.query.Metadata;

import rsamssam.query.Query;

/**
 *
 * @author Julian Peña.
 */
public class FormatterTest {

    static Query query;

    static Formatter formatter;

    static int secondsOfData = 16;
    static int sps = 4;
    static int windowSize = 8;
    static int bins = secondsOfData * sps / windowSize;

    // this is just made up data
    static double rsamValue = 1.0;

    // this is just made up data
    static double maxFreqHz = 1;
    static double maxFreqPower = 100;

    @BeforeAll
    public static void setQuery() {

        query = new Query("JULI", "HHZ", "CM", "00");
        query
                .setFrom(0)
                .setTo(secondsOfData)
                .setWindowSize(windowSize);

        query.setMetadata(new Metadata(sps, 0));
        
        formatter = new Formatter(query.getMetadata().get().start(), query.getTimestep().get());

        // fill the formatter with data
        double rsam = 1d;
        double[] spectra = {0d, 1d, 2d, 3d, 4d, 5d, 6d, 7d};
        double[] avgSsam = {0d, 1d, 2d, 3d, 4d, 5d, 6d, 7d};
        double[] maxFreq = {maxFreqHz, maxFreqPower};

        for (int i = 0; i < bins; i++) {
            formatter.addResult(new Result(rsam, spectra, maxFreq));
        }
        formatter.addAverageSsam(avgSsam);
    }

    @Test
    public void testAverageSsamOutput() {
        String avgSsam = """
                         0.0
                         1.0
                         2.0
                         3.0
                         4.0
                         5.0
                         6.0
                         7.0
                         """;
        assertEquals(formatter.getAverageSsam(), avgSsam, "wrong average ssam");
    }

    @Test
    public void testSsamOutput() {

        String ssam = """
                      0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0
                      1.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0
                      2.0 2.0 2.0 2.0 2.0 2.0 2.0 2.0
                      3.0 3.0 3.0 3.0 3.0 3.0 3.0 3.0
                      4.0 4.0 4.0 4.0 4.0 4.0 4.0 4.0
                      5.0 5.0 5.0 5.0 5.0 5.0 5.0 5.0
                      6.0 6.0 6.0 6.0 6.0 6.0 6.0 6.0
                      7.0 7.0 7.0 7.0 7.0 7.0 7.0 7.0              
                      """;

        assertEquals(ssam, formatter.getSsam(), "wrong ssam");
    }

    @Test
    public void testRsamOutput() {

        String rsam = "";
        Timestamp timestamp;
        for (int i = 0; i < bins; i++) {
            timestamp = new Timestamp(i * query.getTimestep().get());
            rsam += timestamp.toString() + "," + rsamValue + "\n";
        }

        assertEquals(rsam, formatter.getRsam(), "wrong rsam");
    }

    @Test
    public void testMaxFreqsOutput() {

        String maxFreq = "";
        Timestamp timestamp;
        for (int i = 0; i < bins; i++) {
            timestamp = new Timestamp(i * query.getTimestep().get());
            maxFreq += timestamp.toString() + ","
                    + maxFreqHz + ","
                    + maxFreqPower + "\n";
        }

        assertEquals(maxFreq, formatter.getMaxFreqs(), "wrong max freqs");
    }

}
