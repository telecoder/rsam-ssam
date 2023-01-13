package rsamssam.datasources;

import edu.sc.seis.seisFile.mseed.DataHeader;
import edu.sc.seis.seisFile.mseed.DataRecord;

/**
 * Interface for data clients who provide miniseed packets (ex. FDSN, SeedLink,
 * DataLink, etc.)
 *
 * @author Julian Peña.
 */
public interface DataRecordProvider {

    /**
     * Poison object useful to signal a data consumer there is no more packets
     * available. Its only special attribute is the invalid sequence number.
     */
    public static final DataRecord POISON = new DataRecord(new DataHeader(-1, 'D', false));

    /**
     * Returns the most recent miniseed packet available.
     *
     * @return
     */
    public DataRecord getDataRecord();
}
