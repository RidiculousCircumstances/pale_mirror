package io.farfrontier.palemirror.internal.frontier.v3;

/** Physical-provider capability invoked only after vanilla finishes enumerating its save pass. */
public interface FrontierV3EntitySaveBoundary {
    void frontierV3$storedChunk();
    void frontierV3$completeSavePass(boolean complete);
}
