package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Retains the ordered suffix when an execution stage yields on durability.
 * This is scheduling only: no captured world state or physical permission survives. */
final class FrontierV3CooperativeStageCursor {
    private int next;
    void run(int size, BooleanSupplier admitted, IntConsumer execute) {
        while (next < size && admitted.getAsBoolean()) {
            int current = next;
            execute.accept(current);
            next = current + 1;
        }
        if (next == size) next = 0;
    }
}
