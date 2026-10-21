package io.farfrontier.palemirror.internal.frontier.v3;

import java.io.IOException;
import java.util.Map;

/** Durable keyed records; a receipt precedes the caller's physical effect. */
interface FrontierV3PhysicalJournal {
    Map<String, byte[]> image();
    /** Point lookup; implementations with partitioned storage must not hydrate the world. */
    byte[] read(String key) throws IOException;
    /** Conservative discovery; membership never proves a current operation or grants authority. */
    default java.util.Set<String> indexedKeys() throws IOException {
        var keys = new java.util.TreeSet<>(image().keySet()); keys.remove("$meta"); return keys;
    }
    default String layoutDiagnostic() { return "\"layout\":\"resident\""; }
    long append(Map<String, byte[]> replacements) throws IOException;
    void checkHealthy() throws IOException;
    void awaitCheckpoint() throws IOException;
    FrontierV3JournalStore.Pressure pressure();
}
