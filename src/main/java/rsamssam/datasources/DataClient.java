package rsamssam.datasources;

import edu.sc.seis.seisFile.mseed.DataRecord;

import io.vertx.core.Future;

import rsamssam.query.Metadata;
import rsamssam.query.Query;

/**
 * Interface for data clients that return samples in ascii format.
 *
 * @author Julian Peña.
 */
public interface DataClient {

    /**
     * Triggers the download of waveform data according to the given query.
     *
     * @param query A query with valid SCNL identification and time ranges.
     * @return A Future object that will succeed once the download has started
     * (not finished), the future will fail in any other case.
     */
    public Future<Metadata> download(Query query);

    /**
     * Given a DataRecord object, return a Metadata record for it.
     *
     * @param dataRecord
     * @return
     */
    default Metadata getMetadata(DataRecord dataRecord) {
        int sps = (int) dataRecord.getSampleRate();
        long millis = dataRecord.getStartBtime().toInstant().toEpochMilli();
        return new Metadata(sps, millis);
    }
}
