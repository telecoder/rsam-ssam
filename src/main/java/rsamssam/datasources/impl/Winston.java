package rsamssam.datasources.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rsamssam.config.Config;
import rsamssam.datasources.DataClient;
import rsamssam.datasources.SamplesProvider;
import rsamssam.query.Metadata;
import rsamssam.query.Query;

/**
 *
 * @author Julian Peña.
 */
public class Winston implements DataClient, SamplesProvider {

    /**
     * Vertx instance reference.
     */
    private final Vertx vertx;

    /**
     * TCP Client.
     */
    private NetClient client;

    /**
     * Queue for downloaded samples.
     */
    private final LinkedBlockingQueue<Double> queue;

    /**
     * req-id Winston response.
     */
    private String requestId;

    /**
     * pin for Winston response.
     */
    private String pin;

    /**
     * Station ID in Winston response.
     */
    private String S;

    /**
     * Channel ID in Winston response.
     */
    private String C;

    /**
     * Network ID in Winston response.
     */
    private String N;

    /**
     * Location ID in Winston response.
     */
    private String L;

    /**
     * data-type for Winston response.
     */
    private String dataType;

    /**
     * starttime of Winston response.
     */
    private String startTime;

    /**
     * sampling-rate for Winston response. -1 means this is not set yet.
     */
    private int sps = -1;

    /**
     * Requested start time (could be different from what winsons sends).
     */
    private long from;

    /**
     * Are we padding?.
     */
    private boolean padding = false;

    /**
     * How many padding samples are remaining.
     */
    private int gap;

    /**
     * Our logger.
     */
    private final Logger LOG = LoggerFactory.getLogger("Winston");

    public Winston(Vertx vertx) {
        this.vertx = vertx;
        this.queue = new LinkedBlockingQueue<>();
    }

    @Override
    public Future<Metadata> download(Query query) {

        this.from = query.getFrom();

        Promise<Metadata> promise = Promise.promise();

        String winston = Config.getWinstonServer();
        int port = Config.getWinstonServerPort();

        int timeout = (int) (getTimeout(query.getFrom(), query.getTo()));

        LOG.info("we will give {}s to Winston to start sending data", timeout);

        connect(winston, port, timeout)
                .compose(socket -> doRequest(query, socket))
                .onSuccess(metadata -> promise.complete(metadata))
                .onFailure(f -> {
                    LOG.error("Failed to query winston");
                    LOG.error(f.getMessage());
                    promise.fail("Failed to query winston");
                });

        return promise.future();
    }

    /**
     * Creates a TCP connection to the Winston server.
     *
     * @return A future object that will contain the NetSocket object for the
     * connection.
     */
    private Future<NetSocket> connect(String server, int port, int timeout) {

        Promise<NetSocket> promise = Promise.promise();

        NetClientOptions options = new NetClientOptions();
        options
                .setConnectTimeout(2000)
                .setIdleTimeout(timeout);

        client = vertx.createNetClient(options);
        client
                .connect(port, server)
                .onSuccess(socket -> promise.complete(socket))
                .onFailure(f -> {
                    LOG.error("Failed to connect to Winston");
                    LOG.error(f.getMessage());
                    promise.fail("Failed to connect to winston server");
                });

        return promise.future();
    }

    /**
     * Returns a calculated timeout value for the Winston TCP connection. This
     * is necessary due the fact that Winston response delay is somehow
     * proportional to the time span of the query.
     *
     * @param from
     * @param to
     * @return
     */
    private long getTimeout(long from, long to) {

        long days = (to - from) / 86400000;

        if (days <= 1) {
            return 10;
        }

        return days * 5;
    }

    /**
     * Creates a Winston request.
     *
     * @param query The query object
     * @return A vertx Buffer with the Winston request.
     */
    private Buffer getRequest(String N, String S, String C, String L,
            long from, long to) {

        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
        DecimalFormat decimalFormat = (DecimalFormat) numberFormat;
        decimalFormat.applyPattern("#0.000000");

        // not sure if we have to craft a request id, since we are using this
        // connection only for this query.
        String command;
        if (L != null && L.trim().length() > 0) {
            command = "GETSCNL: 1 ";
        } else {
            command = "GETSCN: 1 ";
        }

        Buffer request = Buffer.buffer().appendString(command)
                .appendString(S).appendString(" ")
                .appendString(C).appendString(" ")
                .appendString(N).appendString(" ");

        if (L != null && L.trim().length() > 0) {
            request.appendString(L).appendString(" ");
        }

        request
                .appendString(decimalFormat.format((from - 1000) / 1000))
                .appendString(" ")
                .appendString(decimalFormat.format(to / 1000))
                .appendString(" 0\n");          // 0 is the fill value for gaps

        return request;
    }

    /**
     * Sends a request to the Winston server for the given query.
     *
     * @param query A query object.
     * @param socket A Winston TCP Connection NetSocket.
     * @return A future object that will contain an ArrayList with the raw
     * response sent by the Winston Server. This raw response contains some
     * metadata in the first part.
     */
    private Future<Metadata> doRequest(Query query, NetSocket socket) {

        Promise<Metadata> promise = Promise.promise();

        RecordParser parser = RecordParser.newDelimited(" ", sample -> {

            if (sps == -1) {

                if (requestId == null) {
                    requestId = sample.toString();
                    return;
                } else if (pin == null) {
                    pin = sample.toString();
                    return;
                } else if (S == null) {
                    S = sample.toString();
                    return;
                } else if (C == null) {
                    C = sample.toString();
                    return;
                } else if (N == null) {
                    N = sample.toString();
                    return;
                } else if (L == null) {
                    L = sample.toString();
                    return;
                } else if (dataType == null) {
                    dataType = sample.toString();
                    return;
                } else if (startTime == null) {
                    startTime = sample.toString();
                    return;
                }

                LOG.info("Metadata {} {} {}{}{}{} {} {}",
                        requestId, pin, S, C, N, L, dataType, startTime);

                try {
                    sps = (int) Float.parseFloat(sample.toString());
                } catch (NumberFormatException ex) {
                    LOG.error("sps is not a number: {}", sample.toString());
                    LOG.error("Aborting download");
                    promise.fail("Invalid sps value " + sample.toString());
                    socket.close();
                    return;
                }

                // Winston start-time comes in seconds with an arbitrary 
                // fractions of a second. We only support timestamps in millis.
                double startTimeMillis = Double.parseDouble(startTime) * 1000;

                if (withinTolerance(startTime)) {
                    // we can use the original 'from' value
                    promise.complete(new Metadata(sps, from));
                    return;
                }

                promise.complete(new Metadata(sps, (long) startTimeMillis));

                LOG.info("Start time is not within tolerance");

                padding = true;
                gap = countSamples(from, (long) startTimeMillis, sps);

                return;
            }

            queue.add(Double.valueOf(sample.toString()));
        });

        socket
                .exceptionHandler(t -> {
                    LOG.error("Unexpected exception");
                    LOG.error(t.getMessage());
                })
                .handler(buffer -> {
                    if (buffer.getByte(buffer.length() - 1) != '\n') {
                        parser.handle(buffer);
                    } else {
                        socket.close();
                    }
                })
                .endHandler(e -> {

                    queue.add(POISON);

                    // it looks like sometimes Winston don't send any data
                    // we will try to fail the promise, if the promise was
                    // alreadycompleted then nothing will happen
                    promise.tryFail("Winston didn't send any samples");

                    LOG.info("{} Download completed", query.getId());
                });

        Buffer request = getRequest(query.getN(), query.getS(), query.getC(),
                query.getL(), query.getFrom(), query.getTo());
        LOG.info("{} Request is {}", query.getId(), request.toString().trim());
        socket.write(request);

        return promise.future();
    }

    @Override
    public double getSample() {

        if (padding) {
            if (gap < 0) {
                LOG.info("Will discard {} samples", gap * -1);
                while (gap < 0) {
                    try {
                        queue.take();
                        gap++;
                    } catch (InterruptedException ex) {
                        LOG.error("Winston thread interrupted, aborting");
                        LOG.error(ex.getMessage());
                        return POISON;
                    }
                }
                padding = false;
            } else {
                gap--;
                if (gap < 1) {
                    padding = false;
                }
                return 0;
            }
        }

        try {
            return queue.take();
        } catch (InterruptedException ex) {
            LOG.error("Winston thread interrupted, aborting download");
            LOG.error(ex.getMessage());
            return POISON;
        }
    }

    /**
     * Given two times and a sample rate, calculates how many samples are there.
     *
     * @param from (inclusive)
     * @param until (exclusive)
     * @param sps Samples Per Second
     * @return The number of samples in the given interval.
     */
    private int countSamples(long from, long to, float sps) {

        Duration duration = Duration.of(to - from, ChronoUnit.MILLIS);

        double samplesNeeded = duration.toMillis() / (1 / sps * 1000d);

        if (samplesNeeded < 0) {
            samplesNeeded = Math.ceil(samplesNeeded);
        } else if (samplesNeeded > 0) {
            samplesNeeded = Math.floor(samplesNeeded);
        }

        if (samplesNeeded > Integer.MAX_VALUE) {
            LOG.error("The padding needed is too large!");
            // TODO somebody please do something!
        }

        return (int) samplesNeeded;
    }

    /**
     * Returns wether or not the real start time is within tolerance when
     * compared to the requested start time.
     *
     * @param realStartTime
     * @return
     */
    private boolean withinTolerance(String realStartTime) {
        Double time = Double.parseDouble(realStartTime) * 1000;
        float toleranceInMillis = 1000 / sps;
        double difference = Math.abs(from - time);
        LOG.info("Difference is {} milliseconds", difference);
        return (difference) < toleranceInMillis;
    }

}
