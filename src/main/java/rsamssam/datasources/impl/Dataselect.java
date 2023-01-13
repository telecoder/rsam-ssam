package rsamssam.datasources.impl;

import edu.sc.seis.seisFile.mseed.DataRecord;
import edu.sc.seis.seisFile.mseed.SeedFormatException;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.parsetools.RecordParser;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rsamssam.config.Config;
import rsamssam.datasources.DataClient;
import rsamssam.datasources.DataRecordProvider;
import rsamssam.query.Metadata;
import rsamssam.query.Query;

/**
 * FDSN Dataselect client. Instances of this class should be used for a single
 * query.
 *
 * @author Julian Peña.
 */
public class Dataselect implements DataClient, DataRecordProvider {

    /**
     * FDSN server IP address or fqdn.
     */
    private final String SERVER;

    /**
     * FDSN server port.
     */
    private final int PORT;

    /**
     * FDSN Dataselect service URL.
     */
    private final String DATASELECT_URL;

    /**
     * Vertx reference.
     */
    private final Vertx vertx;

    /**
     * Parser that translates from vertx buffers to DataRecord objects.
     */
    private RecordParser parser;

    /**
     * Query object.
     */
    private Query query;

    /**
     * Margin time given at the beginning and end of the query. Default is 10s.
     */
    private static final long MARGIN_TIME = 10 * 1000;

    /**
     * Queue for downloaded data records.
     */
    private final LinkedBlockingQueue<DataRecord> queue;

    /**
     * Simple flag that indicates if at least one packet was received.
     */
    private boolean gotData = false;

    /**
     * Our logger.
     */
    private final Logger LOG = LoggerFactory.getLogger("Dataselect");

    /**
     * Initializes this client. Since the HTTP client under the hood is the
     * vertx http client, a vertx instance must be passed as parameter.
     *
     * @param vertx A vertx instance. Ideally, this instance should be global to
     * the application.
     */
    public Dataselect(Vertx vertx) {

        this.vertx = vertx;

        queue = new LinkedBlockingQueue();

        SERVER = Config.getFdsnServer();
        PORT = Config.getFdsnServerPort();
        DATASELECT_URL = Config.getFdsnDataselectURL();

        if (SERVER == null || DATASELECT_URL == null) {
            LOG.warn("Bad FDSN configuration, all FDSN queries will fail!");
        }
    }

    /**
     * Starts the download.
     *
     * @param query
     * @return A Future that will eventually succeed if, and only if, the
     * download started (not finished). The download will be performed
     * asynchronously.
     */
    @Override
    public Future<Metadata> download(Query query) {

        if (SERVER == null || SERVER.isBlank()) {
            return Future.failedFuture("No FDSN server configured");
        }

        if (DATASELECT_URL == null || DATASELECT_URL.isBlank()) {
            return Future.failedFuture("No FDSN Dataselect prefix configured");
        }

        if (!query.isValid()) {
            LOG.info("{} Query is not valid: {}", query.getId(), query);
            return Future.failedFuture("Query is invalid");
        }

        this.query = query;

        Optional<String> optionalURL = getRequestURL();
        if (optionalURL.isEmpty()) {
            return Future.failedFuture("Failed to query FDSN server");
        }

        Promise<Metadata> promise = Promise.promise();

        String url = optionalURL.get();
        getRequest(url)
                .compose(this::doRequest)
                .compose(this::onResponse)
                .compose(this::receiveData)
                .onSuccess(metadata -> promise.complete(metadata))
                .onFailure(f -> {
                    LOG.info("{} Download failed", query.getId());
                    promise.fail("Failed to make query");
                });

        return promise.future();
    }

    /**
     * Assembles the FDSN Dataselect query URL.
     *
     * @return A optional that can contain a Dataselect query if all parameters
     * are valid.
     */
    private Optional<String> getRequestURL() {

        if (SERVER == null) {
            LOG.error("No FDSN server has been configured");
            return Optional.empty();
        }

        if (DATASELECT_URL == null) {
            LOG.error("A Dataselect URL has not been configured");
            return Optional.empty();
        }

        StringBuilder stringBuilder = new StringBuilder(DATASELECT_URL);
        stringBuilder
                .append("?net=").append(query.getN())
                .append("&sta=").append(query.getS())
                .append("&cha=").append(query.getC());

        if (query.getL() != null && query.getL().length() > 0) {
            stringBuilder.append("&loc=").append(query.getL());
        } else {
            stringBuilder.append("&loc=--");
        }

        stringBuilder
                .append("&start=")
                .append(formatTime(query.getFrom() - MARGIN_TIME))
                .append("&end=")
                .append(formatTime(query.getTo() + MARGIN_TIME));

        return Optional.of(stringBuilder.toString());
    }

    /**
     * Given an epoch value in milliseconds, return a valid time value for the
     * Dataselect query.
     *
     * @param time
     * @return
     */
    private String formatTime(long epochmillis) {
        return Instant
                .ofEpochMilli(epochmillis)
                .truncatedTo(ChronoUnit.SECONDS)
                .toString().replace("Z", "");
    }

    /**
     * Creates an HTTP request for the FDSN server.
     *
     * @param URL
     * @return A future that will eventually contain the HTTP request object.
     */
    private Future<HttpClientRequest> getRequest(String URL) {

        Promise<HttpClientRequest> promise = Promise.promise();

        HttpClientOptions options = new HttpClientOptions();
        options
                .setConnectTimeout(2000) // unit is milliseconds
                .setIdleTimeout(5);      // unit is seconds

        HttpClient httpClient = vertx.createHttpClient(options);
        httpClient
                .request(HttpMethod.GET, PORT, SERVER, URL)
                .onSuccess(request -> promise.complete(request))
                .onFailure(f -> {
                    LOG.error("{} Failed to make http request", query.getId());
                    LOG.error(f.getMessage());
                    promise.fail("Failed to make http request");
                });

        return promise.future();
    }

    /**
     * Sends the given HTTP request.
     *
     * @param request
     * @return A future that will eventually contain a HTTP response.
     */
    private Future<HttpClientResponse> doRequest(HttpClientRequest request) {
        LOG.info("{} Sending request {}", query.getId(), request.absoluteURI());
        return request.send();
    }

    /**
     * Once the HTTP request has been made and there is a response available
     * this method is called automatically. If the response code is 200, then
     * the data download begins, otherwise the promise is marked as failed and
     * no further processing is made.
     *
     * @param response a vertx HTTP response object.
     * @return A future that will succeed once the response has been fully
     * processed.
     */
    private Future<HttpClientResponse> onResponse(HttpClientResponse response) {

        Promise promise = Promise.promise();

        int statusCode = response.statusCode();

        switch (statusCode) {

            case 200:
                receiveData(response).onSuccess(s -> promise.complete());
                promise.complete(response);
                break;

            case 204:
            case 404:
                LOG.info("{} No data received", query.getId());
                promise.fail("No data returned for this query");

            default:
                LOG.warn("The server responded with {}", statusCode);
                promise.fail("Invalid response " + statusCode);
        }

        return promise.future();
    }

    /**
     * Receives the incoming byte stream and parses DataRecord objects that are
     * put in the queue. If the queue grows too much (more than 30 MB of data)
     * then the download is paused until the queue is smaller than this
     * threshold. The download is paused and resumed automatically.
     * <p>
     * The queue with downloaded DataRecord objects is emptied by calling the
     * getDataRecord method.
     * <p>
     * IMPORTANT: Only 512 bytes miniseed packets are considered valid.
     *
     * @param response The vertx HTTP response object resulting from the
     * request.
     * @return A future that will eventually succeed once the download has
     * ended.
     */
    private Future<Metadata> receiveData(HttpClientResponse response) {

        Promise<Metadata> promise = Promise.promise();

        parser = RecordParser.newFixed(512, response);
        parser
                .handler(buffer -> {

                    DataRecord dataRecord;
                    try {
                        dataRecord = (DataRecord) DataRecord.read(buffer.getBytes());
                    } catch (IOException | SeedFormatException ex) {
                        LOG.error("{} Failed to parse results", query.getId());
                        LOG.error(ex.getMessage());
                        return;
                    }

                    if (!gotData) {
                        gotData = true;
                        promise.complete(getMetadata(dataRecord));
                        LOG.info("{} Downloading miniseed data", query.getId());
                    }

                    // is this packet actually ours?, fdsn could reply with
                    // multiple streams because of wildcards.
                    if (!dataRecord.getHeader().getLocationIdentifier().trim().equalsIgnoreCase(query.getL())) {
                        return;
                    }

                    queue.add(dataRecord);

                    // TODO make this configurable?
                    if (queue.size() > 40000) {
                        parser.pause();
                        watchdog();
                    }
                })
                .exceptionHandler(e -> {
                    // what's going on here?
                    LOG.info("{} Unexpected exception", query.getId());
                    LOG.info(e.getMessage());
                    LOG.info("Will try to continue ...");
                })
                .endHandler(e -> {
                    // poison pill injection
                    queue.add(POISON);
                    LOG.info("{} Download completed", query.getId());
                });

        return promise.future();
    }

    /**
     * Checks whether the data queue is empty enough for the download to be
     * resumed, if there is still too much data on the queue, this method
     * re-schedules itself to run in 1 second.
     */
    private void watchdog() {
        vertx.setTimer(100, check -> {
            if (queue.size() < 10000) {
                parser.resume();
            } else {
                watchdog();
            }
        });
    }

    /**
     * Returns the oldest downloaded Datarecord. This method can potentially
     * block for a long time, so care must be taken. Perhaps this method should
     * be called from a dedicated thread. Once there is no more data then a
     * poison pill is returned (DataRecord object with a sequence number set to
     * -1).
     *
     * Since this method is blocking, if there is an error that forces the
     * thread to stop, the poison pill will be returned immediately.
     *
     * @return
     */
    @Override
    public DataRecord getDataRecord() {
        try {
            return queue.take();
        } catch (InterruptedException ex) {
            LOG.error("{} Failed to get datarecord", query.getId());
            LOG.error(ex.getMessage());
            return POISON;
        }
    }

}
