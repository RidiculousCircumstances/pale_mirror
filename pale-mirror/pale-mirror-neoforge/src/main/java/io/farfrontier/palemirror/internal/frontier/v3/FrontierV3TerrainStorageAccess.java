package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.concurrent.CompletableFuture;

/** Native region storage's actual asynchronous flush, without loading or requesting chunks. */
public interface FrontierV3TerrainStorageAccess {
    CompletableFuture<Void> frontierV3$synchronizeTerrain();
}
