package org.scaffoldeditor.worldexport.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * An output stream that writes to a single Zip entry.
 */
public class ZipEntryOutputStream extends OutputStream {
    private final ZipOutputStream base;

    private boolean isClosed;

    /**
     * Create a ZipEntryOutputStream.
     * @param base The underlying zip output stream.
     * @param entry The zip entry to write to.
     * @throws IOException If an IO exception occurs while writing the entry header.
     */
    public ZipEntryOutputStream(ZipOutputStream base, ZipEntry entry) throws IOException {
        this.base = base;
        base.putNextEntry(entry);
    }

    @Override
    public void write(int val) throws IOException {
        assertNotClosed();
        base.write(val);
    }

    @Override
    public void write(byte[] val) throws IOException {
        assertNotClosed();
        base.write(val);
    }

    @Override
    public void write(byte[] val, int off, int len) throws IOException {
        assertNotClosed();
        base.write(val, off, len);
    }

    @Override
    public void flush() throws IOException {
        assertNotClosed();
        base.flush();
    }

    @Override
    public void close() throws IOException {
        if (isClosed) return;
        base.closeEntry();
        isClosed = true;
    }

    private void assertNotClosed() throws StreamClosedException {
        if (isClosed) throw new StreamClosedException();
    }

    private static class StreamClosedException extends IOException {
        public StreamClosedException() {
            super("This output stream has closed.");
        }
    }
}
