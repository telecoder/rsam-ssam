package rsamssam.core;

import edu.iris.dmc.seedcodec.CodecException;

import edu.sc.seis.seisFile.mseed.Btime;
import edu.sc.seis.seisFile.mseed.DataRecord;
import edu.sc.seis.seisFile.mseed.SeedFormatException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rsamssam.datasources.SamplesProvider;
import rsamssam.query.Query;

/**
 * Decompressor objects receives DataRecord objects and decompress them to
 * obtain raw samples.
 * <p>
 * Decompressor automatically fills data gaps with zeros.
 * <p>
 * Internally, Decompressor uses a blocking queue for storing the raw samples,
 * which are stored in memory and should be extracted via the getSample method.
 * <p>
 * IMPORTANT: it is up to the caller to keep things in control, and not overflow
 * the memory by ingesting too much data into this Decompressor and not
 * extracting the raw samples accordingly.
 * <p>
 * A Decompressor object finish it's job once no more DataRecords are fed to it
 * and no more raw samples are available in it's internal queue, when this
 * happens a poison pill (Double.NaN) is returned.
 *
 * @author Julian Peña.
 */
public class Decompressor implements SamplesProvider {

    /**
     * The query.
     */
    private final Query query;

    /**
     * Query's original initial boundary.
     */
    private final Btime from;

    /**
     * Query's original final boundary.
     */
    private final Btime to;

    /**
     * Sampling rate of the results.
     */
    private float sps;

    /**
     * Calculated time tolerance for detecting gaps/overlaps.
     */
    private float tolerance;

    /**
     * Start time of the current DataRecord.
     */
    private Btime start;

    /**
     * Predicted start time for the expected consecutive DataRecord.
     */
    private Btime nextStart;

    /**
     * Are we currently filling a gap?.
     */
    private boolean padding = false;

    /**
     * How many padding samples are remaining.
     */
    private int gap;

    /**
     * Input queue for DataRecord objects.
     */
    private final LinkedBlockingDeque<DataRecord> input;

    /**
     * Output samples.
     */
    private final LinkedBlockingQueue<Double> output;

    /**
     * Our logger.
     */
    private final Logger LOG = LoggerFactory.getLogger("Decompressor");

    public Decompressor(Query query) {

        this.query = query;
        from = new Btime(Instant.ofEpochMilli(query.getFrom()));
        to = new Btime(Instant.ofEpochMilli(query.getTo()));

        input = new LinkedBlockingDeque<>();
        output = new LinkedBlockingQueue<>();
    }

    /**
     * Adds a Datarecord to the input queue to be decompressed later.
     *
     * @param dataRecord
     */
    public void addDataRecord(DataRecord dataRecord) {
        input.add(dataRecord);
    }

    /**
     * Returns the oldest sample in the output queue.
     * <p>
     * Data gaps are automatically filled with zeros.
     * <p>
     * Once all the raw samples have been retrieved, this method returns a
     * poison pill in the form of Double.NaN to signal the caller that there
     * aren't any more samples to be read.
     * <p>
     * IMPORTANT: This method is blocking and should be, ideally, called from a
     * dedicated thread.
     *
     * @return A raw sample
     */
    @Override
    public double getSample() {

        // are we currently padding?
        if (padding) {
            gap--;
            if (gap < 1) {
                padding = false;
            }
            return 0d;
        }

        try {

            // do we have samples ready?
            if (!output.isEmpty()) {
                return output.take();
            }

            DataRecord dataRecord = input.take();

            // poison pill check. This marks the end of the data
            if (dataRecord.getHeader().getSequenceNum() == -1) {
                return Double.NaN;
            }

            // skip packets older than the requested "from"
            while (dataRecord.getLastSampleBtime().before(from)) {
                dataRecord = input.take();
            }

            // stop once we start receiving packets younger than the requested
            // "to"
            if (dataRecord.getStartBtime().after(to)) {
                return Double.NaN;
            }

            if (start == null) {

                sps = dataRecord.getSampleRate();
                tolerance = 1 / sps;

                Btime packetStart = dataRecord.getStartBtime();

                // does this packet overlaps with the requested "from"?
                if (packetStart.before(from)
                        & dataRecord.getLastSampleBtime().after(from)) {

                    int overlap = countSamples(packetStart, from, sps);
                    addSamples(dataRecord);
                    for (int i = 0; i < overlap; i++) {
                        output.take();
                    }

                    start = from;
                    nextStart = dataRecord.getPredictedNextStartBtime();
                    return output.take();
                }

                // perhaps there is a gap at the beggining?
                if (packetStart.after(from)) {

                    start = from;
                    nextStart = packetStart;

                    // lets fill this gap
                    gap = countSamples(from, packetStart, sps);
                    gap--;
                    if (gap > 1) {
                        padding = true;
                        LOG.info("About to pad {} samples at {}", gap, start);
                    }
                    // since we are not processing this datarecord yet, put it
                    // back
                    input.addFirst(dataRecord);
                    return 0d;
                }

                start = from;
                nextStart = dataRecord.getPredictedNextStartBtime();

            } else if (nextStart.before(dataRecord.getStartBtime())) {

                // there is a gap, lets fill it with zeros
                gap = countSamples(nextStart, dataRecord.getStartBtime(), sps);
                gap--;
                if (gap > 1) {
                    padding = true;
                }

                // since we are not processing this packet yet, we put it back
                input.addFirst(dataRecord);
                nextStart = dataRecord.getStartBtime();

                return 0d;

            } else if (dataRecord.getStartBtime().before(nextStart)) {
                if (!withinTolerance(dataRecord.getStartBtime(), nextStart)) {
                    // probably a retransmission, discard this packets
                    while (dataRecord.getStartBtime().before(nextStart)) {
                        dataRecord = input.take();
                        if (dataRecord.getHeader().getSequenceNum() == -1) {
                            return Double.NaN;
                        }
                    }
                } else {
                    // packet start time is inaccurate but within tolerance
                }
            }

            // does this packet overlaps with the requested "to"?
            if (dataRecord.getStartBtime().before(to)
                    & dataRecord.getLastSampleBtime().after(to)) {
                int keep = countSamples(dataRecord.getStartBtime(), to, sps);
                double[] samples = decompress(dataRecord);
                for (int i = 0; i < keep; i++) {
                    output.add(samples[i]);
                }
                return output.take();
            }

            // datarecord looks good, decompress it
            addSamples(dataRecord);

            return output.take();

        } catch (InterruptedException ex) {
            LOG.error("{} Decompressor interrupted", query.getId());
            LOG.error(ex.getMessage());
            return Double.NaN;
        }

    }

    /**
     * Given two Btimes and a sample rate, calculates how many samples are
     * there.
     *
     * @param from (inclusive)
     * @param until (exclusive)
     * @param sps Samples Per Second
     * @return The number of samples in the given interval.
     */
    private int countSamples(Btime from, Btime until, float sps) {

        Duration d = Duration.between(from.toInstant(), until.toInstant());

        double samplesNeeded = d.toMillis() / (1 / sps * 1000d);

        // the number of samples needed must be a integer number
        if (samplesNeeded != Math.floor(samplesNeeded)) {
            samplesNeeded = Math.floor(samplesNeeded);
        }

        if (samplesNeeded > Integer.MAX_VALUE) {
            LOG.error("{} The padding needed is too large!", query.getId());
        }

        return (int) samplesNeeded;
    }

    /**
     * Given a DataRecord object, extracts it's raw samples and adds them to the
     * output queue. Incoming DataRecord objects must chronologically ordered.
     *
     * @param dataRecord
     */
    private void addSamples(DataRecord dataRecord) {
        nextStart = dataRecord.getPredictedNextStartBtime();
        for (Double sample : decompress(dataRecord)) {
            output.add(sample);
        }
    }

    /**
     * Decompress the given DataRecord.
     *
     * @param dataRecord
     * @return An array of doubles containing the raw samples in the DataRecord
     * or zeros if the DataRecord couldn't be decompressed.
     */
    private double[] decompress(DataRecord dataRecord) {
        try {
            return dataRecord.decompress().getAsDouble();
        } catch (SeedFormatException | CodecException ex) {
            LOG.error("{} Decompression failure", query.getId());
            LOG.error(ex.getMessage());
            return new double[dataRecord.getHeader().getNumSamples()];
        }
    }

    /**
     * Given the end time of a DataRecord object and a start time of a later
     * object, return true if the data records can be considered continuous and
     * non overlapping, false otherwise.
     *
     *
     * @param endTime
     * @param startTime
     * @return
     */
    private boolean withinTolerance(Btime endTime, Btime startTime) {
        long f = endTime.toInstant().toEpochMilli();
        long t = startTime.toInstant().toEpochMilli();
        return (t - f) < (tolerance * 1000);
    }

    /**
     * Are we too busy decompressing already downloaded DataRecords or are we
     * ready to accept more?.
     *
     * @return True if can accept more DataRecords, false otherwise.
     */
    public boolean isHungry() {
        return input.size() < 20000;
    }

}
