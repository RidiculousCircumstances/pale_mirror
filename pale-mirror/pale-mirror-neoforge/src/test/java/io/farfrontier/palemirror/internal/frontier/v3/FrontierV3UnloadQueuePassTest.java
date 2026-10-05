package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3UnloadQueuePassTest {
    @Test void immediateRetryYieldsWithoutDroppingItAndCompletesAfterDependencyProgress() {
        Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        var pass = new FrontierV3UnloadQueuePass();
        var ready = new AtomicBoolean();
        var saved = new AtomicInteger();
        Runnable retry = new Runnable() {
            @Override public void run() {
                if (ready.get()) saved.incrementAndGet();
                else queue.add(this);
            }
        };
        queue.add(retry);
        pass.begin(queue.size());
        assertSame(retry, pass.poll(queue));
        retry.run();
        assertNull(pass.poll(queue), "a retry cannot starve the outer server loop");
        assertEquals(0, saved.get(), "an unready chunk must not be saved");
        assertEquals(1, queue.size(), "the exact callback is retained");
        // The outer loop can now execute the generation/save dependency, not fabricate readiness.
        ready.set(true);
        pass.begin(queue.size());
        Runnable next = pass.poll(queue);
        assertSame(retry, next);
        next.run();
        assertEquals(1, saved.get());
        assertTrue(queue.isEmpty());
        assertNull(pass.poll(queue));
    }

    @Test void retryDoesNotStarveOtherPreviouslyQueuedChunks() {
        Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        var pass = new FrontierV3UnloadQueuePass();
        var saved = new AtomicInteger();
        Runnable retry = new Runnable() {
            @Override public void run() { queue.add(this); }
        };
        queue.add(retry);
        queue.add(saved::incrementAndGet);
        pass.begin(queue.size());
        pass.poll(queue).run();
        pass.poll(queue).run();
        assertNull(pass.poll(queue));
        assertEquals(1, saved.get());
        assertSame(retry, queue.peek());
        assertEquals(1, queue.size());
    }

    @Test void emptyPassCannotConsumeLaterPublication() {
        Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        var pass = new FrontierV3UnloadQueuePass();
        assertThrows(IllegalArgumentException.class, () -> pass.begin(-1));
        pass.begin(0);
        Runnable later = () -> fail("must wait for the next pass");
        queue.add(later);
        assertNull(pass.poll(queue));
        assertSame(later, queue.peek());
        pass.begin(queue.size());
        assertSame(later, pass.poll(queue));
    }
}
