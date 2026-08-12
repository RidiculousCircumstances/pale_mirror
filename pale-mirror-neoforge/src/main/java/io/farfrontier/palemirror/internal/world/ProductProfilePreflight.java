package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.api.PaleMirrorVisuals;
import net.neoforged.fml.ModList;

/** Stops a product server before canonical world data is opened in an unrepresentable profile. */
public final class ProductProfilePreflight {
    public static final String PROFILE_PROPERTY = "pale_mirror.profile";
    public static final String CORE_ONLY = "core-only";

    private ProductProfilePreflight() { }

    public static boolean coreOnly() { return CORE_ONLY.equals(System.getProperty(PROFILE_PROPERTY, "product")); }

    public static void verify() {
        String profile = System.getProperty(PROFILE_PROPERTY, "product");
        if (CORE_ONLY.equals(profile)) return;
        if (!"product".equals(profile)) throw new IllegalStateException("Unknown Pale Mirror profile " + profile);
        requireMod("pale_mirror_visuals", null);
        requireMod("geckolib", "4.9.2");
        requireMod("villageroverhaul", "3.10.17.16");
        requireMod("supplementaries", null);
        var provider = PaleMirrorVisuals.provider().orElseThrow(() ->
                new IllegalStateException("Product profile requires a registered Pale Mirror Visuals provider"));
        AdapterHealth health = provider.health();
        if (health.status() != AdapterHealth.Status.AVAILABLE) {
            throw new IllegalStateException("Pale Mirror Visuals preflight failed: " + health.detail());
        }
        var required = java.util.Set.of(Capability.AUTHORED_REGION_GENESIS,
                Capability.MANAGED_SETTLEMENT_RESIDENTS, Capability.DYNAMIC_WORLD_PRESENTATION,
                Capability.VISIBLE_PM_THREAT_CONTROLLER);
        if (!health.capabilities().containsAll(required)) {
            java.util.LinkedHashSet<Capability> missing = new java.util.LinkedHashSet<>(required);
            missing.removeAll(health.capabilities());
            throw new IllegalStateException("Pale Mirror Visuals is missing required product capabilities " + missing);
        }
    }

    private static void requireMod(String id, String exactVersion) {
        var container = ModList.get().getModContainerById(id).orElseThrow(() ->
                new IllegalStateException("Pale Mirror product profile requires mod " + id));
        String actual = container.getModInfo().getVersion().toString();
        if (exactVersion != null && !exactVersion.equals(actual)) {
            throw new IllegalStateException("Pale Mirror product profile requires " + id + " " + exactVersion
                    + " but found " + actual);
        }
    }
}
