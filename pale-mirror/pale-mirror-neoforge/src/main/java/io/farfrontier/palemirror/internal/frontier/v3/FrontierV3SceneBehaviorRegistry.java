package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssault;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultCauseIdentity;
import io.farfrontier.palemirror.frontier.v3.process.FrontierDurationProcessDriverRegistry;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.server.level.ServerLevel;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
    private static final ReleaseBinding UNBOUND_RELEASE = (checkpoint, state, lease) -> Optional.empty();
    private static final ReleaseBarrier NO_FAMILY_EFFECT = (level, state, lease) -> true;
    private static final FrontierV3SceneBehaviorRegistry CURRENT = new FrontierV3SceneBehaviorRegistry(List.of(
            new Behavior(SceneCauseKind.SETTLEMENT_ASSAULT, FrontierV3SettlementAssaultSceneExecutor::tick,
                    (state, lease, attacker, epoch) -> {
                        SettlementAssault assault = FrontierSceneBehaviors.settlementAssault(lease) == null ? null
                                : state.strategicPlans().settlementAssaults().get(FrontierSceneBehaviors.settlementAssault(lease).assaultId());
                        return assault == null || attacker == null ? null : SettlementAssaultCauseIdentity.strike(assault.id(), attacker, epoch);
                    },
                    FrontierV3SceneBehaviorRegistry::ordinaryStandingPosition,
                    Optional.empty(), "AWAITING_EXACT_FLOOR", ReleaseFailurePolicy.STANDARD, UNBOUND_RELEASE, NO_FAMILY_EFFECT, new FrontierV3SceneReleaseEffects.NoResourceHand(SceneCauseKind.SETTLEMENT_ASSAULT)),
            new Behavior(SceneCauseKind.ENGINEERING_WORKSITE, FrontierV3EngineeringWorkSceneExecutor::tick,
                    (state, lease, attacker, epoch) -> null, FrontierV3SceneBehaviorRegistry::ordinaryStandingPosition,
                    Optional.empty(), "AWAITING_EXACT_FLOOR", ReleaseFailurePolicy.STANDARD, UNBOUND_RELEASE, NO_FAMILY_EFFECT, new FrontierV3SceneReleaseEffects.NoResourceHand(SceneCauseKind.ENGINEERING_WORKSITE)),
            new Behavior(SceneCauseKind.MEDICAL_TREATMENT, FrontierV3MedicalTreatmentSceneExecutor::tick,
                    (state, lease, attacker, epoch) -> null, FrontierV3SceneBehaviorRegistry::ordinaryStandingPosition,
                    Optional.empty(), "AWAITING_EXACT_FLOOR", ReleaseFailurePolicy.STANDARD, UNBOUND_RELEASE, NO_FAMILY_EFFECT, new FrontierV3SceneReleaseEffects.NoResourceHand(SceneCauseKind.MEDICAL_TREATMENT)),
            new Behavior(SceneCauseKind.RESOURCE_SITE_HARVEST, FrontierV3ResourceSiteHarvestSceneExecutor::tick,
                    (state, lease, attacker, epoch) -> null, FrontierV3ResourceSiteHarvestSceneExecutor::harvestStandingPosition,
                    Optional.of(FrontierV3ResourceSiteHarvestSceneExecutor::harvestStandingPosition), "HARVEST_STATION_OBSTRUCTED", ReleaseFailurePolicy.HARVEST_CUSTODY,
                    FrontierV3ResourceSiteHarvestSceneExecutor::releaseBinding,
                    FrontierV3HarvestSceneReleaseBarrier::ready, new FrontierV3HarvestSceneReleaseEffects()),
            new Behavior(SceneCauseKind.PRODUCTION_WORK, FrontierV3ProductionWorkSceneExecutor::tick,
                    (state, lease, attacker, epoch) -> null, FrontierV3SceneBehaviorRegistry::ordinaryStandingPosition,
                    Optional.of(FrontierV3SceneBehaviorRegistry::ordinaryStandingPosition), "AWAITING_EXACT_FLOOR", ReleaseFailurePolicy.STANDARD, UNBOUND_RELEASE,
                    FrontierV3ProductionWorkSceneExecutor::releaseReady, new FrontierV3ProductionSceneReleaseEffects()),
            new Behavior(SceneCauseKind.SERVICE_WORK, FrontierV3SettlementServiceWorkSceneExecutor::tick,
                    (state, lease, attacker, epoch) -> null, FrontierV3SceneBehaviorRegistry::ordinaryStandingPosition,
                    Optional.empty(), "AWAITING_EXACT_FLOOR", ReleaseFailurePolicy.STANDARD, UNBOUND_RELEASE, NO_FAMILY_EFFECT, new FrontierV3SceneReleaseEffects.NoResourceHand(SceneCauseKind.SERVICE_WORK)),
            new Behavior(SceneCauseKind.ROUTE_PATROL, FrontierV3RoutePatrolSceneExecutor::tick,
                    (state, lease, attacker, epoch) -> null, FrontierV3SceneBehaviorRegistry::ordinaryStandingPosition,
                    Optional.empty(), "AWAITING_EXACT_FLOOR", ReleaseFailurePolicy.STANDARD, UNBOUND_RELEASE, NO_FAMILY_EFFECT, new FrontierV3SceneReleaseEffects.NoResourceHand(SceneCauseKind.ROUTE_PATROL))));

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
        // The core enum is not a provider.  Bind every descriptor that names a HOT scene to the
        // actual NeoForge behavior registration before any lease can be admitted or ticked.
        FrontierDurationProcessDriverRegistry.requirePhysicalSceneProviders(java.util.Set.copyOf(byKind.keySet()));
        this.ordered = List.copyOf(registrations);
    }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        CURRENT.tickAll(level, runtime);
    }

    /** Every registered scene family gets one ordered bounded turn; return values are local only. */
    void tickAll(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        runBoundedTurns(ordered.stream().<Runnable>map(behavior -> () -> behavior.tick().tick(level, runtime)).toList());
    }

    static void runBoundedTurns(List<? extends Runnable> turns) {
        for (Runnable turn : turns) turn.run();
    }

    static SubjectId strikeCause(FrontierWorldState state, SceneLease lease, SubjectId attacker, long epoch) {
        for (Behavior behavior : CURRENT.ordered) {
            if (behavior.kind() == lease.cause().kind()) return behavior.strikeCause().apply(state, lease, attacker, epoch);
        }
        throw new IllegalStateException("unregistered NeoForge scene cause: " + lease.cause().kind());
    }


    /** Recovery and ordinary lifecycle release use the same family-owned continuation policy. */
    static Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> releaseBinding(
            io.farfrontier.palemirror.frontier.v3.api.FrontierScheduleView checkpoint,
            FrontierWorldState state, SceneLease lease) {
        for (Behavior behavior : CURRENT.ordered) {
            if (behavior.kind() == lease.cause().kind())
                return Objects.requireNonNull(behavior.releaseBinding().resolve(checkpoint, state, lease),
                        "scene release binding");
        }
        throw new IllegalStateException("unregistered NeoForge scene cause: " + lease.cause().kind());
    }

    /** Pending non-replayable effects belong to the registered family, not generic recovery. */
    static boolean releaseReady(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        return CURRENT.ordered.stream().filter(value -> value.kind() == lease.cause().kind()).findFirst()
                .orElseThrow(() -> new IllegalStateException("unregistered scene release barrier"))
                .releaseBarrier().ready(level, state, lease);
    }

    static FrontierV3SceneReleaseEffects releaseEffects(SceneLease lease) {
        return CURRENT.ordered.stream().filter(value -> value.kind() == lease.cause().kind()).findFirst()
                .orElseThrow(() -> new IllegalStateException("unregistered scene resource release Strategy")).releaseEffects();
    }

    /** The closed behavior registration—not generic scene lifecycle—selects standing policy. */
    static StandingPositionProvider standingPositionProvider(SceneLease lease) {
        for (Behavior behavior : CURRENT.ordered) {
            if (behavior.kind() == lease.cause().kind()) return behavior.standingPositionProvider();
        }
        throw new IllegalStateException("unregistered NeoForge scene cause: " + lease.cause().kind());
    }

    /** A registered physical policy also owns its read-only unavailable-station explanation. */
    static String standingUnavailableReason(SceneLease lease) {
        return standingUnavailableReason(lease.cause().kind());
    }

    static String standingUnavailableReason(SceneCauseKind causeKind) {
        Objects.requireNonNull(causeKind, "scene cause kind");
        for (Behavior behavior : CURRENT.ordered) {
            if (behavior.kind() == causeKind) return behavior.standingUnavailableReason();
        }
        throw new IllegalStateException("unregistered NeoForge scene cause: " + causeKind);
    }

    /**
     * A pre-lease ambient body may use only one registered behavior's standing rule.  The
     * ambient executor never classifies a process/cause itself; duplicate claims fail closed
     * instead of choosing a convenient materialization exception.
     */
    static StandingPositionProvider preLeaseStandingPositionProvider(SceneCauseKind causeKind) {
        Objects.requireNonNull(causeKind, "pre-lease scene cause");
        for (Behavior behavior : CURRENT.ordered) {
            if (behavior.kind() == causeKind) return behavior.preLeaseStandingPositionProvider()
                    .orElseThrow(() -> new IllegalStateException("scene cause has no registered pre-lease standing provider: " + causeKind));
        }
        throw new IllegalStateException("unregistered NeoForge pre-lease scene cause: " + causeKind);
    }

    private static net.minecraft.core.BlockPos ordinaryStandingPosition(ServerLevel level, net.minecraft.core.BlockPos floor) {
        return FrontierV3StandingPosition.aboveExactFloor(level, floor);
    }

    static ReleaseFailurePolicy releaseFailurePolicy(SceneCauseKind kind) {
        return CURRENT.ordered.stream().filter(behavior -> behavior.kind() == kind).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unregistered scene release policy: " + kind)).releaseFailurePolicy();
    }

    enum ReleaseFailurePolicy {
        STANDARD(FrontierV3SceneExecutor::releaseCustodyConflict),
        HARVEST_CUSTODY(FrontierV3ResourceSiteHarvestSceneExecutor::releaseCustodyConflict);
        private final ReleaseFailure failure;
        ReleaseFailurePolicy(ReleaseFailure failure) { this.failure = failure; }
        void conflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                      FrontierWorldState state, SceneLease lease, String reason) {
            failure.report(level, runtime, state, lease, reason);
        }
    }

    @FunctionalInterface
    interface ReleaseFailure {
        void report(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                    FrontierWorldState state, SceneLease lease, String reason);
    }

    record Behavior(SceneCauseKind kind, Tick tick, StrikeCause strikeCause,                     StandingPositionProvider standingPositionProvider, Optional<StandingPositionProvider> preLeaseStandingPositionProvider,
                    String standingUnavailableReason, ReleaseFailurePolicy releaseFailurePolicy, ReleaseBinding releaseBinding,
                    ReleaseBarrier releaseBarrier, FrontierV3SceneReleaseEffects releaseEffects) {
        Behavior {
            Objects.requireNonNull(releaseEffects, "scene atomic resource release Strategy");
            Objects.requireNonNull(releaseBarrier, "scene pending-effect release barrier");
            Objects.requireNonNull(releaseBinding, "scene continuation release policy");
            Objects.requireNonNull(releaseFailurePolicy, "scene release failure policy");
            Objects.requireNonNull(kind, "scene kind");
            if (releaseEffects.family() != kind) throw new IllegalArgumentException("scene resource release Strategy belongs to another family");
            Objects.requireNonNull(tick, "scene tick");
            Objects.requireNonNull(strikeCause, "scene strike cause");
            Objects.requireNonNull(standingPositionProvider, "scene standing provider");
            Objects.requireNonNull(preLeaseStandingPositionProvider, "pre-lease scene standing provider");
            if (standingUnavailableReason == null || !standingUnavailableReason.matches("[A-Z_]+")) {
                throw new IllegalArgumentException("scene standing unavailable reason");
            }
        }
    }

    @FunctionalInterface
    interface Tick { boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime); }

    @FunctionalInterface
    interface StrikeCause { SubjectId apply(FrontierWorldState state, SceneLease lease, SubjectId attacker, long epoch); }

    @FunctionalInterface
    interface ReleaseBinding {
        Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> resolve(
                io.farfrontier.palemirror.frontier.v3.api.FrontierScheduleView checkpoint,
                FrontierWorldState state, SceneLease lease);
    }

    @FunctionalInterface
    interface ReleaseBarrier {
        boolean ready(ServerLevel level, FrontierWorldState state, SceneLease lease);
    }

    /** Typed physical policy owned by a registered behavior, never selected by generic cause tests. */
    @FunctionalInterface
    interface StandingPositionProvider {
        net.minecraft.core.BlockPos resolve(ServerLevel level, net.minecraft.core.BlockPos floor);
    }

}
