package io.farfrontier.palemirror.frontier.v3.persistence;

import java.io.*;

/** Sole header grammar for early installed-world selection and complete aggregate hydration. */
final class FrontierStateSchema {
    // Confirmed container placement survives resource-custody release separately from live bindings.
    // Historical disposable worlds cannot hydrate this changed declaration grammar.
    static final int VERSION = 278;
    private static final int MAGIC = 0x4656334D;
    private FrontierStateSchema() { }
    static void write(DataOutputStream output) throws IOException {
        output.writeInt(MAGIC); output.writeShort(VERSION);
    }
    static void read(DataInputStream input) throws IOException {
        if (input.readInt() != MAGIC) throw new IllegalArgumentException("unknown Frontier v3 state magic");
        int version = input.readUnsignedShort();
        if (version != VERSION) throw new IllegalArgumentException("Frontier v3 state schema " + version
                + " is incompatible with required schema " + VERSION + "; fresh current-schema world required");
    }
}
