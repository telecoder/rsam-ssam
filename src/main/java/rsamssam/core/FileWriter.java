package rsamssam.core;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rsamssam.query.Query;

/**
 * This class implement a very simple file writer.
 *
 * @author Julian Peña.
 */
public class FileWriter {

    /**
     * Our logger.
     */
    private final Logger LOG = LoggerFactory.getLogger("FileWriter");

    /**
     * Creates the output folders for the results and graph of a Query.
     *
     * @param query
     * @return True if the folder was created, false otherwise.
     */
    public Boolean makeOuputFolder(Query query) {
        try {
            Files.createDirectories(Paths.get(query.getOutputPath()));
            return true;
        } catch (IOException ex) {
            LOG.error(ex.getMessage());
            LOG.error("{} Failed to create output directory ", query.getId());
        }
        return false;
    }

    /**
     * Writes a file on disk with the given path and content.
     * <p>
     * IMPORTANT: This method can potentially block for a long time if the file
     * being written is too large. Consider calling this method from a dedicated
     * thread.
     *
     * @param path The path of the file to be written.
     * @param content The content of the file.
     * @return True if the file was written, false otherwise.
     */
    public Boolean write(String path, String content) {

        if (null != content && content.length() < 1) {
            LOG.error("Skipping file {}. It's null or empty", path);
            return false;
        }

        // if the file already exist, then delete it first
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException ex) {
            LOG.error("Failed to delete old file {}. Aborting.", path);
            LOG.error(ex.getMessage());
            return false;
        }

        File file = new File(path);
        try ( var f = new RandomAccessFile(file, "rw")) {
            f.seek(0);
            f.writeBytes(content);
        } catch (IOException ex) {
            LOG.error(ex.getMessage());
            LOG.error("Failed to open file {}", path);
            return false;
        }

        return true;
    }

}
