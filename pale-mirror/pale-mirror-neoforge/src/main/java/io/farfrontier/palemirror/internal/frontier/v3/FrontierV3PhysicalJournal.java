package io.farfrontier.palemirror.internal.frontier.v3;

import java.io.IOException;
import java.util.Map;

/** Durable keyed records; a receipt precedes the caller's physical effect. */
interface FrontierV3PhysicalJournal {
    Map<String, byte[]> image();
    long append(Map<String, byte[]> replacements) throws IOException;
    void checkHealthy() throws IOException;
    void awaitCheckpoint() throws IOException;
    FrontierV3JournalStore.Pressure pressure();
}
