package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
            new LogisticsBehavior(), new SettlementAssaultBehavior(), new EngineeringWorksiteBehavior(), new MedicalTreatmentBehavior(),
            new ResourceSiteHarvestBehavior(), new ProductionWorkBehavior(), new SettlementServiceWorkBehavior(), new RoutePatrolBehavior()));

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
    public static ResourceSiteHarvestSceneCause resourceSiteHarvest(SceneLease lease) { return behavior(lease).resourceSiteHarvest(lease); }
    public static ProductionWorkSceneCause productionWork(SceneLease lease) { return behavior(lease).productionWork(lease); }
    public static SettlementServiceWorkSceneCause serviceWork(SceneLease lease) { return behavior(lease).serviceWork(lease); }
    public static RoutePatrolSceneCause routePatrol(SceneLease lease) { return behavior(lease).routePatrol(lease); }

    public static boolean isLogistics(SceneLease lease) { return behavior(lease).kind() == SceneCauseKind.LOGISTICS; }
    public static boolean isSettlementAssault(SceneLease lease) { return behavior(lease).kind() == SceneCauseKind.SETTLEMENT_ASSAULT; }
    public static boolean isEngineeringWorksite(SceneLease lease) { return behavior(lease).kind() == SceneCauseKind.ENGINEERING_WORKSITE; }
    public static boolean isMedicalTreatment(SceneLease lease) { return behavior(lease).kind() == SceneCauseKind.MEDICAL_TREATMENT; }
    public static boolean isResourceSiteHarvest(SceneLease lease) { return behavior(lease).kind() == SceneCauseKind.RESOURCE_SITE_HARVEST; }
    public static boolean isProductionWork(SceneLease lease) { return behavior(lease).kind() == SceneCauseKind.PRODUCTION_WORK; }
    public static boolean isServiceWork(SceneLease lease) { return behavior(lease).kind() == SceneCauseKind.SERVICE_WORK; }
    public static boolean isRoutePatrol(SceneLease lease) { return behavior(lease).kind() == SceneCauseKind.ROUTE_PATROL; }
    public static boolean owns(SceneLease lease, SubjectId subjectId) { return behavior(lease).owns(lease, subjectId); }
    /** Only an assault scene can exempt its own combatants from another-owner admission. */
    public static boolean ownedBySettlementAssault(SceneLease lease, SubjectId assaultId) {
        return behavior(lease).ownedBySettlementAssault(lease, assaultId);
    }
    public static SubjectId owner(FrontierWorldState state, SceneLease lease) { return behavior(lease).owner(state, lease); }
    public static void validatePrepared(FrontierWorldState state, SceneLease lease) {
        behavior(lease).validatePrepared(state, lease);
    }
    static Set<SubjectId> expectedMembers(FrontierBootstrap bootstrap, HumanPopulation population, Map<SubjectId, ActorLocation> actors,
                                          Map<SubjectId, StructureCondition> structures, Map<SubjectId, RouteOperation> operations,
                                          Map<SubjectId, RouteConstruction> constructions, Map<SubjectId, RouteMaintenance> maintenances,
                                          StrategicPlanState plans, ResourceSiteState resourceSites, Map<SubjectId, ProductionJob> productionJobs,
                                          Map<SubjectId, SettlementServiceWork> serviceWorks, SceneLease lease, Set<SubjectId> leasedOperations) {
        return behavior(lease).expectedMembers(bootstrap, population, actors, structures, operations, constructions, maintenances, plans, resourceSites,
                productionJobs, serviceWorks, lease, leasedOperations);
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
    /**
     * Cause-owned continuation after the generic infrastructure has captured a released scene.
     * The caller intentionally has no concrete-cause branch: a new scene kind must make this
     * policy explicit when it joins the same closed registry as admission and HOT execution.
     */
    public static SceneReleasePlan releasePlan(FrontierWorldState state, SceneLease lease, long submittedAt,
                                               SceneLeaseReleased released) {
        return behavior(lease).releasePlan(state, lease, submittedAt, released);
    }
    /** Cause-owned policy for an unresolved post-restart scene inspection. */
    public static SceneRecoveryPlan recoveryUnresolvedPlan(FrontierWorldState state, SceneLease lease,
                                                           SceneLeaseRecoveryUnresolved unresolved) {
        return behavior(lease).recoveryUnresolvedPlan(state, lease, unresolved);
    }
    /** Cause-owned state that must transition atomically with a recorded scene death. */
    static SceneDeathOutcome afterActorDeath(FrontierWorldState state, SceneLease lease, SubjectId actorId, long atTick) {
        return behavior(lease).afterActorDeath(state, lease, actorId, atTick);
    }

    /** Recovery resumes owned work only when the registered owner still has work to run. */
    public static SceneLeaseStatus recoveredStatus(FrontierWorldState state, SceneLease lease) {
        if (lease.members().stream().anyMatch(member ->
                state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.DEAD)) {
            return SceneLeaseStatus.DRAINING;
        }
        return behavior(lease).recoveredStatus(state, lease);
    }

    record SceneDeathOutcome(HumanPopulation humanPopulation, ResourceSiteState resourceSites, StrategicPlanState strategicPlans,
                             Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> physicalIntents,
                             Map<SubjectId, SettlementServiceWork> serviceWorks,
                             io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState actorExecutions) {
        SceneDeathOutcome {
            Objects.requireNonNull(humanPopulation, "scene-death human population"); Objects.requireNonNull(resourceSites, "scene-death resource sites");
            Objects.requireNonNull(strategicPlans, "scene-death strategic plans"); Objects.requireNonNull(physicalIntents, "scene-death physical intents");
            Objects.requireNonNull(serviceWorks, "scene-death service works");
            Objects.requireNonNull(actorExecutions, "scene-death execution outcomes");
        }
        static SceneDeathOutcome unchanged(FrontierWorldState state) {
            return new SceneDeathOutcome(state.humanPopulation(), state.resourceSites(), state.strategicPlans(), state.physicalIntents(), state.serviceWorks(), state.actorExecutions());
        }
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
        default ResourceSiteHarvestSceneCause resourceSiteHarvest(SceneLease lease) {
            throw new IllegalStateException("scene behavior has no resource-site-harvest binding: " + kind());
        }
        default ProductionWorkSceneCause productionWork(SceneLease lease) {
            throw new IllegalStateException("scene behavior has no production-work binding: " + kind());
        }
        default SettlementServiceWorkSceneCause serviceWork(SceneLease lease) {
            throw new IllegalStateException("scene behavior has no service-work binding: " + kind());
        }
        default RoutePatrolSceneCause routePatrol(SceneLease lease) {
            throw new IllegalStateException("scene behavior has no route-patrol binding: " + kind());
        }
        boolean owns(SceneLease lease, SubjectId subjectId);
        default boolean ownedBySettlementAssault(SceneLease lease, SubjectId assaultId) { return false; }
        SubjectId owner(FrontierWorldState state, SceneLease lease);
        void validatePrepared(FrontierWorldState state, SceneLease lease);
        Set<SubjectId> expectedMembers(FrontierBootstrap bootstrap, HumanPopulation population, Map<SubjectId, ActorLocation> actors,
                                       Map<SubjectId, StructureCondition> structures, Map<SubjectId, RouteOperation> operations,
                                       Map<SubjectId, RouteConstruction> constructions, Map<SubjectId, RouteMaintenance> maintenances,
                                       StrategicPlanState plans, ResourceSiteState resourceSites, Map<SubjectId, ProductionJob> productionJobs,
                                       Map<SubjectId, SettlementServiceWork> serviceWorks, SceneLease lease, Set<SubjectId> leasedOperations);
        StrategicPlanState transitionPlans(FrontierWorldState state, SceneLease lease, SceneLeaseStatus nextStatus);
        StrategicPlanState releasePlans(FrontierWorldState state, SceneLease lease);
        BodyPosition releasedBody(FrontierWorldState state, SceneLease lease, SubjectId actorId, BodyPosition observed);
        SceneReleasePlan releasePlan(FrontierWorldState state, SceneLease lease, long submittedAt, SceneLeaseReleased released);
        SceneRecoveryPlan recoveryUnresolvedPlan(FrontierWorldState state, SceneLease lease, SceneLeaseRecoveryUnresolved unresolved);
        SceneLeaseStatus recoveredStatus(FrontierWorldState state, SceneLease lease);
        default SceneDeathOutcome afterActorDeath(FrontierWorldState state, SceneLease lease, SubjectId actorId, long atTick) {
            return SceneDeathOutcome.unchanged(state);
        }
    }

    private static final class LogisticsBehavior implements SceneBehavior<LogisticsSceneCause> {
        @Override public SceneCauseKind kind() { return SceneCauseKind.LOGISTICS; }
        @Override public SceneLeaseStatus recoveredStatus(FrontierWorldState state, SceneLease lease) {
            LogisticsSceneCause cause = cause(lease);
            RouteOperation operation = state.operations().get(cause.operationId());
            if (operation == null || !operation.cargoId().equals(cause.cargoId())) {
                throw new IllegalArgumentException("scene recovery has no exact owning operation");
            }
            if (operation.stage() == OperationStage.INTERRUPTED) return SceneLeaseStatus.DRAINING;
            if (operation.stage() == OperationStage.FAILED && cause.engagementId().isEmpty()) return SceneLeaseStatus.DRAINING;
            if (operation.stage() == OperationStage.EN_ROUTE
                    && cause.carrierDisposition() == CargoProjectionRetirement.Disposition.REMOVE_PROJECTION) {
                return SceneLeaseStatus.HOT;
            }
            throw new IllegalArgumentException("scene recovery cannot resume a terminal operation");
        }
        @Override public Class<LogisticsSceneCause> causeType() { return LogisticsSceneCause.class; }
        @Override public LogisticsSceneCause sampleCause() { return new LogisticsSceneCause(new SubjectId("operation:registry"), new SubjectId("cargo:registry"), java.util.Optional.empty(), new BlockPosition(0, 0, 0),
                CargoProjectionRetirement.Disposition.REMOVE_PROJECTION); }
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
            cause(lease).engagementId().ifPresent(engagementId -> {
                RouteEngagement engagement = state.strategicPlans().routeEngagements().get(engagementId);
                if (engagement == null || !engagement.commandAuthority().permitsCoordinatedAdvance()) {
                    throw new IllegalArgumentException("engagement scene requires retained command authority");
                }
            });
        }
        @Override public Set<SubjectId> expectedMembers(FrontierBootstrap bootstrap, HumanPopulation population, Map<SubjectId, ActorLocation> actors,
                                                         Map<SubjectId, StructureCondition> structures, Map<SubjectId, RouteOperation> operations,
                                                         Map<SubjectId, RouteConstruction> constructions, Map<SubjectId, RouteMaintenance> maintenances,
                                                         StrategicPlanState plans, ResourceSiteState resourceSites, Map<SubjectId, ProductionJob> productionJobs,
                                                         Map<SubjectId, SettlementServiceWork> serviceWorks, SceneLease lease, Set<SubjectId> leasedOperations) {
            LogisticsSceneCause cause = cause(lease);
            RouteOperation operation = operations.get(cause.operationId());
            if (operation == null || !operation.cargoId().equals(cause.cargoId())) throw new IllegalArgumentException("scene lease must bind its current en-route operation state");
            boolean enRoute = operation.stage() == OperationStage.EN_ROUTE && operation.currentPosition().equals(lease.handoffPosition())
                    && operation.activeTravel().map(travel -> travel.cargoAnchor().surface().support().equals(cause.cargoPosition())).orElse(true);
            boolean interrupted = operation.stage() == OperationStage.INTERRUPTED
                    && (lease.status() == SceneLeaseStatus.DRAINING || lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
            boolean unresolved = operation.stage() == OperationStage.FAILED && cause.engagementId().isEmpty()
                    && (lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART || lease.status() == SceneLeaseStatus.DRAINING);
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
            if (nextStatus == SceneLeaseStatus.DRAINING && engagement.status() == RouteEngagementStatus.UNKNOWN_AFTER_RESTART) {
                // Recovered physical custody is being released, not authorized to attack.
                return state.strategicPlans().transitionEngagement(engagementId, RouteEngagementStatus.HOT);
            }
            if (nextStatus == SceneLeaseStatus.HOT) {
                if (engagement.status() == RouteEngagementStatus.RESOLVED) throw new IllegalArgumentException("an aborted engagement cannot reclaim a HOT scene");
                if (!engagement.commandAuthority().permitsCoordinatedAdvance()) {
                    throw new IllegalArgumentException("engagement cannot enter HOT without retained command authority");
                }
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
        @Override public SceneReleasePlan releasePlan(FrontierWorldState state, SceneLease lease, long submittedAt, SceneLeaseReleased released) {
            LogisticsSceneCause cause = cause(lease);
            RouteOperation operation = state.operations().get(cause.operationId());
            if (operation == null) throw new IllegalArgumentException("scene lease has no owning operation");
            if (cause.engagementId().isPresent()) {
                RouteEngagement engagement = state.strategicPlans().routeEngagements().get(cause.engagementId().orElseThrow());
                if (engagement == null) throw new IllegalArgumentException("scene lease has no canonical engagement");
                if (engagement.status() == RouteEngagementStatus.HOT) return new SceneReleasePlan(operation.settlementId(), released,
                        new SceneContinuation.ResumeEngagement(engagement.id(), submittedAt + 20L));
                if (operation.stage() == OperationStage.INTERRUPTED && engagement.status() == RouteEngagementStatus.RESOLVED
                        && engagement.outcome().filter(outcome -> outcome == RouteEngagementOutcome.ABORTED).isPresent()) {
                    return new SceneReleasePlan(operation.settlementId(), released, new SceneContinuation.None());
                }
                throw new IllegalArgumentException("scene lease cannot resume its interrupted engagement");
            }
            if (operation.stage() == OperationStage.FAILED || operation.stage() == OperationStage.INTERRUPTED) {
                return new SceneReleasePlan(operation.settlementId(), released, new SceneContinuation.None());
            }
            if (operation.participantIds().stream().anyMatch(actor -> state.actorLocations().get(actor).condition().status() == ActorLifeStatus.DEAD)) {
                return new SceneReleasePlan(operation.settlementId(), released,
                        new SceneContinuation.FailOperation(operation.id(), "actor-death"));
            }
            return new SceneReleasePlan(operation.settlementId(), released,
                    new SceneContinuation.ResumeOperation(operation.id(), submittedAt + 100L));
        }
        @Override public SceneRecoveryPlan recoveryUnresolvedPlan(FrontierWorldState state, SceneLease lease, SceneLeaseRecoveryUnresolved unresolved) {
            LogisticsSceneCause cause = cause(lease);
            RouteOperation operation = state.operations().get(cause.operationId());
            if (cause.engagementId().isPresent()) {
                RouteEngagement engagement = state.strategicPlans().routeEngagements().get(cause.engagementId().orElseThrow());
                if (operation == null || !operation.cargoId().equals(cause.cargoId())
                        || engagement == null || !engagement.operationId().equals(operation.id())
                        || !engagement.intercept().equals(cause.cargoPosition())) {
                    throw new IllegalArgumentException("scene recovery evidence has no exact owning engagement");
                }
                boolean active = operation.stage() == OperationStage.EN_ROUTE
                        && engagement.status() == RouteEngagementStatus.UNKNOWN_AFTER_RESTART;
                boolean aborted = operation.stage() == OperationStage.INTERRUPTED
                        && engagement.status() == RouteEngagementStatus.RESOLVED
                        && engagement.outcome().filter(value -> value == RouteEngagementOutcome.ABORTED).isPresent();
                if (!active && !aborted) {
                    throw new IllegalArgumentException("scene recovery evidence has no recoverable engagement");
                }
                // Missing physical members are uncertainty, not a combat result or cargo loss.
                return new SceneRecoveryPlan(operation.settlementId(), unresolved, new SceneContinuation.None());
            }
            if (operation != null && operation.cargoId().equals(cause.cargoId())
                    && (operation.stage() == OperationStage.FAILED || operation.stage() == OperationStage.INTERRUPTED)) {
                return new SceneRecoveryPlan(operation.settlementId(), unresolved, new SceneContinuation.None());
            }
            if (operation == null || operation.stage() != OperationStage.EN_ROUTE) {
                throw new IllegalArgumentException("scene recovery evidence has no active route operation");
            }
            return new SceneRecoveryPlan(operation.settlementId(), unresolved,
                    new SceneContinuation.FailOperation(operation.id(), "scene-recovery-unresolved"));
        }
    }

    private static final class SettlementAssaultBehavior implements SceneBehavior<SettlementAssaultSceneCause> {
        @Override public SceneLeaseStatus recoveredStatus(FrontierWorldState state, SceneLease lease) {
            FrontierSettlementAssaultSceneSupport.require(state, cause(lease));
            return SceneLeaseStatus.HOT;
        }
        @Override public SceneCauseKind kind() { return SceneCauseKind.SETTLEMENT_ASSAULT; }
        @Override public Class<SettlementAssaultSceneCause> causeType() { return SettlementAssaultSceneCause.class; }
        @Override public SettlementAssaultSceneCause sampleCause() { return new SettlementAssaultSceneCause(new SubjectId("assault:registry"), new SubjectId("settlement:registry")); }
        @Override public SettlementAssaultSceneCause settlementAssault(SceneLease lease) { return cause(lease); }
        @Override public boolean owns(SceneLease lease, SubjectId subjectId) { return cause(lease).assaultId().equals(subjectId); }
        @Override public boolean ownedBySettlementAssault(SceneLease lease, SubjectId assaultId) {
            return cause(lease).assaultId().equals(assaultId);
        }
        @Override public SubjectId owner(FrontierWorldState state, SceneLease lease) { return FrontierSettlementAssaultSceneSupport.require(state, cause(lease)).hiveId(); }
        @Override public void validatePrepared(FrontierWorldState state, SceneLease lease) { FrontierSettlementAssaultSceneSupport.validatePrepared(state, lease); }
        @Override public Set<SubjectId> expectedMembers(FrontierBootstrap bootstrap, HumanPopulation population, Map<SubjectId, ActorLocation> actors,
                                                         Map<SubjectId, StructureCondition> structures, Map<SubjectId, RouteOperation> operations,
                                                         Map<SubjectId, RouteConstruction> constructions, Map<SubjectId, RouteMaintenance> maintenances,
                                                         StrategicPlanState plans, ResourceSiteState resourceSites, Map<SubjectId, ProductionJob> productionJobs,
                                                         Map<SubjectId, SettlementServiceWork> serviceWorks, SceneLease lease, Set<SubjectId> leasedOperations) {
            SettlementAssault assault = FrontierSettlementAssaultSceneSupport.require(plans, cause(lease));
            boolean march = assault.tacticalPlan().phase() == TacticalPlanPhase.TRAVEL
                    && assault.status() != SettlementAssaultStatus.COLD_COMBAT
                    || assault.tacticalPlan().phase() == TacticalPlanPhase.RETREAT && !assault.march().complete();
            if (!march && !FrontierSettlementAssaultSceneSupport.targetIntact(bootstrap, structures, assault) && lease.status() != SceneLeaseStatus.CLOSED) throw new IllegalArgumentException("active assault scene target geometry is destroyed");
            boolean valid = switch (lease.status()) {
                case PREPARED -> assault.status() == SettlementAssaultStatus.COLD_COMBAT || assault.status() == SettlementAssaultStatus.APPROACHING;
                case HOT, DRAINING -> assault.status() == SettlementAssaultStatus.HOT;
                case UNKNOWN_AFTER_RESTART -> assault.status() == SettlementAssaultStatus.UNKNOWN_AFTER_RESTART;
                case CONFLICT -> assault.status() == SettlementAssaultStatus.CONFLICT;
                // A CLOSED lease is the retained receipt for a completed epoch.  It remains
                // inspectable while the next COLD epoch either reaches HOT, resolves, or
                // truthfully conflicts after its independent battlefield validation.
                case CLOSED -> assault.status() == SettlementAssaultStatus.APPROACHING || assault.status() == SettlementAssaultStatus.COLD_COMBAT || assault.status() == SettlementAssaultStatus.HOT
                        || assault.status() == SettlementAssaultStatus.RESOLVED || assault.status() == SettlementAssaultStatus.CONFLICT;
            };
            if (!valid) throw new IllegalArgumentException("assault scene lease and canonical lifecycle disagree");
            Set<SubjectId> values = new HashSet<>(assault.attackerIds()); if (!march) values.addAll(assault.defenderIds()); return Set.copyOf(values);
        }
        @Override public StrategicPlanState transitionPlans(FrontierWorldState state, SceneLease lease, SceneLeaseStatus nextStatus) {
            SettlementAssault assault = FrontierSettlementAssaultSceneSupport.require(state, cause(lease));
            return switch (nextStatus) {
                case HOT -> state.strategicPlans().transitionSettlementAssault(assault.id(), SettlementAssaultStatus.HOT);
                case DRAINING -> assault.status() == SettlementAssaultStatus.UNKNOWN_AFTER_RESTART
                        ? state.strategicPlans().transitionSettlementAssault(assault.id(), SettlementAssaultStatus.HOT)
                        : state.strategicPlans();
                case UNKNOWN_AFTER_RESTART -> state.strategicPlans().transitionSettlementAssault(assault.id(), SettlementAssaultStatus.UNKNOWN_AFTER_RESTART);
                case CONFLICT -> state.strategicPlans().transitionSettlementAssault(assault.id(), SettlementAssaultStatus.CONFLICT);
                default -> state.strategicPlans();
            };
        }
        @Override public StrategicPlanState releasePlans(FrontierWorldState state, SceneLease lease) {
            SettlementAssault assault = FrontierSettlementAssaultSceneSupport.require(state, cause(lease));
            return state.strategicPlans().transitionSettlementAssault(assault.id(), assault.tacticalPlan().phase() == TacticalPlanPhase.TRAVEL
                    ? SettlementAssaultStatus.APPROACHING : SettlementAssaultStatus.COLD_COMBAT);
        }
        @Override public BodyPosition releasedBody(FrontierWorldState state, SceneLease lease, SubjectId actorId, BodyPosition observed) {
            // HOT bodies may be observed between the exact provider-approved assault floors
            // while Minecraft unloads them.  The lease is the sole durable COLD/HOT hand-off:
            // retaining a transient body here would make the next COLD battlefield admission
            // depend on a floor that no provider ever approved.
            return lease.memberPosition(actorId);
        }
        @Override public SceneReleasePlan releasePlan(FrontierWorldState state, SceneLease lease, long submittedAt, SceneLeaseReleased released) {
            SettlementAssault assault = FrontierSettlementAssaultSceneSupport.require(state, cause(lease));
            return new SceneReleasePlan(assault.hiveId(), released,
                    new SceneContinuation.ResumeSettlementAssault(assault.id(), submittedAt + 20L));
        }
        @Override public SceneRecoveryPlan recoveryUnresolvedPlan(FrontierWorldState state, SceneLease lease, SceneLeaseRecoveryUnresolved unresolved) {
            return new SceneRecoveryPlan(owner(state, lease), unresolved, new SceneContinuation.None());
        }
        @Override public SceneDeathOutcome afterActorDeath(FrontierWorldState state, SceneLease lease, SubjectId actorId, long atTick) {
            SettlementAssault assault = FrontierSettlementAssaultSceneSupport.require(state, cause(lease));
            if (assault.status() != SettlementAssaultStatus.HOT || !assault.overseerId().equals(actorId)) {
                return SceneDeathOutcome.unchanged(state);
            }
            // Keep the same durable assault, roster and authority.  The physical scene may
            // drain naturally, but it can no longer commit a contact receipt or regain a
            // replacement controller from ambient Minecraft state.
            SettlementAssault next = assault.retreatAfterOverseerLoss(actorId);
            if (assault.tacticalPlan().phase() == TacticalPlanPhase.TRAVEL && !assault.march().complete()) {
                ExpeditionMarchIssue issue = new ExpeditionMarchIssue(ExpeditionMarchIssueKind.CONTROLLER_LOST, actorId,
                        assault.march().memberTopologies().get(actorId).edgeAfterCursor(assault.march().cursor()).id(), assault.march().cursor());
                next = assault.recordMarchIssue(issue).retreatAfterOverseerLoss(actorId);
            }
            StrategicPlanState plans = state.strategicPlans().replaceSettlementAssault(next);
            return new SceneDeathOutcome(state.humanPopulation(), state.resourceSites(), plans,
                    state.physicalIntents(), state.serviceWorks(), state.actorExecutions());
        }
    }

    private static final class EngineeringWorksiteBehavior implements SceneBehavior<EngineeringWorkSceneCause> {
        @Override public SceneLeaseStatus recoveredStatus(FrontierWorldState state, SceneLease lease) {
            EngineeringWorkSceneCause cause = cause(lease);
            EngineeringWorkOrder project = EngineeringWorkOrderSupport.require(state, cause.projectId());
            if (project.confirmedCells() == cause.workCellIndex()) {
                return project.building() ? SceneLeaseStatus.HOT : SceneLeaseStatus.DRAINING;
            }
            if (project.confirmedCells() == cause.workCellIndex() + 1) return SceneLeaseStatus.DRAINING;
            throw new IllegalArgumentException("engineering recovery has no exact retained work cell");
        }
        @Override public SceneCauseKind kind() { return SceneCauseKind.ENGINEERING_WORKSITE; }
        @Override public Class<EngineeringWorkSceneCause> causeType() { return EngineeringWorkSceneCause.class; }
        @Override public EngineeringWorkSceneCause sampleCause() { return new EngineeringWorkSceneCause(new SubjectId("construction:registry"), 0); }
        @Override public EngineeringWorkSceneCause engineeringWorksite(SceneLease lease) { return cause(lease); }
        @Override public boolean owns(SceneLease lease, SubjectId subjectId) { return cause(lease).projectId().equals(subjectId); }
        @Override public SubjectId owner(FrontierWorldState state, SceneLease lease) { return FrontierEngineeringWorkSceneSupport.owner(state, cause(lease)); }
        @Override public void validatePrepared(FrontierWorldState state, SceneLease lease) { FrontierEngineeringWorkSceneSupport.validatePrepared(state, lease); }
        @Override public Set<SubjectId> expectedMembers(FrontierBootstrap bootstrap, HumanPopulation population, Map<SubjectId, ActorLocation> actors,
                                                         Map<SubjectId, StructureCondition> structures, Map<SubjectId, RouteOperation> operations,
                                                         Map<SubjectId, RouteConstruction> constructions, Map<SubjectId, RouteMaintenance> maintenances, StrategicPlanState plans,
                                                         ResourceSiteState resourceSites, Map<SubjectId, ProductionJob> productionJobs,
                                                         Map<SubjectId, SettlementServiceWork> serviceWorks, SceneLease lease, Set<SubjectId> leasedOperations) {
            EngineeringWorkSceneCause cause = cause(lease);
            EngineeringWorkOrder project = EngineeringWorkOrderSupport.registry(constructions, maintenances).get(cause.projectId());
            if (project == null || project.engineeringTeam().isEmpty()) {
                throw new IllegalArgumentException("engineering scene must bind its current exact crew");
            }
            boolean currentCell = project.confirmedCells() == cause.workCellIndex();
            boolean activeCurrentCell = currentCell && project.building();
            boolean confirmedCellDraining = project.confirmedCells() == cause.workCellIndex() + 1
                    && (lease.status() == SceneLeaseStatus.DRAINING || lease.status() == SceneLeaseStatus.CLOSED
                    || lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
            boolean interruptedCurrentCellDraining = currentCell && !project.building()
                    && (lease.status() == SceneLeaseStatus.DRAINING || lease.status() == SceneLeaseStatus.CLOSED
                    || lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
            if (!activeCurrentCell && !confirmedCellDraining && !interruptedCurrentCellDraining) {
                throw new IllegalArgumentException("engineering scene cursor differs from its owner lifecycle: project="
                        + project.id().value() + " causeCell=" + cause.workCellIndex() + " confirmedCells=" + project.confirmedCells()
                        + " building=" + project.building() + " leaseStatus=" + lease.status());
            }
            if (activeCurrentCell && (project.assembly().isEmpty() || project.assembly().orElseThrow().purpose() != EngineeringJourneyPurpose.WORKSITE
                    || !project.assembly().orElseThrow().complete())) {
                throw new IllegalArgumentException("engineering scene must wait for its complete COLD approach");
            }
            return Set.copyOf(project.engineeringTeam().orElseThrow().memberIds());
        }
        @Override public StrategicPlanState transitionPlans(FrontierWorldState state, SceneLease lease, SceneLeaseStatus nextStatus) { return state.strategicPlans(); }
        @Override public StrategicPlanState releasePlans(FrontierWorldState state, SceneLease lease) { return state.strategicPlans(); }
        @Override public BodyPosition releasedBody(FrontierWorldState state, SceneLease lease, SubjectId actorId, BodyPosition observed) {
            return observed;
        }
        @Override public SceneReleasePlan releasePlan(FrontierWorldState state, SceneLease lease, long submittedAt, SceneLeaseReleased released) {
            return new SceneReleasePlan(owner(state, lease), released, new SceneContinuation.None());
        }
        @Override public SceneRecoveryPlan recoveryUnresolvedPlan(FrontierWorldState state, SceneLease lease, SceneLeaseRecoveryUnresolved unresolved) {
            return new SceneRecoveryPlan(owner(state, lease), unresolved, new SceneContinuation.None());
        }
    }

    /** The care owner itself, rather than a synthetic medic mob, owns every treatment scene. */
    private static final class MedicalTreatmentBehavior implements SceneBehavior<MedicalTreatmentSceneCause> {
        @Override public SceneLeaseStatus recoveredStatus(FrontierWorldState state, SceneLease lease) {
            return switch (FrontierMedicalTreatmentSceneSupport.require(state, cause(lease)).status()) {
                case COMPLETED, BLOCKED -> SceneLeaseStatus.DRAINING;
                case PREPARED, TREATING, UNKNOWN_AFTER_RESTART -> SceneLeaseStatus.HOT;
            };
        }
        @Override public SceneCauseKind kind() { return SceneCauseKind.MEDICAL_TREATMENT; }
        @Override public Class<MedicalTreatmentSceneCause> causeType() { return MedicalTreatmentSceneCause.class; }
        @Override public MedicalTreatmentSceneCause sampleCause() { return new MedicalTreatmentSceneCause(new SubjectId("medical:registry")); }
        @Override public MedicalTreatmentSceneCause medicalTreatment(SceneLease lease) { return cause(lease); }
        @Override public boolean owns(SceneLease lease, SubjectId subjectId) { return cause(lease).operationId().equals(subjectId); }
        @Override public SubjectId owner(FrontierWorldState state, SceneLease lease) { return FrontierMedicalTreatmentSceneSupport.owner(state, cause(lease)); }
        @Override public void validatePrepared(FrontierWorldState state, SceneLease lease) { FrontierMedicalTreatmentSceneSupport.validatePrepared(state, lease); }
        @Override public Set<SubjectId> expectedMembers(FrontierBootstrap bootstrap, HumanPopulation population, Map<SubjectId, ActorLocation> actors,
                                                         Map<SubjectId, StructureCondition> structures, Map<SubjectId, RouteOperation> operations,
                                                         Map<SubjectId, RouteConstruction> constructions, Map<SubjectId, RouteMaintenance> maintenances, StrategicPlanState plans,
                                                         ResourceSiteState resourceSites, Map<SubjectId, ProductionJob> productionJobs,
                                                         Map<SubjectId, SettlementServiceWork> serviceWorks, SceneLease lease, Set<SubjectId> leasedOperations) {
            MedicalEvacuationOperation operation = population.medicalOperations().get(cause(lease).operationId());
            if (operation == null) throw new IllegalArgumentException("medical scene has no exact operation");
            boolean valid = switch (lease.status()) {
                case PREPARED -> operation.status() == MedicalEvacuationStatus.PREPARED;
                case HOT, DRAINING -> operation.status() == MedicalEvacuationStatus.PREPARED || operation.status() == MedicalEvacuationStatus.TREATING
                        || operation.status() == MedicalEvacuationStatus.UNKNOWN_AFTER_RESTART || operation.status() == MedicalEvacuationStatus.COMPLETED
                        || operation.status() == MedicalEvacuationStatus.BLOCKED;
                case UNKNOWN_AFTER_RESTART -> operation.status() == MedicalEvacuationStatus.PREPARED || operation.status() == MedicalEvacuationStatus.TREATING
                        || operation.status() == MedicalEvacuationStatus.UNKNOWN_AFTER_RESTART
                        || operation.status() == MedicalEvacuationStatus.COMPLETED || operation.status() == MedicalEvacuationStatus.BLOCKED;
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
        @Override public SceneReleasePlan releasePlan(FrontierWorldState state, SceneLease lease, long submittedAt, SceneLeaseReleased released) {
            return new SceneReleasePlan(owner(state, lease), released, new SceneContinuation.None());
        }
        @Override public SceneRecoveryPlan recoveryUnresolvedPlan(FrontierWorldState state, SceneLease lease, SceneLeaseRecoveryUnresolved unresolved) {
            return new SceneRecoveryPlan(owner(state, lease), unresolved, new SceneContinuation.None());
        }
        @Override public SceneDeathOutcome afterActorDeath(FrontierWorldState state, SceneLease lease, SubjectId actorId, long atTick) {
            MedicalEvacuationOperation operation = FrontierMedicalTreatmentSceneSupport.require(state, cause(lease));
            HumanPopulation population = operation.active() && (operation.patientId().equals(actorId) || operation.team().memberIds().contains(actorId))
                    ? state.humanPopulation().transitionMedicalOperation(operation.id(), MedicalEvacuationStatus.BLOCKED, atTick)
                    : state.humanPopulation();
            return new SceneDeathOutcome(population, state.resourceSites(), state.strategicPlans(), state.physicalIntents(), state.serviceWorks(), state.actorExecutions());
        }
    }

    /** One named agricultural worker, not an ambient Villager or synthetic field animation. */
    private static final class ResourceSiteHarvestBehavior implements SceneBehavior<ResourceSiteHarvestSceneCause> {
        @Override public SceneCauseKind kind() { return SceneCauseKind.RESOURCE_SITE_HARVEST; }
        @Override public Class<ResourceSiteHarvestSceneCause> causeType() { return ResourceSiteHarvestSceneCause.class; }
        @Override public SceneLeaseStatus recoveredStatus(FrontierWorldState state, SceneLease lease) {
            return FrontierResourceSiteHarvestSceneSupport.isTerminalReceiptRelease(state, cause(lease))
                    ? SceneLeaseStatus.DRAINING : SceneLeaseStatus.HOT;
        }
        @Override public ResourceSiteHarvestSceneCause sampleCause() { return new ResourceSiteHarvestSceneCause(
                new SubjectId("site:harvest-registry"), new SubjectId("job:site-harvest-registry")); }
        @Override public ResourceSiteHarvestSceneCause resourceSiteHarvest(SceneLease lease) { return cause(lease); }
        @Override public boolean owns(SceneLease lease, SubjectId subjectId) { return cause(lease).jobId().equals(subjectId); }
        @Override public SubjectId owner(FrontierWorldState state, SceneLease lease) {
            return FrontierResourceSiteHarvestSceneSupport.owner(state, cause(lease));
        }
        @Override public void validatePrepared(FrontierWorldState state, SceneLease lease) {
            FrontierResourceSiteHarvestSceneSupport.validatePrepared(state, lease);
        }
        @Override public Set<SubjectId> expectedMembers(FrontierBootstrap bootstrap, HumanPopulation population, Map<SubjectId, ActorLocation> actors,
                                                         Map<SubjectId, StructureCondition> structures, Map<SubjectId, RouteOperation> operations,
                                                         Map<SubjectId, RouteConstruction> constructions, Map<SubjectId, RouteMaintenance> maintenances,
                                                         StrategicPlanState plans, ResourceSiteState resourceSites, Map<SubjectId, ProductionJob> productionJobs,
                                                         Map<SubjectId, SettlementServiceWork> serviceWorks, SceneLease lease, Set<SubjectId> leasedOperations) {
            ResourceSiteHarvestJob job = resourceSites.sites().values().stream().flatMap(site -> site.harvestJobs().values().stream())
                    .filter(value -> value.id().equals(cause(lease).jobId())).findFirst().orElse(null);
            // A CLOSED lease is retained evidence.  Its matching harvest job is deliberately
            // consumed by the immediately subsequent exact depot receipt, so requiring that
            // transient active record here would make the valid close -> receipt sequence
            // impossible. DRAINING and its unresolved recovery/conflict states retain the
            // body-exit obligation through the exact terminal lineage. PREPARED/HOT still
            // require live work; completed work cannot be replayed by recovery.
            if (job == null) {
                if (lease.status() != SceneLeaseStatus.CLOSED
                        && !((lease.status() == SceneLeaseStatus.DRAINING || lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART
                        || lease.status() == SceneLeaseStatus.CONFLICT)
                        && FrontierResourceSiteHarvestSceneSupport.isTerminalReceiptRelease(bootstrap, resourceSites, cause(lease)))) {
                    throw new IllegalArgumentException("resource-site scene has no exact active harvest");
                }
                if (lease.status() != SceneLeaseStatus.CLOSED) {
                    var site = FrontierResourceSiteHarvestSceneSupport.terminalReceiptSite(bootstrap, resourceSites, cause(lease));
                    return Set.of(resourceSites.site(site.id()).harvestLineages().values().stream()
                            .filter(lineage -> lineage.predecessorJobId().equals(cause(lease).jobId()))
                            .reduce((left, right) -> { throw new IllegalArgumentException("duplicate terminal harvest owner"); })
                            .orElseThrow().workerId());
                }
                return lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toUnmodifiableSet());
            }
            return Set.of(job.workerId());
        }
        @Override public StrategicPlanState transitionPlans(FrontierWorldState state, SceneLease lease, SceneLeaseStatus nextStatus) { return state.strategicPlans(); }
        @Override public StrategicPlanState releasePlans(FrontierWorldState state, SceneLease lease) { return state.strategicPlans(); }
        @Override public BodyPosition releasedBody(FrontierWorldState state, SceneLease lease, SubjectId actorId, BodyPosition observed) {
            ResourceSiteHarvestJob job = null;
            try { job = FrontierResourceSiteHarvestSceneSupport.require(state, cause(lease)); }
            catch (IllegalArgumentException missing) {
                if (!FrontierResourceSiteHarvestSceneSupport.isTerminalReceiptRelease(state, cause(lease))) throw missing;
                if (lease.members().size() != 1 || !lease.members().getFirst().actorId().equals(actorId)) {
                    throw new IllegalArgumentException("terminal resource-site scene release has a foreign worker");
                }
                // The completed job has no retained next cursor. Preserve the one observed
                // body position so its following ambient/successor lifecycle starts from the
                // same exact actor rather than reusing the last crop station as a fake cursor.
                return observed;
            }
            if (!job.workerId().equals(actorId)) throw new IllegalArgumentException("resource-site scene release has a foreign worker");
            if (ResourceSiteHarvestGoal.actorAtDepot(state, job)) {
                if (!ResourceSiteHarvestGoal.current(state, job).arrivedAt(observed.supportingSurface()))
                    throw new IllegalArgumentException("resource-site depot release has no observed service station");
                return observed;
            }
            // The observed supported body is the COLD/HOT hand-off authority.  A route
            // checkpoint is historical work context, not permission to move the farmer.
            return observed;
        }
        @Override public SceneReleasePlan releasePlan(FrontierWorldState state, SceneLease lease, long submittedAt, SceneLeaseReleased released) {
            ResourceSiteHarvestJob job = null;
            try { job = FrontierResourceSiteHarvestSceneSupport.require(state, cause(lease)); }
            catch (IllegalArgumentException missing) {
                if (!FrontierResourceSiteHarvestSceneSupport.isTerminalReceiptRelease(state, cause(lease))) throw missing;
                return new SceneReleasePlan(owner(state, lease), released, new SceneContinuation.None());
            }
            if (state.resourceSites().site(job.siteId()).phase() != ResourceSitePhase.HARVESTING) {
                return new SceneReleasePlan(owner(state, lease), released, new SceneContinuation.None());
            }
            return new SceneReleasePlan(owner(state, lease), released,
                    new SceneContinuation.ResumeResourceSiteHarvest(job.siteId(), job.id()));
        }
        @Override public SceneRecoveryPlan recoveryUnresolvedPlan(FrontierWorldState state, SceneLease lease, SceneLeaseRecoveryUnresolved unresolved) {
            return new SceneRecoveryPlan(owner(state, lease), unresolved, new SceneContinuation.None());
        }
        @Override public SceneDeathOutcome afterActorDeath(FrontierWorldState state, SceneLease lease, SubjectId actorId, long atTick) {
            if (FrontierResourceSiteHarvestSceneSupport.isTerminalReceiptRelease(state, cause(lease))) {
                var site = FrontierResourceSiteHarvestSceneSupport.site(state, cause(lease));
                if (!state.resourceSites().site(site.id()).harvestLineages().values().stream()
                        .filter(lineage -> lineage.predecessorJobId().equals(cause(lease).jobId()))
                        .reduce((left, right) -> { throw new IllegalArgumentException("duplicate terminal harvest owner"); })
                        .orElseThrow().workerId().equals(actorId)) {
                    throw new IllegalArgumentException("terminal harvest death has a foreign worker");
                }
                // The common death owner retires body custody. Completed economic work and
                // its already confirmed output must not be reopened as an UNKNOWN effect.
                return SceneDeathOutcome.unchanged(state);
            }
            ResourceSiteHarvestJob job = FrontierResourceSiteHarvestSceneSupport.require(state, cause(lease));
            if (!job.workerId().equals(actorId)) return SceneDeathOutcome.unchanged(state);
            ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
            ResourceSite site = state.resourceSite(job.siteId());
            ResourceSiteConflictObserved conflict = new ResourceSiteConflictObserved(job.siteId(), site.cropSlots().getFirst(),
                    ResourceSiteDiagnosticProducer.WORKER_DIED);
            ResourceSiteState sites = state.resourceSites().replace(lifecycle.conflicted(
                    ResourceSiteConflictDisposition.terminal(site.cropSlots().getFirst(), ResourceSiteConflictReason.WORKER_DIED,
                            ResourceSiteConflictIncidents.first(lifecycle, conflict))));
            StrategicPlanState plans = state.strategicPlans().transitionTask(job.taskId(), StrategicTaskStatus.BLOCKED);
            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent = state.physicalIntents().get(job.intentId());
            if (intent == null) throw new IllegalArgumentException("dead harvest worker has no exact physical intent");
            Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents = new LinkedHashMap<>(state.physicalIntents());
            intents.put(intent.id(), intent.withRecoveryUnknown(PhysicalIntentRecoveryDiagnosticProducer.RESOURCE_SITE_HARVEST.stamp(intent)));
            return new SceneDeathOutcome(state.humanPopulation(), sites, plans, Map.copyOf(intents), state.serviceWorks(), state.actorExecutions());
        }
    }

    /** One named industrial worker, never an ambient Villager substituted at the workshop. */
    private static final class ProductionWorkBehavior implements SceneBehavior<ProductionWorkSceneCause> {
        @Override public SceneLeaseStatus recoveredStatus(FrontierWorldState state, SceneLease lease) {
            ProductionJob job = FrontierProductionWorkSceneSupport.require(state, cause(lease));
            return blocked(state, job) || job.bakeryWork().isEmpty() && job.workProgress().terminalEffectEligible()
                    ? SceneLeaseStatus.DRAINING : SceneLeaseStatus.HOT;
        }
        private boolean blocked(FrontierWorldState state, ProductionJob job) {
            return state.companies().market().workOrders().values().stream()
                    .filter(value -> value.jobId().equals(job.id())).findFirst()
                    .map(value -> state.strategicPlans().tasks().get(value.taskId()))
                    .map(task -> task.status() == StrategicTaskStatus.BLOCKED).orElse(false);
        }
        @Override public SceneCauseKind kind() { return SceneCauseKind.PRODUCTION_WORK; }
        @Override public Class<ProductionWorkSceneCause> causeType() { return ProductionWorkSceneCause.class; }
        @Override public ProductionWorkSceneCause sampleCause() { return new ProductionWorkSceneCause(new SubjectId("job:production-registry")); }
        @Override public ProductionWorkSceneCause productionWork(SceneLease lease) { return cause(lease); }
        @Override public boolean owns(SceneLease lease, SubjectId subjectId) { return cause(lease).jobId().equals(subjectId); }
        @Override public SubjectId owner(FrontierWorldState state, SceneLease lease) { return FrontierProductionWorkSceneSupport.owner(state, cause(lease)); }
        @Override public void validatePrepared(FrontierWorldState state, SceneLease lease) { FrontierProductionWorkSceneSupport.validatePrepared(state, lease); }
        @Override public Set<SubjectId> expectedMembers(FrontierBootstrap bootstrap, HumanPopulation population, Map<SubjectId, ActorLocation> actors,
                                                         Map<SubjectId, StructureCondition> structures, Map<SubjectId, RouteOperation> operations,
                                                         Map<SubjectId, RouteConstruction> constructions, Map<SubjectId, RouteMaintenance> maintenances,
                                                         StrategicPlanState plans, ResourceSiteState resourceSites, Map<SubjectId, ProductionJob> productionJobs,
                                                         Map<SubjectId, SettlementServiceWork> serviceWorks, SceneLease lease, Set<SubjectId> leasedOperations) {
            ProductionJob job = productionJobs.get(cause(lease).jobId());
            if (job == null && lease.status() == SceneLeaseStatus.CLOSED) return lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toUnmodifiableSet());
            return Set.of(Objects.requireNonNull(job, "production-work scene job").workerId());
        }
        @Override public StrategicPlanState transitionPlans(FrontierWorldState state, SceneLease lease, SceneLeaseStatus nextStatus) { return state.strategicPlans(); }
        @Override public StrategicPlanState releasePlans(FrontierWorldState state, SceneLease lease) { return state.strategicPlans(); }
        @Override public BodyPosition releasedBody(FrontierWorldState state, SceneLease lease, SubjectId actorId, BodyPosition observed) {
            ProductionJob job = FrontierProductionWorkSceneSupport.require(state, cause(lease));
            if (!job.workerId().equals(actorId)) throw new IllegalArgumentException("production scene release has a foreign worker");
            if (job.bakeryWork().isPresent()) return observed;
            return job.workTraversal().linearCorridorSurfaces().get(job.traversalCursor()).standingBody();
        }
        @Override public SceneReleasePlan releasePlan(FrontierWorldState state, SceneLease lease, long submittedAt, SceneLeaseReleased released) {
            ProductionJob job = FrontierProductionWorkSceneSupport.require(state, cause(lease));
            SceneContinuation continuation = blocked(state, job) ? new SceneContinuation.FinalizeProductionWork(lease.id(), job.id())
                    // The bakery retains its own stable scheduled review across every
                    // custody phase. The legacy completion continuation only accepts an
                    // output-ready corridor job and must not dispatch for a bakery handoff.
                    : job.bakeryWork().isPresent() ? new SceneContinuation.None()
                    : job.workProgress().terminalEffectEligible()
                    ? new SceneContinuation.ResumeProductionCompletion(job.id(), Math.addExact(submittedAt, 1L))
                    : new SceneContinuation.None();
            return new SceneReleasePlan(owner(state, lease), released, continuation);
        }
        @Override public SceneRecoveryPlan recoveryUnresolvedPlan(FrontierWorldState state, SceneLease lease, SceneLeaseRecoveryUnresolved unresolved) {
            return new SceneRecoveryPlan(owner(state, lease), unresolved, new SceneContinuation.None());
        }
    }

    /** One named resident and one retained field/workshop station, never an ambient substitute. */
    private static final class SettlementServiceWorkBehavior implements SceneBehavior<SettlementServiceWorkSceneCause> {
        @Override public SceneLeaseStatus recoveredStatus(FrontierWorldState state, SceneLease lease) {
            return switch (FrontierSettlementServiceWorkSceneSupport.require(state, cause(lease)).phase()) {
                case COMPLETED, BLOCKED -> SceneLeaseStatus.DRAINING;
                case PREPARED, APPROACH_INPUT, INPUT_ISSUE_PENDING, APPROACH_WORK, WORKING,
                        EFFECT_READY, UNKNOWN_AFTER_RESTART -> SceneLeaseStatus.HOT;
            };
        }
        @Override public SceneCauseKind kind() { return SceneCauseKind.SERVICE_WORK; }
        @Override public Class<SettlementServiceWorkSceneCause> causeType() { return SettlementServiceWorkSceneCause.class; }
        @Override public SettlementServiceWorkSceneCause sampleCause() { return new SettlementServiceWorkSceneCause(new SubjectId("service:registry")); }
        @Override public SettlementServiceWorkSceneCause serviceWork(SceneLease lease) { return cause(lease); }
        @Override public boolean owns(SceneLease lease, SubjectId subjectId) { return cause(lease).workId().equals(subjectId); }
        @Override public SubjectId owner(FrontierWorldState state, SceneLease lease) {
            return FrontierSettlementServiceWorkSceneSupport.owner(state, cause(lease));
        }
        @Override public void validatePrepared(FrontierWorldState state, SceneLease lease) {
            FrontierSettlementServiceWorkSceneSupport.validatePrepared(state, lease);
        }
        @Override public Set<SubjectId> expectedMembers(FrontierBootstrap bootstrap, HumanPopulation population, Map<SubjectId, ActorLocation> actors,
                                                         Map<SubjectId, StructureCondition> structures, Map<SubjectId, RouteOperation> operations,
                                                         Map<SubjectId, RouteConstruction> constructions, Map<SubjectId, RouteMaintenance> maintenances,
                                                         StrategicPlanState plans, ResourceSiteState resourceSites, Map<SubjectId, ProductionJob> productionJobs,
                                                         Map<SubjectId, SettlementServiceWork> serviceWorks, SceneLease lease, Set<SubjectId> leasedOperations) {
            SettlementServiceWork work = serviceWorks.get(cause(lease).workId());
            if (work == null) {
                if (lease.status() != SceneLeaseStatus.CLOSED) throw new IllegalArgumentException("service-work scene has no exact active work");
                return lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toUnmodifiableSet());
            }
            boolean lifecycleMatches = switch (lease.status()) {
                case PREPARED -> work.phase() == SettlementServiceWorkPhase.PREPARED || work.phase() == SettlementServiceWorkPhase.APPROACH_INPUT;
                // The exact worker remains HOT for the bounded terminal adapter.  Draining at
                // EFFECT_READY would erase the actor/body precondition before the observed
                // decontamination effect can be made durable and reconciled.
                case HOT -> FrontierSettlementServiceWorkSceneSupport.sceneEligible(work.phase())
                        || work.phase() == SettlementServiceWorkPhase.EFFECT_READY || work.phase() == SettlementServiceWorkPhase.BLOCKED
                        // A reclaimed exact body resumes the same uncertain physical effect;
                        // only its observed postcondition may complete it.
                        || work.phase() == SettlementServiceWorkPhase.UNKNOWN_AFTER_RESTART;
                case DRAINING -> work.phase() == SettlementServiceWorkPhase.EFFECT_READY || work.phase() == SettlementServiceWorkPhase.COMPLETED
                        || work.phase() == SettlementServiceWorkPhase.BLOCKED;
                // Losing custody of a HOT body at server stop is not evidence that an
                // unstarted input/work/effect changed.  Preserve its retained cursor and
                // stage while the lease is UNKNOWN; a physical-intent inspector separately
                // moves the work itself to UNKNOWN_AFTER_RESTART if an effect was in flight.
                case UNKNOWN_AFTER_RESTART -> work.phase().active() || work.phase() == SettlementServiceWorkPhase.COMPLETED
                        || work.phase() == SettlementServiceWorkPhase.BLOCKED;
                case CONFLICT -> work.phase().active();
                case CLOSED -> work.phase() == SettlementServiceWorkPhase.COMPLETED || work.phase() == SettlementServiceWorkPhase.BLOCKED
                        || work.phase() == SettlementServiceWorkPhase.UNKNOWN_AFTER_RESTART;
            };
            if (!lifecycleMatches) throw new IllegalArgumentException("service-work scene and aggregate lifecycle disagree");
            return Set.of(work.workerId());
        }
        @Override public StrategicPlanState transitionPlans(FrontierWorldState state, SceneLease lease, SceneLeaseStatus nextStatus) {
            return state.strategicPlans();
        }
        @Override public StrategicPlanState releasePlans(FrontierWorldState state, SceneLease lease) { return state.strategicPlans(); }
        @Override public BodyPosition releasedBody(FrontierWorldState state, SceneLease lease, SubjectId actorId, BodyPosition observed) {
            SettlementServiceWork work = FrontierSettlementServiceWorkSceneSupport.require(state, cause(lease));
            if (!work.workerId().equals(actorId)) throw new IllegalArgumentException("service-work scene release has a foreign worker");
            return observed;
        }
        @Override public SceneReleasePlan releasePlan(FrontierWorldState state, SceneLease lease, long submittedAt, SceneLeaseReleased released) {
            return new SceneReleasePlan(owner(state, lease), released, new SceneContinuation.None());
        }
        @Override public SceneRecoveryPlan recoveryUnresolvedPlan(FrontierWorldState state, SceneLease lease, SceneLeaseRecoveryUnresolved unresolved) {
            return new SceneRecoveryPlan(owner(state, lease), unresolved, new SceneContinuation.None());
        }
        @Override public SceneDeathOutcome afterActorDeath(FrontierWorldState state, SceneLease lease, SubjectId actorId, long atTick) {
            SettlementServiceWork work = FrontierSettlementServiceWorkSceneSupport.require(state, cause(lease));
            if (!work.workerId().equals(actorId)) return SceneDeathOutcome.unchanged(state);
            Map<SubjectId, SettlementServiceWork> works = new LinkedHashMap<>(state.serviceWorks());
            works.put(work.id(), work.withPhase(SettlementServiceWorkPhase.BLOCKED, 0));
            Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents = new LinkedHashMap<>(state.physicalIntents());
            for (io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId intentId : java.util.List.of(work.inputIssueIntentId(), work.endpointIntentId())) {
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent = intents.get(intentId);
                if (intent != null && (intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.PREPARED
                        || intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING)) {
                    PhysicalIntentRecoveryDiagnosticProducer producer = switch (intent.lifecycleOwner()) {
                        case SETTLEMENT_SERVICE_WORK -> PhysicalIntentRecoveryDiagnosticProducer.SETTLEMENT_SERVICE_WORK;
                        case SETTLEMENT_SERVICE_DECONTAMINATION -> PhysicalIntentRecoveryDiagnosticProducer.SETTLEMENT_SERVICE_DECONTAMINATION;
                        default -> throw new IllegalArgumentException("service-work scene has a foreign lifecycle owner");
                    };
                    intents.put(intent.id(), intent.withRecoveryUnknown(producer.stamp(intent)));
                }
            }
            return new SceneDeathOutcome(state.humanPopulation(), state.resourceSites(), state.strategicPlans(), Map.copyOf(intents), Map.copyOf(works), state.actorExecutions());
        }
    }

    /** Class-D route patrol: generic guard motion never substitutes for this retained formation. */
    private static final class RoutePatrolBehavior implements SceneBehavior<RoutePatrolSceneCause> {
        @Override public SceneLeaseStatus recoveredStatus(FrontierWorldState state, SceneLease lease) {
            return FrontierRoutePatrolSceneSupport.require(state, cause(lease)).active()
                    ? SceneLeaseStatus.HOT : SceneLeaseStatus.DRAINING;
        }
        @Override public SceneCauseKind kind() { return SceneCauseKind.ROUTE_PATROL; }
        @Override public Class<RoutePatrolSceneCause> causeType() { return RoutePatrolSceneCause.class; }
        @Override public RoutePatrolSceneCause sampleCause() { return new RoutePatrolSceneCause(new SubjectId("task:route-patrol-registry")); }
        @Override public RoutePatrolSceneCause routePatrol(SceneLease lease) { return cause(lease); }
        @Override public boolean owns(SceneLease lease, SubjectId subjectId) { return cause(lease).taskId().equals(subjectId); }
        @Override public SubjectId owner(FrontierWorldState state, SceneLease lease) {
            return FrontierRoutePatrolSceneSupport.owner(state, cause(lease));
        }
        @Override public void validatePrepared(FrontierWorldState state, SceneLease lease) {
            FrontierRoutePatrolSceneSupport.validatePrepared(state, lease);
        }
        @Override public Set<SubjectId> expectedMembers(FrontierBootstrap bootstrap, HumanPopulation population, Map<SubjectId, ActorLocation> actors,
                                                         Map<SubjectId, StructureCondition> structures, Map<SubjectId, RouteOperation> operations,
                                                         Map<SubjectId, RouteConstruction> constructions, Map<SubjectId, RouteMaintenance> maintenances,
                                                         StrategicPlanState plans, ResourceSiteState resourceSites, Map<SubjectId, ProductionJob> productionJobs,
                                                         Map<SubjectId, SettlementServiceWork> serviceWorks, SceneLease lease, Set<SubjectId> leasedOperations) {
            RoutePatrol patrol = plans.routePatrols().get(cause(lease).taskId());
            if (patrol == null) {
                if (lease.status() != SceneLeaseStatus.CLOSED) throw new IllegalArgumentException("route-patrol scene has no retained patrol");
                return lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toUnmodifiableSet());
            }
            boolean allowed = switch (lease.status()) {
                case PREPARED -> patrol.active();
                // A terminal patrol can remain HOT only for the one bounded drain turn which
                // captures the same exact surviving formation. It may never resume movement.
                case HOT -> patrol.active() || patrol.status() == RoutePatrolStatus.ROUTE_CLEAR
                        || patrol.status() == RoutePatrolStatus.OBSTRUCTION_CONFIRMED || patrol.status() == RoutePatrolStatus.BLOCKED
                        || patrol.status() == RoutePatrolStatus.FAILED;
                case DRAINING -> patrol.active() || patrol.status() == RoutePatrolStatus.ROUTE_CLEAR
                        || patrol.status() == RoutePatrolStatus.OBSTRUCTION_CONFIRMED || patrol.status() == RoutePatrolStatus.BLOCKED
                        || patrol.status() == RoutePatrolStatus.FAILED;
                case UNKNOWN_AFTER_RESTART, CONFLICT -> patrol.active() || patrol.status() == RoutePatrolStatus.BLOCKED
                        || patrol.status() == RoutePatrolStatus.FAILED || patrol.status() == RoutePatrolStatus.ROUTE_CLEAR
                        || patrol.status() == RoutePatrolStatus.OBSTRUCTION_CONFIRMED;
                case CLOSED -> true;
            };
            if (!allowed) throw new IllegalArgumentException("route-patrol scene and retained patrol lifecycle disagree");
            return Set.copyOf(patrol.memberIds());
        }
        @Override public StrategicPlanState transitionPlans(FrontierWorldState state, SceneLease lease, SceneLeaseStatus nextStatus) {
            return state.strategicPlans();
        }
        @Override public StrategicPlanState releasePlans(FrontierWorldState state, SceneLease lease) { return state.strategicPlans(); }
        @Override public BodyPosition releasedBody(FrontierWorldState state, SceneLease lease, SubjectId actorId, BodyPosition observed) {
            return FrontierRoutePatrolSceneSupport.releasedBody(state, lease, actorId, observed);
        }
        @Override public SceneReleasePlan releasePlan(FrontierWorldState state, SceneLease lease, long submittedAt, SceneLeaseReleased released) {
            RoutePatrol patrol = FrontierRoutePatrolSceneSupport.require(state, cause(lease));
            SceneContinuation continuation = patrol.active()
                    ? new SceneContinuation.ResumeRoutePatrol(patrol.taskId(), Math.addExact(submittedAt,
                    state.bootstrap().ruleset().cadence().routePatrolStepInterval()))
                    : new SceneContinuation.None();
            return new SceneReleasePlan(owner(state, lease), released, continuation);
        }
        @Override public SceneRecoveryPlan recoveryUnresolvedPlan(FrontierWorldState state, SceneLease lease, SceneLeaseRecoveryUnresolved unresolved) {
            return new SceneRecoveryPlan(owner(state, lease), unresolved,
                    new SceneContinuation.BlockRoutePatrol(cause(lease).taskId()));
        }
        @Override public SceneDeathOutcome afterActorDeath(FrontierWorldState state, SceneLease lease, SubjectId actorId, long atTick) {
            RoutePatrol patrol = FrontierRoutePatrolSceneSupport.require(state, cause(lease));
            if (!patrol.memberIds().contains(actorId) || !patrol.active()) return SceneDeathOutcome.unchanged(state);
            // Death is the patrol's terminal evidence.  Keep task and patrol terminal states in
            // the same authoritative transaction; a later generic continuation would leave a
            // briefly active task with a dead member and permit a second planner to race it.
            StrategicPlanState plans = state.strategicPlans().blockPatrol(patrol.taskId(), RoutePatrolBlockReason.MISSING_OWNED_BODY)
                    .transitionTask(patrol.taskId(), StrategicTaskStatus.BLOCKED);
            return new SceneDeathOutcome(state.humanPopulation(), state.resourceSites(), plans,
                    state.physicalIntents(), state.serviceWorks(), RoutePatrolExecutionAuthority.retired(state, patrol));
        }
    }
}
