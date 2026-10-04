package io.farfrontier.palemirror.internal.frontier.v3;

/** A host cannot expose saved actors without a verified canonical runtime. */
final class FrontierV3StartupExposure {
    private FrontierV3StartupExposure() { }

    static void requireActive(FrontierV3RuntimeStatus status, RuntimeException cause) {
        if (status.kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) {
            throw new IllegalStateException("Frontier v3 startup failed; refusing world exposure: "
                    + status.detail().orElse(status.kind().name()), cause);
        }
    }
}
