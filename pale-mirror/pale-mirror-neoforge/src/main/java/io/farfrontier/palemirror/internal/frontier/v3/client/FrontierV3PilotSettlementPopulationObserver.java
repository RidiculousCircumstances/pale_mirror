package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One bounded normal-client census for a settlement's first visible turn.
 *
 * <p>The only server input is the already received, pre-visit population receipt. It supplies
 * each canonical resident's deterministic UUID and retained station. This observer then finds
 * those UUIDs exclusively in the local client entity set; it cannot choose, move, create, or
 * query a body after the visit begins. That makes a generic shared spawn row observable rather
 * than allowing a later server diagnostic to rationalize it.</p>
 */
final class FrontierV3PilotSettlementPopulationObserver {
    private static long actionStartedTick = Long.MIN_VALUE;
    private static long elapsedClientTicks;
    private static Map<UUID, Expected> expected = Map.of();
    private static Map<UUID, Observation> observations = Map.of();
    private static long completeSince = Long.MIN_VALUE;

    private FrontierV3PilotSettlementPopulationObserver() { }

    static boolean observe(Minecraft minecraft, JsonObject action, long startedTick, int actionStep, String milestone) {
        if (actionStartedTick != startedTick) {
            reset(); actionStartedTick = startedTick;
            expected = expectedPopulation(action);
            observations = new LinkedHashMap<>();
        }
        elapsedClientTicks++;
        if (expected.isEmpty()) return timeout(minecraft, action, "first-ingress population lacks one complete pre-visit settlement receipt");
        double maximum = action.has("maxDistance") ? action.get("maxDistance").getAsDouble() : 128.0D;
        Map<UUID, Entity> local = localResidents(minecraft, maximum);
        for (Map.Entry<UUID, Expected> entry : expected.entrySet()) {
            Entity body = local.get(entry.getKey());
            if (body != null) {
                Observation prior = observations.putIfAbsent(entry.getKey(), Observation.first(body));
                if (prior != null) prior.sample(body);
            }
        }
        if (observations.size() != expected.size()) return timeout(minecraft, action,
                "ordinary first ingress did not render every exact settlement resident; expected=" + expected.size() + " observed=" + observations.size());
        if (completeSince == Long.MIN_VALUE) completeSince = elapsedClientTicks;
        if (elapsedClientTicks - completeSince < action.get("durationTicks").getAsLong()) return false;
        emit(action, actionStep, milestone); return true;
    }

    static void reset() {
        actionStartedTick = Long.MIN_VALUE; elapsedClientTicks = 0L; expected = Map.of(); observations = Map.of(); completeSince = Long.MIN_VALUE;
    }

    private static Map<UUID, Expected> expectedPopulation(JsonObject action) {
        String settlement = action.get("settlementId").getAsString();
        FrontierV3TestPilotClient.ObservedDiagnostic diagnostic = FrontierV3TestPilotClient.diagnostics.get(
                new FrontierV3TestPilotClient.DiagnosticIdentity("settlement_population", settlement));
        if (diagnostic == null || !diagnostic.value().has("status") || !"ok".equals(diagnostic.value().get("status").getAsString())
                || !diagnostic.value().has("residents") || !diagnostic.value().get("residents").isJsonArray()) return Map.of();
        JsonArray values = diagnostic.value().getAsJsonArray("residents");
        if (values.size() < 20 || values.size() > 40 || !diagnostic.value().has("residentCount")
                || diagnostic.value().get("residentCount").getAsInt() != values.size()) return Map.of();
        Map<UUID, Expected> parsed = new LinkedHashMap<>();
        for (var value : values) {
            if (!value.isJsonObject()) return Map.of(); JsonObject resident = value.getAsJsonObject();
            if (!resident.has("actor") || !resident.has("entityUuid") || !resident.has("position") || !resident.has("dutyPhase")) return Map.of();
            try {
                UUID uuid = UUID.fromString(resident.get("entityUuid").getAsString()); JsonObject position = resident.getAsJsonObject("position");
                Expected next = new Expected(resident.get("actor").getAsString(), uuid, new Vec3(position.get("x").getAsDouble() + .5D,
                        position.get("y").getAsDouble(), position.get("z").getAsDouble() + .5D), resident.get("dutyPhase").getAsString());
                if (!next.actorId().startsWith("resident:") || parsed.putIfAbsent(uuid, next) != null) return Map.of();
            } catch (IllegalArgumentException | NullPointerException malformed) { return Map.of(); }
        }
        return Map.copyOf(parsed);
    }

    private static Map<UUID, Entity> localResidents(Minecraft minecraft, double maximum) {
        if (minecraft.level == null || minecraft.player == null) return Map.of();
        ResourceLocation villager = ResourceLocation.withDefaultNamespace("villager");
        Map<UUID, Entity> visible = new LinkedHashMap<>();
        minecraft.level.getEntitiesOfClass(Entity.class, minecraft.player.getBoundingBox().inflate(maximum), entity ->
                !entity.isRemoved() && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(villager) && expected.containsKey(entity.getUUID()))
                .forEach(entity -> visible.put(entity.getUUID(), entity));
        return visible;
    }

    private static boolean timeout(Minecraft minecraft, JsonObject action, String detail) {
        if (elapsedClientTicks * 50L >= action.get("timeoutMs").getAsLong()) throw new IllegalStateException(detail);
        return false;
    }

    private static void emit(JsonObject action, int actionStep, String milestone) {
        JsonObject value = new JsonObject(); value.addProperty("kind", "pilot_settlement_population");
        value.addProperty("id", action.get("settlementId").getAsString()); value.addProperty("status", "ok");
        value.addProperty("residentCount", expected.size()); value.addProperty("durationTicks", action.get("durationTicks").getAsLong());
        JsonArray residents = new JsonArray(); expected.values().stream().sorted(Comparator.comparing(Expected::actorId)).forEach(expectedResident -> {
            Observation observed = observations.get(expectedResident.uuid()); JsonObject row = new JsonObject();
            row.addProperty("actor", expectedResident.actorId()); row.addProperty("entityUuid", expectedResident.uuid().toString());
            row.addProperty("dutyPhase", expectedResident.dutyPhase()); row.add("first", position(observed.first()));
            row.addProperty("samples", observed.samples()); row.addProperty("maxDisplacement", observed.maxDisplacement());
            row.addProperty("maxStep", observed.maxStep()); residents.add(row);
        });
        value.add("residents", residents); FrontierV3PilotSessionControl.stampDiagnostic(value, actionStep, milestone);
        PaleMirrorMod.LOGGER.info("PMV3_PILOT_DIAGNOSTIC {}", value);
    }

    private static JsonObject position(Vec3 value) {
        JsonObject result = new JsonObject(); result.addProperty("x", value.x); result.addProperty("y", value.y); result.addProperty("z", value.z); return result;
    }

    private record Expected(String actorId, UUID uuid, Vec3 station, String dutyPhase) { }
    private static final class Observation {
        private final Vec3 first; private Vec3 previous; private int samples; private double maxDisplacement; private double maxStep;
        private Observation(Vec3 first) { this.first = first; previous = first; samples = 1; }
        static Observation first(Entity body) { return new Observation(body.position()); }
        void sample(Entity body) { Vec3 current = body.position(); maxDisplacement = Math.max(maxDisplacement, horizontal(first, current));
            maxStep = Math.max(maxStep, horizontal(previous, current)); previous = current; samples++; }
        Vec3 first() { return first; } int samples() { return samples; } double maxDisplacement() { return maxDisplacement; } double maxStep() { return maxStep; }
    }
    private static double horizontal(Vec3 left, Vec3 right) { return Math.hypot(left.x - right.x, left.z - right.z); }
}
