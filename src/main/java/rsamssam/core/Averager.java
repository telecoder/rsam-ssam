package rsamssam.core;

import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rsamssam.query.Query;

/**
 * The Averager function is to reduce the total amount of results according to a
 * calculated averaging factor.
 * <p>
 * Currently this averaging factor is just the amount of days processed, so, a
 * five day rsam-ssam graph will produce the same amount of results than a
 * single day.
 * <p>
 * Results should be added to a averager instance and it will automatically
 * schedules averaging rounds as results are coming in.
 * <p>
 * An internal blocking queue is used to keep the averaged results. This results
 * should be extracted until this queue is empty.
 * <p>
 * IMPORTANT: Spectra results are trimmed to the cutoff frequency.
 *
 * @author Julian Peña.
 */
public class Averager {

    /**
     * Window size for the query.
     */
    private final int windowSize;

    /**
     * Frequency resolution in the spectra results.
     */
    private final double frequencyResolution;

    /**
     * How many results are reduced to just one after averaging them?.
     */
    private final long averagingFactor;

    /**
     * Incoming queue for results rsam-ssam.
     */
    private final LinkedBlockingQueue<Result> input;

    /**
     * Averaged results.
     */
    private final LinkedBlockingQueue<Result> output;

    /**
     * Spectra resulting after averaging results.
     */
    private final double[] averageSsam;

    /**
     * Are there any resuls pending of averaging?.
     */
    private Boolean resultsPending = true;

    /**
     * The window size required for accommodating spectra results to the cutoff
     * frequency.
     */
    private final int cutoffWindow;

    /**
     * Total number of results. This value is only calculated/relevant when the
     * query only spans over a day or less, in which case we pad with results if
     * needed to complete the day.
     */
    private int resultsPerDay;

    /**
     * How many results have been retrieved from this averager?. This value is
     * only relevant for single day queries.
     */
    private int retrieved = 0;

    /**
     * Our logger.
     */
    private final Logger LOG = LoggerFactory.getLogger("Averager");

    public Averager(Query query, int sps) {

        windowSize = query.getWindowSize();
        cutoffWindow = query.getCutoffWindowSize().get();
        averagingFactor = query.getAveraging();
        frequencyResolution = 1d * sps / windowSize;

        input = new LinkedBlockingQueue<>();
        output = new LinkedBlockingQueue<>();

        averageSsam = new double[cutoffWindow];

        LOG.info("{} Averaging factor: {}", query.getId(), averagingFactor);

        if (averagingFactor == 1) {
            // if the day is not complete, how many results we should padd?
            // no leap seconds support, don't actualy matter here ...
            resultsPerDay = (int) Math.ceil(86400d * sps / windowSize);
            LOG.info("{} {} bins expected", query.getId(), resultsPerDay);
        }
    }

    /**
     * Adds a Result record to the internal queue, if the amount of results in
     * the queue is equal or greater than the averaging factor then an averaging
     * round is triggered.
     *
     * @param result
     */
    public void addResult(Result result) {
        
        input.add(result);
        
        if (averagingFactor == 1) {
            // this counter will helps later determine if padding is needed to
            // complete the single day graph
            resultsPerDay--;
        }

        if (input.size() >= averagingFactor) {
            average();
        }
    }

    /**
     * Performs a round of averaging and produces a single result from a group
     * of results.
     */
    private void average() {

        double rsam = 0;
        double[] spectra = new double[cutoffWindow];

        int count = 0;                      // well ... a counter
        Result result;                      // current result

        while (count < averagingFactor) {

            result = input.poll();

            // no more elements
            if (result == null) {
                break;
            }

            // the processor uses NaN in rsam as a indicator that this bin
            // should be "discarded" as it is probable a data gap.
            if (!Double.isNaN(result.rsam())) {
                rsam += result.rsam();
            }

            // if the bin is to be "discarded", we choose here just to add the
            // unitary array that comes as spectra ... we'll see how this goes
            for (int i = 0; i < cutoffWindow; i++) {
                spectra[i] += result.spectra()[i];
            }

            count++;
        }

        rsam = rsam / averagingFactor;

        double[] maxFreq = new double[2];

        for (int i = 0; i < cutoffWindow; i++) {

            spectra[i] = spectra[i] / averagingFactor;
            
            averageSsam[i] += spectra[i];

            if (spectra[i] > maxFreq[1]) {
                maxFreq[0] = i * frequencyResolution;
                maxFreq[1] = spectra[i];
            }

        }

        output.add(new Result(rsam, spectra, maxFreq));
    }

    /**
     * Returns and removes the oldest result from the internal queue.
     * <p>
     * IMPORTANT: This method can potentially block for an INFINITE amount of
     * time, you should call it only if you are completely sure that there are
     * elements available to be returned by calling the hasResults method first.
     *
     * @return
     * @throws InterruptedException
     */
    public Result getResult() throws InterruptedException {
        retrieved++;
        return output.take();
    }

    /**
     * Calculates and returns the average SSAM.
     *
     * @return
     */
    public double[] getAverageSsam() {

        // lets find the most powerfull frequency
        double maxEnergy = averageSsam[0];
        for (int i = 0; i < averageSsam.length; i++) {
            if (maxEnergy < averageSsam[i]) {
                maxEnergy = averageSsam[i];
            }
        }
        
        // now lets normalize the average spectra
        for (int i = 0; i < averageSsam.length; i++) {
            
            // this is the legit average, however ...
            // averageSsam[i] = averageSsam[i] / maxEnergy;
            
            // doing it this way we accentuate a little bit more the differences
            // fot the human eye
            averageSsam[i] = Math.pow(50, averageSsam[i] / maxEnergy);
        }

        return averageSsam;
    }

    /**
     * Do we have results pending of averaging?
     *
     * @return
     */
    public boolean hasResults() {

        if (resultsPending) {
            return true;
        }

        if (averagingFactor == 1 & resultsPerDay > 0) {
            return true;
        }

        return !output.isEmpty();
    }

    /**
     * Signals this Averager instance that there are no more samples coming in,
     * thus, if there are pending samples needed to complete a bin, this method
     * pads with zeros.
     * <p>
     * You MUST specifically call this method once all samples have been added.
     */
    public void complete() {

        if (averagingFactor > 1) {

            if (input.isEmpty()) {
                resultsPending = false;
                return;
            }

            // do we need to pad to complete the last round of averaging?
            if (input.size() < averagingFactor) {
                var r = new Result(Double.NaN, new double[cutoffWindow], null);
                int padd = (int) averagingFactor - input.size();
                for (int i = 0; i < padd; i++) {
                    input.add(r);
                }
                average();
            }
        }

        // only in the case of 1 day queries, we padd to complete 24 hours
        if (averagingFactor == 1 && resultsPerDay > 0) {

            int pad = resultsPerDay - retrieved;

            double rsam = Double.NaN;
            double[] ssam = new double[cutoffWindow];
            double[] maxFreq = {Double.NaN, Double.NaN};

            Result result = new Result(rsam, ssam, maxFreq);

            // how may result we have to add?
            LOG.info("Will add {} results to complete the day", resultsPerDay);
            while (pad > 0) {
                addResult(result);
                pad--;
            }
        }
        resultsPending = false;
    }

}
