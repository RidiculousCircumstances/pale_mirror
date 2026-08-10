package io.farfrontier.palemirror.internal.integration.millenaire;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Explicit opt-in; native villages are never selected for the authored campaign by default. */
public final class MillenaireIntegrationConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ALLOW_CAMPAIGN;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ALLOW_CAMPAIGN = builder.comment("Allow a read-only native Millenaire village to host the Pale Mirror 0.2 campaign.",
                        "PM will own only its external economy, routes and PM-built depot; it will not move population, ruin, grow or construct the native village.")
                .define("allowMillenaireCampaign", false);
        SPEC = builder.build();
    }

    private MillenaireIntegrationConfig() { }
}
