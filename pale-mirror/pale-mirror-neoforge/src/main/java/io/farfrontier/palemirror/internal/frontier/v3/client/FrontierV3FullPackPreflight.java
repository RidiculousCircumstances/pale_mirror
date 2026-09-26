package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import java.util.Set;
import java.util.TreeSet;

/** Fails the opt-in full-pack pilot before Quick Play when required client mods are absent. */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class FrontierV3FullPackPreflight {
    private static final String REQUIRED_MODS_PROPERTY = "pale_mirror.frontier_v3.test_pilot.required_mods";
    private FrontierV3FullPackPreflight() { }


    @SubscribeEvent
    public static void verifyRequiredModsBeforeQuickPlay(FMLClientSetupEvent event) {
        String configured = System.getProperty(REQUIRED_MODS_PROPERTY, "");
        if (configured.isBlank()) return;
        Set<String> required = new TreeSet<>();
        for (String id : configured.split(",")) {
            String trimmed = id.trim();
            if (trimmed.isEmpty() || !trimmed.matches("[a-z][a-z0-9_-]*")) {
                throw new IllegalStateException("Malformed full-pack required mod ID " + id);
            }
            required.add(trimmed);
        }
        if (required.isEmpty()) throw new IllegalStateException("Full-pack required mod inventory is empty");
        Set<String> loaded = new TreeSet<>();
        ModList.get().getMods().forEach(info -> loaded.add(info.getModId()));
        Set<String> missing = new TreeSet<>(required);
        missing.removeAll(loaded);
        JsonObject inventory = new JsonObject();
        inventory.addProperty("status", missing.isEmpty() ? "PASS" : "REJECTED");
        inventory.add("required", stringArray(required));
        inventory.add("loaded", stringArray(loaded));
        inventory.add("missing", stringArray(missing));
        PaleMirrorMod.LOGGER.info("PMV3_PILOT_LOADED_MODS {}", inventory);
        if (!missing.isEmpty()) throw new IllegalStateException("Full-pack client is missing required mods " + missing);
    }
private static JsonArray stringArray(Iterable<String> values) {
    JsonArray result = new JsonArray();
    values.forEach(result::add);
    return result;
}
}
