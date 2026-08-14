package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DeterministicGenesisWorkersTest {
    @Test void preservesIndexOrderAcrossConcurrentCompletion() {
        try (DeterministicGenesisWorkers workers = new DeterministicGenesisWorkers(4)) {
            CountDownLatch entered = new CountDownLatch(4);
            List<Integer> result = workers.mapIndexed(8, index -> {
                entered.countDown();
                try {
                    if (!entered.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("workers serialized");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return index * index;
            });

            assertEquals(List.of(0, 1, 4, 9, 16, 25, 36, 49), result);
        }
    }

    @Test void nestedWorkRunsInlineWithoutStarvingThePool() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            try (DeterministicGenesisWorkers workers = new DeterministicGenesisWorkers(3)) {
                assertEquals(List.of(3, 33, 63), workers.mapIndexed(3, outer ->
                        workers.mapIndexed(3, inner -> outer * 10 + inner).stream()
                                .mapToInt(Integer::intValue).sum()));
            }
        });
    }

    @Test void workerFailureIsVisibleAndPoolCanBeClosed() {
        try (DeterministicGenesisWorkers workers = new DeterministicGenesisWorkers(2)) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> workers.mapIndexed(8, index -> {
                        if (index == 2) throw new IllegalArgumentException("bad terrain probe");
                        return index;
                    }));
            assertEquals("bad terrain probe", failure.getMessage());
        }
    }
}
