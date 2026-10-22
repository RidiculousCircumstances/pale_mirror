package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Shared bounded ordered append lane. Tasks carry immutable storage bytes,
 * never a Minecraft world, canonical reducer or mutable domain image. */
final class FrontierPersistenceIo {
    private static final Executor WRITER = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            // A paged write wave admits up to32 section drains; keep bounded room
            // for the other physical stores and canonical journal on this SAME lane.
            new ArrayBlockingQueue<>(64), task -> {
                var thread = new Thread(task, "PM durability journal"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private FrontierPersistenceIo() { }
    static Executor writer() { return WRITER; }
}
