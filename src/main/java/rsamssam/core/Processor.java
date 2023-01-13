package rsamssam.core;

import java.util.Arrays;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import rsamssam.config.Config;
import rsamssam.config.Names;

/**
 * This class implements the ssam and rsam logic. This class does not perform
 * all the necessary calculations for the final graph, some computations are
 * done while averaging the results.
 *
 * @author Julian Peña.
 */
public class Processor {

    /**
     * Maximum consecutive number of zero valued samples. If there are more than
     * this number then it is considered a data gap.
     */
    private final int zeroTolerance;

    /**
     * Apache's FFT computer.
     */
    private final FastFourierTransformer fft;

    /**
     * An unitary array is used in the spectra output to indicate that there is
     * a data gap.
     */
    private final double[] unitaryArray;

    /**
     * Dummy array for the dominant frequency part of the Result record. This is
     * just a temporary holder since the computation is performed in the
     * averaging phase.
     */
    private final double[] dummyMaxFreq = {Double.NaN, Double.NaN};

    /**
     * Window function used to pre-process bins. Window functions are used
     * reduce spectra artifacts in the window boundaries.
     */
    private double[] window;

    /**
     * We limit the output results to frequencies below or equal to the cutoff
     * frequency.
     */
    private final int cutoffWindow;

    /**
     * Response factor.
     */
    private final double responseFactor;

    /**
     * Creates a processor instance for performing rsam and fft computations
     * over arrays of samples.
     *
     * @param windowSize The desired window size (size of bins to be processed).
     * @param windowName The name for the window function to be applied to bins.
     * @param cutoffWindowSize If there is a desired cutoff frequency, then this
     * value MUST be smaller than the window size.
     * @param responseFactor The response factor to be applied to each sample
     * before processing.
     */
    public Processor(int windowSize, String windowName, int cutoffWindowSize,
            double responseFactor) {

        this.cutoffWindow = cutoffWindowSize;
        this.responseFactor = responseFactor;

        zeroTolerance = Config.getZeroTolerance();

        fft = new FastFourierTransformer(DftNormalization.STANDARD);

        // this array is used to fill gaps in the ssam output (the ssam graph
        // uses a logarithmic scale thus the output will contain zeros).
        unitaryArray = new double[cutoffWindowSize];
        for (int i = 0; i < cutoffWindowSize; i++) {
            unitaryArray[i] = 1;
        }

        setWindowFunction(windowSize, windowName);
    }

    /**
     * Builds and returns a window of the appropriate size.
     *
     * @param size Window size.
     * @param windowName The window function name (can be UNIFORM or HANN).
     * @return an array of doubles with values between 0 and 1 that represents
     * the window function.
     */
    private void setWindowFunction(int size, String windowName) {

        window = new double[size];

        if (windowName.equalsIgnoreCase(Names.WINDOW_UNIFORM)) {
            for (int i = 0; i < size; i++) {
                window[i] = 1;
            }
            return;
        }

        // default window is HANN
        for (int i = 0; i < size; i++) {
            window[i] = (1 - Math.cos(2 * Math.PI * i / (size - 1))) / 2;
        }
    }

    /**
     * Performs the rsam and FFT on the given bin.
     *
     * @param bin
     * @return A Result record containing the rsam value and the spectra for the
     * given bin. If the bin contained too many zeros, then it is considered to
     * be a data gap, in which case, a rsam value of Double.NaN is returned and
     * a unitary array is returned as spectra.
     */
    public Result process(double[] bin) {

        removeMedian(bin);
        
        if (applyFactors(bin)) {
            return new Result(rsamBin(bin), fftBin(bin), dummyMaxFreq);
        }

        // too many consecutive zeros in the bin. probably a data gap.
        return new Result(Double.NaN, unitaryArray, null);
    }

    /**
     * Applies the window function and response factor to the bin (performed in
     * place).
     *
     * @param bin
     * @return True if the bin was valid, false otherwise (if the bin contain
     * too many consecutive zeros).
     */
    private boolean applyFactors(double[] bin) {

        int zeros = 0;

        for (int i = 0; i < bin.length; i++) {

            if (bin[i] == 0) {
                zeros++;
            } else {
                zeros = 0;
            }

            if (zeros >= zeroTolerance) {
                return false;
            }

            bin[i] *= window[i] * responseFactor;
        }

        return true;
    }

    /**
     * Calculates and returns the rsam value for the given bin.
     *
     * @param bin The array of samples.
     * @return the computed rsam value.
     */
    private double rsamBin(double[] bin) {
        return Arrays
                .stream(bin)
                .map(Math::abs)
                .sum() / bin.length;
    }

    /**
     * Performs the FFT on the given array.
     *
     * @param bin The array of samples.
     * @return an array with the magnitudes of the spectrum obtained from
     * performing the FFT.
     */
    private double[] fftBin(double[] bin) {

        double[] spectra = new double[cutoffWindow];

        Complex[] complex = fft.transform(bin, TransformType.FORWARD);

        for (int i = 0; i < cutoffWindow; i++) {
            spectra[i] = 20 * Math.log10(complex[i].abs());
            if(Double.isInfinite(spectra[i])){
                System.out.println("Mega shit");
                System.out.println(Arrays.toString(bin));
            }
        }

        return spectra;
    }

    /**
     * Removes the median (DC level) from each sample in the given array. This
     * is performed in place.
     *
     * @param bin The array of samples.
     */
    private void removeMedian(double[] bin) {
        double[] orderedInput = Arrays.copyOf(bin, bin.length);
        Arrays.sort(orderedInput);
        double median = orderedInput[orderedInput.length / 2];
        for (int i = 0; i < bin.length; i++) {
            bin[i] -= median;
        }
    }

}
