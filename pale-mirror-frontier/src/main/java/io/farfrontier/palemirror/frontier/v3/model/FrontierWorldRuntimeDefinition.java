package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
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

/** Pure composition root for the fresh 1024x1024 Frontier v3 profile. */
public final class FrontierWorldRuntimeDefinition {
    public static final SubjectId PHYSICAL_EXECUTOR = new SubjectId("system:physical_executor");
    private FrontierWorldRuntimeDefinition() { }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId worldId, long seed) {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(worldId, seed);
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        return new FrontierEngineConfiguration<>(worldId, initial, SimInstant.ZERO,
                FrontierWorldRuntimeDefinition::planCommand,
                FrontierWorldRuntimeDefinition::planScheduled,
                FrontierWorldRuntimeDefinition::reduce,
                new FrontierWorldStateCodec(), FrontierWorldRuntimeDefinition::projection,
                new EngineLimits(4_096, 1_200L, 4_096), List.of(pulse(1, 100), productionStart(bootstrap.settlements().getFirst().id(), 1, 200), contractDemand(1, 450)), TransactionCommitter.noOp());
    }

    public static PayloadCodecs payloadCodecs() { return FrontierWorldPayloadCodecs.create(); }

    private static CommandPlan planCommand(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command) {
        if (!(command.payload() instanceof PhysicalIntentTransition transition) || !PHYSICAL_EXECUTOR.equals(command.actor())) {
            return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                    io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, "command is not a trusted physical intent transition"));
        }
        PhysicalIntent intent = state.physicalIntents().get(transition.intentId());
        if (intent == null) return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, "physical intent is unknown"));
        RouteOperation operation = state.operations().get(intent.causeSubjectId());
        if (operation == null) return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, "physical intent has no owning operation"));
        return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), transition)));
    }

    static List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planScheduled(FrontierWorldState state, ScheduledAction action) {
        return switch (action.kind()) {
            case "frontier.infection.pulse" -> planInfectionPulse(state, action);
            case "frontier.settlement.production.start" -> planProductionStart(state, action);
            case "frontier.settlement.production.complete" -> planProductionCompletion(state, action);
            case "frontier.supply.contract.demand" -> planContractDemand(state, action);
            case "frontier.supply.cargo.load" -> planCargoLoad(state, action);
            case "frontier.operation.progress" -> planOperationProgress(state, action);
            default -> throw new IllegalStateException("unknown v3 scheduled action: " + action.kind());
        };
    }
    private static List<ProposedEvent> planInfectionPulse(FrontierWorldState state, ScheduledAction action) {
        int ordinal = ordinal(action.id().value());
        List<InfectionCell> cells = state.infection().keySet().stream().sorted(java.util.Comparator.comparingInt(InfectionCell::x).thenComparingInt(InfectionCell::z)).toList();
        InfectionCell source = cells.get(Math.floorMod(ordinal - 1, cells.size()));
        InfectionCell target = switch (Math.floorMod(ordinal - 1, 4)) {
            case 0 -> new InfectionCell(source.x() + 1, source.z()); case 1 -> new InfectionCell(source.x(), source.z() + 1);
            case 2 -> new InfectionCell(source.x() - 1, source.z()); default -> new InfectionCell(source.x(), source.z() - 1);
        };
        if (!state.bootstrap().bounds().contains(target.originAtY(64))) target = source;
        FixedRatio prior = state.infection().getOrDefault(target, new FixedRatio(io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO));
        long raw = Math.min(io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE, Math.addExact(prior.value().raw(), 125_000L));
        return List.of(new ProposedEvent(action.subject(), new InfectionChanged(target, new FixedRatio(new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(raw)))),
                new ProposedEvent(action.subject(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(pulse(ordinal + 1, action.dueAt().ticks() + 100L))));
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
        if (operation == null || operation.stage() != OperationStage.EN_ROUTE) throw new IllegalStateException("route operation is not available for progression");
        int nextRouteIndex = operation.routeIndex() + 1;
        OperationStage nextStage = nextRouteIndex == operation.route().size() - 1 ? OperationStage.ARRIVED : OperationStage.EN_ROUTE;
        List<ProposedEvent> events = new java.util.ArrayList<>();
        events.add(new ProposedEvent(operation.settlementId(), new OperationAdvanced(operation.id(), nextRouteIndex, nextStage)));
        if (nextStage == OperationStage.ARRIVED) {
            events.add(new ProposedEvent(operation.settlementId(), new PhysicalIntentPrepared(cargoHandoffIntent(operation))));
        }
        if (nextStage == OperationStage.EN_ROUTE) {
            events.add(new ProposedEvent(operation.settlementId(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(operationProgress(operation, action.dueAt().ticks() + 100L))));
        }
        return List.copyOf(events);
    }
    private static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierEvent event) {
        return switch (event.payload()) {
            case InfectionChanged changed -> state.withInfection(changed.cell(), changed.intensity());
            case ProductionStarted started -> reduceProductionStarted(state, event.subject(), started);
            case ProductionCompleted completed -> reduceProductionCompleted(state, event.subject(), completed);
            case ProductionBlocked blocked -> reduceProductionBlocked(state, event.subject(), blocked);
            case SupplyContractCreated created -> reduceContractCreated(state, event.subject(), created);
            case CargoLoaded loaded -> reduceCargoLoaded(state, event.subject(), loaded);
            case OperationCreated created -> reduceOperationCreated(state, event.subject(), created);
            case OperationAdvanced advanced -> reduceOperationAdvanced(state, event.subject(), advanced);
            case PhysicalIntentPrepared prepared -> reducePhysicalIntentPrepared(state, event.subject(), prepared);
            case PhysicalIntentTransition transition -> reducePhysicalIntentTransition(state, event.subject(), transition);
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
        if (!operation.route().getFirst().equals(settlement.anchor()) || !operation.route().getLast().equals(state.bootstrap().hive().seedNests().getFirst().anchor())) {
            throw new IllegalArgumentException("route operation must use the deterministic settlement-to-nest route");
        }
        return state.createOperation(operation);
    }
    private static FrontierWorldState reduceOperationAdvanced(FrontierWorldState state, SubjectId subject, OperationAdvanced advanced) {
        RouteOperation operation = state.operations().get(advanced.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("route advancement subject does not own operation");
        return state.advanceOperation(advanced.operationId(), advanced.routeIndex(), advanced.stage());
    }
    private static FrontierWorldState reducePhysicalIntentPrepared(FrontierWorldState state, SubjectId subject, PhysicalIntentPrepared prepared) {
        PhysicalIntent intent = prepared.intent();
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
        RouteOperation operation = state.operations().get(intent.causeSubjectId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("physical intent transition subject does not own operation");
        return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observationId());
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
    private static ProductionJob productionJob(io.farfrontier.palemirror.frontier.v3.api.SubjectId settlementId, io.farfrontier.palemirror.frontier.v3.api.SubjectId facilityId,
                                                io.farfrontier.palemirror.frontier.v3.api.SubjectId workerId, ExactItemStack input, int ordinal) {
        String settlementNumber = settlementId.value().substring("settlement:".length());
        return new ProductionJob(new io.farfrontier.palemirror.frontier.v3.api.SubjectId("job:production-" + settlementNumber + "-" + ordinal), settlementId, facilityId, workerId,
                input.id(), new io.farfrontier.palemirror.frontier.v3.api.SubjectId("item:production-" + settlementNumber + "-" + ordinal + "-bread"), "minecraft:bread", input.count());
    }
    private static RouteOperation routeOperation(FrontierWorldState state, SupplyContract contract) {
        Settlement settlement = settlement(state, contract.settlementId());
        SubjectId hauler = settlement.residents().stream().filter(resident -> resident.role() == ResidentRole.HAULER).sorted(Comparator.comparing(Resident::id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("settlement lacks hauler" )).id();
        SubjectId guard = settlement.residents().stream().filter(resident -> resident.role() == ResidentRole.GUARD).sorted(Comparator.comparing(Resident::id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("settlement lacks guard" )).id();
        BlockPosition origin = settlement.anchor();
        BlockPosition destination = state.bootstrap().hive().seedNests().getFirst().anchor();
        List<BlockPosition> route = List.of(origin,
                new BlockPosition(-375, origin.y(), -150), new BlockPosition(-390, origin.y(), 50),
                new BlockPosition(-405, origin.y(), 250), destination);
        int ordinal = ordinal(contract.id().value());
        return new RouteOperation(new SubjectId("operation:supply-1-" + ordinal), settlement.id(), contract.cargoId(), contract.recipientId(),
                List.of(hauler, guard), route, 0, OperationStage.EN_ROUTE);
    }
    private static PhysicalIntent cargoHandoffIntent(RouteOperation operation) {
        BlockPosition destination = operation.route().getLast();
        FixedPosition origin = new FixedPosition(FixedScalar.whole(destination.x()), FixedScalar.whole(destination.y()), FixedScalar.whole(destination.z()));
        return new PhysicalIntent(new PhysicalIntentId("intent:cargo-handoff-" + operation.id().value().substring("operation:".length())),
                PhysicalIntentKind.CARGO_HANDOFF, PhysicalIntentStatus.PREPARED, operation.id(),
                List.of(operation.id(), operation.cargoId()), origin, 0, PhysicalPostcondition.CARGO_HANDOFF_OBSERVED);
    }
    private static ScheduledAction pulse(int ordinal, long due) {
        return new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:infection-pulse-" + ordinal),
                new SimInstant(due), 0, new io.farfrontier.palemirror.frontier.v3.api.SubjectId("hive:frontier"), "frontier.infection.pulse", 1);
    }
    private static ScheduledAction productionStart(io.farfrontier.palemirror.frontier.v3.api.SubjectId settlementId, int ordinal, long due) {
        String settlementNumber = settlementId.value().substring("settlement:".length());
        return new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:production-start-" + settlementNumber + "-" + ordinal), new SimInstant(due), 0, settlementId, "frontier.settlement.production.start", 1);
    }
    private static ScheduledAction productionCompletion(ProductionJob job, long due) {
        return new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId(
                "schedule:production-complete-" + job.id().value().substring("job:".length())),
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
    private static int ordinal(String id) {
        int separator = id.lastIndexOf('-');
        if (separator < 0 || separator == id.length() - 1) throw new IllegalArgumentException("scheduled work identity lacks ordinal: " + id);
        try {
            int value = Integer.parseInt(id.substring(separator + 1));
            if (value <= 0) throw new IllegalArgumentException("scheduled work ordinal must be positive: " + id);
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("scheduled work identity has malformed ordinal: " + id, error);
        }
    }
    private static FrontierWorldState fail(String type) { throw new IllegalStateException("unregistered v3 world event: " + type); }

    private static FrontierWorldProjection projection(
            FrontierWorldState state, WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision revision,
            SimInstant instant, ProjectionQuery query
    ) {
        FrontierBootstrap bootstrap = state.bootstrap();
        int residents = bootstrap.settlements().stream().mapToInt(settlement -> settlement.residents().size()).sum();
        return new FrontierWorldProjection(worldId, revision, instant, bootstrap.canonicalSha256(), bootstrap.settlements().size(),
                residents, bootstrap.hive().bioforms().size(), state.infection().size(), state.inventory().items().size(), state.productionJobs().size(),
                state.operations().size(),
                (int) state.physicalIntents().values().stream().filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED).count(),
                (int) state.physicalIntents().values().stream().filter(intent -> intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART).count());
    }
}
