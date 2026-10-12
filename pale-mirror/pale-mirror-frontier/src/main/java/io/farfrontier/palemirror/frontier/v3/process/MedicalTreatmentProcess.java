package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Exact local care owner: physical supply receipt precedes every patient recovery. */
public final class MedicalTreatmentProcess {
    private MedicalTreatmentProcess() { }

    /** Returns no event when a settlement health review has no admissible patient/team/supply. */
    public static List<ProposedEvent> planStart(FrontierWorldState state, SubjectId settlementId, int ordinal) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId);
        if (state.humanPopulation().medicalOperations().values().stream().anyMatch(operation -> operation.active() && operation.settlementId().equals(settlementId))) {
            return List.of();
        }
        HumanAssignmentProjection assignments = HumanAssignmentProjection.compile(state);
        Optional<ResidentProfile> patient = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId))
                .filter(resident -> state.humanPopulation().health(resident.id()).status() == ResidentHealthStatus.INFECTED)
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                .filter(resident -> assignments.idle(resident.id()))
                .filter(resident -> !state.humanPopulation().meals().containsKey(resident.id())
                        && !state.actorMovements().containsKey(resident.id()))
                .min(Comparator.comparing(ResidentProfile::id));
        Optional<ResidentProfile> medic = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId) && resident.capability(HumanCapability.MEDICINE) > 0)
                .filter(resident -> patient.isEmpty() || !resident.id().equals(patient.orElseThrow().id()))
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                .filter(resident -> assignments.idle(resident.id()))
                .filter(resident -> !state.humanPopulation().meals().containsKey(resident.id())
                        && !state.actorMovements().containsKey(resident.id()))
                .min(Comparator.comparingInt((ResidentProfile resident) -> resident.capability(HumanCapability.MEDICINE))
                        .reversed().thenComparing(ResidentProfile::id));
        Optional<SettlementStructure> infirmary = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.INFIRMARY)
                .filter(structure -> state.structureConditions().get(structure.id()) != StructureCondition.DESTROYED).min(Comparator.comparing(SettlementStructure::id));
        if (patient.isEmpty() || medic.isEmpty() || infirmary.isEmpty()) return List.of();
        Optional<ExactItemStack> supply = ResourceStorageSelection.exactSources(state, settlementId,
                MedicalEvacuationStateSupport.FIRST_TREATMENT_SUPPLY, 1, new SurfaceAnchor(infirmary.orElseThrow().anchor()),
                java.util.Set.of(ResourceStorageRole.WAREHOUSE)).stream()
                .filter(item -> MedicalEvacuationStateSupport.supplyAvailable(state,
                        ((InventoryCustody.ContainerSlot) item.custody()).containerId())).findFirst();
        if (patient.isEmpty() || medic.isEmpty() || supply.isEmpty() || infirmary.isEmpty()) return List.of();
        SubjectId id = new SubjectId("medical:" + settlementId.value().substring("settlement:".length()) + "-" + ordinal);
        MedicalEvacuationOperation operation = new MedicalEvacuationOperation(id, settlementId, patient.orElseThrow().id(), infirmary.orElseThrow().id(),
                MedicalEvacuationTeam.forOperation(id, settlementId, List.of(medic.orElseThrow().id())), supply.orElseThrow().id(),
                ((InventoryCustody.ContainerSlot) supply.orElseThrow().custody()).containerId(),
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:" + id.value().replace(':', '-') + "-consume"), MedicalEvacuationStatus.PREPARED, -1L);
        PhysicalIntent intent = new PhysicalIntent(operation.consumptionIntentId(), PhysicalIntentKind.EXACT_ITEM_CONSUMPTION, PhysicalIntentStatus.PREPARED,
                id, PhysicalIntentRoleBinding.medicalTreatmentConsumption(id, operation.supplyItemId()), new FixedPosition(FixedScalar.whole(infirmary.orElseThrow().anchor().x()),
                FixedScalar.whole(infirmary.orElseThrow().anchor().y()), FixedScalar.whole(infirmary.orElseThrow().anchor().z())), 0,
                PhysicalPostcondition.EXACT_ITEM_CONSUMED_OBSERVED, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.MEDICAL_TREATMENT);
        return List.of(new ProposedEvent(settlementId, new MedicalTreatmentStarted(operation, MedicalExecutionAuthority.admission(state, operation))), new ProposedEvent(settlementId, new PhysicalIntentPrepared(intent)));
    }

    public static List<ProposedEvent> planTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition, long now) {
        MedicalEvacuationOperation operation = operationForIntent(state, intent);
        ProposedEvent physical = new ProposedEvent(operation.settlementId(), transition);
        return switch (transition.status()) {
            case RUNNING -> {
                if (operation.status() != MedicalEvacuationStatus.PREPARED) throw new IllegalArgumentException("medical treatment may start only from prepared care");
                yield List.of(physical, new ProposedEvent(operation.settlementId(), new MedicalTreatmentTransition(operation.id(), MedicalEvacuationStatus.TREATING, MedicalExecutionAuthority.current(state, operation))));
            }
            case CONFIRMED -> {
                if (operation.status() == MedicalEvacuationStatus.BLOCKED) yield List.of(physical);
                if (operation.status() != MedicalEvacuationStatus.TREATING && operation.status() != MedicalEvacuationStatus.UNKNOWN_AFTER_RESTART) {
                    throw new IllegalArgumentException("medical treatment receipt has no active care operation");
                }
                if (state.actorLocations().get(operation.patientId()).condition().status() != ActorLifeStatus.ALIVE) yield List.of(physical,
                        new ProposedEvent(operation.settlementId(), new MedicalTreatmentTransition(operation.id(), MedicalEvacuationStatus.BLOCKED, MedicalExecutionAuthority.current(state, operation))));
                yield List.of(physical, new ProposedEvent(operation.settlementId(),
                        new ResidentHealthTransition(operation.patientId(), ResidentHealthStatus.RECOVERING, now)),
                        new ProposedEvent(operation.settlementId(), new MedicalTreatmentTransition(operation.id(), MedicalEvacuationStatus.COMPLETED, MedicalExecutionAuthority.current(state, operation))));
            }
            case UNKNOWN_AFTER_RESTART -> {
                if (operation.status() == MedicalEvacuationStatus.BLOCKED) yield List.of(physical);
                if (operation.status() != MedicalEvacuationStatus.PREPARED && operation.status() != MedicalEvacuationStatus.TREATING) {
                    throw new IllegalArgumentException("medical treatment uncertainty has no active care operation");
                }
                yield List.of(physical, new ProposedEvent(operation.settlementId(),
                        new MedicalTreatmentTransition(operation.id(), MedicalEvacuationStatus.UNKNOWN_AFTER_RESTART, MedicalExecutionAuthority.current(state, operation))));
            }
            case CONFLICTED -> {
                if (intent.status() != PhysicalIntentStatus.PREPARED
                        || operation.status() != MedicalEvacuationStatus.PREPARED && operation.status() != MedicalEvacuationStatus.BLOCKED)
                    throw new IllegalArgumentException("only unbegun medical consumption can be cancelled without an effect receipt");
                if (operation.active()) requireCancellableSource(state, operation);
                yield operation.active() ? List.of(physical, new ProposedEvent(operation.settlementId(),
                        new MedicalTreatmentTransition(operation.id(), MedicalEvacuationStatus.BLOCKED,
                                MedicalExecutionAuthority.current(state, operation)))) : List.of(physical);
            }
            case PREPARED -> throw new IllegalArgumentException("medical consumption cannot transition back to prepared");
        };
    }

    static boolean hasSupplyCommitment(FrontierWorldState state, SubjectId item) {
        return state.humanPopulation().medicalOperations().values().stream().anyMatch(operation -> {
            if (!operation.supplyItemId().equals(item)) return false;
            var intent = state.physicalIntents().get(operation.consumptionIntentId());
            if (intent != null) MedicalPhysicalAuthority.requireIntent(operation, intent);
            return operation.active() || intent != null && intent.status() != PhysicalIntentStatus.CONFIRMED;
        });
    }

    static List<ProposedEvent> retirePreEffectForDeath(FrontierWorldState before, FrontierWorldState after, SubjectId actor,
                                                      io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command) {
        var execution = before.actorExecutions().current(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.MEDICAL_TREATMENT).get(actor);
        if (execution == null) return List.of();
        var operation = after.humanPopulation().medicalOperations().get(execution.activityOwnerId());
        if (operation == null || operation.status() != MedicalEvacuationStatus.BLOCKED
                || !MedicalExecutionAuthority.participants(operation).contains(actor))
            throw new IllegalArgumentException("medical death follow-up lost its declared blocked care owner");
        var intent = after.physicalIntents().get(operation.consumptionIntentId());
        if (intent == null) throw new IllegalArgumentException("medical death follow-up lost its exact stock intent");
        MedicalPhysicalAuthority.requireIntent(operation, intent);
        if (intent.status() != PhysicalIntentStatus.PREPARED) return List.of();
        var transition = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFLICTED, Optional.empty());
        var plan = FrontierWorldProcessCatalog.physicalLifecycles().planTransition(after, command, intent, transition);
        if (!(plan instanceof io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted accepted))
            throw new IllegalArgumentException("medical casualty cannot retire its exact unbegun stock effect: " + plan);
        return accepted.events();
    }

    static List<ProposedEvent> planSupplyDeparture(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command,
                                                  SubjectId item, ProposedEvent observation) {
        var events = new java.util.ArrayList<ProposedEvent>();
        for (var operation : state.humanPopulation().medicalOperations().values().stream()
                .filter(value -> value.supplyItemId().equals(item)).sorted(Comparator.comparing(MedicalEvacuationOperation::id)).toList()) {
            var intent = state.physicalIntents().get(operation.consumptionIntentId());
            if (intent == null) {
                if (operation.active()) throw new IllegalArgumentException("active medical stock commitment lost its declared consumption intent");
                continue;
            }
            MedicalPhysicalAuthority.requireIntent(operation, intent);
            if (intent.status() == PhysicalIntentStatus.CONFIRMED || intent.status() == PhysicalIntentStatus.CONFLICTED) continue;
            requireCancellableSource(state, operation);
            var transition = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFLICTED, Optional.empty());
            var plan = FrontierWorldProcessCatalog.physicalLifecycles().planTransition(state, command, intent, transition);
            if (!(plan instanceof io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted accepted))
                throw new IllegalArgumentException("medical source departure cannot retire its exact prepared effect: " + plan);
            events.addAll(accepted.events());
        }
        events.add(observation); // The exact owner/effect are retired before its input disappears.
        return List.copyOf(events);
    }

    static FrontierWorldState observeSupplyDeparture(FrontierWorldState state, SubjectId item,
                                                      InventoryCustody source, ExactInventory inventory) {
        for (var operation : state.humanPopulation().medicalOperations().values().stream()
                .filter(value -> value.supplyItemId().equals(item)).sorted(Comparator.comparing(MedicalEvacuationOperation::id)).toList()) {
            var intent = state.physicalIntents().get(operation.consumptionIntentId());
            if (operation.active() || intent != null && intent.status() != PhysicalIntentStatus.CONFLICTED
                    && intent.status() != PhysicalIntentStatus.CONFIRMED)
                throw new IllegalArgumentException("medical source departure lacks the preceding exact owner/effect retirement");
            if (intent != null && intent.status() == PhysicalIntentStatus.CONFLICTED)
                state = FrontierWorldProcessCatalog.physicalLifecycles().compactCancelledBeforeEffect(state, intent);
        }
        return state.withInventory(inventory);
    }

    static boolean mayCompactConsumption(FrontierWorldState state, PhysicalIntent intent) {
        if (PhysicalIntentResolvedRetentionPolicy.confirmedReceiptWithoutRecovery().mayCompact(state, intent)) return true;
        var operation = state.humanPopulation().medicalOperations().get(intent.causeSubjectId());
        return intent.status() == PhysicalIntentStatus.CONFLICTED && operation != null
                && operation.status() == MedicalEvacuationStatus.BLOCKED
                && PhysicalIntentResolvedRetentionPolicy.cancelledBeforeEffect().mayCompact(state, intent);
    }

    private static void requireCancellableSource(FrontierWorldState state, MedicalEvacuationOperation operation) {
        if (MedicalPhysicalAuthority.pendingForContainer(state, operation.supplyContainerId()))
            throw new IllegalArgumentException("medical source has an unresolved physical effect or presentation boundary");
        var intent = state.physicalIntents().get(operation.consumptionIntentId());
        if (intent == null || intent.status() != PhysicalIntentStatus.PREPARED)
            throw new IllegalArgumentException("medical source cancellation requires its exact unbegun effect");
    }

    private static FrontierWorldState quiesceBeforeBlock(FrontierWorldState state, MedicalEvacuationOperation operation) {
        var scenes = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isMedicalTreatment)
                .filter(lease -> FrontierSceneBehaviors.medicalTreatment(lease).operationId().equals(operation.id()))
                .filter(lease -> lease.status() != SceneLeaseStatus.CLOSED).toList();
        if (scenes.size() > 1) throw new IllegalArgumentException("medical cancellation has competing presentation scopes");
        if (scenes.isEmpty()) return state;
        var lease = scenes.getFirst();
        if (operation.status() == MedicalEvacuationStatus.PREPARED && FrontierSceneLeaseStateSupport.canAbortPrepared(state, lease))
            return FrontierSceneLeaseStateSupport.abortPrepared(state, lease.id());
        return lease.status() == SceneLeaseStatus.HOT ? state.transitionSceneLease(lease.id(), SceneLeaseStatus.DRAINING) : state;
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, MedicalTreatmentStarted started) {
        MedicalEvacuationOperation operation = started.operation();
        if (!subject.equals(operation.settlementId())) throw new IllegalArgumentException("medical treatment start lacks settlement owner");
        if (operation.status() != MedicalEvacuationStatus.PREPARED
                || !ResourceStorageSelection.permits(state.inventory(), operation.supplyContainerId(), operation.settlementId(),
                    MedicalEvacuationStateSupport.FIRST_TREATMENT_SUPPLY, java.util.Set.of(ResourceStorageRole.WAREHOUSE))
                || !MedicalEvacuationStateSupport.supplyAvailable(state, operation.supplyContainerId()))
            throw new IllegalArgumentException("medical treatment admission has no eligible selected warehouse supply");
        MedicalEvacuationStateSupport.validate(state.bootstrap(), state.humanPopulation().startMedicalOperation(operation), state.actorLocations(),
                state.structureConditions(), state.inventory(), state.physicalIntents());
        return ActorExecutionComposition.LIFECYCLE.prepareVacantGroup(state, started.executions()).commit(state,
                FrontierWorldStateUpdate.begin().humanPopulation(state.humanPopulation().startMedicalOperation(operation)));
    }

    public static FrontierWorldState reduceTransition(FrontierWorldState state, SubjectId subject, long atTick, MedicalTreatmentTransition transition) {
        MedicalEvacuationOperation operation = state.humanPopulation().medicalOperations().get(transition.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("medical treatment transition lacks operation owner");
        MedicalExecutionAuthority.requireCurrent(state, operation, transition.executions());
        if (transition.status() == MedicalEvacuationStatus.TREATING && operation.status() != MedicalEvacuationStatus.PREPARED
                || transition.status() == MedicalEvacuationStatus.COMPLETED && operation.status() != MedicalEvacuationStatus.TREATING
                        && operation.status() != MedicalEvacuationStatus.UNKNOWN_AFTER_RESTART
                || transition.status() == MedicalEvacuationStatus.UNKNOWN_AFTER_RESTART && operation.status() != MedicalEvacuationStatus.PREPARED
                        && operation.status() != MedicalEvacuationStatus.TREATING
                || transition.status() == MedicalEvacuationStatus.BLOCKED && !operation.active()) {
            throw new IllegalArgumentException("medical treatment lifecycle transition is invalid");
        }
        var population = state.humanPopulation().transitionMedicalOperation(operation.id(), transition.status(), atTick);
        if (transition.status() == MedicalEvacuationStatus.BLOCKED)
            state = quiesceBeforeBlock(state, operation);
        var reduced = state.withChanges(FrontierWorldStateUpdate.begin().humanPopulation(population).actorExecutions(
                population.medicalOperations().get(operation.id()).active() ? state.actorExecutions() : MedicalExecutionAuthority.retired(state, operation)));
        var intent = reduced.physicalIntents().get(operation.consumptionIntentId());
        return intent != null && intent.status() == PhysicalIntentStatus.CONFLICTED && mayCompactConsumption(reduced, intent)
                ? FrontierWorldProcessCatalog.physicalLifecycles().compactCancelledBeforeEffect(reduced, intent) : reduced;
    }

    public static MedicalEvacuationOperation operationForIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) throw new IllegalArgumentException("medical treatment has invalid physical intent kind");
        MedicalEvacuationOperation operation = state.humanPopulation().medicalOperations().get(intent.causeSubjectId());
        if (operation == null || !operation.consumptionIntentId().equals(intent.id()) || !intent.roles().equals(PhysicalIntentRoleBinding.medicalTreatmentConsumption(operation.id(), operation.supplyItemId()))) {
            throw new IllegalArgumentException("medical treatment intent has no active exact operation");
        }
        return operation;
    }
}
