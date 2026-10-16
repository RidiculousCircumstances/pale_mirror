package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.WeakHashMap;

/** Change-only diagnostic witnesses; no admission, navigation or recovery authority. */
final class FrontierV3PhysicalWaitTrace {
    private static final Map<Entity, String> WAITS = new WeakHashMap<>();
    private static final Map<net.minecraft.server.level.ServerLevel, Map<SubjectId, String>> ADMISSIONS = new WeakHashMap<>();
    private FrontierV3PhysicalWaitTrace() { }

    static void admission(net.minecraft.server.level.ServerLevel level, SubjectId actor, String gate) {
        var waits = ADMISSIONS.computeIfAbsent(level, unused -> new java.util.LinkedHashMap<>());
        if (gate.equals(waits.get(actor))) return;
        if (waits.size() >= 256 && !waits.containsKey(actor)) waits.remove(waits.keySet().iterator().next());
        waits.put(actor, gate);
        PaleMirrorMod.LOGGER.warn("PMV3_PHYSICAL_WAIT subject={} reason={}", actor, gate);
    }

    static void actor(Entity body, FrontierWorldState state, SubjectId actor, String gate) {
        if (body == null) return;
        var tag = body.getPersistentData();
        String reason = "gate=" + gate + ";ambient=" + state.ambientLeases().get(actor)
                + ";declared=" + FrontierV3ActorOwnerBinding.from(body)
                + ";sceneTag=" + tag.getString(FrontierV3SceneExecutor.LEASE_KEY)
                + ";sceneRevision=" + tag.getLong(FrontierV3SceneExecutor.REVISION_KEY)
                + ";ambientActorTag=" + tag.getString(FrontierV3AmbientActorExecutor.ACTOR_KEY);
        if (body.level() instanceof net.minecraft.server.level.ServerLevel level) {
            var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
            reason += ";inactiveCarrier=" + ledger.hasCarrier(actor)
                    + ";adoption=" + ledger.pendingAdoption(actor)
                    + ";departureConflict=" + ledger.hasDepartureConflict(actor);
        }
        emit(body, actor.value(), reason);
    }

    static void bakery(Entity body, FrontierWorldState state, ProductionJob job, String gate) {
        SubjectId depot = ProductionServiceAccess.point(state, job);
        var boundary = ServiceAccessCoordinator.boundary(state, depot);
        String meals = state.humanPopulation().meals().values().stream()
                .filter(meal -> !meal.portable() && meal.depotId().equals(depot)).sorted(java.util.Comparator.comparing(ResidentMeal::residentId))
                .limit(8).map(meal -> meal.residentId().value() + ":" + meal.phase() + ":occupies="
                    + boundary.occupied(state.actorLocations().get(meal.residentId()).body())
                    + ":ambient=" + state.ambientLeases().get(meal.residentId()))
                .collect(java.util.stream.Collectors.joining("|"));
        emit(body, job.id().value(), "gate=" + gate + ";phase=" + job.bakeryWork().orElseThrow().phase()
                + ";servicePoint=" + depot.value() + ";meals=" + meals);
    }

    static void clear(Entity body) { WAITS.remove(body); }

    static void reconciliation(Entity body, SceneLease lease, String reason) {
        emit(body, lease.id().value(), "reconciliation=" + reason + ";sceneRevision=" + lease.revision());
    }

    private static void emit(Entity body, String subject, String reason) {
        if (!reason.equals(WAITS.put(body, reason))) PaleMirrorMod.LOGGER.warn(
                "PMV3_PHYSICAL_WAIT subject={} entity={} position={} reason={}", subject,
                body.getUUID(), body.position(), reason);
    }
}
