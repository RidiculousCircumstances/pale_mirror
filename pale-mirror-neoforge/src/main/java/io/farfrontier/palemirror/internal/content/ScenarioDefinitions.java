package io.farfrontier.palemirror.internal.content;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.domain.ScenarioArchetype;
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

/** Reload validates a complete candidate map before atomically exposing it. */
public final class ScenarioDefinitions extends SimpleJsonResourceReloadListener {
    public static final ScenarioDefinitions INSTANCE = new ScenarioDefinitions();
    private static final AtomicReference<Map<ResourceLocation, ScenarioDefinition>> CURRENT = new AtomicReference<>(Map.of());

    private ScenarioDefinitions() {
        super(new Gson(), "pale_mirror/scenarios");
    }

    public static Map<ResourceLocation, ScenarioDefinition> current() { return CURRENT.get(); }
    public static List<ScenarioDefinition> forSource(InfectionSourceId source) {
        return CURRENT.get().values().stream().filter(value -> value.infectionSource().equals(source))
                .sorted(java.util.Comparator.comparing(value -> value.id().toString())).toList();
    }
    public static List<ScenarioDefinition> forSourceAndArchetype(InfectionSourceId source, ScenarioArchetype archetype) {
        return CURRENT.get().values().stream().filter(value -> value.infectionSource().equals(source) && value.archetype() == archetype)
                .sorted(java.util.Comparator.comparing(value -> value.id().toString())).toList();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, ScenarioDefinition> compiled = new LinkedHashMap<>();
        resources.forEach((resourceId, element) -> {
            ScenarioDefinition definition = compile(resourceId, element.getAsJsonObject());
            if (compiled.put(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate scenario definition " + definition.id());
            }
        });
        CURRENT.set(Map.copyOf(compiled));
        PaleMirrorMod.LOGGER.info("Loaded {} Pale Mirror scenario definition(s)", compiled.size());
    }

    private static ScenarioDefinition compile(ResourceLocation resourceId, JsonObject json) {
        ResourceLocation id = ResourceLocation.parse(requiredString(json, "id", resourceId));
        InfectionSourceId source = new InfectionSourceId(requiredString(json, "infection_source", resourceId));
        int version = requiredInt(json, "version", resourceId);
        if (version < 1) throw new IllegalArgumentException(resourceId + " has invalid version");
        String policy = requiredString(json, "policy", resourceId);
        ScenarioArchetype archetype = ScenarioArchetype.parse(optionalRawString(json, "archetype", resourceId, "investigation_recovery"));
        String encounterProfile = optionalString(json, "encounter_profile", resourceId);
        int cooldownSteps = requiredInt(json, "cooldown_steps", resourceId);
        if (cooldownSteps < 0) throw new IllegalArgumentException(resourceId + " has negative cooldown_steps");
        JsonArray rawStages = requiredArray(json, "stages", resourceId);
        List<String> stages = rawStages.asList().stream().map(JsonElement::getAsString).toList();
        List<String> requiredStages = archetype == ScenarioArchetype.INVESTIGATION_RECOVERY
                ? List.of("OFFERED", "INVESTIGATE", "RECOVER", "RESOLVED")
                : List.of("OFFERED", "ASSESS", "RESPOND", "RESOLVED");
        if (!stages.containsAll(requiredStages)) {
            throw new IllegalArgumentException(resourceId + " does not define stages for " + archetype);
        }
        Set<Capability> capabilities = new LinkedHashSet<>();
        for (JsonElement capability : requiredArray(json, "required_capabilities", resourceId)) {
            try { capabilities.add(Capability.valueOf(capability.getAsString())); }
            catch (IllegalArgumentException failure) { throw new IllegalArgumentException(resourceId + " declares unknown capability " + capability, failure); }
        }
        return new ScenarioDefinition(id, source, version, Set.copyOf(capabilities), List.copyOf(stages), policy, cooldownSteps,
                encounterProfile, archetype);
    }

    private static String requiredString(JsonObject json, String name, ResourceLocation resource) {
        if (!json.has(name) || !json.get(name).isJsonPrimitive()) throw new IllegalArgumentException(resource + " requires string " + name);
        return json.get(name).getAsString();
    }

    private static String optionalString(JsonObject json, String name, ResourceLocation resource) {
        return optionalString(json, name, resource, "");
    }

    private static String optionalString(JsonObject json, String name, ResourceLocation resource, String defaultValue) {
        if (!json.has(name)) return defaultValue;
        if (!json.get(name).isJsonPrimitive()) throw new IllegalArgumentException(resource + " requires string " + name);
        return ResourceLocation.parse(json.get(name).getAsString()).toString();
    }

    private static String optionalRawString(JsonObject json, String name, ResourceLocation resource, String defaultValue) {
        if (!json.has(name)) return defaultValue;
        if (!json.get(name).isJsonPrimitive()) throw new IllegalArgumentException(resource + " requires string " + name);
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
