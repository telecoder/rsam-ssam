package rsamssam.query;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import rsamssam.config.Config;
import rsamssam.config.Names;

/**
 * Custom json deserializer for Query objects. Mean to be used with the
 * queries.json file.
 *
 * @author Julian Peña.
 */
public class QueryDeserializer implements JsonDeserializer<Query> {

    @Override
    public Query deserialize(JsonElement jsonElement, Type typeOfT,
            JsonDeserializationContext context) throws JsonParseException {

        JsonObject json = jsonElement.getAsJsonObject();

        // if any of these fails an exception will be thrown
        String N = json.get(Names.NETWORK).getAsString();
        String S = json.get(Names.STATION).getAsString();
        String C = json.get(Names.COMPONENT).getAsString();
        String L = json.get(Names.LOCATION).getAsString();

        Query query = new Query(S, C, N, L);

        if (json.has(Names.QUERY_TYPE)) {
            query.setType(QueryType.valueOf(json.get(Names.QUERY_TYPE).getAsString()));
        } else {
            query.setType(QueryType.valueOf(Config.getDefaultQueryType()));
        }

        if (json.has(Names.CUTOFF_FREQUENCY)) {
            query.setCutoffFrequency(json.get(Names.CUTOFF_FREQUENCY).getAsInt());
        }

        if (json.has(Names.MAX_POWER)) {
            query.setMaxPower(json.get(Names.MAX_POWER).getAsInt());
        }

        if (json.has(Names.WEB_QUERY)) {
            query.setWebQuery(json.get(Names.WEB_QUERY).getAsBoolean());
        }

        if (json.has(Names.FROM) && json.has(Names.TO)) {

            long from, to;
            if (json.has(Names.FROM_TIME) && json.has(Names.TO_TIME)) {
                from = millis(json.get(Names.FROM), json.get(Names.FROM_TIME));
                to = millis(json.get(Names.TO), json.get(Names.TO_TIME));
            } else {
                from = millis(json.get(Names.FROM), null);
                to = millis(json.get(Names.TO), null);
            }

            query
                    .setFrom(from)
                    .setTo(to);
        }

        if (json.has(Names.GRAPH_FORMAT)) {
            query.setGraphFormat(json.get(Names.GRAPH_FORMAT).getAsString());
        }

        if (json.has(Names.GRAPH_WIDTH) && json.has(Names.GRAPH_HEIGHT)) {
            query
                    .setGraphWidth(json.get(Names.GRAPH_WIDTH).getAsInt())
                    .setGraphHeight(json.get(Names.GRAPH_HEIGHT).getAsInt());
        }

        if (json.has(Names.WINDOW_SIZE)) {
            query.setWindowSize(json.get(Names.WINDOW_SIZE).getAsInt());
        }

        if (json.has(Names.WINDOW_FUNCTION)) {
            query.setWindowFunction(json.get(Names.WINDOW_FUNCTION).getAsString());
        }

        if (json.has(Names.RESPONSE_FACTOR)) {
            query.setResponseFactor(json.get(Names.RESPONSE_FACTOR).getAsDouble());
        }

        return query;
    }

    private long millis(JsonElement date, JsonElement time) {

        if (time == null) {
            return LocalDate
                    .parse(date.getAsString())
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli();
        }

        String datetime = date.getAsString() + " " + time.getAsString();
        String format = "yyyy-MM-dd HH:mm";

        return LocalDateTime
                .parse(datetime, DateTimeFormatter.ofPattern(format, Locale.US))
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
    }

}
