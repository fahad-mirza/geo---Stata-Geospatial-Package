package com.stata.sfi;

/**
 * Package-private helper for the stand-ins. Not part of Stata's API, so the stub check skips it
 * (it checks public classes only).
 */
final class Stub {
    private Stub() {
    }

    static UnsupportedOperationException fail() {
        return new UnsupportedOperationException(
                "sfi-stub: compile-only stand-in for com.stata.sfi was called. "
                + "This code only works inside Stata, where Stata's own classes are loaded.");
    }
}
