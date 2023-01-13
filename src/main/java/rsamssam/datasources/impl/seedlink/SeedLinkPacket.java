package rsamssam.datasources.impl.seedlink;

import edu.sc.seis.seisFile.mseed.DataRecord;
import edu.sc.seis.seisFile.mseed.SeedFormatException;

import io.vertx.core.buffer.Buffer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Julian Peña.
 */
public class SeedLinkPacket {

    /**
     * The SeedLink packet header (8 bytes).
     */
    private final String header;

    /**
     * The SeedLink packet payload (512 bytes).
     */
    private final byte[] payload;

    /**
     * INFO commands can have responses panning multiple miniseed records. This
     * flag indicates there are more packets coming.
     */
    private final boolean isLastOne;

    /**
     * Our logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger("SeedLinkPacket");

    private SeedLinkPacket(String header, byte[] payload, boolean isLastOne) {
        this.header = header;
        this.payload = payload;
        this.isLastOne = isLastOne;
    }

    /**
     * Returns this SeedLinkPacket as a byte array.
     *
     * @return
     */
    public byte[] bytes() {
        return buffer().getBytes();
    }

    /**
     * Returns this SeedLinkPacket as a vertx buffer.
     *
     * @return
     */
    public Buffer buffer() {
        return Buffer.buffer(header.getBytes()).appendBytes(payload);
    }

    /**
     * Parses a SeedLink packet.
     *
     * @param bytes
     * @return A optional that can contain a SeedLink packet object if
     * successfully parsed, empty otherwise.
     */
    public static Optional<SeedLinkPacket> of(byte[] bytes) {

        // Packet size must be 2 + 6 + 512 = 520 bytes ... right?
        if (bytes.length != 520) {
            return Optional.empty();
        }

        // first two bytes must be SL
        if (bytes[0] != 'S' || bytes[1] != 'L') {
            return Optional.empty();
        }

        String h = new String(Arrays.copyOfRange(bytes, 0, 8));

        /**
         * if the last byte in the header is * it means there are more INFO
         * response packets coming.
         */
        boolean expectMore = false;
        if (h.charAt(7) == '*') {
            expectMore = true;
        }

        byte[] p = Arrays.copyOfRange(bytes, 8, 520);

        return Optional.of(new SeedLinkPacket(h, p, expectMore));
    }

    /**
     * Returns the sequence number as a String.
     *
     * @return
     */
    public String getSequenceNumber() {
        return header.substring(2);
    }

    /**
     * Return true if more INFO packet responses are expected, false otherwise.
     *
     * @return
     */
    public boolean expectMore() {
        return isLastOne;
    }

    /**
     * Tries to parse the payload in this SeedLink packet as a miniseed record
     * object.
     *
     * @return
     */
    public Optional<DataRecord> getMiniseed() {
        try {
            return Optional.of((DataRecord) DataRecord.read(payload));
        } catch (SeedFormatException | IOException ex) {
            LOG.error("Failed to parse payload as a miniseed record");
            LOG.error(ex.getMessage());
            return Optional.empty();
        }
    }

}
