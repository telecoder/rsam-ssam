package rsamssam.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.FaviconHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.templ.jade.JadeTemplateEngine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.stream.Collectors;

import rsamssam.config.Config;
import static rsamssam.config.Names.*;
import rsamssam.core.RsamSsam;
import rsamssam.history.GraphsHistory;
import rsamssam.query.Query;
import rsamssam.query.QueryDeserializer;
import rsamssam.query.QueryType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is a vertx verticle that provides a web server. It implements the
 * web page of the application.
 *
 * @author Julian Peña.
 */
public class WebServer extends AbstractVerticle {

    /**
     * Json (des) serializer.
     */
    private Gson gson;

    /**
     * Web server port to listen on.
     */
    private static int port;

    /**
     * HTML template engine.
     */
    private JadeTemplateEngine jade;

    /**
     * Timestam property name for cookies.
     */
    private static final String TIMESTAMP = "timestamp";

    private static final String CUSTOM_COOKIE = "rsam-ssam-custom-cookie";

    /**
     * Our logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger("WebServer");

    @Override
    public void start(Promise<Void> startPromise) throws Exception {

        LOG.info("Starting web server");

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Query.class, new QueryDeserializer());
        gson = gsonBuilder.create();

        jade = JadeTemplateEngine.create(vertx);

        Router router = Router.router(vertx);

        // favicon route
        router.route().handler(FaviconHandler.create(vertx, "web/favicon.ico"));

        // index
        router.get("/").handler(this::onIndex);
        router.post("/").handler(this::onIndex);

        // custom graph generation
        router.get("/custom").handler(this::onCustom);
        router.post("/custom").handler(this::onCustom);

        // filtered graphs
        router.get("/latest").handler(this::getGraphsWithFilter);
        router.get("/latest/:filters").handler(this::getGraphsWithFilter);

        // these routes are for static assets
        router
                .route("/" + OUTPUT_DIR + "/*")
                .handler(StaticHandler.create(OUTPUT_DIR));

        router
                .route("/" + WEB_ASSETS + "/*")
                .handler(StaticHandler.create(WEB_ASSETS));

        // no matching resource?, go to index
        router.route().last().handler(this::onIndex);

        port = Config.getWebPort();
        if (port < 1024 || port > 65335) {
            LOG.warn("Web port looks suspicious: {}, you sure?", port);
        }

        vertx
                .createHttpServer()
                .requestHandler(router)
                .listen(port, "0.0.0.0")
                .onSuccess(s -> {
                    LOG.info("Listening on port {}", port);
                    startPromise.complete();
                })
                .onFailure(f -> {
                    LOG.error("Couldn't start web server");
                    LOG.error(f.getMessage());
                    LOG.error("Exiting");
                    System.exit(1);
                });
    }

    /**
     * Retrieves the main page (index.jade) which contains the latest graphs.
     *
     * @param routingContext The vertx routing context.
     */
    private void onIndex(RoutingContext routingContext) {

        HttpServerRequest request = routingContext.request();
        HttpServerResponse response = routingContext.response();

        // this is needed when we are receiving form data from the index page
        if (request.method() == HttpMethod.POST) {
            request.setExpectMultipart(true);
        }

        request
                .endHandler(handler -> {

                    String year = request.getFormAttribute("year");
                    String month = request.getFormAttribute("month");
                    String day = request.getFormAttribute("day");

                    HashMap<String, Object> contextMap = new HashMap<>();
                    getGraphs(contextMap, year, month, day, null);

                    jade
                            .render(contextMap, "web/index.jade")
                            .onSuccess(html -> response.end(html))
                            .onFailure(f -> routingContext.fail(f));
                });
    }

    /**
     * This methods handles the request for a subset of the all available
     * graphs. The graphs are filtered by the string filter supplied as the url
     * parameter /latest/filter. If the filter is null (/latest or /latest/)
     * then a 301 redirect is performed to the main page.
     *
     * @param routingContext The vertx routing context.
     */
    private void getGraphsWithFilter(RoutingContext routingContext) {

        HttpServerRequest request = routingContext.request();
        HttpServerResponse response = routingContext.response();

        String filters = request.getParam("filters");

        if (filters == null || filters.isBlank()) {
            response
                    .setStatusCode(301)
                    .putHeader("Location", "http://" + request.host())
                    .end();
            return;
        }

        HashMap<String, Object> contextMap = new HashMap<>();
        getGraphs(contextMap, null, null, null, filters);
        jade
                .render(contextMap, "web/latest.jade")
                .onSuccess(result -> response.end(result))
                .onFailure(failure -> {
                    LOG.error("Failed to render page for latest graphs");
                    LOG.error(failure.getMessage());
                    routingContext.fail(failure);
                });
    }

    /**
     * Handles the form retrieved from the user's browser and triggers a
     * processing cycle for the specified query. Once processing is completed,
     * an new page (result.jade) is rendered to the user.
     *
     * @param routingContext The vertx routing context.
     */
    private void onCustom(RoutingContext routingContext) {

        HttpServerRequest request = routingContext.request();
        HttpServerResponse response = routingContext.response();

        if (request.method() == HttpMethod.GET) {
            request
                    .endHandler(v -> {
                        Cookie cookie = request.getCookie(CUSTOM_COOKIE);
                        Map<String, Object> map = getMapFromCookie(cookie);
                        jade
                                .render(map, "web/custom.jade")
                                .onSuccess(html -> response.end(html))
                                .onFailure(f -> routingContext.fail(f));
                    });
            return;
        }

        // POST request
        request
                .setExpectMultipart(true)
                .endHandler(v -> {

                    Query query = getQuery(request);
                    response.addCookie(makeCookie(query));

                    RsamSsam rsamSsam = new RsamSsam(vertx, query);
                    rsamSsam
                            .makeGraph()
                            .onSuccess(s -> {

                                // timestamp prevents a browser cache hit?
                                String path = query.getGraphPath()
                                        + "?" + System.currentTimeMillis();
                                var map = new HashMap<String, Object>();
                                map.put(GRAPH, path);

                                jade
                                        .render(map, "web/result.jade")
                                        .onSuccess(html -> response.end(html))
                                        .onFailure(f -> routingContext.fail(f));

                            })
                            .onFailure(f -> {
                                // TODO make this pretty
                                response
                                        .putHeader("content-type", "text/html")
                                        .end("<h1>" + f.getMessage() + "</h1>");
                            });
                });
    }

    /**
     * Puts graph paths in the vertx routing context. This makes them available
     * in the index template.
     *
     * @param context A HashMap object.
     * @param year Requested year. If null then the most recent year available
     * is used.
     * @param month Requested month. If null then the most recent month
     * available is used.
     * @param day Requested day. If null then the most recent day available is
     * used.
     * @param filters Comma separated list of filters. If a graph name does not
     * contain any of the filters in it, then it is removed from the retrieved
     * graphs.
     */
    private void getGraphs(HashMap<String, Object> context,
            String year, String month, String day, String filters) {

        GraphsHistory history = new GraphsHistory(vertx);
        List<String> graphs;

        if (year == null || year.isBlank() || month == null || month.isBlank()
                || day == null || day.isBlank()) {
            graphs = history.latestGraphs();
            if (!graphs.isEmpty()) {
                year = history.getLatestYearIndex() + "";
                month = history.getLatestMonthIndex() + "";
                day = history.getLatestDayIndex() + "";
            }
        } else {
            graphs = history.graphsForDate(year, month, day, filters);
            Map<String, String> indexes = history.indexes(year, month, day);
            year = indexes.get("year");
            month = indexes.get("month");
            day = indexes.get("day");
        }

        context.put("history", history);
        context.put("graphs", addTimestamps(graphs));

        context.put("selectedYear", year);
        context.put("selectedMonth", month);
        context.put("selectedDay", day);

        context.put("historyJson", gson.toJson(history));
    }

    /**
     * This method appends the current time to each graph in the list. This is
     * made to prevent the browser to serve a cached image to the user instead
     * of the most recent one (they always have the same file name).
     *
     * @param graphs The graph list
     * @return The graph list with appended timestamps.
     */
    private List addTimestamps(List<String> graphs) {
        for (ListIterator<String> i = graphs.listIterator(); i.hasNext();) {
            String item = i.next();
            i.set(item.substring(item.indexOf(OUTPUT_DIR))
                    + "?" + System.currentTimeMillis());
        }
        return graphs;
    }

    /**
     * Makes a Query object from the form data in the http request.
     *
     * @param request
     * @return
     */
    private Query getQuery(HttpServerRequest request) {

        JsonObject json = new JsonObject();

        request
                .formAttributes()
                .forEach(e -> json.addProperty(e.getKey(), e.getValue()));

        Query query = gson.fromJson(json, Query.class);
        query.setWebQuery(true);

        // TODO make this and option on the web interface
        try {
            query.setType(QueryType.valueOf(Config.getDefaultQueryType()));
        } catch (EnumConstantNotPresentException ex) {
            LOG.error("Default query type is invalid (config.properties)");
            LOG.info("Using fdsn as default query type");
            query.setType(QueryType.fdsn);
        }

        return query;
    }

    /**
     * Tries to parse the content of the cookie stored in the users browser, if
     * any.
     *
     * @param request
     * @return A map containing all entries, or empty if the user does not have
     * a cookie for us.
     */
    private Map<String, Object> getMapFromCookie(Cookie cookie) {

        Map<String, Object> map = new HashMap<>();

        // form options
        map.put("windowSizes", FormOptions.windowSizes);
        map.put("windowFunctions", FormOptions.windowFunctions);
        map.put("graphFormats", FormOptions.graphFormats);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        if (cookie != null) {

            String cookieContent = cookie.getValue();

            map.putAll(
                    Arrays
                            .asList(cookieContent.split("\\?"))
                            .stream()
                            .map(entry -> entry.split("="))
                            .filter(array -> array.length == 2)
                            .collect(Collectors.toMap(a -> a[0], a -> a[1]))
            );

            if (map.containsKey(TIMESTAMP)) {
                Long timestamp = Long.valueOf((String) map.get(TIMESTAMP));
                if (System.currentTimeMillis() - timestamp > 2 * 60 * 1000) {
                    map.put("from", today);
                    map.put("to", today);
                }
            }

            return map;
        }

        // lets just return made up data
        map.put(NETWORK, "LI");
        map.put(STATION, "JULI");
        map.put(COMPONENT, "HHZ");
        map.put(LOCATION, "00");

        map.put(FROM, today);
        map.put(TO, today);

        map.put(WINDOW_SIZE, Query.DEFAULT_WINDOW_SIZE);
        map.put(WINDOW_FUNCTION, Query.DEFAULT_WINDOW_FUNCTION);
        map.put(CUTOFF_FREQUENCY, Query.DEFAULT_CUTOFF_FREQUENCY);

        map.put(GRAPH_FORMAT, Query.DEFAULT_GRAPH_FORMAT);
        map.put(GRAPH_WIDTH, Query.DEFAULT_GRAPH_WIDTH);
        map.put(GRAPH_HEIGHT, Query.DEFAULT_GRAPH_HEIGHT);
        map.put(MAX_POWER, Query.DEFAULT_MAX_POWER);

        map.put(TIMESTAMP, System.currentTimeMillis());

        return map;
    }

    /**
     * Returns a cookie for sending it to the user browser.
     *
     * @param query The effective query object crafted by the user.
     * @return
     */
    private Cookie makeCookie(Query query) {

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder
                .append(NETWORK).append("=").append(query.getN())
                .append("?")
                .append(STATION).append("=").append(query.getS())
                .append("?")
                .append(COMPONENT).append("=").append(query.getC())
                .append("?");

        if (query.getL() != null && !query.getL().isBlank()) {
            stringBuilder
                    .append(LOCATION).append("=").append(query.getL())
                    .append("?");
        }

        stringBuilder.append(FROM).append("=").append(formatDate(query.getFrom()))
                .append("?")
                .append(TO).append("=").append(formatDate(query.getTo()))
                .append("?")
                .append(WINDOW_SIZE).append("=").append(query.getWindowSize())
                .append("?")
                .append(WINDOW_FUNCTION).append("=").append(query.getWindow())
                .append("?")
                .append(CUTOFF_FREQUENCY).append("=").append(query.getCutoffFrequency())
                .append("?")
                .append(MAX_POWER).append("=").append(query.getMaxPower())
                .append("?")
                .append(GRAPH_FORMAT).append("=").append(query.getGraphFormat())
                .append("?")
                .append(GRAPH_WIDTH).append("=").append(query.getGraphWidth())
                .append("?")
                .append(GRAPH_HEIGHT).append("=").append(query.getGraphHeight())
                .append("?")
                .append(TIMESTAMP).append("=").append(System.currentTimeMillis());

        return Cookie.cookie(CUSTOM_COOKIE, stringBuilder.toString());
    }

    /**
     * Formats a data given in milliseconds from epoch into the format use in
     * the web interface.
     *
     * @param millis
     * @return
     */
    private String formatDate(long millis) {
        return LocalDate
                .ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

}
