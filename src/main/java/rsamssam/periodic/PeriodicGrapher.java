package rsamssam.periodic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rsamssam.config.Names;
import rsamssam.core.RsamSsam;
import rsamssam.query.Query;
import rsamssam.query.QueryDeserializer;

/**
 * Generates rsam-ssam graphs for all queries in the queries.json file.
 *
 * @author Julian Peña.
 */

public class PeriodicGrapher {

    /**
     * Vertx reference.
     */
    private final Vertx vertx;

    /**
     * Vertx filesystem helper.
     */
    private final FileSystem fileSystem;

    /**
     * Json ser/deserializer.
     */
    private final Gson gson;

    /**
     * Our logger.
     */
    private final Logger LOG = LoggerFactory.getLogger("Periodic");

    /**
     * Initializes this periodic grapher instance. A vertx instance is needed
     * since the vertx api is used for reading the queries.json file and
     * for it's promises/futures support, this may be replaced in the future.
     *
     * @param vertx The vertx instance.
     */
    public PeriodicGrapher(Vertx vertx) {

        this.vertx = vertx;
        fileSystem = vertx.fileSystem();

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Query.class, new QueryDeserializer());
        gson = gsonBuilder.create();
    }

    /**
     * Starts the creation of rsam-ssam graphs for all the queries present in
     * the queries.json file.
     * <p>
     * Graphs are produced sequentially.
     */
    public void makeGraphs() {

        // if the time is 00:00 AM then this would be the last graph for the day 
        // (yesterday), so we apply some delay in order to give some time to 
        // the data source to get all packets for that day.
        LocalDateTime now = LocalDateTime
                .now(ZoneId.of(ZoneOffset.UTC.getId()))
                .truncatedTo(ChronoUnit.MINUTES);

        // delay is 30 seconds (30000 milliseconds)
        int delay = (now.getHour() == 0 && now.getMinute() == 0) ? 30000 : 0;
        if (delay > 0) {
            LOG.info("It is midnight, sleeping for 30 seconds");
        }

        LocalDateTime yesterday = now.minus(1, ChronoUnit.DAYS);

        getQueries()
                .compose(queries -> {

                    if (queries.length < 1) {
                        return Future.succeededFuture();
                    }

                    Promise chainPromise = Promise.promise();
                    Future chainFuture = chainPromise.future();

                    long from = yesterday.toInstant(ZoneOffset.UTC).toEpochMilli();
                    long to = now.toInstant(ZoneOffset.UTC).toEpochMilli();
                    
                    for (Query query : queries) {

                        if (delay > 0) {
                            query.setFrom(from).setTo(to);
                        }

                        RsamSsam rsamSsam = new RsamSsam(vertx, query);

                        // in either case we try to continue with the next query
                        chainFuture = chainFuture
                                .compose(onSuccess -> rsamSsam.makeGraph(),
                                        onFailure -> rsamSsam.makeGraph());
                    }

                    // this wil trigger the execution chain
                    if (delay == 0) {
                        chainPromise.complete();
                    } else {
                        vertx.setTimer(delay, t -> chainPromise.complete());
                    }

                    return chainFuture;
                })
                .onFailure(f -> LOG.error("Failed to create some/all graphs"));
    }

    /**
     * Parses the queries.json file and returns and array of queries.
     *
     * @return A future that will eventually contain an array of Query objects.
     */
    private Future<Query[]> getQueries() {

        Promise<Query[]> promise = Promise.promise();

        fileSystem
                .readFile(Names.QUERIES_FILE)
                .onSuccess(buffer -> {

                    String json = buffer.toString();

                    try {
                        promise.complete(gson.fromJson(json, Query[].class));
                    } catch (JsonSyntaxException ex) {
                        LOG.error("Syntax errors in queries.json file?");
                        LOG.error(ex.getMessage());
                        promise.fail("Failed to parse queries file");
                    }
                })
                .onFailure(f -> {
                    LOG.warn("Is the queries.json file present?");
                    LOG.error(f.getMessage());
                    promise.fail("Failed read queries file");
                });

        return promise.future();
    }

}
