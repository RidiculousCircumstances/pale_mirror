package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection; import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition; import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import static io.farfrontier.palemirror.frontier.v3.model.FrontierWorldScheduleSupport.ordinal;
/** Pure composition root for the fresh 1024x1024 Frontier v3 profile. */
public final class FrontierWorldRuntimeDefinition {
    public static final SubjectId PHYSICAL_EXECUTOR = new SubjectId("system:physical_executor");
    private FrontierWorldRuntimeDefinition() { } public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId worldId, long seed) {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(worldId, seed); FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        return new FrontierEngineConfiguration<>(worldId, initial, SimInstant.ZERO, FrontierWorldRuntimeDefinition::planCommand,
                FrontierWorldRuntimeDefinition::planScheduled, FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096), initialSchedule(bootstrap), TransactionCommitter.noOp()); }
    private static List<ScheduledAction> initialSchedule(FrontierBootstrap bootstrap) {
        List<ScheduledAction> actions = new java.util.ArrayList<>(List.of(HiveInfectionProcess.pulse(1, 100), productionStart(bootstrap.settlements().getFirst().id(), 1, 200),
                contractDemand(1, 450), HiveGrowthProcess.start(1, 600), StructuralRepairProcess.scan(1, 800), RouteConstructionProcess.scan(1, 900), DecontaminationProcess.scan(1, 1_000)));
        for (int index = 0; index < bootstrap.settlements().size(); index++) {
            actions.add(StrategicObjectiveProcess.review(bootstrap.settlements().get(index).id(), 1, 2_000L + index * 100L));
        }
        actions.add(StrategicObjectiveProcess.review(bootstrap.hive().id(), 1, 3_200L)); return List.copyOf(actions);
    }
    public static PayloadCodecs payloadCodecs() { return FrontierWorldPayloadCodecs.create(); } private static CommandPlan planCommand(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command) {
        if (!PHYSICAL_EXECUTOR.equals(command.actor())) {
            return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                    io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, "command is not from the trusted physical executor"));
        }
        if (command.payload() instanceof PhysicalIntentTransition transition) {
            PhysicalIntent intent = state.physicalIntents().get(transition.intentId()); if (intent == null) return rejected("physical intent is unknown");
            if (intent.kind() == PhysicalIntentKind.STRUCTURAL_REPAIR || intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION) {
                return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierWorldStateSupport.semanticOwner(state.bootstrap(), state.hiveColony(), intent.causeSubjectId()), transition)));
            }
            if (intent.kind() == PhysicalIntentKind.DECONTAMINATION) return new CommandPlan.Accepted(List.of(new ProposedEvent(DecontaminationProcess.owner(state, intent.causeSubjectId()).id(), transition)));
            RouteOperation operation = state.operations().get(intent.causeSubjectId());
            if (operation == null) return rejected("physical intent has no owning operation");
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), transition)));
        }
        if (command.payload() instanceof SceneLeasePrepared prepared) {
            RouteOperation operation = state.operations().get(prepared.lease().operationId()); if (operation == null) return rejected("scene lease has no owning operation");
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), prepared)));
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
            if (operation.participantIds().stream().anyMatch(actor -> state.actorLocations().get(actor).condition().status() == ActorLifeStatus.DEAD)) {
                return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), released),
                        new ProposedEvent(operation.settlementId(), new OperationFailed(operation.id(), "actor-death"))));
            }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), released),
                    new ProposedEvent(operation.settlementId(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(
                            operationProgress(operation, command.submittedAt().ticks() + 100L)))));
        }
        if (command.payload() instanceof ActorDied death) {
            SceneLease lease = state.sceneLeases().get(death.leaseId());
            if (lease == null || lease.status() != SceneLeaseStatus.HOT
                    || lease.members().stream().noneMatch(member -> member.actorId().equals(death.actorId()))) {
                return rejected("actor death is not evidence for an active HOT scene member");
            }
            RouteOperation operation = state.operations().get(lease.operationId());
            if (operation == null) return rejected("actor death has no owning operation");
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), death),
                    new ProposedEvent(operation.settlementId(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING))));
        }
        if (command.payload() instanceof AmbientActorDied death) return AmbientActorProcess.plan(state, death);
        if (command.payload() instanceof AmbientActorObserved observation) return AmbientActorProcess.plan(state, observation);
        if (command.payload() instanceof AmbientLeasePrepared || command.payload() instanceof AmbientLeaseTransition || command.payload() instanceof AmbientLeaseReleased) return AmbientActorProcess.planLease(state, command.payload());
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
        return switch (action.kind()) {
            case "frontier.infection.pulse" -> HiveInfectionProcess.plan(state, action);
            case "frontier.settlement.production.start" -> planProductionStart(state, action);
            case "frontier.settlement.production.complete" -> planProductionCompletion(state, action);
            case "frontier.supply.contract.demand" -> planContractDemand(state, action);
            case "frontier.supply.cargo.load" -> planCargoLoad(state, action);
            case "frontier.operation.progress" -> planOperationProgress(state, action);
            case "frontier.hive.growth.start" -> HiveGrowthProcess.planStart(state, action);
            case "frontier.hive.growth.complete" -> HiveGrowthProcess.planCompletion(state, action);
            case "frontier.structural_repair.scan" -> StructuralRepairProcess.plan(state, action);
            case "frontier.route_construction.scan" -> RouteConstructionProcess.plan(state, action);
            case "frontier.decontamination.scan" -> DecontaminationProcess.plan(state, action);
            case "frontier.objective.review" -> StrategicObjectiveProcess.plan(state, action);
            default -> throw new IllegalStateException("unknown v3 scheduled action: " + action.kind());
        };
    }
    private static List<ProposedEvent> planProductionStart(FrontierWorldState state, ScheduledAction action) {
        int ordinal = ordinal(action.id().value());
        Settlement settlement = settlement(state, action.subject());
        SettlementStructure workshop = workshop(settlement);
        if (state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) {
            return blockedStart(action, settlement, workshop, ProductionBlockReason.FACILITY_UNAVAILABLE);
        }
        SubjectId depotId = FrontierWorldState.depotId(settlement.id());
        Optional<ExactItemStack> input = state.inventory().items().values().stream().sorted(Comparator.comparing(ExactItemStack::id))
                .filter(item -> item.itemKind().equals("minecraft:wheat") && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depotId))
                .findFirst();
        if (input.isEmpty()) return blockedStart(action, settlement, workshop, ProductionBlockReason.INPUT_UNAVAILABLE);
        Resident worker = crafter(settlement);
        ProductionJob job = productionJob(settlement.id(), workshop.id(), worker.id(), input.orElseThrow(), ordinal);
        return List.of(new ProposedEvent(settlement.id(), new ProductionStarted(job, input.orElseThrow().id())),
                new ProposedEvent(settlement.id(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(productionCompletion(job, action.dueAt().ticks() + 100L))));
    }
    private static List<ProposedEvent> planProductionCompletion(FrontierWorldState state, ScheduledAction action) {
        ProductionJob job = state.productionJobs().get(action.subject());
        if (job == null) throw new IllegalStateException("production completion has no active job: " + action.subject().value());
        Settlement settlement = settlement(state, job.settlementId());
        SettlementStructure workshop = workshop(settlement);
        if (state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) {
            return List.of(new ProposedEvent(settlement.id(), new ProductionBlocked(settlement.id(), workshop.id(), job.id(), ProductionBlockReason.FACILITY_UNAVAILABLE)),
                    new ProposedEvent(settlement.id(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(productionCompletion(job, action.dueAt().ticks() + 100L))));
        }
        SubjectId depotId = FrontierWorldState.depotId(settlement.id());
        OptionalInt slot = state.inventory().firstFreeSlot(depotId);
        if (slot.isEmpty()) {
            return List.of(new ProposedEvent(settlement.id(), new ProductionBlocked(settlement.id(), workshop.id(), job.id(), ProductionBlockReason.OUTPUT_STORAGE_UNAVAILABLE)),
                    new ProposedEvent(settlement.id(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(productionCompletion(job, action.dueAt().ticks() + 100L))));
        }
        ExactItemStack output = new ExactItemStack(job.outputItemId(), job.outputItemKind(), job.outputCount(), new InventoryCustody.ContainerSlot(depotId, slot.getAsInt()));
        return List.of(new ProposedEvent(settlement.id(), new ProductionCompleted(job.id(), output)),
                new ProposedEvent(settlement.id(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(productionStart(settlement.id(), ordinal(job.id().value()) + 1, action.dueAt().ticks() + 400L))));
    }
    private static List<ProposedEvent> blockedStart(ScheduledAction action, Settlement settlement, SettlementStructure workshop, ProductionBlockReason reason) {
        return List.of(new ProposedEvent(settlement.id(), new ProductionBlocked(settlement.id(), workshop.id(), workshop.id(), reason)),
                new ProposedEvent(settlement.id(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(productionStart(settlement.id(), ordinal(action.id().value()) + 1, action.dueAt().ticks() + 200L))));
    }
    private static List<ProposedEvent> planContractDemand(FrontierWorldState state, ScheduledAction action) {
        Settlement settlement = settlement(state, new SubjectId("settlement:1"));
        ExactItemStack bread = state.inventory().items().values().stream().sorted(Comparator.comparing(ExactItemStack::id))
                .filter(item -> item.itemKind().equals("minecraft:bread") && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(FrontierWorldState.depotId(settlement.id())))
                .findFirst().orElseThrow(() -> new IllegalStateException("supply demand has no exact bread output"));
        int ordinal = ordinal(action.id().value());
        SupplyContract contract = new SupplyContract(new SubjectId("contract:supply-1-" + ordinal), settlement.id(), state.bootstrap().hive().id(),
                new SubjectId("cargo:supply-1-" + ordinal), bread.itemKind(), bread.count(), ContractStatus.ORDERED);
        return List.of(new ProposedEvent(settlement.id(), new SupplyContractCreated(contract)),
                new ProposedEvent(settlement.id(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(cargoLoad(contract, action.dueAt().ticks() + 50L))));
    }
    private static List<ProposedEvent> planCargoLoad(FrontierWorldState state, ScheduledAction action) {
        SupplyContract contract = state.contracts().get(action.subject());
        if (contract == null || contract.status() != ContractStatus.ORDERED) throw new IllegalStateException("cargo load has no ordered contract");
        SubjectId depot = FrontierWorldState.depotId(contract.settlementId());
        ExactItemStack item = state.inventory().items().values().stream().sorted(Comparator.comparing(ExactItemStack::id))
                .filter(value -> value.itemKind().equals(contract.itemKind()) && value.count() == contract.itemCount()
                        && value.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot))
                .findFirst().orElseThrow(() -> new IllegalStateException("contract cargo is unavailable in its depot"));
        RouteOperation operation = routeOperation(state, contract);
        return List.of(new ProposedEvent(contract.settlementId(), new CargoLoaded(contract.id(), new CargoBatch(contract.cargoId(), contract.settlementId(), List.of(item.id())))),
                new ProposedEvent(contract.settlementId(), new OperationCreated(operation)),
                new ProposedEvent(contract.settlementId(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(operationProgress(operation, action.dueAt().ticks() + 100L))));
    }
    private static List<ProposedEvent> planOperationProgress(FrontierWorldState state, ScheduledAction action) {
        RouteOperation operation = state.operations().get(action.subject());
        if (operation == null || operation.stage() != OperationStage.EN_ROUTE) return List.of();
        Optional<SceneLease> lease = state.sceneLeases().values().stream().filter(value -> value.operationId().equals(operation.id())
                && value.status() != SceneLeaseStatus.CLOSED).findFirst();
        if (lease.isPresent()) return List.of(new ProposedEvent(operation.settlementId(), new OperationColdSuspended(operation.id(), lease.orElseThrow().id())));
        if (!FrontierRouteNetwork.isPassable(state.bootstrap(), operation.route(), state.physicalDeltas())) {
            return List.of(new ProposedEvent(operation.settlementId(), new OperationFailed(operation.id(), "route-obstructed")));
        }
        int nextRouteIndex = operation.routeIndex() + 1;
        OperationStage nextStage = nextRouteIndex == operation.route().size() - 1 ? OperationStage.ARRIVED : OperationStage.EN_ROUTE;
        List<ProposedEvent> events = new java.util.ArrayList<>(); events.add(new ProposedEvent(operation.settlementId(), new OperationAdvanced(operation.id(), nextRouteIndex, nextStage)));
        if (nextStage == OperationStage.ARRIVED) {
            events.add(new ProposedEvent(operation.settlementId(), new PhysicalIntentPrepared(cargoHandoffIntent(operation))));
        }
        if (nextStage == OperationStage.EN_ROUTE) {
            events.add(new ProposedEvent(operation.settlementId(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(operationProgress(operation, action.dueAt().ticks() + 100L))));
        }
        return List.copyOf(events);
    } private static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierEvent event) {
        if (event.payload() instanceof AmbientLeasePrepared || event.payload() instanceof AmbientLeaseTransition || event.payload() instanceof AmbientLeaseReleased) {
            return AmbientActorProcess.reduceLease(state, event.subject(), event.instant(), event.payload());
        }
        return switch (event.payload()) {
            case InfectionChanged changed -> state.withInfection(changed.cell(), changed.intensity());
            case ProductionStarted started -> reduceProductionStarted(state, event.subject(), started);
            case ProductionCompleted completed -> reduceProductionCompleted(state, event.subject(), completed);
            case ProductionBlocked blocked -> reduceProductionBlocked(state, event.subject(), blocked);
            case SupplyContractCreated created -> reduceContractCreated(state, event.subject(), created);
            case CargoLoaded loaded -> reduceCargoLoaded(state, event.subject(), loaded);
            case OperationCreated created -> reduceOperationCreated(state, event.subject(), created);
            case OperationAdvanced advanced -> reduceOperationAdvanced(state, event.subject(), advanced);
            case OperationColdSuspended suspended -> reduceOperationColdSuspended(state, event.subject(), suspended);
            case PhysicalIntentPrepared prepared -> reducePhysicalIntentPrepared(state, event.subject(), prepared);
            case PhysicalIntentTransition transition -> reducePhysicalIntentTransition(state, event.subject(), transition);
            case SceneLeasePrepared prepared -> reduceSceneLeasePrepared(state, event.subject(), event.instant(), prepared);
            case SceneLeaseTransition transition -> reduceSceneLeaseTransition(state, event.subject(), transition);
            case SceneLeaseReleased released -> reduceSceneLeaseReleased(state, event.subject(), released);
            case ActorDied death -> reduceActorDied(state, event.subject(), death);
            case AmbientActorDied death -> AmbientActorProcess.reduce(state, event.subject(), death);
            case AmbientActorObserved observation -> AmbientActorProcess.reduce(state, event.subject(), observation);
            case StructureDamaged damaged -> reduceStructureDamaged(state, event.subject(), damaged);
            case PhysicalDeltaObserved observed -> FrontierWorldPhysicalObservationProcess.reduce(state, event.subject(), observed);
            case ResourceDeposited deposited -> FrontierWorldPhysicalObservationProcess.reduceResourceDeposit(state, event.subject(), deposited);
            case OperationFailed failed -> reduceOperationFailed(state, event.subject(), failed);
            case ExactItemCustodyChanged changed -> reduceExactItemCustodyChanged(state, event.subject(), changed);
            case InventoryConflictObserved observed -> reduceInventoryConflict(state, event.subject(), observed);
            case ContainerSurfaceTransition transition -> ContainerSurfaceProcess.reduce(state, event.subject(), transition);
            case HiveGrowthStarted started -> HiveGrowthProcess.reduceStarted(state, event.subject(), started);
            case HiveGrowthCompleted completed -> HiveGrowthProcess.reduceCompleted(state, event.subject(), completed);
            case HiveGrowthBlocked blocked -> HiveGrowthProcess.reduceBlocked(state, event.subject(), blocked);
            case RouteConstructionStarted started -> RouteConstructionStateSupport.reduceStarted(state, event.subject(), started);
            case RouteTopologyCutover cutover -> RouteConstructionStateSupport.reduceCutover(state, event.subject(), cutover);
            case StrategicObjectiveSelected selected -> StrategicObjectiveProcess.reduceObjective(state, event.subject(), selected);
            case StrategicTaskPlanned planned -> StrategicObjectiveProcess.reduceTask(state, event.subject(), planned);
            default -> fail(event.payload().type());
        };
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
        Settlement settlement = settlement(state, operation.settlementId());
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
        if (!death && !obstruction) {
            throw new IllegalArgumentException("operation failure lacks a dead participant or observed route obstruction");
        }
        return state.failOperation(failed.operationId());
    }
    private static FrontierWorldState reduceExactItemCustodyChanged(FrontierWorldState state, SubjectId subject, ExactItemCustodyChanged changed) {
        if (!subject.equals(FrontierWorldStateSupport.itemOwner(state, changed))) throw new IllegalArgumentException("item custody observation lacks its canonical owner");
        return state.withInventory(state.inventory().moveObservedItem(changed.itemId(), changed.from(), changed.to()));
    }
    private static FrontierWorldState reduceInventoryConflict(FrontierWorldState state, SubjectId subject, InventoryConflictObserved observed) {
        InventoryConflict conflict = observed.conflict();
        ContainerRecord container = state.inventory().containers().get(conflict.containerId());
        if (container == null || !subject.equals(container.ownerId())) throw new IllegalArgumentException("inventory conflict lacks its container owner");
        return state.withInventory(state.inventory().recordConflict(conflict));
    }
    private static FrontierWorldState reduceProductionStarted(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject, ProductionStarted started) {
        ProductionJob job = started.job();
        requireProductionSubject(subject, job.settlementId());
        Settlement settlement = settlement(state, job.settlementId());
        SettlementStructure workshop = workshop(settlement);
        if (!workshop.id().equals(job.facilityId()) || state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) throw new IllegalArgumentException("production start facility is unavailable");
        if (!crafter(settlement).id().equals(job.workerId())) throw new IllegalArgumentException("production start worker is not the deterministic facility crafter");
        ExactItemStack input = state.inventory().items().get(started.inputItemId());
        if (input == null || !input.itemKind().equals("minecraft:wheat") || !(input.custody() instanceof InventoryCustody.ContainerSlot slot)
                || !slot.containerId().equals(FrontierWorldState.depotId(settlement.id()))) throw new IllegalArgumentException("production start input is unavailable or not in its depot");
        if (input.count() != job.outputCount() || !job.outputItemKind().equals("minecraft:bread")) throw new IllegalArgumentException("production output is not a verified wheat conversion");
        return state.startProductionJob(job, started.inputItemId());
    }
    private static FrontierWorldState reduceProductionCompleted(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject, ProductionCompleted completed) {
        ProductionJob job = state.productionJobs().get(completed.jobId());
        if (job == null) throw new IllegalArgumentException("production completion has no active job");
        requireProductionSubject(subject, job.settlementId());
        Settlement settlement = settlement(state, job.settlementId());
        SettlementStructure workshop = workshop(settlement);
        if (state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) throw new IllegalArgumentException("production completion facility is unavailable");
        if (!(completed.output().custody() instanceof InventoryCustody.ContainerSlot slot)
                || !slot.containerId().equals(FrontierWorldState.depotId(settlement.id()))) {
            throw new IllegalArgumentException("production output is not stored in its settlement depot");
        }
        if (state.inventory().firstFreeSlot(slot.containerId()).orElse(-1) != slot.slot()) throw new IllegalArgumentException("production output does not target the deterministic free depot slot");
        return state.completeProductionJob(completed.jobId(), completed.output());
    }
    private static FrontierWorldState reduceProductionBlocked(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject, ProductionBlocked blocked) {
        requireProductionSubject(subject, blocked.settlementId());
        Settlement settlement = settlement(state, blocked.settlementId());
        SettlementStructure workshop = workshop(settlement);
        if (!workshop.id().equals(blocked.facilityId())) throw new IllegalArgumentException("production block refers to a foreign facility");
        SubjectId depotId = FrontierWorldState.depotId(settlement.id());
        boolean wheatPresent = state.inventory().items().values().stream().anyMatch(item -> item.itemKind().equals("minecraft:wheat")
                && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depotId));
        switch (blocked.reason()) {
            case INPUT_UNAVAILABLE -> {
                if (!blocked.workId().equals(workshop.id()) || wheatPresent || state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) throw new IllegalArgumentException("production input block precondition does not hold");
            }
            case OUTPUT_STORAGE_UNAVAILABLE -> {
                if (!state.productionJobs().containsKey(blocked.workId()) || state.inventory().firstFreeSlot(depotId).isPresent()) throw new IllegalArgumentException("production storage block precondition does not hold");
            }
            case FACILITY_UNAVAILABLE -> {
                if (state.structureConditions().get(workshop.id()) == StructureCondition.INTACT) throw new IllegalArgumentException("production facility block precondition does not hold");
            }
        }
        return state;
    }
    private static void requireProductionSubject(io.farfrontier.palemirror.frontier.v3.api.SubjectId actual, io.farfrontier.palemirror.frontier.v3.api.SubjectId expected) {
        if (!expected.equals(actual)) throw new IllegalArgumentException("production event subject does not own the work");
    }
    private static Settlement settlement(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId settlementId) {
        return state.bootstrap().settlements().stream().filter(value -> value.id().equals(settlementId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown production settlement: " + settlementId.value()));
    }
    private static SettlementStructure workshop(Settlement settlement) {
        return settlement.structures().stream().filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst()
                .orElseThrow(() -> new IllegalStateException("settlement lacks workshop"));
    }
    private static Resident crafter(Settlement settlement) {
        return settlement.residents().stream().filter(value -> value.role() == ResidentRole.CRAFTER).sorted(Comparator.comparing(Resident::id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("settlement lacks crafter"));
    }
    private static ProductionJob productionJob(io.farfrontier.palemirror.frontier.v3.api.SubjectId settlementId,
                                                io.farfrontier.palemirror.frontier.v3.api.SubjectId facilityId, io.farfrontier.palemirror.frontier.v3.api.SubjectId workerId, ExactItemStack input, int ordinal) {
        String settlementNumber = settlementId.value().substring("settlement:".length());
        return new ProductionJob(new io.farfrontier.palemirror.frontier.v3.api.SubjectId("job:production-" + settlementNumber + "-" + ordinal), settlementId, facilityId, workerId, input.id(),
                new io.farfrontier.palemirror.frontier.v3.api.SubjectId("item:production-" + settlementNumber + "-" + ordinal + "-bread"), "minecraft:bread", input.count());
    }
    private static RouteOperation routeOperation(FrontierWorldState state, SupplyContract contract) {
        Settlement settlement = settlement(state, contract.settlementId());
        SubjectId hauler = settlement.residents().stream().filter(resident -> resident.role() == ResidentRole.HAULER).sorted(Comparator.comparing(Resident::id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("settlement lacks hauler" )).id();
        SubjectId guard = settlement.residents().stream().filter(resident -> resident.role() == ResidentRole.GUARD).sorted(Comparator.comparing(Resident::id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("settlement lacks guard" )).id();
        List<BlockPosition> route = state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id());
        int ordinal = ordinal(contract.id().value());
        return new RouteOperation(new SubjectId("operation:supply-1-" + ordinal), settlement.id(), contract.cargoId(), contract.recipientId(),
                List.of(hauler, guard), route, 0, OperationStage.EN_ROUTE);
    }
    private static PhysicalIntent cargoHandoffIntent(RouteOperation operation) {
        BlockPosition destination = operation.route().getLast(); FixedPosition origin = new FixedPosition(FixedScalar.whole(destination.x()), FixedScalar.whole(destination.y()), FixedScalar.whole(destination.z()));
        return new PhysicalIntent(new PhysicalIntentId("intent:cargo-handoff-" + operation.id().value().substring("operation:".length())),
                PhysicalIntentKind.CARGO_HANDOFF, PhysicalIntentStatus.PREPARED, operation.id(),
                List.of(operation.id(), operation.cargoId()), origin, 0, PhysicalPostcondition.CARGO_HANDOFF_OBSERVED);
    }
    private static ScheduledAction productionStart(io.farfrontier.palemirror.frontier.v3.api.SubjectId settlementId, int ordinal, long due) {
        String settlementNumber = settlementId.value().substring("settlement:".length());
        return new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:production-start-" + settlementNumber + "-" + ordinal), new SimInstant(due), 0, settlementId, "frontier.settlement.production.start", 1);
    }
    private static ScheduledAction productionCompletion(ProductionJob job, long due) {
        return new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:production-complete-" + job.id().value().substring("job:".length())),
                new SimInstant(due), 0, job.id(), "frontier.settlement.production.complete", 1);
    }
    private static ScheduledAction contractDemand(int ordinal, long due) {
        return new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:contract-demand-" + ordinal), new SimInstant(due), 0, new SubjectId("settlement:1"), "frontier.supply.contract.demand", 1);
    }
    private static ScheduledAction cargoLoad(SupplyContract contract, long due) {
        return new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:cargo-load-" + contract.id().value().substring("contract:".length())), new SimInstant(due), 0, contract.id(), "frontier.supply.cargo.load", 1);
    }
    private static ScheduledAction operationProgress(RouteOperation operation, long due) {
        return new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:operation-progress-" + operation.id().value().substring("operation:".length())),
                new SimInstant(due), 0, operation.id(), "frontier.operation.progress", 1);
    }
    private static FrontierWorldState fail(String type) { throw new IllegalStateException("unregistered v3 world event: " + type); }
}
