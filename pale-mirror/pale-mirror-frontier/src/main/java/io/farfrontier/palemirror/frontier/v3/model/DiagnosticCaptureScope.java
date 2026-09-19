package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import java.util.Optional;

/** Scoped host-to-reducer identity handoff; a missing scope remains explicitly unavailable. */
public final class DiagnosticCaptureScope implements AutoCloseable {
    private static final ThreadLocal<DiagnosticRuntimeIdentity> CURRENT = new ThreadLocal<>();
    private final DiagnosticRuntimeIdentity prior;
    private DiagnosticCaptureScope(DiagnosticRuntimeIdentity value) { prior = CURRENT.get(); CURRENT.set(value); }
    public static DiagnosticCaptureScope open(DiagnosticRuntimeIdentity value) { return new DiagnosticCaptureScope(Objects.requireNonNull(value, "diagnostic capture identity")); }
    static Optional<DiagnosticRuntimeIdentity> current() { return Optional.ofNullable(CURRENT.get()); }
    @Override public void close() { if (prior == null) CURRENT.remove(); else CURRENT.set(prior); }
}
