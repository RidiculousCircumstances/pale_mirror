package io.farfrontier.palemirror.internal.production;

/** Startup composition supplies the domain adapter. No client or block entity invents production state. */
public final class ProductionBenchInteraction {
    private static ProductionBenchAccess access;
    private ProductionBenchInteraction() { }
    public static void install(ProductionBenchAccess provider) {
        if (access != null) throw new IllegalStateException("production bench access already installed");
        access = java.util.Objects.requireNonNull(provider);
    }
    public static ProductionBenchAccess access() {
        if (access == null) throw new IllegalStateException("production bench access is not installed");
        return access;
    }
}
