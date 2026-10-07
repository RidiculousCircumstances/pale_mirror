package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.Objects;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle.*;

/** Read-only server diagnostic assembly from one active runtime snapshot. */
final class FrontierV3ServerDiagnostic {
    private FrontierV3ServerDiagnostic() { }

    static String render(MinecraftServer server, FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime, String view, String id) {
        Objects.requireNonNull(server, "server"); Objects.requireNonNull(view, "view"); Objects.requireNonNull(id, "id");
        if (!"execution".equals(view) && !FrontierV3DiagnosticView.accepts(view, id)) {
            return FrontierV3DiagnosticJson.unavailableRuntime(view, id);
        }
        if (!ownsPhysicalWorld(server) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) {
            return FrontierV3DiagnosticJson.unavailableRuntime(view, id);
        }
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow();
        if ("execution".equals(view)) return FrontierV3PhysicalExecutionDiagnostic.render(checkpoint);
        if ("first_visibility".equals(view)) return FrontierV3HotHandoffDiagnostic.render(
                FrontierV3PhysicalWorld.require(server), runtime, id, checkpoint);
        if ("status".equals(view)) return FrontierV3DiagnosticJson.operatorStatus(checkpoint, runtime.decodedState().orElseThrow(),
                fastForwardRequests(server), id);
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        if ("process".equals(view)) return FrontierV3DiagnosticJson.bounded(view, id, checkpoint,
                FrontierV3ProcessDiagnosticJson.render(id, checkpoint, state,
                    FrontierV3ActorPositionView.observed(FrontierV3PhysicalWorld.require(server), state, checkpoint.instant().ticks()), "OBSERVED_HOT_OR_CANONICAL_COLD",
                    movement -> FrontierV3ActorMovementNavigation.waitReason(FrontierV3PhysicalWorld.require(server), state, movement)));
        if ("field_physical".equals(view)) return FrontierV3DiagnosticJson.bounded(view, id, checkpoint,
                FrontierV3ResourceFieldPhysicalDiagnostic.render(checkpoint, state,
                        FrontierV3ResourceSiteLedger.get(FrontierV3PhysicalWorld.require(server)), id,
                        FrontierV3PhysicalWorld.require(server), runtime));
        if ("settlement_population".equals(view)) {
            try {
                io.farfrontier.palemirror.frontier.v3.api.SubjectId settlementId = new io.farfrontier.palemirror.frontier.v3.api.SubjectId(id);
                java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, FrontierV3AmbientAdmissionDiagnostic> admissions =
                        new java.util.LinkedHashMap<>();
                net.minecraft.server.level.ServerLevel level = FrontierV3PhysicalWorld.require(server);
                state.humanPopulation().residents().values().stream()
                        .filter(resident -> resident.settlementId().equals(settlementId))
                        .sorted(java.util.Comparator.comparing(resident -> resident.id().value()))
                        .forEach(resident -> admissions.put(resident.id(), FrontierV3AmbientActorExecutor.admissionDiagnostic(
                                level, runtime, state, resident.id())));
                return FrontierV3DiagnosticJson.settlementPopulation(checkpoint, state, id, admissions);
            } catch (IllegalArgumentException ignored) {
                return FrontierV3DiagnosticJson.settlementPopulation(checkpoint, state, id, java.util.Map.of());
            }
        }
        if ("performance".equals(view)) return FrontierV3ServerLifecycle.performanceDiagnostic(server, runtime, checkpoint, state);
        if ("projection_work".equals(view)) return FrontierV3ProjectionWorkDiagnostic.render(checkpoint, runtime);
        if ("player_resource".equals(view)) return FrontierV3PlayerResourceDiagnostic.render(checkpoint, state, server, id);
        if ("traversal_foundry".equals(view)) return FrontierV3TraversalFoundryDiagnostic.render(checkpoint, state,
                FrontierV3PhysicalWorld.require(server), id);
        if ("hive_foundry".equals(view)) return FrontierV3HiveFoundryDiagnostic.render(checkpoint, state,
                FrontierV3PhysicalWorld.require(server), id);
        if ("hive_mobilization".equals(view)) return FrontierV3HiveMobilizationDiagnostic.render(checkpoint, state,
                FrontierV3PhysicalWorld.require(server), id);
        java.util.Optional<FrontierV3AmbientAdmissionDiagnostic> admission = java.util.Optional.empty();
        if ("actor".equals(view)) {
            try {
                admission = java.util.Optional.of(FrontierV3AmbientActorExecutor.admissionDiagnostic(
                        FrontierV3PhysicalWorld.require(server), runtime, state,
                        new io.farfrontier.palemirror.frontier.v3.api.SubjectId(id)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        java.util.Optional<FrontierV3ResourceSiteHarvestExecutor.Readiness> harvestReadiness = java.util.Optional.empty();
        if ("intent".equals(view)) {
            try {
                harvestReadiness = FrontierV3ResourceSiteHarvestExecutor.readiness(
                        FrontierV3PhysicalWorld.require(server), state,
                        new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(id));
            } catch (IllegalArgumentException ignored) {
            }
        }
        java.util.Optional<FrontierV3SceneReadiness.Value> sceneReadiness = java.util.Optional.empty();
        if ("scene".equals(view)) {
            try {
                sceneReadiness = FrontierV3SceneReadiness.forSubject(FrontierV3PhysicalWorld.require(server), state,
                        new io.farfrontier.palemirror.frontier.v3.api.SubjectId(id));
            } catch (IllegalArgumentException ignored) {
            }
        }
        java.util.Optional<FrontierV3ContainerSurfaceExecutor.Readiness> containerReadiness = java.util.Optional.empty();
        if ("container".equals(view)) {
            try {
                containerReadiness = java.util.Optional.of(FrontierV3ContainerSurfaceExecutor.readiness(
                        FrontierV3PhysicalWorld.require(server), state, new io.farfrontier.palemirror.frontier.v3.api.SubjectId(id)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        java.util.Optional<FrontierV3EquipmentIssueExecutor.Readiness> equipmentIssueReadiness = java.util.Optional.empty();
        java.util.Optional<FrontierV3EquipmentReturnExecutor.Readiness> equipmentReturnReadiness = java.util.Optional.empty();
        if ("intent".equals(view)) {
            try {
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent = state.physicalIntents().get(
                        new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(id));
                if (intent != null && intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_ISSUE) {
                    equipmentIssueReadiness = java.util.Optional.of(FrontierV3EquipmentIssueExecutor.readinessDetail(
                            FrontierV3PhysicalWorld.require(server), state, intent));
                } else if (intent != null && intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_RETURN) {
                    equipmentReturnReadiness = java.util.Optional.of(FrontierV3EquipmentReturnExecutor.readinessDetail(
                            FrontierV3PhysicalWorld.require(server), state, intent));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return FrontierV3DiagnosticJson.render(view, id, checkpoint, state,
                "trace".equals(view) ? FrontierV3DiagnosticTrace.latest(server, id) : java.util.Optional.empty(), admission, harvestReadiness, sceneReadiness,
                containerReadiness, equipmentIssueReadiness, equipmentReturnReadiness);
    }
}
