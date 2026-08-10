package io.farfrontier.palemirror.internal.content;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Atomically loaded authored regions; a running region pins its values in SavedData. */
public final class CampaignRegionDefinitions extends SimpleJsonResourceReloadListener {
    public static final CampaignRegionDefinitions INSTANCE = new CampaignRegionDefinitions();
    private static final AtomicReference<Map<ResourceLocation, CampaignRegionDefinition>> CURRENT = new AtomicReference<>(Map.of());

    private CampaignRegionDefinitions() { super(new Gson(), "pale_mirror/regions"); }

    public static CampaignRegionDefinition require(ResourceLocation id) {
        CampaignRegionDefinition definition = CURRENT.get().get(id);
        if (definition == null) throw new IllegalStateException("Missing campaign region definition " + id);
        return definition;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, CampaignRegionDefinition> compiled = new LinkedHashMap<>();
        resources.forEach((resourceId, element) -> {
            CampaignRegionDefinition definition = compile(resourceId, element.getAsJsonObject());
            if (compiled.put(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate campaign region definition " + definition.id());
            }
        });
        CURRENT.set(Map.copyOf(compiled));
        PaleMirrorMod.LOGGER.info("Loaded {} Pale Mirror campaign region definition(s)", compiled.size());
    }

    private static CampaignRegionDefinition compile(ResourceLocation resource, JsonObject json) {
        ResourceLocation id = ResourceLocation.parse(requiredString(json, "id", resource));
        int version = requiredInt(json, "version", resource);
        int population = requiredInt(json, "population", resource);
        int production = requiredInt(json, "iron_production", resource);
        int demand = requiredInt(json, "iron_demand", resource);
        int stock = requiredInt(json, "initial_iron_stock", resource);
        int stockCapacity = requiredInt(json, "iron_stock_capacity", resource);
        int rationedDemand = requiredInt(json, "rationed_iron_demand", resource);
        int defence = requiredInt(json, "defence", resource);
        long delay = requiredInt(json, "crisis_delay_steps", resource);
        long rationReserve = requiredInt(json, "ration_reserve_steps", resource);
        long requestReserve = requiredInt(json, "request_reserve_steps", resource);
        int defenceLoss = requiredInt(json, "defence_loss_per_unavailable_step", resource);
        int stableRecovery = requiredInt(json, "stable_recovery_steps", resource);
        long currentWindow = requiredInt(json, "route_current_window_steps", resource);
        long expiryWindow = requiredInt(json, "route_expiry_window_steps", resource);
        if (version < 1 || population <= 0 || production < 0 || demand < 0 || stock < 0 || stock > stockCapacity
                || stockCapacity < 0 || rationedDemand < 0 || rationedDemand > demand
                || defence < 0 || defence > 100 || delay < 0 || rationReserve < 0 || requestReserve < 0
                || requestReserve > rationReserve || defenceLoss < 0 || stableRecovery < 1
                || currentWindow < 0 || expiryWindow < currentWindow) {
            throw new IllegalArgumentException(resource + " has invalid campaign region values");
        }
        return new CampaignRegionDefinition(id, version,
                new InfectionSourceId(requiredString(json, "infection_source", resource)), population, production, demand,
                stock, stockCapacity, rationedDemand, defence, delay, rationReserve, requestReserve,
                defenceLoss, stableRecovery, currentWindow, expiryWindow);
    }

    private static String requiredString(JsonObject json, String name, ResourceLocation resource) {
        if (!json.has(name) || !json.get(name).isJsonPrimitive()) throw new IllegalArgumentException(resource + " requires string " + name);
        return json.get(name).getAsString();
    }

    private static int requiredInt(JsonObject json, String name, ResourceLocation resource) {
        if (!json.has(name) || !json.get(name).isJsonPrimitive()) throw new IllegalArgumentException(resource + " requires integer " + name);
        return json.get(name).getAsInt();
    }
}
