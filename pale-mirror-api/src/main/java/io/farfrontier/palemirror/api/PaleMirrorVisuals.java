package io.farfrontier.palemirror.api;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Single-provider experimental visual SPI. Registration is immutable for one JVM lifetime. */
public final class PaleMirrorVisuals {
    private static final AtomicReference<VisualProvider> PROVIDER = new AtomicReference<>();

    private PaleMirrorVisuals() { }

    public static void register(VisualProvider provider) {
        if (provider == null || !PROVIDER.compareAndSet(null, provider)) {
            throw new IllegalStateException("A Pale Mirror visual provider is already registered");
        }
    }

    public static Optional<VisualProvider> provider() { return Optional.ofNullable(PROVIDER.get()); }
}
