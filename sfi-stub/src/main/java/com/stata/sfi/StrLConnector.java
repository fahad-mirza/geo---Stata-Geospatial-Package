package com.stata.sfi;

import java.io.IOException;

/**
 * Compile-only stand-in for {@code com.stata.sfi.StrLConnector}: a handle on one strL cell
 * (one variable, one observation), used to read and write binary data such as WKB geometry.
 *
 * <p>Signatures from https://www.stata.com/java/api19/com/stata/sfi/StrLConnector.html
 * (checked 23 Oct 2026). Use it in try-with-resources so close() always runs.
 */
public class StrLConnector implements AutoCloseable {
    public StrLConnector(int var, long obs) throws IOException {
        throw Stub.fail();
    }

    public StrLConnector(Frame frame, int var, long obs) throws IOException {
        throw Stub.fail();
    }

    @Override
    public void close() { throw Stub.fail(); }                       // synchronized
    public long getPosition() { throw Stub.fail(); }
    public void setPosition(long pos) { throw Stub.fail(); }         // synchronized
    public long getSize() { throw Stub.fail(); }                     // synchronized
    public boolean isBinary() throws IOException { throw Stub.fail(); } // synchronized
    public void reset() { throw Stub.fail(); }                       // synchronized
}
