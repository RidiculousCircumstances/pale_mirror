package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection; import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent; import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind; import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition; import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.EngineLimits;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionCommitter;
import java.util.List;
/** Pure composition root for the fresh 1024x1024 Frontier v3 profile. */
public final class FrontierWorldRuntimeDefinition {
    public static final SubjectId PHYSICAL_EXECUTOR = new SubjectId("system:physical_executor");
    private FrontierWorldRuntimeDefinition() { }
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId worldId, long seed) { return configuration(worldId, seed, true); }
    /** Development-only uncontested logistics fixture; production always uses {@link #configuration(WorldId, long)}. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> developmentUncontestedSupplyConfiguration(WorldId worldId, long seed) {
        return configuration(worldId, seed, false);
    }
    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId worldId, long seed, boolean autonomousInterception) {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(worldId, seed); FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        return new FrontierEngineConfiguration<>(worldId, initial, SimInstant.ZERO, FrontierWorldRuntimeDefinition::planCommand,
                (state, action) -> planScheduled(state, action, autonomousInterception), FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(bootstrap), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096), initialSchedule(bootstrap), TransactionCommitter.noOp()); }
    /** Development-only deterministic scene fixture; production always uses {@link #configuration(WorldId, long)}. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> developmentHotSceneStrikeConfiguration(WorldId worldId, long seed) {
        FrontierWorldState initial = FrontierDevelopmentScenarios.hotSceneStrikeState(worldId, seed);
        return new FrontierEngineConfiguration<>(worldId, initial, new SimInstant(2_600L), FrontierWorldRuntimeDefinition::planCommand,
                (state, action) -> planScheduled(state, action, true), FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(initial.bootstrap()), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096), List.of(), TransactionCommitter.noOp()); }
    private static List<ScheduledAction> initialSchedule(FrontierBootstrap bootstrap) {
        List<ScheduledAction> actions = new java.util.ArrayList<>(List.of(StructuralRepairProcess.scan(1, 800),
                RouteConstructionProcess.scan(1, 900), DecontaminationProcess.scan(1, 1_000)));
        for (int index = 0; index < bootstrap.settlements().size(); index++) {
            actions.add(StrategicObjectiveProcess.review(bootstrap.settlements().get(index).id(), 1, 2_000L + index * 100L));
            actions.add(PopulationBirthProcess.review(bootstrap.settlements().get(index).id(), 1, 6_000L + index * 100L));
        }
        FrontierResourceSitePlan.compile(bootstrap).keySet().stream().sorted().forEach(site -> actions.add(ResourceSiteProcess.preparation(site, ResourceSiteProcess.INITIAL_PREPARATION_TICK)));
        actions.add(StrategicObjectiveProcess.review(bootstrap.hive().id(), 1, 3_200L)); return List.copyOf(actions);
    }
    public static PayloadCodecs payloadCodecs() { return FrontierWorldPayloadCodecs.create(); } static CommandPlan planCommand(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command) {
        if (!PHYSICAL_EXECUTOR.equals(command.actor())) {
            return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                    io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, "command is not from the trusted physical executor"));
        }
        if (command.payload() instanceof ResidentBorn birth) {
            return rejected("resident birth is emitted only by a confirmed population permit");
        }
        if (command.payload() instanceof ResidentMigrated migration) {
            try { state.recordResidentMigration(migration); } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(migration.destinationSettlementId(), migration)));
        }
        if (command.payload() instanceof PhysicalIntentTransition || command.payload() instanceof PhysicalIntentPrepared) {
            return FrontierPhysicalIntentCommandProcess.plan(state, command);
        }
        if (command.payload() instanceof SceneLeasePrepared prepared) {
            RouteOperation operation = state.operations().get(prepared.lease().operationId()); if (operation == null) return rejected("scene lease has no owning operation");
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), prepared)));
        }
        if (command.payload() instanceof SceneLeaseHandoff handoff) {
            RouteOperation operation = state.operations().get(handoff.lease().operationId()); if (operation == null) return rejected("scene hand-off has no owning operation");
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), handoff)));
        }
        if (command.payload() instanceof SceneLeaseTransition transition) {
            SceneLease lease = state.sceneLeases().get(transition.leaseId()); if (lease == null) return rejected("scene lease is unknown");
            RouteOperation operation = state.operations().get(lease.operationId());
            if (operation == null) return rejected("scene lease has no owning operation");
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), transition)));
        }
        if (command.payload() instanceof SceneLeaseReleased released) {
            SceneLease lease = state.sceneLeases().get(released.leaseId()); if (lease == null) return rejected("scene lease is unknown");
            RouteOperation operation = state.operations().get(lease.operationId());
            if (operation == null) return rejected("scene lease has no owning operation");
            if (lease.engagementId().isPresent()) {
                RouteEngagement engagement = state.strategicPlans().routeEngagements().get(lease.engagementId().orElseThrow());
                if (engagement == null || engagement.status() != RouteEngagementStatus.HOT) return rejected("scene lease has no HOT engagement to resume");
                return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), released),
                        new ProposedEvent(operation.settlementId(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(
                                HiveRouteEngagementProcess.combat(engagement, command.submittedAt().ticks() + 20L)))));
            }
            if (operation.participantIds().stream().anyMatch(actor -> state.actorLocations().get(actor).condition().status() == ActorLifeStatus.DEAD)) {
                List<ProposedEvent> events = new java.util.ArrayList<>(); events.add(new ProposedEvent(operation.settlementId(), released));
                events.addAll(SupplyOperationProcess.failed(state, operation, "actor-death")); return new CommandPlan.Accepted(List.copyOf(events));
            }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), released),
                    new ProposedEvent(operation.settlementId(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(
                            SupplyOperationProcess.operationProgress(operation, command.submittedAt().ticks() + 100L)))));
        }
        if (command.payload() instanceof SceneLeaseRecoveryUnresolved unresolved) {
            SceneLease lease = state.sceneLeases().get(unresolved.leaseId());
            if (lease == null || lease.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART || lease.recoveryEvidence().isPresent()) {
                return rejected("scene recovery evidence does not bind one unresolved restart lease");
            }
            if (lease.engagementId().isPresent()) return rejected("engagement scene recovery needs its own outcome policy");
            RouteOperation operation = state.operations().get(lease.operationId());
            if (operation == null || operation.stage() != OperationStage.EN_ROUTE) return rejected("scene recovery evidence has no active route operation");
            try {
                List<ProposedEvent> events = new java.util.ArrayList<>();
                events.add(new ProposedEvent(operation.settlementId(), unresolved));
                events.addAll(SupplyOperationProcess.failed(state, operation, "scene-recovery-unresolved"));
                return new CommandPlan.Accepted(List.copyOf(events));
            } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ActorDied death) {
            SceneLease lease = state.sceneLeases().get(death.leaseId());
            if (lease == null || (lease.status() != SceneLeaseStatus.HOT && lease.status() != SceneLeaseStatus.DRAINING)
                    || lease.members().stream().noneMatch(member -> member.actorId().equals(death.actorId()))) {
                return rejected("actor death is not evidence for an active scene member");
            }
            RouteOperation operation = state.operations().get(lease.operationId());
            if (operation == null) return rejected("actor death has no owning operation");
            List<ProposedEvent> events = new java.util.ArrayList<>();
            events.add(new ProposedEvent(operation.settlementId(), death));
            if (lease.status() == SceneLeaseStatus.HOT) {
                events.add(new ProposedEvent(operation.settlementId(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING)));
            }
            return new CommandPlan.Accepted(List.copyOf(events));
        }
        if (command.payload() instanceof AmbientActorDied death) return AmbientActorProcess.plan(state, death);
        if (command.payload() instanceof AmbientActorObserved observation) return AmbientActorProcess.plan(state, observation);
        if (command.payload() instanceof AmbientLeasePrepared || command.payload() instanceof AmbientLeaseTransition || command.payload() instanceof AmbientLeaseReleased) return AmbientActorProcess.planLease(state, command.payload());
        if (command.payload() instanceof ResourceSiteConflictObserved conflict) try { return new CommandPlan.Accepted(ResourceSiteProcess.planConflict(state, conflict)); }
        catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        if (command.payload() instanceof StructureDamaged damage) {
            try {
                state.recordStructureDamage(damage);
            } catch (IllegalArgumentException invalid) {
                return rejected(invalid.getMessage());
            }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierWorldStateSupport.structureSettlement(state.bootstrap(), damage.structureId()), damage)));
        }
        if (command.payload() instanceof PhysicalDeltaObserved observed) return FrontierWorldPhysicalObservationProcess.plan(state, observed);
        if (command.payload() instanceof ResourceDeposited deposited) return FrontierWorldPhysicalObservationProcess.planResourceDeposit(state, deposited);
        if (command.payload() instanceof ExactItemCustodyChanged changed) {
            ExactItemStack item = state.inventory().items().get(changed.itemId());
            if (item == null || !item.custody().equals(changed.from())) return rejected("observed item source differs from canonical custody");
            return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierWorldStateSupport.itemOwner(state, changed), changed)));
        }
        if (command.payload() instanceof ExactItemDestroyed destroyed) {
            ExactItemStack item = state.inventory().items().get(destroyed.itemId());
            if (item == null || !item.custody().equals(destroyed.source())) return rejected("destroyed item source differs from canonical custody");
            return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierWorldStateSupport.itemOwner(state, destroyed), destroyed)));
        }
        if (command.payload() instanceof CargoCarrierReleased released) {
            SceneLease lease = state.sceneLeases().get(released.leaseId());
            if (lease == null || !lease.cargoId().equals(released.cargoId()) || lease.status() != SceneLeaseStatus.HOT) {
                return rejected("cargo carrier release lacks one HOT matching scene lease");
            }
            if (!CargoCarrierIdentity.id(lease).equals(released.carrierId())) return rejected("cargo carrier identity is not canonical for its scene");
            RouteOperation operation = state.operations().get(lease.operationId());
            if (operation == null) return rejected("cargo carrier release has no owning operation");
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), released)));
        }
        if (command.payload() instanceof InventoryConflictObserved observed) {
            InventoryConflict conflict = observed.conflict();
            ContainerRecord container = state.inventory().containers().get(conflict.containerId());
            if (container == null || conflict.slot() >= container.slotCount()
                    || (!state.inventory().items().containsKey(conflict.subjectId()) && !state.inventory().containers().containsKey(conflict.subjectId()))) {
                return rejected("inventory conflict references an unknown exact surface");
            }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(container.ownerId(), observed)));
        }
        if (command.payload() instanceof ContainerSurfaceTransition transition) {
            return ContainerSurfaceProcess.plan(state, transition);
        }
        return rejected("command is not a trusted physical transition or scene lease");
    }
    private static CommandPlan.Rejected rejected(String message) { return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, message));
    }
    static List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planScheduled(FrontierWorldState state, ScheduledAction action) {
        return planScheduled(state, action, true);
    }
    private static List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planScheduled(FrontierWorldState state, ScheduledAction action,
                                                                                               boolean autonomousInterception) {
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = switch (action.kind()) {
            case "frontier.hive.infection.task" -> HiveInfectionProcess.plan(state, action);
            case "frontier.settlement.production.task.start" -> ProductionProcess.planStart(state, action);
            case "frontier.settlement.production.task.complete" -> ProductionProcess.planCompletion(state, action);
            case "frontier.supply.task.start" -> SupplyOperationProcess.planStart(state, action);
            case "frontier.supply.cargo.load" -> SupplyOperationProcess.planCargoLoad(state, action, autonomousInterception);
            case "frontier.operation.progress" -> SupplyOperationProcess.planProgress(state, action);
            case "frontier.hive.growth.task.start" -> HiveGrowthProcess.planStart(state, action);
            case "frontier.hive.growth.task.complete" -> HiveGrowthProcess.planCompletion(state, action);
            case "frontier.population.birth.review" -> PopulationBirthProcess.planReview(state, action);
            case "frontier.population.birth.complete" -> PopulationBirthProcess.planCompletion(state, action);
            case "frontier.resource_site.growth" -> ResourceSiteProcess.planGrowth(state, action);
            case "frontier.resource_site.prepare" -> ResourceSiteProcess.planPreparation(state, action);
            case "frontier.resource_site.harvest" -> ResourceSiteHarvestProcess.plan(state, action);
            case "frontier.objective.resource_harvest" -> StrategicObjectiveProcess.planResourceHarvestOpportunity(state, action);
            case "frontier.structural_repair.scan" -> StructuralRepairProcess.plan(state, action);
            case "frontier.route_construction.scan" -> RouteConstructionProcess.plan(state, action);
            case "frontier.route_construction.start" -> RouteConstructionProcess.planStart(state, action);
            case "frontier.route_patrol.start" -> RoutePatrolProcess.planStart(state, action);
            case "frontier.route_patrol.progress" -> RoutePatrolProcess.planProgress(state, action);
            case "frontier.hive_route_engagement.start" -> HiveRouteEngagementProcess.planStart(state, action);
            case "frontier.hive_route_engagement.progress" -> HiveRouteEngagementProcess.planProgress(state, action);
            case "frontier.hive_route_engagement.readiness" -> HiveRouteEngagementProcess.planReadiness(state, action);
            case "frontier.hive_route_engagement.combat" -> HiveRouteEngagementProcess.planCombat(state, action);
            case "frontier.decontamination.scan" -> DecontaminationProcess.plan(state, action);
            case "frontier.objective.review" -> StrategicObjectiveProcess.plan(state, action);
            case "frontier.objective.interrupt" -> StrategicObjectiveProcess.planOpportunity(state, action);
            default -> throw new IllegalStateException("unknown v3 scheduled action: " + action.kind());
        };
        // A known planner can deliberately find that a durable physical observation has already
        // invalidated its work. That no-op must still become a persisted schedule transition:
        // otherwise a later tick/restart would rediscover the same head and quarantine the world.
        return planned.isEmpty() ? List.of(new ProposedEvent(action.subject(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Cancelled(action.id()))) : planned;
    }
    private static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierEvent event) {
        if (event.payload() instanceof AmbientLeasePrepared || event.payload() instanceof AmbientLeaseTransition || event.payload() instanceof AmbientLeaseReleased) {
            return AmbientActorProcess.reduceLease(state, event.subject(), event.instant(), event.payload());
        }
        return switch (event.payload()) {
            case InfectionChanged changed -> state.withInfection(changed.cell(), changed.intensity());
            case ProductionStarted started -> ProductionProcess.reduceStarted(state, event.subject(), started);
            case ProductionCompleted completed -> ProductionProcess.reduceCompleted(state, event.subject(), completed);
            case ProductionBlocked blocked -> ProductionProcess.reduceBlocked(state, event.subject(), blocked);
            case SupplyContractCreated created -> reduceContractCreated(state, event.subject(), created);
            case CargoLoaded loaded -> reduceCargoLoaded(state, event.subject(), loaded);
            case OperationCreated created -> reduceOperationCreated(state, event.subject(), created);
            case OperationAdvanced advanced -> reduceOperationAdvanced(state, event.subject(), advanced);
            case OperationColdSuspended suspended -> reduceOperationColdSuspended(state, event.subject(), suspended);
            case PhysicalIntentPrepared prepared -> reducePhysicalIntentPrepared(state, event.subject(), prepared);
            case PhysicalIntentTransition transition -> reducePhysicalIntentTransition(state, event.subject(), transition);
            case SceneLeasePrepared prepared -> reduceSceneLeasePrepared(state, event.subject(), event.instant(), prepared);
            case SceneLeaseHandoff handoff -> reduceSceneLeaseHandoff(state, event.subject(), event.instant(), handoff);
            case SceneLeaseTransition transition -> reduceSceneLeaseTransition(state, event.subject(), transition);
            case SceneLeaseReleased released -> reduceSceneLeaseReleased(state, event.subject(), released);
            case SceneLeaseRecoveryUnresolved unresolved -> reduceSceneLeaseRecoveryUnresolved(state, event.subject(), unresolved);
            case ActorDied death -> reduceActorDied(state, event.subject(), death);
            case AmbientActorDied death -> AmbientActorProcess.reduce(state, event.subject(), death);
            case AmbientActorObserved observation -> AmbientActorProcess.reduce(state, event.subject(), observation);
            case ResidentBorn birth -> reduceResidentBorn(state, event.subject(), birth);
            case ResidentMigrated migration -> reduceResidentMigrated(state, event.subject(), migration);
            case ResidentBirthStarted started -> PopulationBirthProcess.reduceStarted(state, event.subject(), started);
            case ResidentBirthCancelled cancelled -> PopulationBirthProcess.reduceCancelled(state, event.subject(), cancelled);
            case ResourceSiteGrowthAdvanced advanced -> ResourceSiteProcess.reduceGrowth(state, event.subject(), advanced);
            case ResourceSitePreparationStarted started -> ResourceSiteProcess.reducePreparationStarted(state, event.subject(), started);
            case ResourceSiteHarvestStarted started -> ResourceSiteHarvestProcess.reduceStarted(state, event.subject(), started);
            case ResourceSiteConflictObserved conflict -> ResourceSiteProcess.reduceConflict(state, event.subject(), conflict);
            case StructureDamaged damaged -> reduceStructureDamaged(state, event.subject(), damaged);
            case PhysicalDeltaObserved observed -> FrontierWorldPhysicalObservationProcess.reduce(state, event.subject(), observed);
            case ResourceDeposited deposited -> FrontierWorldPhysicalObservationProcess.reduceResourceDeposit(state, event.subject(), deposited);
            case OperationFailed failed -> reduceOperationFailed(state, event.subject(), failed);
            case ExactItemCustodyChanged changed -> reduceExactItemCustodyChanged(state, event.subject(), changed);
            case ExactItemDestroyed destroyed -> reduceExactItemDestroyed(state, event.subject(), destroyed);
            case CargoCarrierReleased released -> reduceCargoCarrierReleased(state, event.subject(), released);
            case InventoryConflictObserved observed -> reduceInventoryConflict(state, event.subject(), observed);
            case ContainerSurfaceTransition transition -> ContainerSurfaceProcess.reduce(state, event.subject(), transition);
            case HiveGrowthStarted started -> HiveGrowthProcess.reduceStarted(state, event.subject(), started);
            case HiveGrowthCompleted completed -> HiveGrowthProcess.reduceCompleted(state, event.subject(), completed);
            case HiveGrowthBlocked blocked -> HiveGrowthProcess.reduceBlocked(state, event.subject(), blocked);
            case RouteConstructionStarted started -> RouteConstructionStateSupport.reduceStarted(state, event.subject(), started);
            case RouteTopologyCutover cutover -> RouteConstructionStateSupport.reduceCutover(state, event.subject(), cutover);
            case RoutePatrolStarted started -> RoutePatrolProcess.reduceStarted(state, event.subject(), started);
            case RoutePatrolAdvanced advanced -> RoutePatrolProcess.reduceAdvanced(state, event.subject(), advanced);
            case RoutePatrolObstructionConfirmed confirmed -> RoutePatrolProcess.reduceObstruction(state, event.subject(), confirmed);
            case RoutePatrolFailed failed -> RoutePatrolProcess.reduceFailed(state, event.subject(), failed);
            case RouteEngagementStarted started -> HiveRouteEngagementProcess.reduceStarted(state, event.subject(), started);
            case RouteEngagementAttackerAdvanced advanced -> HiveRouteEngagementProcess.reduceAdvanced(state, event.subject(), advanced);
            case RouteEngagementTransition transition -> HiveRouteEngagementProcess.reduceTransition(state, event.subject(), transition);
            case RouteEngagementStrike strike -> HiveRouteEngagementProcess.reduceStrike(state, event.subject(), strike);
            case RouteEngagementResolved resolved -> HiveRouteEngagementProcess.reduceResolved(state, event.subject(), resolved);
            case StrategicObjectiveSelected selected -> StrategicObjectiveProcess.reduceObjective(state, event.subject(), selected);
            case StrategicTaskPlanned planned -> StrategicObjectiveProcess.reduceTask(state, event.subject(), planned);
            case StrategicTaskTransition transition -> StrategicObjectiveProcess.reduceTaskTransition(state, event.subject(), transition);
            default -> fail(event.payload().type());
        };
    }
    private static FrontierWorldState reduceResidentBorn(FrontierWorldState state, SubjectId subject, ResidentBorn birth) {
        return PopulationBirthProcess.reduceBorn(state, subject, birth);
    }
    private static FrontierWorldState reduceResidentMigrated(FrontierWorldState state, SubjectId subject, ResidentMigrated migration) {
        if (!subject.equals(migration.destinationSettlementId())) throw new IllegalArgumentException("resident migration lacks destination settlement owner");
        return state.recordResidentMigration(migration);
    }
    private static FrontierWorldState reduceContractCreated(FrontierWorldState state, SubjectId subject, SupplyContractCreated created) {
        SupplyContract contract = created.contract();
        if (!subject.equals(contract.settlementId())) throw new IllegalArgumentException("contract subject does not own settlement");
        SubjectId depot = FrontierWorldState.depotId(contract.settlementId());
        boolean backed = state.inventory().items().values().stream().anyMatch(item -> item.itemKind().equals(contract.itemKind())
                && item.count() == contract.itemCount() && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot));
        if (!backed) throw new IllegalArgumentException("supply contract has no exact depot-backed item");
        return state.createSupplyContract(created.contract());
    }
    private static FrontierWorldState reduceCargoLoaded(FrontierWorldState state, SubjectId subject, CargoLoaded loaded) {
        SupplyContract contract = state.contracts().get(loaded.contractId());
        if (contract == null || !subject.equals(contract.settlementId()) || !loaded.cargo().id().equals(contract.cargoId()) || loaded.cargo().itemIds().size() != 1) throw new IllegalArgumentException("cargo load does not match its contract");
        ExactItemStack item = state.inventory().items().get(loaded.cargo().itemIds().getFirst());
        if (item == null || !item.itemKind().equals(contract.itemKind()) || item.count() != contract.itemCount()) throw new IllegalArgumentException("cargo item does not match contract demand");
        return state.loadContractCargo(loaded.contractId(), loaded.cargo());
    }
    private static FrontierWorldState reduceOperationCreated(FrontierWorldState state, SubjectId subject, OperationCreated created) {
        RouteOperation operation = created.operation();
        if (!subject.equals(operation.settlementId()) || operation.stage() != OperationStage.EN_ROUTE || operation.routeIndex() != 0) {
            throw new IllegalArgumentException("route operation must begin en-route at its owning settlement");
        }
        SupplyContract contract = state.contracts().values().stream().filter(value -> value.cargoId().equals(operation.cargoId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("route operation cargo has no supply contract"));
        if (contract.status() != ContractStatus.LOADED || !contract.settlementId().equals(operation.settlementId()) || !contract.recipientId().equals(operation.destinationId())) {
            throw new IllegalArgumentException("route operation does not match its loaded supply contract");
        }
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), operation.settlementId());
        if (!operation.route().equals(state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id()))) {
            throw new IllegalArgumentException("route operation must use the deterministic settlement-to-nest route");
        }
        return state.createOperation(operation);
    }
    private static FrontierWorldState reduceOperationAdvanced(FrontierWorldState state, SubjectId subject, OperationAdvanced advanced) {
        RouteOperation operation = state.operations().get(advanced.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("route advancement subject does not own operation");
        return state.advanceOperation(advanced.operationId(), advanced.routeIndex(), advanced.stage());
    }
    private static FrontierWorldState reduceOperationColdSuspended(FrontierWorldState state, SubjectId subject, OperationColdSuspended suspended) {
        RouteOperation operation = state.operations().get(suspended.operationId());
        SceneLease lease = state.sceneLeases().get(suspended.leaseId());
        if (operation == null || !subject.equals(operation.settlementId()) || lease == null || !lease.operationId().equals(operation.id())
                || lease.status() == SceneLeaseStatus.CLOSED || lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART) {
            throw new IllegalArgumentException("cold operation suspension lacks an active matching scene lease");
        }
        return state;
    }
    private static FrontierWorldState reducePhysicalIntentPrepared(FrontierWorldState state, SubjectId subject, PhysicalIntentPrepared prepared) {
        PhysicalIntent intent = prepared.intent();
        if (intent.kind() == PhysicalIntentKind.STRUCTURAL_REPAIR) return StructuralRepairProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION) return RouteConstructionProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.DECONTAMINATION) return DecontaminationProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_PREPARATION) return ResourceSiteProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST) return ResourceSiteHarvestProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.PRODUCTION_TRANSFORMATION) {
            ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
            if (job == null || !subject.equals(job.settlementId())) throw new IllegalArgumentException("production transformation must be prepared by its settlement");
            ProductionTransformationStateSupport.validateIntent(state, intent);
            return state.preparePhysicalIntent(intent);
        }
        if (intent.kind() == PhysicalIntentKind.CARGO_LOADING) return CargoLoadingStateSupport.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) {
            if (state.hiveColony().growthJobs().containsKey(intent.causeSubjectId())) return HiveGrowthProcess.reducePrepared(state, subject, intent);
            if (state.humanPopulation().birthJobs().containsKey(intent.causeSubjectId())) return PopulationBirthProcess.reducePrepared(state, subject, intent);
            throw new IllegalArgumentException("exact consumption has no supported owning process");
        }
        if (intent.kind() == PhysicalIntentKind.SCENE_STRIKE) {
            SceneStrikeStateSupport.validateIntent(state, intent);
            RouteOperation operation = state.operations().get(intent.causeSubjectId());
            if (!subject.equals(operation.settlementId())) throw new IllegalArgumentException("scene strike must be prepared by its operation settlement");
            return state.preparePhysicalIntent(intent);
        }
        if (intent.kind() == PhysicalIntentKind.EXPLOSION) {
            if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("explosion intent must be prepared by the hive");
            ExplosionStateSupport.validateIntent(state, intent);
            return state.preparePhysicalIntent(intent);
        }
        RouteOperation operation = state.operations().get(intent.causeSubjectId());
        if (operation == null || operation.stage() != OperationStage.ARRIVED || !subject.equals(operation.settlementId())) {
            throw new IllegalArgumentException("physical intent must be prepared by an arrived route operation owner");
        }
        if (intent.kind() != PhysicalIntentKind.CARGO_HANDOFF || !intent.subjectIds().contains(operation.cargoId())
                || !intent.subjectIds().contains(operation.id())) throw new IllegalArgumentException("physical intent does not own arrived cargo hand-off");
        return state.preparePhysicalIntent(intent);
    }
    private static FrontierWorldState reducePhysicalIntentTransition(FrontierWorldState state, SubjectId subject, PhysicalIntentTransition transition) {
        PhysicalIntent intent = state.physicalIntents().get(transition.intentId());
        if (intent == null) throw new IllegalArgumentException("physical intent transition has no prepared intent");
        if (intent.kind() == PhysicalIntentKind.STRUCTURAL_REPAIR || intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION || intent.kind() == PhysicalIntentKind.DECONTAMINATION) {
            SubjectId owner = intent.kind() == PhysicalIntentKind.DECONTAMINATION ? DecontaminationProcess.owner(state, intent.causeSubjectId()).id()
                    : FrontierWorldStateSupport.semanticOwner(state.bootstrap(), state.hiveColony(), intent.causeSubjectId());
            if (!subject.equals(owner)) {
                throw new IllegalArgumentException("structural repair transition lacks its owning settlement");
            }
            if (intent.kind() == PhysicalIntentKind.DECONTAMINATION) DecontaminationProcess.taskForIntent(state, intent, StrategicTaskStatus.ACTIVE);
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.EXPLOSION) {
            if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("explosion transition lacks hive ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_PREPARATION) {
            if (!subject.equals(intent.causeSubjectId())) {
                throw new IllegalArgumentException("resource-site preparation transition lacks site ownership");
            }
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST) {
            if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("resource-site harvest transition lacks site ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.PRODUCTION_TRANSFORMATION) {
            ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
            if (job == null || !subject.equals(job.settlementId())) throw new IllegalArgumentException("production transformation transition lacks settlement ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.CARGO_LOADING) return CargoLoadingStateSupport.reduceTransition(state, subject, intent, transition);
        if (intent.kind() == PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) {
            HiveGrowthJob job = state.hiveColony().growthJobs().get(intent.causeSubjectId());
            if (job != null) {
                if (!subject.equals(job.hiveId())) throw new IllegalArgumentException("hive growth consumption transition lacks hive ownership");
                return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
            }
            ResidentBirthJob birth = state.humanPopulation().birthJobs().get(intent.causeSubjectId());
            if (birth == null || !subject.equals(birth.settlementId())) throw new IllegalArgumentException("resident birth consumption transition lacks settlement ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        RouteOperation operation = state.operations().get(intent.causeSubjectId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("physical intent transition subject does not own operation");
        return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
    }
    private static FrontierWorldState reduceSceneLeasePrepared(FrontierWorldState state, SubjectId subject, SimInstant instant, SceneLeasePrepared prepared) {
        SceneLease lease = prepared.lease();
        RouteOperation operation = state.operations().get(lease.operationId());
        if (operation == null || !subject.equals(operation.settlementId()) || !lease.handoffInstant().equals(instant)) {
            throw new IllegalArgumentException("scene lease does not match its current operation hand-off");
        }
        return state.prepareSceneLease(lease);
    }
    private static FrontierWorldState reduceSceneLeaseHandoff(FrontierWorldState state, SubjectId subject, SimInstant instant, SceneLeaseHandoff handoff) {
        SceneLease lease = handoff.lease();
        RouteOperation operation = state.operations().get(lease.operationId());
        if (operation == null || !subject.equals(operation.settlementId()) || !lease.handoffInstant().equals(instant)) {
            throw new IllegalArgumentException("scene hand-off does not match its current operation hand-off");
        }
        return state.handoffAmbientScene(handoff);
    }
    private static FrontierWorldState reduceSceneLeaseTransition(FrontierWorldState state, SubjectId subject, SceneLeaseTransition transition) {
        SceneLease lease = state.sceneLeases().get(transition.leaseId());
        RouteOperation operation = lease == null ? null : state.operations().get(lease.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("scene lease transition lacks its owning operation");
        return state.transitionSceneLease(transition.leaseId(), transition.status());
    }
    private static FrontierWorldState reduceSceneLeaseReleased(FrontierWorldState state, SubjectId subject, SceneLeaseReleased released) {
        SceneLease lease = state.sceneLeases().get(released.leaseId());
        RouteOperation operation = lease == null ? null : state.operations().get(lease.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("scene release lacks its owning operation");
        return state.releaseSceneLease(released.leaseId(), released.members());
    }
    private static FrontierWorldState reduceActorDied(FrontierWorldState state, SubjectId subject, ActorDied death) {
        SceneLease lease = state.sceneLeases().get(death.leaseId());
        RouteOperation operation = lease == null ? null : state.operations().get(lease.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("actor death lacks its owning operation");
        return state.recordActorDeath(death);
    }
    private static FrontierWorldState reduceStructureDamaged(FrontierWorldState state, SubjectId subject, StructureDamaged damage) {
        if (!subject.equals(FrontierWorldStateSupport.structureSettlement(state.bootstrap(), damage.structureId()))) {
            throw new IllegalArgumentException("structure damage lacks its owning settlement");
        }
        return state.recordStructureDamage(damage);
    }
    private static FrontierWorldState reduceOperationFailed(FrontierWorldState state, SubjectId subject, OperationFailed failed) {
        RouteOperation operation = state.operations().get(failed.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("operation failure lacks its owning settlement");
        boolean death = operation.participantIds().stream().anyMatch(actor -> state.actorLocations().get(actor).condition().status() == ActorLifeStatus.DEAD);
        boolean obstruction = "route-obstructed".equals(failed.reason())
                && !FrontierRouteNetwork.isPassable(state.bootstrap(), operation.route(), state.physicalDeltas());
        boolean recoveryUnresolved = "scene-recovery-unresolved".equals(failed.reason()) && state.sceneLeases().values().stream()
                .anyMatch(lease -> lease.operationId().equals(operation.id()) && lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART
                        && lease.recoveryEvidence().isPresent());
        if (!death && !obstruction && !recoveryUnresolved) {
            throw new IllegalArgumentException("operation failure lacks a dead participant or observed route obstruction");
        }
        return state.failOperation(failed.operationId());
    }
    private static FrontierWorldState reduceSceneLeaseRecoveryUnresolved(FrontierWorldState state, SubjectId subject, SceneLeaseRecoveryUnresolved unresolved) {
        SceneLease lease = state.sceneLeases().get(unresolved.leaseId());
        RouteOperation operation = lease == null ? null : state.operations().get(lease.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("scene recovery evidence lacks its owning operation");
        return FrontierSceneLeaseStateSupport.recoveryUnresolved(state, unresolved);
    }
    private static FrontierWorldState reduceExactItemCustodyChanged(FrontierWorldState state, SubjectId subject, ExactItemCustodyChanged changed) {
        if (!subject.equals(FrontierWorldStateSupport.itemOwner(state, changed))) throw new IllegalArgumentException("item custody observation lacks its canonical owner");
        return state.withInventory(state.inventory().moveObservedItem(changed.itemId(), changed.from(), changed.to()));
    }
    private static FrontierWorldState reduceExactItemDestroyed(FrontierWorldState state, SubjectId subject, ExactItemDestroyed destroyed) {
        ExactItemStack item = state.inventory().items().get(destroyed.itemId());
        if (item == null || !item.custody().equals(destroyed.source())
                || !subject.equals(FrontierWorldStateSupport.itemOwner(state, destroyed))) {
            throw new IllegalArgumentException("item destruction lacks its canonical owner");
        }
        return state.withInventory(state.inventory().destroyObservedItem(destroyed.itemId(), destroyed.source()));
    }
    private static FrontierWorldState reduceCargoCarrierReleased(FrontierWorldState state, SubjectId subject, CargoCarrierReleased released) {
        SceneLease lease = state.sceneLeases().get(released.leaseId());
        RouteOperation operation = lease == null ? null : state.operations().get(lease.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("cargo carrier release lacks its owning settlement");
        return state.releaseCargoCarrier(released);
    }
    private static FrontierWorldState reduceInventoryConflict(FrontierWorldState state, SubjectId subject, InventoryConflictObserved observed) {
        InventoryConflict conflict = observed.conflict();
        ContainerRecord container = state.inventory().containers().get(conflict.containerId());
        if (container == null || !subject.equals(container.ownerId())) throw new IllegalArgumentException("inventory conflict lacks its container owner");
        return state.withInventory(state.inventory().recordConflict(conflict));
    }
    private static FrontierWorldState fail(String type) { throw new IllegalStateException("unregistered v3 world event: " + type); }
}
