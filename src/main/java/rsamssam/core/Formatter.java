package rsamssam.core;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;


/**
 * This class implements a results formatter. The resulting Strings are suitable
 * for writing to disk.
 *
 * @author Julian Peña.
 */
public class Formatter {

    /**
     * Start time.
     */
    private final long start;

    /**
     * Time step between rsam-ssam results.
     */
    private final long timestep;

    /**
     * Just an auxiliary index for keeping track of results.
     */
    private int entryIndex;

    /**
     * Timestamp for rsam and max freq output files.
     */
    private Timestamp timestamp;

    /**
     * Builder for the rsam String representation.
     */
    private final StringBuilder rsam;

    /**
     * Builder for the average ssam String representation.
     */
    private final StringBuilder averageSsam;

    /**
     * Holder for the ssam results. This is used to generate the final output
     * String.
     */
    private final List<double[]> ssam;

    /**
     * Builder for the max frequencies String representation.
     */
    private final StringBuilder maxFreqs;

    /**
     * Formatter initializer.
     *
     * @param start the start time.
     * @param timestep the time step between results.
     */
    public Formatter(long start, long timestep) {

        this.start = start;
        this.timestep = timestep;

        rsam = new StringBuilder();
        ssam = new ArrayList<>();
        averageSsam = new StringBuilder();
        maxFreqs = new StringBuilder();
    }

    /**
     * Adds a single Result to this formatter.
     *
     * @param result
     */
    public void addResult(Result result) {
        timestamp = new Timestamp(start + entryIndex * timestep);
        addToRsam(result.rsam());
        addToSsam(result.spectra());
        addToMaxFreqs(result.maxFreq());
        entryIndex++;
    }

    /**
     * Adds a single rsam value to the rsam output builder.
     *
     * @param rsamValue
     */
    private void addToRsam(Double rsamValue) {
        rsam
                .append(timestamp)
                .append(",")
                .append(rsamValue)
                .append("\n");
    }

    /**
     * Adds a single spectra to the ssam output builder.
     *
     * @param spectra
     */
    private void addToSsam(double[] spectra) {
        ssam.add(spectra);
    }

    /**
     * Adds a single max frequency entry to the maxFreq output builder.
     *
     * @param maxFreq an array containing a pair of frequency and power values.
     */
    private void addToMaxFreqs(double[] maxFreq) {
        maxFreqs
                .append(timestamp)
                .append(",")
                .append(maxFreq[0])
                .append(",")
                .append(maxFreq[1])
                .append("\n");
    }

    /**
     * Adds the average ssam to the average ssam output builder. This is a
     * single result, so this method should be used only once.
     *
     * @param averageSsam
     */
    public void addAverageSsam(double[] averageSsam) {
        for (int i = 0; i < averageSsam.length; i++) {
            this.averageSsam.append(averageSsam[i]).append("\n");
        }
    }

    /**
     * Returns the average ssam as a String.
     *
     * @return
     */
    public String getAverageSsam() {
        return averageSsam.toString();
    }

    /**
     * Returns the rsam as a String.
     *
     * @return
     */
    public String getRsam() {
        return rsam.toString();
    }

    /**
     * Return the ssam as a String.
     *
     * @return
     */
    public String getSsam() {

        StringBuilder stringBuilder = new StringBuilder();

        int rows = ssam.get(0).length;
        int columns = ssam.size();

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                stringBuilder.append(ssam.get(column)[row]).append(' ');
            }
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            stringBuilder.append("\n");
        }

        return stringBuilder.toString();
    }

    /**
     * Returns the maximum frequencies as a String.
     *
     * @return
     */
    public String getMaxFreqs() {
        return maxFreqs.toString();
    }

}
