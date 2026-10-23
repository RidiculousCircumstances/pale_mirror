package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Retains the ordered suffix when an execution stage yields on durability.
 * This is scheduling only: no captured world state or physical permission survives. */
final class FrontierV3CooperativeStageCursor {
    private int next;
    void run(int size, BooleanSupplier admitted, IntConsumer execute) {
        run(size, admitted, () -> true, execute);
    }
    void run(int size, BooleanSupplier admitted, BooleanSupplier remainingBudget, IntConsumer execute) {
        boolean progressed = false;
        while (next < size && (!progressed || remainingBudget.getAsBoolean()) && admitted.getAsBoolean()) {
            int current = next;
            execute.accept(current);
            next = current + 1; progressed = true;
        }
        if (next == size) next = 0;
    }
}
