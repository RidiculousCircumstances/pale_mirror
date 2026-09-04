package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.server.level.ServerLevel;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Closed NeoForge behavior registry for scene-local materialization.
 *
 * <p>The ordering is deterministic admission policy, not a global scene lock. Every registered
 * behavior receives one bounded turn each server tick, so an unrelated or unloaded assault can
 * never starve an engineering worksite in another naturally loaded area. Actor admission remains
 * exclusive in canonical state. Generic lifecycle code has no concrete cause tests and never
 * calls a sibling executor directly.</p>
 */
final class FrontierV3SceneBehaviorRegistry {
    private static final FrontierV3SceneBehaviorRegistry CURRENT = new FrontierV3SceneBehaviorRegistry(List.of(
            new Behavior(SceneCauseKind.SETTLEMENT_ASSAULT, FrontierV3SettlementAssaultSceneExecutor::tick,
                    lease -> FrontierSceneBehaviors.settlementAssault(lease).assaultId(), false),
            new Behavior(SceneCauseKind.ENGINEERING_WORKSITE, FrontierV3EngineeringWorkSceneExecutor::tick,
                    lease -> null, false),
            new Behavior(SceneCauseKind.MEDICAL_TREATMENT, FrontierV3MedicalTreatmentSceneExecutor::tick,
                    lease -> null, false),
            new Behavior(SceneCauseKind.RESOURCE_SITE_HARVEST, FrontierV3ResourceSiteHarvestSceneExecutor::tick,
                    lease -> null, false),
            new Behavior(SceneCauseKind.PRODUCTION_WORK, FrontierV3ProductionWorkSceneExecutor::tick,
                    lease -> null, false),
            new Behavior(SceneCauseKind.SERVICE_WORK, FrontierV3SettlementServiceWorkSceneExecutor::tick,
                    lease -> null, false),
            new Behavior(SceneCauseKind.ROUTE_PATROL, FrontierV3RoutePatrolSceneExecutor::tick,
                    lease -> null, false),
            new Behavior(SceneCauseKind.LOGISTICS, FrontierV3SceneExecutor::tickLogistics,
                    lease -> FrontierSceneBehaviors.logistics(lease).engagementId().isPresent() ? FrontierSceneBehaviors.logistics(lease).operationId() : null, true)));

    private final List<Behavior> ordered;

    FrontierV3SceneBehaviorRegistry(List<Behavior> registrations) {
        Objects.requireNonNull(registrations, "scene behavior registrations");
        EnumMap<SceneCauseKind, Behavior> byKind = new EnumMap<>(SceneCauseKind.class);
        for (Behavior registration : registrations) {
            Objects.requireNonNull(registration, "scene behavior");
            if (byKind.putIfAbsent(registration.kind(), registration) != null) {
                throw new IllegalArgumentException("duplicate NeoForge scene behavior: " + registration.kind());
            }
        }
        FrontierV3SceneBehaviorRegistration.requireCompleteKinds(List.copyOf(byKind.keySet()));
        this.ordered = List.copyOf(registrations);
    }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state != null) FrontierV3SceneExecutor.cleanClosedBodies(level, state);
        CURRENT.tickAll(level, runtime);
    }

    /** Every registered scene family gets one ordered bounded turn; return values are local only. */
    void tickAll(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        runBoundedTurns(ordered.stream().<Runnable>map(behavior -> () -> behavior.tick().tick(level, runtime)).toList());
    }

    static void runBoundedTurns(List<? extends Runnable> turns) {
        for (Runnable turn : turns) turn.run();
    }

    static SubjectId strikeCause(SceneLease lease) {
        for (Behavior behavior : CURRENT.ordered) {
            if (behavior.kind() == lease.cause().kind()) return behavior.strikeCause().apply(lease);
        }
        throw new IllegalStateException("unregistered NeoForge scene cause: " + lease.cause().kind());
    }

    static boolean hasCargoCarrier(SceneLease lease) {
        for (Behavior behavior : CURRENT.ordered) {
            if (behavior.kind() == lease.cause().kind()) return behavior.hasCargoCarrier();
        }
        throw new IllegalStateException("unregistered NeoForge scene cause: " + lease.cause().kind());
    }

    record Behavior(SceneCauseKind kind, Tick tick, Function<SceneLease, SubjectId> strikeCause, boolean hasCargoCarrier) {
        Behavior { Objects.requireNonNull(kind, "scene kind"); Objects.requireNonNull(tick, "scene tick"); Objects.requireNonNull(strikeCause, "scene strike cause"); }
    }

    @FunctionalInterface
    interface Tick { boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime); }
}
