package rsamssam.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Julian Peña.
 */
public class ProcessorTest {

    static Processor processor;
    static Result result;

    static int windowSize = 32;
    static String windowName = "hann";
    static int cutoffWindowSize = windowSize;
    static int freq = 8;
    static int dcLevel = 10;
    static int amplitude = 20;
    static double responseFactor = 1;

    // The following results were taken from a snapshot ..., these tests could
    // definetely be improved
    static double[] spectra = {
        -19.618572444069354, -18.78499960866594, -16.561414185552785,
        -13.342490233400653, -9.171395939346148, -3.586624753578376,
        5.124040368378957, 38.184634457904366, 43.80663396340583,
        38.18463445790436, 5.124040368378502, -3.5866247535790814,
        -9.171395939343402, -13.342490233398426, -16.561414185557517,
        -18.784999608668258, -19.618572444064803, -18.784999608667775,
        -16.561414185557503, -13.34249023339871, -9.171395939343402,
        -3.5866247535791644, 5.124040368378503, 38.18463445790436,
        43.80663396340583, 38.184634457904366, 5.124040368378955,
        -3.58662475357829, -9.171395939346148, -13.34249023340037,
        -16.561414185552795, -18.784999608666425};

    static double rsam = 4.843750000000006;

    public ProcessorTest() {
    }

    @BeforeAll
    public static void setUpClass() {
        processor = new Processor(windowSize, windowName, cutoffWindowSize, responseFactor);
        result = processor.process(getSamples(windowSize, amplitude, freq, dcLevel));
    }

    @Test
    public void fftIsCorrect() {
        assertArrayEquals(spectra, result.spectra(), "wrong fft spectra");
    }

    @Test
    public void rsamIsCorrect() {
        assertEquals(rsam, result.rsam(), "wrong rsam");
    }

    static private double[] getSamples(int windowSize, int amplitude, int freq,
            int dc) {

        double[] samples = new double[windowSize];
        double step = 2 * Math.PI / windowSize;

        for (int i = 0; i < windowSize; i++) {
            samples[i] = dc + amplitude * Math.sin(freq * i * step);
        }

        return samples;
    }

}
