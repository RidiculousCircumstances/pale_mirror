package io.farfrontier.palemirror.internal.content;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.ThreatTierPolicy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Atomic reloadable tier policy. Invalid data leaves the active policy untouched. */
public final class ThreatTierDefinitions extends SimpleJsonResourceReloadListener {
    public static final ThreatTierDefinitions INSTANCE = new ThreatTierDefinitions();
    private static final AtomicReference<ThreatTierPolicy> CURRENT = new AtomicReference<>(ThreatTierPolicy.DEFAULT);

    private ThreatTierDefinitions() { super(new Gson(), "pale_mirror/threat_tiers"); }
    public static ThreatTierPolicy current() { return CURRENT.get(); }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        if (resources.isEmpty()) {
            CURRENT.set(ThreatTierPolicy.DEFAULT);
            return;
        }
        if (resources.size() != 1) throw new IllegalArgumentException("Exactly one PM threat tier policy is allowed");
        Map.Entry<ResourceLocation, JsonElement> entry = resources.entrySet().iterator().next();
        JsonObject json = entry.getValue().getAsJsonObject();
        ThreatTierPolicy compiled = new ThreatTierPolicy(requiredLong(json, "infested_at_active_steps", entry.getKey()),
                requiredLong(json, "siege_at_active_steps", entry.getKey()),
                requiredLong(json, "apex_at_active_steps", entry.getKey()));
        CURRENT.set(compiled);
        PaleMirrorMod.LOGGER.info("Loaded PM threat tier policy {}", entry.getKey());
    }

    private static long requiredLong(JsonObject json, String name, ResourceLocation resource) {
        if (!json.has(name) || !json.get(name).isJsonPrimitive()) {
            throw new IllegalArgumentException(resource + " requires integer " + name);
        }
        return json.get(name).getAsLong();
    }
}
