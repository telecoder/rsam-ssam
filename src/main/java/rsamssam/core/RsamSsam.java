package rsamssam.core;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rsamssam.datasources.DataClient;
import rsamssam.datasources.DataRecordProvider;
import rsamssam.datasources.SamplesProvider;
import rsamssam.datasources.impl.Dataselect;
import rsamssam.datasources.impl.SeedLink;
import rsamssam.datasources.impl.Winston;
import rsamssam.config.Config;
import rsamssam.query.Query;

/**
 * This class encapsulates the whole rsam-ssam functionality. Given a query
 * object it will produce a rsam-ssam graph.
 * <p>
 * An instance of this class must be created for every new query/graph.
 * <p>
 * Multiple threads are used along the process of generating a graph (data
 * downloading, processing, averaging, etc.).
 *
 * @author Julian Peña.
 */
public class RsamSsam {

    /**
     * Our query.
     */
    private final Query query;

    /**
     * Our fdsn client.
     */
    private final DataClient dataClient;

    /**
     * Samples packager.
     */
    private Packager packager;

    /**
     * Does the heavy lifting stuff (rsam and ssam calculations).
     */
    private Processor processor;

    /**
     * Decompress DataRecord objects and extract the raw samples.
     */
    private Decompressor decompressor;

    /**
     * Average the results in case of a result set that is too big.
     */
    private Averager averager;

    /**
     * The promise that backups the whole process and signals the caller about
     * the result.
     */
    private final Promise<Boolean> promise;

    /**
     * Our logger.
     */
    private final Logger LOG = LoggerFactory.getLogger("RsamSsam");

    /**
     * Executor service for running task on threads from a thread pool.
     */
    private static final ExecutorService executor;

    static {
        executor = Executors.newFixedThreadPool(Config.getThreadPoolSize());
    }

    /**
     * Creates a new RsamSsam instance. Note that nothing will be done until the
     * makeGraph method is called.
     *
     * @param vertx A vertx instance (needed by the dataselect client).
     * @param query The query object with valid SCNL and time ranges.
     */
    public RsamSsam(Vertx vertx, Query query) {

        // beware this object will be mutated along the processing chain, since
        // there are query fields unknown at creation time (effective time 
        // range of the data, the sampling rate, etc.) 
        this.query = query;

        // because reasons ... the minimum time range is 1 day
        if (query.getTo() <= query.getFrom()) {
            query.setTo(Instant
                    .ofEpochMilli(query.getFrom())
                    .plus(1, ChronoUnit.DAYS)
                    .toEpochMilli());
        }

        switch (query.getType()) {
            case fdsn ->
                dataClient = new Dataselect(vertx);
            case seedlink ->
                dataClient = new SeedLink(vertx);
            case winston ->
                dataClient = new Winston(vertx);
            default ->
                throw new IllegalArgumentException("Query type invalid");
        }

        promise = Promise.promise();
    }

    /**
     * Tries to make an rsam-ssam graph for the query object. This method is
     * asynchronous and will return immediately, the caller must watch the
     * Future object for the result.
     *
     * @return A Future object that will eventually succeed if the graph was
     * created, it will fail otherwise.
     */
    public Future<Boolean> makeGraph() {

        dataClient
                .download(query)
                .onSuccess(metadata -> {
                    // once the query has metadata, we can skip checking for
                    // empty optionals later
                    LOG.info("{} {}", query.getType(), metadata);
                    query.setMetadata(metadata);
                    if (query.needsDecompression()) {
                        decompressDataRecords();
                    } else {
                        packageBins();
                    }
                })
                .onFailure(f -> promise.fail("No samples were downloaded"));

        return promise.future();
    }

    /**
     * Starts the extraction of raw samples from the DataRecord objects being
     * downloaded. The data extraction goes along the download since these
     * operations run on different threads.
     */
    private void decompressDataRecords() {

        LOG.info("Decompressing");

        executor.submit(() -> {

            decompressor = new Decompressor(query);

            var dataRecord = ((DataRecordProvider) dataClient).getDataRecord();

            if (dataRecord.getHeader().getSequenceNum() == -1) {
                // no point in going further, there is no data
                LOG.info("{} No data for query", query.getId());
                promise.fail("Server returned no data");
                return;
            }

            // we start packaging samples as soon as we get the first datarecord
            packageBins();

            while (dataRecord.getHeader().getSequenceNum() != -1) {

                decompressor.addDataRecord(dataRecord);

                if (!decompressor.isHungry()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                    }
                }
                dataRecord = ((DataRecordProvider) dataClient).getDataRecord();
            }

            // we add the poison pill again as a signal to the decompressor
            decompressor.addDataRecord(dataRecord);
        });
    }

    /**
     * Starts the packaging of raw samples in arrays which size is determined by
     * the query's window size. The resulting arrays (bins) are suitable for
     * performing the FFT on them.
     * <p>
     * The process of packaging is run in an dedicated thread that can run in
     * parallel to the data download, decompressing and processing threads.
     * <p>
     * If this operation's thread is interrupted, then the promise is failed and
     * no further processing is made.
     */
    private void packageBins() {

        LOG.info("Packaging");

        executor.submit(() -> {

            packager = new Packager(query.getWindowSize());

            // at this point we can create our processor since some datarecords 
            // were already received, thus we have now the sps for the query.
            processor = new Processor(query.getWindowSize(), query.getWindow(),
                    query.getCutoffWindowSize().get(),
                    query.getResponseFactor());

            try {

                SamplesProvider samplesProvider;
                if (query.needsDecompression()) {
                    samplesProvider = decompressor;
                } else {
                    samplesProvider = (SamplesProvider) dataClient;
                }

                double sample = samplesProvider.getSample();

                // we start the processing once we begin receving samples
                processBins();

                while (!Double.isNaN(sample)) {
                    packager.addSample(sample);
                    sample = samplesProvider.getSample();
                }

                // we add the poison pill again as a signal to the packager
                packager.addSample(Double.NaN);

                LOG.info("All samples have been packaged. Poison returned.");

            } catch (InterruptedException ex) {
                LOG.error("Packager thread interrupted {}", query.getId());
                LOG.error(ex.getMessage());
                promise.fail("Failed to process query");
            }

        }, "ssam-packager-" + query.getId());
    }

    /**
     * Starts the processing of bins on a dedicated thread that can run in
     * parallel to the data download, decompressing, and packaging threads.
     * <p>
     * The processing of bins includes averaging the results if the time range
     * is bigger than 24 hours.
     */
    private void processBins() {

        LOG.info("Processing");

        executor.submit(() -> {

            double[] bin = packager.getBin();   // packaged bin

            averager = new Averager(query, query.getMetadata().get().sps());

            while (bin.length > 0) {
                averager.addResult(processor.process(bin));
                bin = packager.getBin();
            }

            averager.complete();

            LOG.info("{} Done processing and averaging", query.getId());

            // once all the data has been processed and averaged, we can start
            // formatting the results
            formatResults();

        }, "ssam-processor&averager-" + query.getId());
    }

    /**
     * Formats the results in order to write them to disk. This operation is run
     * on a dedicated thread.
     * <p>
     * If this operation's thread is interrupted, the promise is failed and no
     * further processing is made.
     */
    private void formatResults() {

        LOG.info("{} Formatting results", query.getId());

        executor.submit(() -> {

            if (!averager.hasResults()) {
                promise.fail("The query has no data to be processed");
                return;
            }

            var formatter = new Formatter(query.getMetadata().get().start(),
                    query.getTimestep().get());

            Result result;
            while (averager.hasResults()) {
                try {
                    result = averager.getResult();
                    formatter.addResult(result);
                } catch (InterruptedException ex) {
                    LOG.error("{}", query.getId(), ex.getMessage());
                    promise.fail("Failed to process query");
                    return;
                }
            }

            formatter.addAverageSsam(averager.getAverageSsam());

            // once the results have been formatted, we can write them to disk
            writeResults(formatter);

        }, "ssam-formatter-" + query.getId());
    }

    /**
     * Writes the formatted results to disk. This operation is run on a
     * dedicated thread.
     * <p>
     * If the operation fails then the promise is failed and no attempt is made
     * to make a graph.
     */
    private void writeResults(Formatter formatter) {

        LOG.info("{} Writing computation results to disk", query.getId());

        FileWriter fileWriter = new FileWriter();

        executor.submit(() -> {

            if (!fileWriter.makeOuputFolder(query)) {
                LOG.error("{} Failed to make output folder", query.getId());
                promise.fail("Failed to make output folder for query results");
                return;
            }

            String basePath = query.getOutputPath();

            String rsam = basePath + "/" + query.getRSAMFileName();
            String ssam = basePath + "/" + query.getSSAMFileName();
            String avgSsam = basePath + "/" + query.getAverageSSAMFileName();
            String maxFreqs = basePath + "/" + query.getMaxFreqsFileName();

            if (fileWriter.write(rsam, formatter.getRsam())
                    && fileWriter.write(ssam, formatter.getSsam())
                    && fileWriter.write(avgSsam, formatter.getAverageSsam())
                    && fileWriter.write(maxFreqs, formatter.getMaxFreqs())) {
                plot();
            } else {
                LOG.error("{} Failed to write files", query.getId());
                promise.fail("Failed to write query results to disk");
            }

        }, "ssam-writer-" + query.getId());
    }

    /**
     * Calls the gnuplot wrapper. If the graph is successfully created then the
     * promise is completed.
     * <p>
     * Once this method is completed this RsamSsam instance has fulfilled it's
     * purpose and can be discarded.
     */
    private void plot() {

        executor.submit(() -> {

            Plotter plotter = new Plotter(query);

            if (plotter.plot(query)) {
                promise.complete(true);
            } else {
                promise.fail("Failed to make graph");
            }
        }, "ssam-plotter-" + query.getId());
    }

}
