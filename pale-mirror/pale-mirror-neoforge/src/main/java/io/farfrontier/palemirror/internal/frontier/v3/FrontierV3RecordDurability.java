package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Owner-thread receipts for submitted record images, not semantic execution permission.
 * Owners must reject dirty/absent rows before asking about the submitted image.
 */
final class FrontierV3RecordDurability<K> {
    private final Map<K, CompletableFuture<Long>> receipts = new HashMap<>();

    void submitted(K key, CompletableFuture<Long> receipt) {
        receipts.put(Objects.requireNonNull(key), Objects.requireNonNull(receipt));
    }

    boolean ready(K key) {
        var receipt = receipts.get(key);
        if (receipt == null) return true; // Recovered or synchronously forced image.
        if (!receipt.isDone()) return false;
        receipt.join(); // Propagate failed writes; never wait on the owner thread.
        receipts.remove(key);
        return true;
    }

    void forget(K key) { receipts.remove(key); }
}
