package io.farfrontier.palemirror.frontier.v3.persistence;

import java.io.*;

/** Sole header grammar for early installed-world selection and complete aggregate hydration. */
final class FrontierStateSchema {
    // Runtime field declarations are explicit, not inferred from the genesis layout.
    // Accounts retain exact cumulative cash turnover. Never invent history for old worlds.
    // Prepared container projections retain their exact physical predecessor.
    // Population event grammar includes exact displaced-pickup reapproach.
    // Every expedition provision allocation retains its independently selected
    // source port; replay must never substitute the cargo warehouse.
    // Individual durable items retain condition through every custody boundary.
    static final int VERSION = 303;
    private static final int MAGIC = 0x4656334D;
    private FrontierStateSchema() { }
    static void write(DataOutputStream output) throws IOException {
        output.writeInt(MAGIC); output.writeShort(VERSION);
    }
    static int read(DataInputStream input) throws IOException {
        if (input.readInt() != MAGIC) throw new IllegalArgumentException("unknown Frontier v3 state magic");
        int version = input.readUnsignedShort();
        if (version != VERSION) throw new IllegalArgumentException("Frontier v3 state schema " + version
                + " is incompatible with required schema " + VERSION + "; fresh current-schema world required");
        return version;
    }
}
