package io.farfrontier.palemirror.visuals.genesis;

/** Signals that one region center cannot satisfy the bounded dry MineSite contract. */
public final class DryMineSiteUnavailableException extends IllegalStateException {
    public DryMineSiteUnavailableException(String message) {
        super(message);
    }
}
