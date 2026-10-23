package com.stata.sfi;

/**
 * Compile-only stand-in for {@code com.stata.sfi.FrameException}, thrown by Frame.create,
 * Frame.connect and Frame.clone.
 *
 * <p>Signatures from https://www.stata.com/java/api19/com/stata/sfi/FrameException.html
 * (checked 23 Oct 2026).
 */
public class FrameException extends SFIException {
    private static final long serialVersionUID = 1L;

    public FrameException(int errorCode) {
        super(null, errorCode);
    }

    public FrameException(String message, int errorCode) {
        super(message, errorCode);
    }
}
