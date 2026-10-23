package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.thread.ProcessorMailbox;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ReturnFenceMailboxTest {
    @Test void nativeFlushMailboxCompletesFenceBeforeDeserializationWithoutServerTaskPump() {
        // EntityStorage.flush drains this exact native mailbox, not its server dispatcher.
        try (var queue = ProcessorMailbox.<Runnable>create(ignored -> { }, "entity-deserializer")) {
            var source = new CompletableFuture<Optional<CompoundTag>>();
            var published = new AtomicBoolean(); var released = new AtomicBoolean();
            var durable = new CompletableFuture<Long>();
            var fenced = FrontierV3DepartureReturnReadFence.beforeDeserialize(source, queue::tell,
                    ignored -> { published.set(true); return durable; }, () -> released.set(true));
            var deserialized = fenced.thenApplyAsync(raw -> {
                assertTrue(published.get()); return raw;
            }, queue::tell);
            source.complete(Optional.of(new CompoundTag()));
            assertFalse(deserialized.isDone());
            queue.runAll();
            assertTrue(published.get()); assertFalse(released.get());
            assertFalse(deserialized.isDone(), "native mailbox remains free while the journal is pending");
            durable.complete(1L);
            assertFalse(deserialized.isDone(), "writer cannot deserialize or release on its own thread");
            queue.runAll();
            assertTrue(deserialized.isDone()); assertFalse(deserialized.isCompletedExceptionally());
            assertTrue(released.get());
        }
    }

    @Test void failedFenceFailsNativeLoadAndReleasesReadReservation() {
        try (var queue = ProcessorMailbox.<Runnable>create(ignored -> { }, "entity-deserializer")) {
            var released = new AtomicBoolean(); var loaded = new AtomicBoolean();
            var fenced = FrontierV3DepartureReturnReadFence.beforeDeserialize(
                    CompletableFuture.completedFuture(Optional.empty()), queue::tell,
                    ignored -> { throw new IllegalStateException("publication failed"); }, () -> released.set(true));
            fenced.thenRun(() -> loaded.set(true));
            queue.runAll();
            assertTrue(fenced.isCompletedExceptionally()); assertTrue(released.get()); assertFalse(loaded.get());
        }
    }

    @Test void failedAsyncFenceCannotDeserializeAndReleasesOnNativeMailbox() {
        try (var queue = ProcessorMailbox.<Runnable>create(ignored -> { }, "entity-deserializer")) {
            var durable = new CompletableFuture<Long>();
            var released = new AtomicBoolean();
            var fenced = FrontierV3DepartureReturnReadFence.beforeDeserialize(
                    CompletableFuture.completedFuture(Optional.empty()), queue::tell,
                    ignored -> durable, () -> released.set(true));
            queue.runAll();
            durable.completeExceptionally(new IllegalStateException("write failed"));
            assertFalse(released.get()); assertFalse(fenced.isDone());
            queue.runAll();
            assertTrue(fenced.isCompletedExceptionally()); assertTrue(released.get());
        }
    }
}
