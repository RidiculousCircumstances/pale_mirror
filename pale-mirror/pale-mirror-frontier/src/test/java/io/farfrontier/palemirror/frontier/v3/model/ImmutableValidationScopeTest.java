package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableValidationScopeTest {
    @Test void failedNestedChecksRestoreTheOuterObserverAndCannotInstallTrustedInputs() {
        var metrics = new Metrics(); var check = new ImmutableInvariantCheck<Integer>("owned.test");
        var failedInput = new Object(); var attempts = new AtomicInteger();
        ImmutableValidationScope.observed(metrics, "outer", () -> {
            assertEquals(1, check.value(() -> 1, new Object()));
            assertThrows(IllegalArgumentException.class, () -> ImmutableValidationScope.observed(metrics, "inner", () ->
                    check.value(() -> { attempts.incrementAndGet(); throw new IllegalArgumentException("invalid input"); }, failedInput)));
            assertEquals(2, check.value(() -> { attempts.incrementAndGet(); return 2; }, failedInput));
            return null;
        });
        assertEquals(2, attempts.get(), "a failed check must not install reusable success");
        assertEquals(List.of("outer", "inner", "outer"), metrics.closed);
        check.value(() -> 3, new Object());
        assertEquals(3, metrics.closed.size(), "the lexical observer must be gone after exit");
    }
    private static final class Metrics implements FrontierExecutionMetrics {
        final List<String> closed = new ArrayList<>();
        @Override public boolean detailsEnabled() { return true; }
        @Override public Span detail(Stage stage, String kind, String owner) { return () -> closed.add(owner); }
        @Override public Span begin(Stage stage, String kind, String owner) { return () -> { }; }
        @Override public void observeQueue(SimInstant at, int depth, Optional<ScheduledAction> deferred) { }
        @Override public Snapshot snapshot() { return Snapshot.empty(); }
    }
}
