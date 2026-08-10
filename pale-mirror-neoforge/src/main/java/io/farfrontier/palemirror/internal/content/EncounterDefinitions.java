package io.farfrontier.palemirror.internal.content;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.ThreatTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Atomic registry of optional encounter profiles. Content failure retains the previous registry. */
public final class EncounterDefinitions extends SimpleJsonResourceReloadListener {
    public static final EncounterDefinitions INSTANCE = new EncounterDefinitions();
    private static final int MAX_ACTORS = 16;
    private static final AtomicReference<Map<ResourceLocation, EncounterProfile>> CURRENT = new AtomicReference<>(Map.of());

    private EncounterDefinitions() { super(new Gson(), "pale_mirror/encounters"); }

    public static Map<ResourceLocation, EncounterProfile> current() { return CURRENT.get(); }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, EncounterProfile> compiled = new LinkedHashMap<>();
        resources.forEach((resourceId, element) -> {
            EncounterProfile profile = compile(resourceId, element.getAsJsonObject());
            if (compiled.put(ResourceLocation.parse(profile.id()), profile) != null) throw new IllegalArgumentException("Duplicate encounter profile " + profile.id());
        });
        CURRENT.set(Map.copyOf(compiled));
        PaleMirrorMod.LOGGER.info("Loaded {} Pale Mirror encounter profile(s)", compiled.size());
    }

    private static EncounterProfile compile(ResourceLocation resourceId, JsonObject json) {
        String id = ResourceLocation.parse(requiredString(json, "id", resourceId)).toString();
        int version = requiredInt(json, "version", resourceId);
        List<EncounterProfile.ActorSlot> actors = json.has("actors") ? compileActors(requiredArray(json, "actors", resourceId), resourceId)
                : List.of();
        List<EncounterProfile.Composition> compositions = json.has("compositions")
                ? compileCompositions(requiredArray(json, "compositions", resourceId), resourceId) : List.of();
        return new EncounterProfile(id, version, actors, compositions);
    }

    private static List<EncounterProfile.Composition> compileCompositions(JsonArray rawCompositions, ResourceLocation resourceId) {
        if (rawCompositions.isEmpty()) throw new IllegalArgumentException(resourceId + " must not define an empty compositions array");
        Set<String> ids = new LinkedHashSet<>();
        return rawCompositions.asList().stream().map(value -> {
            JsonObject composition = value.getAsJsonObject();
            String id = requiredString(composition, "id", resourceId);
            if (!id.matches("[a-z0-9_/-]+") || !ids.add(id)) {
                throw new IllegalArgumentException(resourceId + " has invalid or duplicate composition id " + id);
            }
            ThreatTier tier = ThreatTier.valueOf(requiredString(composition, "tier", resourceId));
            int weight = composition.has("weight") ? requiredInt(composition, "weight", resourceId) : 1;
            return new EncounterProfile.Composition(id, tier, weight,
                    compileActors(requiredArray(composition, "actors", resourceId), resourceId));
        }).toList();
    }

    private static List<EncounterProfile.ActorSlot> compileActors(JsonArray rawActors, ResourceLocation resourceId) {
        if (rawActors.size() > MAX_ACTORS) throw new IllegalArgumentException(resourceId + " exceeds " + MAX_ACTORS + " actor slots");
        Set<String> slots = new LinkedHashSet<>();
        return rawActors.asList().stream().map(value -> {
            JsonObject actor = value.getAsJsonObject();
            String slot = requiredString(actor, "slot", resourceId);
            if (!slot.matches("[a-z0-9_/-]+") || !slots.add(slot)) {
                throw new IllegalArgumentException(resourceId + " has invalid or duplicate actor slot " + slot);
            }
            String profile = ResourceLocation.parse(requiredString(actor, "actor_profile", resourceId)).toString();
            ThreatTier minimumTier = actor.has("minimum_tier")
                    ? ThreatTier.valueOf(requiredString(actor, "minimum_tier", resourceId)) : ThreatTier.FOOTHOLD;
            return new EncounterProfile.ActorSlot(slot, profile, minimumTier);
        }).toList();
    }

    private static String requiredString(JsonObject json, String name, ResourceLocation resource) {
        if (!json.has(name) || !json.get(name).isJsonPrimitive()) throw new IllegalArgumentException(resource + " requires string " + name);
        return json.get(name).getAsString();
    }

    private static int requiredInt(JsonObject json, String name, ResourceLocation resource) {
        if (!json.has(name) || !json.get(name).isJsonPrimitive()) throw new IllegalArgumentException(resource + " requires integer " + name);
        return json.get(name).getAsInt();
    }

    private static JsonArray requiredArray(JsonObject json, String name, ResourceLocation resource) {
        if (!json.has(name) || !json.get(name).isJsonArray()) throw new IllegalArgumentException(resource + " requires array " + name);
        return json.getAsJsonArray(name);
    }
}
