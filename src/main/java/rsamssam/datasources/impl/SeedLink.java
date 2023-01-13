package rsamssam.datasources.impl;

import edu.sc.seis.seisFile.mseed.DataRecord;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rsamssam.config.Config;
import rsamssam.datasources.DataClient;
import rsamssam.datasources.DataRecordProvider;
import rsamssam.datasources.impl.seedlink.SeedLinkClient;
import rsamssam.query.Metadata;
import rsamssam.query.Query;

/**
 *
 * @author Julian Peña.
 */
public class SeedLink implements DataClient, DataRecordProvider {

    /**
     * Vertx reference.
     */
    private final Vertx vertx;

    /**
     * Our actual SeedLink client.
     */
    private final SeedLinkClient seedLinkClient;

    /**
     * Queue for downloaded data records.
     */
    private final LinkedBlockingQueue<DataRecord> queue;

    /**
     * Have we receive at least one DataRecord from which to extract metadata?.
     */
    private boolean gotResults = false;

    /**
     * Internal timer to keep track of timeouts.
     */
    private Long timer = null;

    /**
     * Our logger.
     */
    private final Logger LOG = LoggerFactory.getLogger("SeedLink");

    public SeedLink(Vertx vertx) {

        this.vertx = vertx;

        seedLinkClient = new SeedLinkClient(vertx,
                Config.getSeedLinkServer(),
                Config.getSeedLinkServerPort());

        queue = new LinkedBlockingQueue();
    }

    @Override
    public Future<Metadata> download(Query query) {

        Promise<Metadata> promise = Promise.promise();

        seedLinkClient
                .connect()
                .compose(c -> seedLinkClient.doHello())
                .compose(c -> seedLinkClient.doStation(query.getS() + " " + query.getN()))
                .compose(c -> seedLinkClient.doSelect(query.getL() + query.getC() + ".D"))
                .compose(c -> {
                    if (fetchOrTime(query)) {
                        return seedLinkClient.doFetch(0, query.getFrom());
                    }
                    return seedLinkClient.doTime(query.getFrom(), query.getTo());

                })
                .compose(c -> seedLinkClient.doEnd())
                .compose(c -> {
                    return seedLinkClient
                            .getDatarecords(5, datarecord -> {
                                if (!gotResults) {
                                    gotResults = true;
                                    promise.complete(getMetadata(datarecord));
                                }
                                add(datarecord);
                            });
                })
                .onSuccess(s -> LOG.info("{} Download completed", query.getId()))
                .onComplete(c -> {
                    // it could happen that a download get aborted mid time
                    if (gotResults) {
                        queue.add(POISON);
                    } else {
                        LOG.info("Failed to download data over seedlink");
                        promise.tryFail("Failed to connect to server");
                    }
                });

        return promise.future();
    }

    @Override
    public DataRecord getDataRecord() {
        try {
            return queue.take();
        } catch (InterruptedException ex) {
            LOG.error("SeedLink thread was interrupted, aborting download");
            LOG.error(ex.getMessage());
            return POISON;
        }
    }

    /**
     * Determines what SeedLink command must be used for the given Query. If the
     * end time of a Query is in the future, then FETCH must be used (it
     * instructs the server to close the session once it has sent the last
     * packet available) , otherwise TIME must be used.
     *
     * @param query
     * @return True means FETCH, false means TIME.
     */
    private boolean fetchOrTime(Query query) {
        return Instant.now().isBefore(Instant.ofEpochMilli(query.getTo()));

    }

    /**
     * Adds a DataRecord to the internal queue, signaling the underlying
     * SeedLinkClient to pause when the queues reach a threshold.
     *
     * @param dataRecord
     */
    private void add(DataRecord dataRecord) {

        queue.add(dataRecord);

        // TODO make this configurable?
        if (queue.size() > 40000) {
            seedLinkClient.pause();
            watchdog();
        }
    }

    /**
     * Checks the size of the internal queue and signals the underlying
     * SeedLinkClient to resume the download if needed. If at the time of
     * checking the size is too big, then this method re-schedules itself to run
     * a second later.
     */
    private void watchdog() {

        // we already have a timer set
        if (timer != null) {
            return;
        }

        timer = vertx.setTimer(1000, check -> {
            timer = null;
            if (queue.size() < 10000) {
                seedLinkClient.resume();
            } else {
                watchdog();
            }
        });
    }

}
