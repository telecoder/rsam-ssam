package rsamssam.datasources.impl.seedlink;

import edu.sc.seis.seisFile.mseed.DataRecord;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Incomplete implementation of a SeedLink client.
 *
 * @author Julian Peña.
 */
public class SeedLinkClient {

    /**
     * Vertx reference.
     */
    private final Vertx vertx;

    /**
     * Our tcp socket.
     */
    private NetSocket socket;

    /**
     * SeedLink Server address.
     */
    private final String server;

    /**
     * SeedLink server port.
     */
    private final int port;

    private enum ConnectionState {
        TCP_DOWN, TCP_UP, HANDSHAKING, IDLE,
        WAITING_MODIFIER_RESPONSE, STREAMING;
    }

    /**
     * SeedLink connection state.
     */
    private ConnectionState state = ConnectionState.TCP_DOWN;

    /**
     * Our logger.
     */
    private final Logger LOG = LoggerFactory.getLogger("SeedLinkClient");

    public SeedLinkClient(Vertx vertx, String server, int port) {
        this.vertx = vertx;
        this.server = server;
        this.port = port;
    }

    /**
     * Creates the tcp client, tries to connect and initialize the TCP socket.
     *
     * @return A succeeded future if a TCP connection was established.
     */
    public Future connect() {

        if (state == ConnectionState.TCP_UP) {
            return Future.failedFuture("Already connected");
        }

        if (state != ConnectionState.TCP_DOWN) {
            return Future.failedFuture("The connection is not down");
        }

        LOG.info("Connecting to {}:{}", server, port);

        Promise<Boolean> promise = Promise.promise();

        NetClientOptions options = new NetClientOptions();
        options
                .setConnectTimeout(5000)
                .setReconnectAttempts(0)
                .setIdleTimeout(5);

        vertx
                .createNetClient(options)
                .connect(port, server)
                .onSuccess(result -> {
                    socket = result;
                    state = ConnectionState.TCP_UP;
                    LOG.info("Connected");
                    promise.complete();
                })
                .onFailure(failure -> {
                    LOG.error("Connection failed {}:{} failed", server, port);
                    LOG.error(failure.getMessage());
                    promise.fail("Connection failed");
                });

        return promise.future();
    }

    /**
     * Sends a HELLO command to the SeedLink server and sets a completion timer.
     *
     * @return A future that will succeed when the the SeedLink server answers
     * the HELLO command, the future will fail if the answer was not well formed
     * or timeout.
     */
    public Future doHello() {

        if (state != ConnectionState.TCP_UP
                & state != ConnectionState.IDLE) {
            LOG.info("Can't handshake while connection is: {}", state);
            return Future.failedFuture("Can issue a HELLO command right now");
        }

        LOG.info(Command.HELLO);

        ConnectionState oldState = state;

        // there is a possibility for a race here (and other methods), a 
        // solution would be to use a vertx CompositeFuture instead of 
        // sequential Future composition
        return send(Command.HELLO)
                .compose(c -> {
                    state = ConnectionState.HANDSHAKING;
                    return expectGreeting(1);
                })
                .onSuccess(s -> state = ConnectionState.IDLE)
                .onFailure(f -> state = oldState);
    }

    public Future doStation(String selector) {
        return doSelector(Command.STATION, selector);
    }

    public Future doSelect(String selector) {
        return doSelector(Command.SELECT, selector);
    }

    private Future doSelector(String command, String selector) {

        if (state != ConnectionState.IDLE) {
            LOG.info("Can't do {} while in {} state", command, state);
            return Future.failedFuture("Connection is not IDLE");
        }

        String slCommand = command;
        if (selector != null) {
            slCommand += " " + selector;
        }

        LOG.info(slCommand);

        return send(slCommand)
                .compose(c -> {
                    state = ConnectionState.WAITING_MODIFIER_RESPONSE;
                    return expectOK(1);
                })
                .onComplete(c -> state = ConnectionState.IDLE)
                .onSuccess(s -> LOG.info("OK"))
                .onFailure(f -> LOG.error("Selector not send or accepted"));
    }

    public Future doTime(Long begin, Long end) {

        if (state != ConnectionState.IDLE) {
            LOG.error("Connection is not IDLE");
            return Future.failedFuture("Connection is not IDLE");
        }

        state = ConnectionState.WAITING_MODIFIER_RESPONSE;

        String command = Command.TIME;

        if (begin != null) {
            command += " " + formatDateTime(begin);
            if (end != null) {
                command += " " + formatDateTime(end);
            }
        }

        LOG.info(command);

        return send(command)
                .compose(c -> expectOK(1))
                .onComplete(c -> state = ConnectionState.IDLE)
                .onSuccess(s -> LOG.info("OK"))
                .onFailure(f -> LOG.error("Failed to send TIME command"));
    }

    /**
     * Sends a FETCH commmand to the SeedLink server.
     *
     * @param n The optional sequence number, can be null.
     * @param begin The optional begin date-time, can be null.
     * @return A Future that will eventually tell if the command was accepted or
     * not by the server.
     */
    public Future doFetch(Integer n, Long begin) {

        if (state != ConnectionState.IDLE) {
            LOG.error("Can't FETCH, connection is not IDLE");
            return Future.failedFuture("Connection is not IDLE");
        }

        String command = Command.FETCH;

        if (n != null) {
            command += " " + n;
            if (begin != null) {
                command += " " + formatDateTime(begin);
            }
        }

        LOG.info(command);

        return send(command)
                .compose(c -> {
                    state = ConnectionState.WAITING_MODIFIER_RESPONSE;
                    return expectOK(1);
                })
                .onSuccess(s -> state = ConnectionState.IDLE)
                .onFailure(f -> LOG.error("Failed to send FETCH command"));
    }

    /**
     * Sends an END command to the SeedLink server.
     *
     * @return A future that will succeed when the END command is confirmed to
     * have been sent to the SeedLink server. The future will fail if the END
     * was not send or in case of timeout.
     */
    public Future doEnd() {

        if (state != ConnectionState.IDLE) {
            LOG.error("Can't start streaming data. Connection is not IDLE");
            return Future.failedFuture("Connection is not IDLE");
        }

        LOG.info(Command.END);

        return send(Command.END)
                .onSuccess(s -> state = ConnectionState.STREAMING)
                .onFailure(f -> state = ConnectionState.IDLE);
    }

    /**
     * Process the incoming seedlink packets, extracts the DataRecord objects
     * within them and reports them in the given handler.
     *
     * @param timeout The maximum time we will wait without receiving packets
     * before aborting the streaming state.
     * @param handler A handler where to report back the incoming DataRecord
     * objects. A null value will be passed once the streams has no more
     * packets.
     * @return A Future that will succeed once there is no more seedlink packets
     * to process, or fail in case of timeout or another unexpected error.
     */
    public Future getDatarecords(int timeout, Handler<DataRecord> handler) {

        Promise promise = Promise.promise();

        ByteBuf bytebuf = Unpooled.buffer(5200);

        socket
                .handler(buffer -> {

                    bytebuf.writeBytes(buffer.getBytes());

                    while (bytebuf.writerIndex() >= 520) {

                        parseSeedLinkPacket(bytebuf.readBytes(520))
                                .ifPresent(datarecord -> handler.handle(datarecord));

                        bytebuf.discardReadBytes();
                    }

                    if (bytebuf.getByte(0) == 'S') {
                        return;
                    }

                    if (bytebuf.writerIndex() != 3) {
                        return;
                    }

                    // stream end
                    if (bytebuf.getByte(0) == 'E' && bytebuf.getByte(1) == 'N'
                            && bytebuf.getByte(2) == 'D') {
                        promise.complete();
                    }
                })
                .closeHandler(c -> {
                    state = ConnectionState.TCP_DOWN;
                    LOG.info("Socket closed");
                    promise.tryComplete();
                });

        return promise.future();
    }

    /**
     * Expects the server response to HELLO commands issued by this client.
     *
     * @param timeout in seconds.
     * @return A Future object that will eventually contain the seedlink server
     * version and description. In case of timeout or error a failed future will
     * be returned.
     */
    private Future<String> expectGreeting(int timeout) {

        Promise promise = Promise.promise();

        // {server version, server description}
        String[] response = {null, null};

        vertx.setTimer(timeout * 1000, t -> promise.tryFail("timeout"));

        RecordParser lineParser = RecordParser
                .newDelimited(Character.toString(13) + Character.toString(10))
                .handler(buffer -> {
                    if (response[0] == null) {
                        response[0] = buffer.toString();
                    } else {
                        response[1] = buffer.toString();
                        LOG.info(response[0]);
                        LOG.info(response[1]);
                        promise.complete(response[0] + "\n" + response[1]);
                    }
                });

        socket.handler(buffer -> lineParser.handle(buffer));

        return promise.future();
    }

    /**
     * Expects the server response to modifier commands issued by this client.
     *
     * @param timeout in seconds.
     * @return A Future object that will eventually succeed if and OK response
     * is received, and will fail in case of ERROR or timeout.
     */
    private Future expectOK(int timeout) {

        Promise promise = Promise.promise();

        vertx.setTimer(timeout * 1000, t -> promise.tryFail("timeout"));

        RecordParser lineParser = RecordParser
                .newDelimited(Character.toString(13) + Character.toString(10))
                .handler(buffer -> {
                    String line = buffer.toString();
                    switch (line) {
                        case "OK":
                            promise.complete();
                            break;
                        case "ERROR":
                        default:
                            promise.tryFail("ERROR");
                    }
                });

        socket.handler(buffer -> lineParser.handle(buffer));

        return promise.future();
    }

    /**
     * Writes the given command to the socket.
     *
     * @param command
     * @return
     */
    private Future<Void> send(String command) {
        return socket
                .write(command + Character.toString(13))
                .onFailure(f -> {
                    LOG.error("Failed to send command {}", command);
                    LOG.error(f.getMessage());
                });
    }

    /**
     * Attempts to parse a SeedLinkPacket and extract the DataRecord object
     * within it.
     *
     * @param bytebuf A ByteBuf that MUST have EXACTLY 520 bytes.
     * @return A Optional than can contain a DataRecord object if successfully
     * parsed.
     */
    private Optional<DataRecord> parseSeedLinkPacket(ByteBuf bytebuf) {

        byte[] bytes = new byte[520];
        bytebuf.readBytes(bytes);

        Optional<SeedLinkPacket> optional = SeedLinkPacket.of(bytes);
        if (optional.isEmpty()) {
            return Optional.empty();
        }

        return optional.get().getMiniseed();
    }

    /**
     * Formats a time given in milliseconds since epoch to SeedLink format.
     *
     * @param time
     * @return
     */
    private String formatDateTime(long time) {
        return DateTimeFormatter
                .ofPattern("YYYY,MM,dd,HH,mm,ss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.ofEpochMilli(time));
    }

    /**
     * Signals the underlying socket to pause the reading of data.
     */
    public void pause() {
        LOG.info("Pausing");
        socket.pause();
    }

    /**
     * Signals the underlying socket to resume the reading of data..
     */
    public void resume() {
        LOG.info("Resuming");
        socket.resume();
    }

}
