package rsamssam.core;

import edu.iris.dmc.seedcodec.B1000Types;
import edu.iris.dmc.seedcodec.Steim2;

import edu.sc.seis.seisFile.mseed.Blockette1000;
import edu.sc.seis.seisFile.mseed.Btime;
import edu.sc.seis.seisFile.mseed.DataHeader;
import edu.sc.seis.seisFile.mseed.DataRecord;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import rsamssam.query.Query;

/**
 *
 * @author Julian Peña.
 */
public class DecompressorTest {

    static Decompressor decompressor;
    static int numSamples = 100;

    public DecompressorTest() {
    }

    @Test
    public void decompressedSamplesAreCorrect() {

        Query query = new Query("JULI", "HHZ", "CM", "00");
        query.setFrom(0);
        query.setTo(1000);

        decompressor = new Decompressor(query);

        int[] samples = getSamples(numSamples);

        decompressor.addDataRecord(getDataRecordAtSecond(0));

        // add poison pill to decompressor
        decompressor.addDataRecord(getDecompressorPoison());

        int[] decompressedSamples = new int[samples.length];

        double sample = decompressor.getSample();
        int index = 0;
        while (!Double.isNaN(sample)) {
            decompressedSamples[index] = (int) sample;
            sample = decompressor.getSample();
            index++;
        }

        assertArrayEquals(samples, decompressedSamples, "samples corrupted");
    }

    @Test
    public void gapIsFilledWithZeros() {

        Query query = new Query("JULI", "HHZ", "CM", "00");
        query.setFrom(0);
        query.setTo(3000);

        decompressor = new Decompressor(query);

        // the expected samples
        int[] samples = new int[numSamples * 3];
        System.arraycopy(getSamples(numSamples), 0, samples, 0, numSamples);
        System.arraycopy(new int[numSamples], 0, samples, numSamples, numSamples);
        System.arraycopy(getSamples(numSamples), 0, samples, numSamples * 2, numSamples);

        // samples starting in second 0
        decompressor.addDataRecord(getDataRecordAtSecond(0));
        // samples in second 1 are missing
        // samples starting in second 2
        decompressor.addDataRecord(getDataRecordAtSecond(2));

        // add poison pill to decompressor
        decompressor.addDataRecord(getDecompressorPoison());

        int[] decompressedSamples = new int[numSamples * 3];

        double sample = decompressor.getSample();
        int index = 0;
        while (!Double.isNaN(sample)) {
            decompressedSamples[index] = (int) sample;
            sample = decompressor.getSample();
            index++;
        }

        assertArrayEquals(samples, decompressedSamples, "gap not filled correctly");
    }

    /**
     * Returns a DataRecord object (miniseed packet) with samples starting the
     * given second from epoch.
     *
     * @param secondFromEpoch
     * @return
     */
    private DataRecord getDataRecordAtSecond(int secondFromEpoch) {

        try {
            DataHeader header = new DataHeader(0, 'D', false);
            header.setStartBtime(new Btime(secondFromEpoch));
            header.setSampleRate(numSamples);
            header.setNetworkCode("CM");
            header.setStationIdentifier("JULI");
            header.setChannelIdentifier("HHZ");
            header.setNetworkCode("00");
            header.setNumSamples((short) numSamples);

            DataRecord dataRecord = new DataRecord(header);

            Blockette1000 blockette = new Blockette1000();
            blockette.setEncodingFormat((byte) B1000Types.STEIM2);
            blockette.setWordOrder((byte) 1);
            blockette.setDataRecordLength((byte) 9);

            dataRecord.addBlockette(blockette);
            dataRecord.setData(Steim2.encode(getSamples(numSamples), 7).getEncodedData());

            return dataRecord;

        } catch (Exception ex) {
            System.out.println("Failed to make DataRecord");
            System.out.println(ex.getMessage());
        }

        return null;
    }

    /**
     *
     * @return The poison for the decompressor (needed to indicate that no more
     * packets are expected).
     */
    private DataRecord getDecompressorPoison() {
        DataHeader header = new DataHeader(-1, 'D', false);
        return new DataRecord(header);
    }

    /**
     * Return a array of samples resembling a sine wave.
     *
     * @param numSamples
     * @return
     */
    private int[] getSamples(int numSamples) {

        int[] samples = new int[numSamples];

        double step = 2 * Math.PI / numSamples;

        for (int i = 0; i < numSamples; i++) {
            samples[i] = (int) (Math.sin(i * step) * 1000);
        }

        return samples;
    }

}
