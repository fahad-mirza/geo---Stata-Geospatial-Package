package com.stata.sfi;

import java.io.IOException;

/**
 * Compile-only stand-in for {@code com.stata.sfi.Frame} (access to a named Stata frame).
 *
 * <p>Signatures copied from https://www.stata.com/java/api19/com/stata/sfi/Frame.html
 * (checked 23 Oct 2026). Unlike {@link Data}, most methods are instance methods on a Frame
 * object obtained from {@link #create(String)} or {@link #connect(String)}.
 * Note the difference from Data: Frame.storeBytes takes (var, obs, ...), Data.storeBytes takes a
 * StrLConnector.
 */
public final class Frame {
    private Frame() {
    }

    // ----- Static frame utilities (all synchronized) -----
    public static Frame create(String name) throws FrameException { throw Stub.fail(); }
    public static Frame connect(String name) throws FrameException { throw Stub.fail(); }
    public static String getCWF() { throw Stub.fail(); }                    // name of the current working frame
    public static String getFrameAt(int index) { throw Stub.fail(); }
    public static int getFrameCount() { throw Stub.fail(); }
    public static String[] getFrames() { throw Stub.fail(); }

    // ----- Frame-level operations (synchronized unless marked) -----
    public Frame clone(String newName) throws FrameException { throw Stub.fail(); }
    public int changeToCWF() { throw Stub.fail(); }
    public int drop() { throw Stub.fail(); }
    public int rename(String newName) { throw Stub.fail(); }
    public String getName() { throw Stub.fail(); }                          // thread-safe

    // ----- Variables (synchronized unless marked) -----
    public int addVarByte(String varname) { throw Stub.fail(); }
    public int addVarDouble(String varname) { throw Stub.fail(); }
    public int addVarFloat(String varname) { throw Stub.fail(); }
    public int addVarInt(String varname) { throw Stub.fail(); }
    public int addVarLong(String varname) { throw Stub.fail(); }
    public int addVarStr(String varname, int length) { throw Stub.fail(); }
    public int addVarStrL(String varname) { throw Stub.fail(); }
    public int dropVar(int var) { throw Stub.fail(); }
    public int renameVar(int var, String newname) { throw Stub.fail(); }
    public int getVarCount() { throw Stub.fail(); }                         // thread-safe
    public int getVarIndex(String varname) { throw Stub.fail(); }
    public String getVarName(int var) { throw Stub.fail(); }                // thread-safe
    public String getVarLabel(int var) { throw Stub.fail(); }               // thread-safe
    public int setVarLabel(int var, String label) { throw Stub.fail(); }
    public String getVarFormat(int var) { throw Stub.fail(); }              // thread-safe
    public int setVarFormat(int var, String format) { throw Stub.fail(); }
    public int getType(int var) { throw Stub.fail(); }                      // thread-safe
    public int getStrVarWidth(int var) { throw Stub.fail(); }               // thread-safe
    public boolean isVarTypeStr(int var) { throw Stub.fail(); }             // thread-safe
    public boolean isVarTypeStrL(int var) { throw Stub.fail(); }            // thread-safe
    public boolean isVarTypeString(int var) { throw Stub.fail(); }          // thread-safe
    public boolean isAlias(int var) { throw Stub.fail(); }                  // thread-safe

    // ----- Observations -----
    public long getObsTotal() { throw Stub.fail(); }                        // thread-safe
    public int setObsTotal(long obs) { throw Stub.fail(); }

    // ----- Values -----
    public double getNum(int var, long obs) { throw Stub.fail(); }                          // thread-safe
    public String getStr(int index, long obs) { throw Stub.fail(); }
    public String getStrf(int index, long obs) { throw Stub.fail(); }                       // thread-safe
    public String getFormattedValue(int var, long obs, boolean bValueLabel) { throw Stub.fail(); }
    public int storeNum(int var, long obs, double value) { throw Stub.fail(); }
    public int storeNumFast(int var, long obs, double value) { throw Stub.fail(); }         // thread-safe; fails silently
    public int storeStr(int var, long obs, String value) { throw Stub.fail(); }
    public int storeStrf(int var, long obs, String value) { throw Stub.fail(); }
    public int storeStrfFast(int var, long obs, String value) { throw Stub.fail(); }        // thread-safe; fails silently
    public void updateModified() { throw Stub.fail(); }

    // ----- strL -----
    public int allocateStrL(StrLConnector dsc, long size) throws IOException { throw Stub.fail(); }
    public int allocateStrL(StrLConnector dsc, long size, boolean binary) throws IOException { throw Stub.fail(); }
    public int readBytes(StrLConnector dsc, byte[] b) throws IOException { throw Stub.fail(); }
    public int readBytes(StrLConnector dsc, byte[] b, int off, int len) throws IOException { throw Stub.fail(); }
    public int writeBytes(StrLConnector dsc, byte[] b) { throw Stub.fail(); }
    public int writeBytes(StrLConnector dsc, byte[] b, int off, int len) { throw Stub.fail(); }
    public int storeBytes(int var, long obs, byte[] bytes, boolean binary) { throw Stub.fail(); }
}
