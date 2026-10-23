package com.stata.sfi;

/**
 * Compile-only stand-in for {@code com.stata.sfi.SFIException}, the parent of FrameException.
 *
 * <p>Only {@link #getErrorCode()} is known from the FrameException page
 * (https://www.stata.com/java/api19/com/stata/sfi/FrameException.html). Its return type (int) and
 * the constructor are UNVERIFIED: the constructor is protected here so the stub check, which
 * compares public members only, does not depend on it. check_stubs.do confirms getErrorCode().
 */
public class SFIException extends Exception {
    private static final long serialVersionUID = 1L;

    protected SFIException(String message, int errorCode) {
        super(message);
        throw Stub.fail();
    }

    public int getErrorCode() { throw Stub.fail(); }
}
