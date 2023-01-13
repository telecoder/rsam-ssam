package rsamssam.core;

import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Packager receives samples and "package" them in arrays according to the
 * Query's window parameter.
 *
 * @author Julian Peña.
 */
public class Packager {

    /**
     * Auxiliary array holder for samples being packaged.
     */
    private final double[] bin;

    /**
     * This index keeps track of the position that will be occupied by the next
     * sample coming in.
     */
    private int index;

    /**
     * Maximun index in the bins.
     */
    private final int maxIndex;

    /**
     * Queue that holds all the bins.
     */
    private final LinkedBlockingQueue<double[]> queue;

    /**
     * Capacity of the bins queue.
     */
    private final int CAPACITY = 1000;

    /**
     * Our logger.
     */
    private final Logger LOG = LoggerFactory.getLogger("Packager");

    public Packager(int windowSize) {

        bin = new double[windowSize];

        index = 0;
        maxIndex = windowSize - 1;

        queue = new LinkedBlockingQueue<>(CAPACITY);
    }

    /**
     * Adds a sample to be packaged.
     *
     * IMPORTANT: This method can potentially block, if the internal samples
     * queue is full then it will wait for space to be available.
     *
     * @param sample
     * @throws java.lang.InterruptedException
     */
    public void addSample(double sample) throws InterruptedException {

        // poison pill check - this marks the end of the stream
        if (Double.isNaN(sample)) {
            // in case of incomplete bin then padd with zeros
            if (index > 0) {
                while (index <= maxIndex) {
                    bin[index] = 0;
                    index++;
                }
                packageBin();
            }

            // insert our poison pill for consumers down the road
            // this is just an empty double array
            queue.put(new double[0]);

            return;
        }

        bin[index] = sample;
        index++;

        if (index > maxIndex) {
            packageBin();
            index = 0;
        }
    }

    /**
     * Adds a bin to the output queue.
     *
     * @throws InterruptedException
     */
    private void packageBin() throws InterruptedException {
        double[] clone = new double[bin.length];
        System.arraycopy(bin, 0, clone, 0, bin.length);
        queue.put(clone);
    }

    /**
     * Returns an array of samples. The number of samples returned is equal to
     * the Query's window size.
     * <p>
     * When no more data is inside this Packager object, this method returns a
     * "poison pill", which is just an array of length 0.
     * <p>
     * IMPORTANT: This method blocks until there is something to return, so it
     * is better to call this method from a separated thread.
     *
     * @return An array containing samples.
     */
    public double[] getBin() {
        try {
            return queue.take();
        } catch (InterruptedException ex) {
            LOG.error("Unexpected Interruption while returning sample");
            LOG.error(ex.getMessage());
            // return the poison pill
            return new double[0];
        }
    }

}
