package io.farfrontier.palemirror.frontier.v3.api;

import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;

/** Observational post-publication port; neither a reducer nor a persistence participant. */
@FunctionalInterface
public interface CanonicalPublicationObserver<S> {
    void published(TransactionRecord transaction, S before, S after);
}
