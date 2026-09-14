package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded read-only local pose series for a named client-rendered retained body. */
final class FrontierV3PilotMotionObserver {
    private static long actionStartedTick = Long.MIN_VALUE;
    private static int entityRuntimeId = -1;
    private static List<Pose> samples = List.of();
    private static Set<Long> sampledTicks = Set.of();
    /** Monotonic client turns expose a player-visible server freeze even while game time stops. */
    private static long elapsedClientTicks;

    private FrontierV3PilotMotionObserver() { }

    static boolean observe(Minecraft minecraft, JsonObject action, long startedTick, int actionStep, String milestone) {
        if (actionStartedTick != startedTick) { reset(); actionStartedTick = startedTick; }
        long clientTick = elapsedClientTicks++;
        ResourceLocation expectedType = ResourceLocation.parse(action.get("entityType").getAsString());
        // Once this bounded action has selected its exact rendered body, a terminal receipt may
        // legitimately retire the process diagnostic. Keep observing that same runtime body so
        // the carrier can prove that completion/release did not silently despawn and recreate
        // the worker between two diagnostic polls.
        String expectedName = entityRuntimeId < 0 ? expectedName(action) : "";
        if (expectedName == null) {
            if (clientTick * 50L >= action.get("timeoutMs").getAsLong()) {
                throw new IllegalStateException("motion observation lacks the exact worker presentation diagnostic: " + action.get("id").getAsString());
            }
            return false;
        }
        double maximum = action.has("maxDistance") ? action.get("maxDistance").getAsDouble() : 64.0D;
        Vec3 anchor = action.has("anchor") ? position(action.getAsJsonObject("anchor")) : null;
        Entity body = entityRuntimeId < 0
                ? (anchor != null ? nearest(minecraft, expectedType, anchor, maximum)
                : expectedName.isBlank() ? nearest(minecraft, expectedType, minecraft.player.position(), maximum)
                : FrontierV3TestPilotPresentation.nearestVisibleNamedEntity(minecraft, expectedType, expectedName, maximum))
                : minecraft.level.getEntity(entityRuntimeId);
        if (!matches(body, expectedType, expectedName)) {
            if (clientTick * 50L >= action.get("timeoutMs").getAsLong()) {
                throw new IllegalStateException("local named motion body was unavailable: " + expectedType + " " + expectedName);
            }
            return false;
        }
        if (entityRuntimeId < 0) { entityRuntimeId = body.getId(); samples = new ArrayList<>(); sampledTicks = new HashSet<>(); }
        int interval = action.get("sampleEveryTicks").getAsInt();
        long gameTime = minecraft.level.getGameTime();
        // The declarative action caps this collection at 12,000 ticks.  Keep the complete
        // requested bounded window: truncating it at 256 could make a station-only prefix look
        // like evidence for the later retained travel and work phases.
        if (clientTick % interval == 0L && samples.size() < action.get("durationTicks").getAsInt() && sampledTicks.add(clientTick)) {
            // Read the already-delivered process result and periodically renew it through the
            // same ordinary player diagnostic path.  A rendered pose alone must never be
            // mistaken for a semantic work duty.
            if (clientTick % 20L == 0L) minecraft.player.connection.sendCommand("pale_mirror v3 inspect process " + action.get("id").getAsString());
            // This is a bounded diagnostic correlation, not a second pose source: it answers
            // whether a visually held remote body was also held by the authoritative server.
            // The actor receipt pairs a read-only authoritative position with the rendered one.
            // It shares the process receipt's bounded cadence: a motion carrier must not turn
            // diagnostic polling itself into a high-frequency scene-admission workload.
            String worker = workerId(action);
            if (worker != null && clientTick % 20L == 0L) minecraft.player.connection.sendCommand("pale_mirror v3 inspect actor " + worker);
            // The received swing event starts before the interpolated attack amount becomes
            // positive on some client turns.  Both fields are ordinary client-rendered state;
            // observing either preserves the distinction between a declared work dwell and a
            // frozen body without inventing a presentation signal.
            boolean workAnimation = body instanceof LivingEntity living && (living.swinging || living.getAttackAnim(1.0F) > 0.0F);
            // This is deliberately an observed client value, paired with the rendered body
            // position.  It lets the native carrier distinguish a frozen authoritative body
            // from a body which has a delivered interpolation/velocity but is not rendering it;
            // it is never an alternative motion oracle.
            Vec3 velocity = body.getDeltaMovement();
            Vec3 authoritative = authoritativePosition(action);
            samples.add(new Pose(clientTick, gameTime, body.getX(), body.getY(), body.getZ(), velocity.x, velocity.z,
                    authoritative == null ? null : authoritative.x, authoritative == null ? null : authoritative.z,
                    workAnimation, semanticPhase(action, body.getId())));
        }
        if (clientTick < action.get("durationTicks").getAsLong()) return false;
        emit(action, expectedType, interval, actionStep, milestone); return true;
    }

    static void reset() { actionStartedTick = Long.MIN_VALUE; entityRuntimeId = -1; samples = List.of(); sampledTicks = Set.of(); elapsedClientTicks = 0L; }

    /** Reads only a diagnostic the normal client packet handler already retained. */
    private static JsonObject receivedDiagnostic(String kind, String id) {
        FrontierV3TestPilotClient.ObservedDiagnostic observed = FrontierV3TestPilotClient.diagnostics.get(new FrontierV3TestPilotClient.DiagnosticIdentity(kind, id));
        return observed == null ? null : observed.value();
    }

    /** Never fall back to nearest-of-type: a motion receipt belongs to the current exact lease worker. */
    private static String expectedName(JsonObject action) {
        if (action.has("nameContains")) return action.get("nameContains").getAsString();
        JsonObject process = receivedDiagnostic("process", action.get("id").getAsString());
        if (process == null || !process.has("identity") || !process.get("identity").isJsonObject()) return null;
        JsonObject identity = process.getAsJsonObject("identity");
        return identity.has("workerPresentation") && identity.get("workerPresentation").isJsonPrimitive()
                ? identity.get("workerPresentation").getAsString() : null;
    }
    private static String semanticPhase(JsonObject action, int bodyRuntimeId) {
        // A station gesture is delivered by the server at PREPARED -> RUNNING and carries its
        // exact typed duty. Prefer it only for its six-tick bounded lifetime: unlike the
        // ordinary 20-tick inspect cadence, this keeps a visible interaction from being
        // labelled with a stale pre-arrival TRAVELLING receipt.
        String activeCue = io.farfrontier.palemirror.internal.client.PaleMirrorStationWorkGestureClient.observedDutyPhase(bodyRuntimeId);
        if (activeCue != null) return activeCue;
        JsonObject process = receivedDiagnostic("process", action.get("id").getAsString());
        if (process == null || !process.has("result") || !process.get("result").isJsonObject()) return "UNOBSERVED";
        JsonObject result = process.getAsJsonObject("result");
        if (result.has("dutyPhase") && result.get("dutyPhase").isJsonPrimitive()) return result.get("dutyPhase").getAsString();
        if (!result.has("sitePhase") || !result.has("intentStatus")) return "UNOBSERVED";
        if (process.has("cursor") && process.get("cursor").isJsonObject()) {
            JsonObject cursor = process.getAsJsonObject("cursor");
            if (cursor.has("index") && cursor.has("length") && cursor.get("index").getAsInt() < cursor.get("length").getAsInt() - 1) {
                return "TRAVELLING:" + result.get("intentStatus").getAsString();
            }
        }
        return "HARVESTING:" + result.get("intentStatus").getAsString();
    }
    private static String workerId(JsonObject action) {
        JsonObject process = receivedDiagnostic("process", action.get("id").getAsString());
        if (process == null || !process.has("identity") || !process.get("identity").isJsonObject()) return null;
        JsonObject identity = process.getAsJsonObject("identity");
        return identity.has("worker") && identity.get("worker").isJsonPrimitive() ? identity.get("worker").getAsString() : null;
    }
    private static Vec3 authoritativePosition(JsonObject action) {
        String worker = workerId(action);
        JsonObject actor = worker == null ? null : receivedDiagnostic("actor", worker);
        if (actor == null || !actor.has("physicalAdmission") || !actor.get("physicalAdmission").isJsonObject()) return null;
        JsonObject admission = actor.getAsJsonObject("physicalAdmission");
        if (!admission.has("observedExact") || !admission.get("observedExact").isJsonObject()) return null;
        JsonObject observed = admission.getAsJsonObject("observedExact");
        return observed.has("x") && observed.has("y") && observed.has("z")
                ? new Vec3(observed.get("x").getAsDouble(), observed.get("y").getAsDouble(), observed.get("z").getAsDouble()) : null;
    }

    private static boolean matches(Entity body, ResourceLocation expectedType, String expectedName) {
        return body != null && !body.isRemoved() && BuiltInRegistries.ENTITY_TYPE.getKey(body.getType()).equals(expectedType)
                && (expectedName.isBlank() || body.getCustomName() != null && body.getCustomName().getString().contains(expectedName));
    }
    private static Entity nearest(Minecraft minecraft, ResourceLocation expectedType, Vec3 anchor, double maximum) {
        return minecraft.level.getEntitiesOfClass(Entity.class, new net.minecraft.world.phys.AABB(anchor, anchor).inflate(maximum), entity ->
                        !entity.isRemoved() && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(expectedType)
                                && minecraft.player.hasLineOfSight(entity))
                .stream().sorted(java.util.Comparator.comparingDouble((Entity entity) -> entity.distanceToSqr(anchor)).thenComparing(Entity::getUUID))
                .findFirst().orElse(null);
    }
    private static Vec3 position(JsonObject value) { return new Vec3(value.get("x").getAsDouble(), value.get("y").getAsDouble(), value.get("z").getAsDouble()); }
    private static void emit(JsonObject action, ResourceLocation type, int interval, int actionStep, String milestone) {
        JsonObject value = new JsonObject(); value.addProperty("kind", "pilot_motion"); value.addProperty("id", action.get("id").getAsString());
        value.addProperty("status", "ok"); value.addProperty("entityType", type.toString()); value.addProperty("sampleEveryTicks", interval);
        value.addProperty("durationTicks", action.get("durationTicks").getAsLong()); value.addProperty("entityRuntimeId", entityRuntimeId);
        Entity retained = minecraftEntity();
        if (retained != null) value.addProperty("entityUuid", retained.getUUID().toString());
        JsonArray values = new JsonArray(); for (Pose pose : samples) { JsonObject sample = new JsonObject(); sample.addProperty("tick", pose.tick()); sample.addProperty("serverTick", pose.serverTick());
            sample.addProperty("x", pose.x()); sample.addProperty("y", pose.y()); sample.addProperty("z", pose.z());
            sample.addProperty("velocityX", pose.velocityX()); sample.addProperty("velocityZ", pose.velocityZ()); sample.addProperty("workAnimation", pose.workAnimation());
            if (pose.authoritativeX() != null) { sample.addProperty("authoritativeX", pose.authoritativeX()); sample.addProperty("authoritativeZ", pose.authoritativeZ()); }
            sample.addProperty("semanticPhase", pose.semanticPhase()); values.add(sample); }
        value.add("samples", values); FrontierV3PilotSessionControl.stampDiagnostic(value, actionStep, milestone);
        PaleMirrorMod.LOGGER.info("PMV3_PILOT_DIAGNOSTIC {}", value);
    }
    private static Entity minecraftEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null || entityRuntimeId < 0 ? null : minecraft.level.getEntity(entityRuntimeId);
    }
    private record Pose(long tick, long serverTick, double x, double y, double z, double velocityX, double velocityZ,
                        Double authoritativeX, Double authoritativeZ, boolean workAnimation, String semanticPhase) { }
}
