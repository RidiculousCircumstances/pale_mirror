package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Closed canonical scene-behavior registry.
 *
 * <p>Generic HOT/COLD infrastructure must enter this class rather than make a policy decision
 * from a cause's concrete Java type.  A new sealed cause therefore supplies one behavior here,
 * and duplicate, missing, or mismatched registrations fail while the registry is assembled.
 * Wire codecs intentionally remain separately exhaustive compatibility boundaries.</p>
 */
public final class FrontierSceneBehaviors {
    private static final FrontierSceneBehaviors CURRENT = new FrontierSceneBehaviors(List.of(
            new LogisticsBehavior(), new SettlementAssaultBehavior(), new EngineeringWorksiteBehavior(), new MedicalTreatmentBehavior()));

    private final Map<SceneCauseKind, SceneBehavior<?>> behaviors;

    FrontierSceneBehaviors(List<? extends SceneBehavior<?>> registrations) {
        Objects.requireNonNull(registrations, "scene behavior registrations");
        EnumMap<SceneCauseKind, SceneBehavior<?>> registered = new EnumMap<>(SceneCauseKind.class);
        for (SceneBehavior<?> behavior : registrations) {
            Objects.requireNonNull(behavior, "scene behavior");
            if (registered.putIfAbsent(behavior.kind(), behavior) != null) {
                throw new IllegalArgumentException("duplicate scene behavior: " + behavior.kind());
            }
            if (!behavior.kind().equals(behavior.causeType().cast(behavior.sampleCause()).kind())) {
                throw new IllegalArgumentException("scene behavior cause kind does not match registration: " + behavior.kind());
            }
        }
        requireCompleteKinds(registered.keySet());
        this.behaviors = Map.copyOf(registered);
    }

    /** Package-visible only to prove fail-closed registry assembly without starting an engine. */
    static void requireCompleteKindsForTest(List<SceneCauseKind> kinds) {
        Objects.requireNonNull(kinds, "scene behavior kinds");
        Set<SceneCauseKind> distinct = new HashSet<>();
        for (SceneCauseKind kind : kinds) {
            if (!distinct.add(Objects.requireNonNull(kind, "scene cause kind"))) throw new IllegalArgumentException("duplicate scene behavior: " + kind);
        }
        requireCompleteKinds(distinct);
    }

    private static void requireCompleteKinds(Set<SceneCauseKind> kinds) {
        Set<SceneCauseKind> missing = new HashSet<>(Set.of(SceneCauseKind.values()));
        missing.removeAll(kinds);
        if (!missing.isEmpty()) throw new IllegalArgumentException("missing scene behaviors: " + missing);
    }

    static SceneBehavior<?> behavior(SceneLease lease) {
        Objects.requireNonNull(lease, "scene lease");
        return CURRENT.require(lease.cause());
    }

    private SceneBehavior<?> require(SceneCause cause) {
        SceneBehavior<?> behavior = behaviors.get(Objects.requireNonNull(cause, "scene cause").kind());
        if (behavior == null) throw new IllegalStateException("unregistered scene cause: " + cause.kind());
        return behavior.require(cause);
    }

    /** Typed logistics facts are available only to logistics-specific policy. */
    public static LogisticsSceneCause logistics(SceneLease lease) {
        return behavior(lease).logistics(lease);
    }

    /** Typed assault facts are available only to assault-specific policy. */
    public static SettlementAssaultSceneCause settlementAssault(SceneLease lease) {
        return behavior(lease).settlementAssault(lease);
    }

    /** Typed work-site facts are available only to engineering-scene policy. */
    public static EngineeringWorkSceneCause engineeringWorksite(SceneLease lease) {
        return behavior(lease).engineeringWorksite(lease);
    }
    public static MedicalTreatmentSceneCause medicalTreatment(SceneLease lease) { return behavior(lease).medicalTreatment(lease); }

    public static boolean isLogistics(SceneLease lease) { return behavior(lease).kind() == SceneCauseKind.LOGISTICS; }
    public static boolean isSettlementAssault(SceneLease lease) { return behavior(lease).kind() == SceneCauseKind.SETTLEMENT_ASSAULT; }
    public static boolean isEngineeringWorksite(SceneLease lease) { return behavior(lease).kind() == SceneCauseKind.ENGINEERING_WORKSITE; }
    public static boolean isMedicalTreatment(SceneLease lease) { return behavior(lease).kind() == SceneCauseKind.MEDICAL_TREATMENT; }
    public static boolean owns(SceneLease lease, SubjectId subjectId) { return behavior(lease).owns(lease, subjectId); }
    public static SubjectId owner(FrontierWorldState state, SceneLease lease) { return behavior(lease).owner(state, lease); }
    public static void validatePrepared(FrontierWorldState state, SceneLease lease) { behavior(lease).validatePrepared(state, lease); }
    static Set<SubjectId> expectedMembers(FrontierBootstrap bootstrap, HumanPopulation population, Map<SubjectId, ActorLocation> actors,
                                          Map<SubjectId, StructureCondition> structures, Map<SubjectId, RouteOperation> operations,
                                          Map<SubjectId, RouteConstruction> constructions, StrategicPlanState plans, SceneLease lease, Set<SubjectId> leasedOperations) {
        return behavior(lease).expectedMembers(bootstrap, population, actors, structures, operations, constructions, plans, lease, leasedOperations);
    }
    static StrategicPlanState transitionPlans(FrontierWorldState state, SceneLease lease, SceneLeaseStatus nextStatus) {
        return behavior(lease).transitionPlans(state, lease, nextStatus);
    }
    static StrategicPlanState releasePlans(FrontierWorldState state, SceneLease lease) {
        return behavior(lease).releasePlans(state, lease);
    }
    static BodyPosition releasedBody(FrontierWorldState state, SceneLease lease, SubjectId actorId, BodyPosition observed) {
        return behavior(lease).releasedBody(state, lease, actorId, observed);
    }
    /** Cause-owned state that must transition atomically with a recorded scene death. */
    static HumanPopulation afterActorDeath(FrontierWorldState state, SceneLease lease, SubjectId actorId, long atTick) {
        return behavior(lease).afterActorDeath(state, lease, actorId, atTick);
    }

    interface SceneBehavior<C extends SceneCause> {
        SceneCauseKind kind();
        Class<C> causeType();
        C sampleCause();
        default SceneBehavior<C> require(SceneCause cause) {
            if (!causeType().isInstance(cause)) {
                throw new IllegalStateException("scene cause type does not match registered kind " + kind());
            }
            return this;
        }
        default C cause(SceneLease lease) { return causeType().cast(lease.cause()); }
        default LogisticsSceneCause logistics(SceneLease lease) {
            throw new IllegalStateException("scene behavior has no logistics binding: " + kind());
        }
        default SettlementAssaultSceneCause settlementAssault(SceneLease lease) {
            throw new IllegalStateException("scene behavior has no settlement-assault binding: " + kind());
        }
        default EngineeringWorkSceneCause engineeringWorksite(SceneLease lease) {
            throw new IllegalStateException("scene behavior has no engineering-worksite binding: " + kind());
        }
        default MedicalTreatmentSceneCause medicalTreatment(SceneLease lease) {
            throw new IllegalStateException("scene behavior has no medical-treatment binding: " + kind());
        }
        boolean owns(SceneLease lease, SubjectId subjectId);
        SubjectId owner(FrontierWorldState state, SceneLease lease);
        void validatePrepared(FrontierWorldState state, SceneLease lease);
        Set<SubjectId> expectedMembers(FrontierBootstrap bootstrap, HumanPopulation population, Map<SubjectId, ActorLocation> actors,
                                       Map<SubjectId, StructureCondition> structures, Map<SubjectId, RouteOperation> operations,
                                       Map<SubjectId, RouteConstruction> constructions, StrategicPlanState plans, SceneLease lease, Set<SubjectId> leasedOperations);
        StrategicPlanState transitionPlans(FrontierWorldState state, SceneLease lease, SceneLeaseStatus nextStatus);
        StrategicPlanState releasePlans(FrontierWorldState state, SceneLease lease);
        BodyPosition releasedBody(FrontierWorldState state, SceneLease lease, SubjectId actorId, BodyPosition observed);
        default HumanPopulation afterActorDeath(FrontierWorldState state, SceneLease lease, SubjectId actorId, long atTick) {
            return state.humanPopulation();
        }
    }

    private static final class LogisticsBehavior implements SceneBehavior<LogisticsSceneCause> {
        @Override public SceneCauseKind kind() { return SceneCauseKind.LOGISTICS; }
        @Override public Class<LogisticsSceneCause> causeType() { return LogisticsSceneCause.class; }
        @Override public LogisticsSceneCause sampleCause() { return new LogisticsSceneCause(new SubjectId("operation:registry"), new SubjectId("cargo:registry"), java.util.Optional.empty(), new BlockPosition(0, 0, 0)); }
        @Override public LogisticsSceneCause logistics(SceneLease lease) { return cause(lease); }
        @Override public boolean owns(SceneLease lease, SubjectId subjectId) {
            LogisticsSceneCause cause = cause(lease);
            return cause.operationId().equals(subjectId) || cause.engagementId().filter(subjectId::equals).isPresent();
        }
        @Override public SubjectId owner(FrontierWorldState state, SceneLease lease) {
            RouteOperation operation = state.operations().get(cause(lease).operationId());
            if (operation == null) throw new IllegalArgumentException("scene lease has no owning operation");
            return operation.settlementId();
        }
        @Override public void validatePrepared(FrontierWorldState state, SceneLease lease) {
            RouteOperation operation = state.operations().get(cause(lease).operationId());
            if (operation != null && operation.activeTravel().isPresent()
                    && !operation.activeTravel().orElseThrow().cargoAnchor().surface().support().equals(cause(lease).cargoPosition())) {
                throw new IllegalArgumentException("scene lease must retain the exact canonical cargo position");
            }
        }
        @Override public Set<SubjectId> expectedMembers(FrontierBootstrap bootstrap, HumanPopulation population, Map<SubjectId, ActorLocation> actors,
                                                         Map<SubjectId, StructureCondition> structures, Map<SubjectId, RouteOperation> operations,
                                                         Map<SubjectId, RouteConstruction> constructions, StrategicPlanState plans, SceneLease lease, Set<SubjectId> leasedOperations) {
            LogisticsSceneCause cause = cause(lease);
            RouteOperation operation = operations.get(cause.operationId());
            if (operation == null || !operation.cargoId().equals(cause.cargoId())) throw new IllegalArgumentException("scene lease must bind its current en-route operation state");
            boolean enRoute = operation.stage() == OperationStage.EN_ROUTE && operation.currentPosition().equals(lease.handoffPosition())
                    && operation.activeTravel().map(travel -> travel.cargoAnchor().surface().support().equals(cause.cargoPosition())).orElse(true);
            boolean interrupted = operation.stage() == OperationStage.INTERRUPTED
                    && (lease.status() == SceneLeaseStatus.DRAINING || lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
            boolean unresolved = operation.stage() == OperationStage.FAILED && lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART && lease.recoveryEvidence().isPresent();
            if (lease.status() != SceneLeaseStatus.CLOSED && !enRoute && !interrupted && !unresolved) throw new IllegalArgumentException("active scene lease must bind its current en-route operation state");
            if (lease.status() != SceneLeaseStatus.CLOSED && !leasedOperations.add(cause.operationId())) throw new IllegalArgumentException("operation cannot have multiple active scene leases");
            if (cause.engagementId().isEmpty()) return Set.copyOf(operation.participantIds());
            RouteEngagement engagement = plans.routeEngagements().get(cause.engagementId().orElseThrow());
            boolean aborted = operation.stage() == OperationStage.INTERRUPTED
                    && (lease.status() == SceneLeaseStatus.DRAINING || lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART)
                    && engagement != null && engagement.status() == RouteEngagementStatus.RESOLVED
                    && engagement.outcome().filter(value -> value == RouteEngagementOutcome.ABORTED).isPresent();
            if (engagement == null || !engagement.operationId().equals(operation.id()) || !cause.cargoPosition().equals(engagement.intercept())
                    || lease.status() != SceneLeaseStatus.CLOSED && engagement.status() != RouteEngagementStatus.COLD_COMBAT
                    && engagement.status() != RouteEngagementStatus.HOT && engagement.status() != RouteEngagementStatus.UNKNOWN_AFTER_RESTART
                    && engagement.status() != RouteEngagementStatus.CONFLICT && !aborted) throw new IllegalArgumentException("scene lease must bind one active canonical engagement");
            Set<SubjectId> values = new HashSet<>(operation.participantIds()); values.addAll(engagement.attackerIds()); return Set.copyOf(values);
        }
        @Override public StrategicPlanState transitionPlans(FrontierWorldState state, SceneLease lease, SceneLeaseStatus nextStatus) {
            LogisticsSceneCause cause = cause(lease);
            if (cause.engagementId().isEmpty()) return state.strategicPlans();
            SubjectId engagementId = cause.engagementId().orElseThrow();
            RouteEngagement engagement = state.strategicPlans().routeEngagements().get(engagementId);
            if (engagement == null) throw new IllegalArgumentException("scene lease has no canonical engagement");
            if (nextStatus == SceneLeaseStatus.HOT) {
                if (engagement.status() == RouteEngagementStatus.RESOLVED) throw new IllegalArgumentException("an aborted engagement cannot reclaim a HOT scene");
                return state.strategicPlans().transitionEngagement(engagementId, RouteEngagementStatus.HOT);
            }
            if (nextStatus == SceneLeaseStatus.UNKNOWN_AFTER_RESTART && engagement.status() != RouteEngagementStatus.RESOLVED) return state.strategicPlans().transitionEngagement(engagementId, RouteEngagementStatus.UNKNOWN_AFTER_RESTART);
            if (nextStatus == SceneLeaseStatus.CONFLICT && engagement.status() != RouteEngagementStatus.RESOLVED) return state.strategicPlans().transitionEngagement(engagementId, RouteEngagementStatus.CONFLICT);
            return state.strategicPlans();
        }
        @Override public StrategicPlanState releasePlans(FrontierWorldState state, SceneLease lease) {
            LogisticsSceneCause cause = cause(lease);
            return cause.engagementId().map(id -> {
                RouteEngagement engagement = state.strategicPlans().routeEngagements().get(id);
                return engagement != null && engagement.status() != RouteEngagementStatus.RESOLVED
                        ? state.strategicPlans().transitionEngagement(id, RouteEngagementStatus.COLD_COMBAT) : state.strategicPlans();
            }).orElse(state.strategicPlans());
        }
        @Override public BodyPosition releasedBody(FrontierWorldState state, SceneLease lease, SubjectId actorId, BodyPosition observed) {
            RouteOperation operation = state.operations().get(cause(lease).operationId());
            boolean checkpoint = cause(lease).engagementId().isEmpty() && operation != null && operation.stage() == OperationStage.EN_ROUTE && operation.activeTravel().isPresent();
            return checkpoint ? operation.activeTravel().orElseThrow().formation().get(actorId) : observed;
        }
    }

    private static final class SettlementAssaultBehavior implements SceneBehavior<SettlementAssaultSceneCause> {
        @Override public SceneCauseKind kind() { return SceneCauseKind.SETTLEMENT_ASSAULT; }
        @Override public Class<SettlementAssaultSceneCause> causeType() { return SettlementAssaultSceneCause.class; }
        @Override public SettlementAssaultSceneCause sampleCause() { return new SettlementAssaultSceneCause(new SubjectId("assault:registry"), new SubjectId("settlement:registry")); }
        @Override public SettlementAssaultSceneCause settlementAssault(SceneLease lease) { return cause(lease); }
        @Override public boolean owns(SceneLease lease, SubjectId subjectId) { return cause(lease).assaultId().equals(subjectId); }
        @Override public SubjectId owner(FrontierWorldState state, SceneLease lease) { return FrontierSettlementAssaultSceneSupport.require(state, cause(lease)).hiveId(); }
        @Override public void validatePrepared(FrontierWorldState state, SceneLease lease) { FrontierSettlementAssaultSceneSupport.validatePrepared(state, lease); }
        @Override public Set<SubjectId> expectedMembers(FrontierBootstrap bootstrap, HumanPopulation population, Map<SubjectId, ActorLocation> actors,
                                                         Map<SubjectId, StructureCondition> structures, Map<SubjectId, RouteOperation> operations,
                                                         Map<SubjectId, RouteConstruction> constructions, StrategicPlanState plans, SceneLease lease, Set<SubjectId> leasedOperations) {
            SettlementAssault assault = FrontierSettlementAssaultSceneSupport.require(plans, cause(lease));
            if (!FrontierSettlementAssaultSceneSupport.targetIntact(bootstrap, structures, assault) && lease.status() != SceneLeaseStatus.CLOSED) throw new IllegalArgumentException("active assault scene target geometry is destroyed");
            boolean valid = switch (lease.status()) {
                case PREPARED -> assault.status() == SettlementAssaultStatus.COLD_COMBAT;
                case HOT, DRAINING -> assault.status() == SettlementAssaultStatus.HOT;
                case UNKNOWN_AFTER_RESTART -> assault.status() == SettlementAssaultStatus.UNKNOWN_AFTER_RESTART;
                case CONFLICT -> assault.status() == SettlementAssaultStatus.CONFLICT;
                case CLOSED -> assault.status() == SettlementAssaultStatus.COLD_COMBAT || assault.status() == SettlementAssaultStatus.RESOLVED;
            };
            if (!valid) throw new IllegalArgumentException("assault scene lease and canonical lifecycle disagree");
            Set<SubjectId> values = new HashSet<>(assault.attackerIds()); values.addAll(assault.defenderIds()); return Set.copyOf(values);
        }
        @Override public StrategicPlanState transitionPlans(FrontierWorldState state, SceneLease lease, SceneLeaseStatus nextStatus) {
            SettlementAssault assault = FrontierSettlementAssaultSceneSupport.require(state, cause(lease));
            return switch (nextStatus) {
                case HOT -> state.strategicPlans().transitionSettlementAssault(assault.id(), SettlementAssaultStatus.HOT);
                case UNKNOWN_AFTER_RESTART -> state.strategicPlans().transitionSettlementAssault(assault.id(), SettlementAssaultStatus.UNKNOWN_AFTER_RESTART);
                case CONFLICT -> state.strategicPlans().transitionSettlementAssault(assault.id(), SettlementAssaultStatus.CONFLICT);
                default -> state.strategicPlans();
            };
        }
        @Override public StrategicPlanState releasePlans(FrontierWorldState state, SceneLease lease) {
            return state.strategicPlans().transitionSettlementAssault(FrontierSettlementAssaultSceneSupport.require(state, cause(lease)).id(), SettlementAssaultStatus.COLD_COMBAT);
        }
        @Override public BodyPosition releasedBody(FrontierWorldState state, SceneLease lease, SubjectId actorId, BodyPosition observed) {
            return observed;
        }
    }

    private static final class EngineeringWorksiteBehavior implements SceneBehavior<EngineeringWorkSceneCause> {
        @Override public SceneCauseKind kind() { return SceneCauseKind.ENGINEERING_WORKSITE; }
        @Override public Class<EngineeringWorkSceneCause> causeType() { return EngineeringWorkSceneCause.class; }
        @Override public EngineeringWorkSceneCause sampleCause() { return new EngineeringWorkSceneCause(new SubjectId("construction:registry"), 0); }
        @Override public EngineeringWorkSceneCause engineeringWorksite(SceneLease lease) { return cause(lease); }
        @Override public boolean owns(SceneLease lease, SubjectId subjectId) { return cause(lease).projectId().equals(subjectId); }
        @Override public SubjectId owner(FrontierWorldState state, SceneLease lease) { return FrontierEngineeringWorkSceneSupport.owner(state, cause(lease)); }
        @Override public void validatePrepared(FrontierWorldState state, SceneLease lease) { FrontierEngineeringWorkSceneSupport.validatePrepared(state, lease); }
        @Override public Set<SubjectId> expectedMembers(FrontierBootstrap bootstrap, HumanPopulation population, Map<SubjectId, ActorLocation> actors,
                                                         Map<SubjectId, StructureCondition> structures, Map<SubjectId, RouteOperation> operations,
                                                         Map<SubjectId, RouteConstruction> constructions, StrategicPlanState plans,
                                                         SceneLease lease, Set<SubjectId> leasedOperations) {
            EngineeringWorkSceneCause cause = cause(lease);
            RouteConstruction project = constructions.get(cause.projectId());
            if (project == null || project.team().isEmpty()) {
                throw new IllegalArgumentException("engineering scene must bind its current active construction crew");
            }
            boolean currentCell = project.confirmedCells() == cause.workCellIndex();
            boolean confirmedCellDraining = project.confirmedCells() == cause.workCellIndex() + 1
                    && (lease.status() == SceneLeaseStatus.DRAINING || lease.status() == SceneLeaseStatus.CLOSED);
            if ((currentCell && project.status() != RouteConstructionStatus.BUILDING) || (!currentCell && !confirmedCellDraining)) {
                throw new IllegalArgumentException("engineering scene cursor differs from its construction lifecycle");
            }
            if (currentCell && (project.assembly().isEmpty() || !project.assembly().orElseThrow().complete())) {
                throw new IllegalArgumentException("engineering scene must wait for its complete COLD approach");
            }
            return Set.copyOf(project.team().orElseThrow().memberIds());
        }
        @Override public StrategicPlanState transitionPlans(FrontierWorldState state, SceneLease lease, SceneLeaseStatus nextStatus) { return state.strategicPlans(); }
        @Override public StrategicPlanState releasePlans(FrontierWorldState state, SceneLease lease) { return state.strategicPlans(); }
        @Override public BodyPosition releasedBody(FrontierWorldState state, SceneLease lease, SubjectId actorId, BodyPosition observed) {
            return observed;
        }
    }

    /** The care owner itself, rather than a synthetic medic mob, owns every treatment scene. */
    private static final class MedicalTreatmentBehavior implements SceneBehavior<MedicalTreatmentSceneCause> {
        @Override public SceneCauseKind kind() { return SceneCauseKind.MEDICAL_TREATMENT; }
        @Override public Class<MedicalTreatmentSceneCause> causeType() { return MedicalTreatmentSceneCause.class; }
        @Override public MedicalTreatmentSceneCause sampleCause() { return new MedicalTreatmentSceneCause(new SubjectId("medical:registry")); }
        @Override public MedicalTreatmentSceneCause medicalTreatment(SceneLease lease) { return cause(lease); }
        @Override public boolean owns(SceneLease lease, SubjectId subjectId) { return cause(lease).operationId().equals(subjectId); }
        @Override public SubjectId owner(FrontierWorldState state, SceneLease lease) { return FrontierMedicalTreatmentSceneSupport.owner(state, cause(lease)); }
        @Override public void validatePrepared(FrontierWorldState state, SceneLease lease) { FrontierMedicalTreatmentSceneSupport.validatePrepared(state, lease); }
        @Override public Set<SubjectId> expectedMembers(FrontierBootstrap bootstrap, HumanPopulation population, Map<SubjectId, ActorLocation> actors,
                                                         Map<SubjectId, StructureCondition> structures, Map<SubjectId, RouteOperation> operations,
                                                         Map<SubjectId, RouteConstruction> constructions, StrategicPlanState plans,
                                                         SceneLease lease, Set<SubjectId> leasedOperations) {
            MedicalEvacuationOperation operation = population.medicalOperations().get(cause(lease).operationId());
            if (operation == null) throw new IllegalArgumentException("medical scene has no exact operation");
            boolean valid = switch (lease.status()) {
                case PREPARED -> operation.status() == MedicalEvacuationStatus.PREPARED;
                case HOT, DRAINING -> operation.status() == MedicalEvacuationStatus.PREPARED || operation.status() == MedicalEvacuationStatus.TREATING
                        || operation.status() == MedicalEvacuationStatus.UNKNOWN_AFTER_RESTART || operation.status() == MedicalEvacuationStatus.COMPLETED
                        || operation.status() == MedicalEvacuationStatus.BLOCKED;
                case UNKNOWN_AFTER_RESTART -> operation.status() == MedicalEvacuationStatus.PREPARED || operation.status() == MedicalEvacuationStatus.TREATING
                        || operation.status() == MedicalEvacuationStatus.UNKNOWN_AFTER_RESTART;
                case CONFLICT -> operation.active();
                // A pre-effect scene may close when no player remains.  Its retained COLD
                // operation is intentionally eligible for a later naturally loaded scene.
                case CLOSED -> operation.status() == MedicalEvacuationStatus.PREPARED || operation.status() == MedicalEvacuationStatus.COMPLETED
                        || operation.status() == MedicalEvacuationStatus.BLOCKED;
            };
            if (!valid) throw new IllegalArgumentException("medical scene and operation lifecycle disagree");
            Set<SubjectId> members = new HashSet<>(operation.team().memberIds()); members.add(operation.patientId()); return Set.copyOf(members);
        }
        @Override public StrategicPlanState transitionPlans(FrontierWorldState state, SceneLease lease, SceneLeaseStatus nextStatus) { return state.strategicPlans(); }
        @Override public StrategicPlanState releasePlans(FrontierWorldState state, SceneLease lease) { return state.strategicPlans(); }
        @Override public BodyPosition releasedBody(FrontierWorldState state, SceneLease lease, SubjectId actorId, BodyPosition observed) {
            return observed;
        }
        @Override public HumanPopulation afterActorDeath(FrontierWorldState state, SceneLease lease, SubjectId actorId, long atTick) {
            MedicalEvacuationOperation operation = FrontierMedicalTreatmentSceneSupport.require(state, cause(lease));
            return operation.active() && (operation.patientId().equals(actorId) || operation.team().memberIds().contains(actorId))
                    ? state.humanPopulation().transitionMedicalOperation(operation.id(), MedicalEvacuationStatus.BLOCKED, atTick)
                    : state.humanPopulation();
        }
    }
}
