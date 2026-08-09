package io.farfrontier.palemirror.internal.content;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.farfrontier.palemirror.domain.WorldObjectId;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import org.slf4j.Logger;

/** Atomic datapack registry for the exact-version Crimson siege profile. */
public final class CrimsonSiegeDefinitions extends SimpleJsonResourceReloadListener {
    public static final CrimsonSiegeDefinitions INSTANCE = new CrimsonSiegeDefinitions();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicReference<Map<ResourceLocation, CrimsonSiegeDefinition>> CURRENT = new AtomicReference<>(Map.of());

    private CrimsonSiegeDefinitions() { super(new Gson(), "pale_mirror/sieges"); }

    public static CrimsonSiegeDefinition defaultDefinition() {
        return CURRENT.get().get(ResourceLocation.fromNamespaceAndPath("pale_mirror", "crimson_apex"));
    }

    public static String selectBoss(CrimsonSiegeDefinition definition, long worldSeed, WorldObjectId siteId, long revision) {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, worldSeed);
        hash = mix(hash, siteId.value().hashCode());
        hash = mix(hash, revision);
        hash = mix(hash, "crimson_apex_boss".hashCode());
        return definition.bossProfiles().get(Math.floorMod((int) (hash ^ (hash >>> 32)), definition.bossProfiles().size()));
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager,
                         net.minecraft.util.profiling.ProfilerFiller profiler) {
        Map<ResourceLocation, CrimsonSiegeDefinition> compiled = new LinkedHashMap<>();
        try {
            for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
                JsonObject json = entry.getValue().getAsJsonObject();
                ResourceLocation id = ResourceLocation.parse(required(json, "id", entry.getKey()));
                int version = json.get("version").getAsInt();
                JsonArray bosses = json.getAsJsonArray("bosses");
                List<String> profiles = bosses.asList().stream().map(JsonElement::getAsString).toList();
                compiled.put(id, new CrimsonSiegeDefinition(id, version, profiles));
            }
            CURRENT.set(Map.copyOf(compiled));
            LOGGER.info("Loaded {} Pale Mirror Crimson siege definition(s)", compiled.size());
        } catch (RuntimeException failure) {
            LOGGER.error("Rejected Pale Mirror Crimson siege reload; retaining prior registry", failure);
        }
    }

    private static String required(JsonObject json, String key, ResourceLocation source) {
        if (!json.has(key) || json.get(key).getAsString().isBlank()) throw new IllegalArgumentException(source + " missing " + key);
        return json.get(key).getAsString();
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001b3L;
    }
}
