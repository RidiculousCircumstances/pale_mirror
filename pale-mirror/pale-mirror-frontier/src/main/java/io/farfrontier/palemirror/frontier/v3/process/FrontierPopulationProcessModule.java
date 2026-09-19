package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;
import java.util.Set;

/** Exact owner for resident demography, migration, provisioning and health facts. */
final class FrontierPopulationProcessModule implements FrontierWorldProcessModule {
    @Override public List<PhysicalIntentLifecycleCapability> physicalIntentLifecycleCapabilities() {
        return List.of(birthConsumptionCapability(), medicalConsumptionCapability(), provisionConsumptionCapability());
    }

    private static PhysicalIntentLifecycleCapability birthConsumptionCapability() {
        return new FunctionalPhysicalIntentLifecycleCapability(PhysicalIntentLifecycleOwner.POPULATION_MIGRATION,
                Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EXACT_ITEM_CONSUMPTION),
                (state, command, prepared) -> FrontierWorldCommandPlanner.rejected("physical executor cannot prepare population consumption"),
                (state, command, intent, transition) -> new CommandPlan.Accepted(
                        PopulationBirthProcess.planTransition(state, intent, transition, command.submittedAt().ticks())),
                PopulationBirthProcess::reducePrepared,
                (state, subject, intent, transition) -> {
                    ResidentBirthJob birth = state.humanPopulation().birthJobs().get(intent.causeSubjectId());
                    if (birth == null || !subject.equals(birth.settlementId())) {
                        throw new IllegalArgumentException("resident birth consumption transition lacks settlement ownership");
                    }
                    return reducePopulationConsumption(state, intent, transition);
                }, PhysicalIntentLifecycleRetirementPolicy.of(
                        (state, command, intent, transition) -> new CommandPlan.Accepted(
                                PopulationBirthProcess.planTransition(state, intent, transition, command.submittedAt().ticks())),
                        (state, subject, intent, transition) -> {
                            ResidentBirthJob birth = state.humanPopulation().birthJobs().get(intent.causeSubjectId());
                            if (birth == null || !subject.equals(birth.settlementId())) {
                                throw new IllegalArgumentException("resident birth consumption retirement lacks settlement ownership");
                            }
                            return reducePopulationConsumption(state, intent, transition);
                        }), intent -> FencedRecoveryAsset.EFFECT,
                retirementAccount(PhysicalIntentLifecycleOwner.POPULATION_MIGRATION));
    }

    private static PhysicalIntentLifecycleCapability medicalConsumptionCapability() {
        return new FunctionalPhysicalIntentLifecycleCapability(PhysicalIntentLifecycleOwner.MEDICAL_TREATMENT,
                Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EXACT_ITEM_CONSUMPTION),
                (state, command, prepared) -> FrontierWorldCommandPlanner.rejected("physical executor cannot prepare medical consumption"),
                (state, command, intent, transition) -> {
                    if ((transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING
                            || transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED)
                            && !FrontierMedicalTreatmentSceneSupport.permitsCurrentConsumptionIntent(state, intent)) {
                        return FrontierWorldCommandPlanner.rejected("medical treatment consumption requires its current HOT infirmary scene");
                    }
                    return new CommandPlan.Accepted(MedicalTreatmentProcess.planTransition(state, intent, transition, command.submittedAt().ticks()));
                },
                (state, subject, intent) -> {
                    MedicalEvacuationOperation operation = state.humanPopulation().medicalOperations().get(intent.causeSubjectId());
                    if (operation == null || !subject.equals(operation.settlementId())) {
                        throw new IllegalArgumentException("medical treatment must be prepared by its settlement");
                    }
                    MedicalTreatmentProcess.operationForIntent(state, intent);
                    return state.preparePhysicalIntent(intent);
                },
                (state, subject, intent, transition) -> {
                    MedicalEvacuationOperation operation = state.humanPopulation().medicalOperations().get(intent.causeSubjectId());
                    if (operation == null || !subject.equals(operation.settlementId())) {
                        throw new IllegalArgumentException("medical treatment consumption transition lacks settlement ownership");
                    }
                    MedicalTreatmentProcess.operationForIntent(state, intent);
                    return reducePopulationConsumption(state, intent, transition);
                }, PhysicalIntentLifecycleRetirementPolicy.of(
                        (state, command, intent, transition) -> {
                            if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED
                                    && !FrontierMedicalTreatmentSceneSupport.permitsCurrentConsumptionIntent(state, intent)) {
                                return FrontierWorldCommandPlanner.rejected("medical treatment consumption requires its current HOT infirmary scene");
                            }
                            return new CommandPlan.Accepted(MedicalTreatmentProcess.planTransition(state, intent, transition, command.submittedAt().ticks()));
                        },
                        (state, subject, intent, transition) -> {
                            MedicalEvacuationOperation operation = state.humanPopulation().medicalOperations().get(intent.causeSubjectId());
                            if (operation == null || !subject.equals(operation.settlementId())) {
                                throw new IllegalArgumentException("medical treatment consumption retirement lacks settlement ownership");
                            }
                            MedicalTreatmentProcess.operationForIntent(state, intent);
                            return reducePopulationConsumption(state, intent, transition);
                        }), intent -> FencedRecoveryAsset.EFFECT,
                retirementAccount(PhysicalIntentLifecycleOwner.MEDICAL_TREATMENT));
    }

    private static PhysicalIntentLifecycleCapability provisionConsumptionCapability() {
        return new FunctionalPhysicalIntentLifecycleCapability(PhysicalIntentLifecycleOwner.SETTLEMENT_PROVISION,
                Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EXACT_ITEM_CONSUMPTION),
                (state, command, prepared) -> FrontierWorldCommandPlanner.rejected("physical executor cannot prepare settlement provision"),
                (state, command, intent, transition) -> new CommandPlan.Accepted(
                        SettlementProvisionProcess.planTransition(state, intent, transition, command.submittedAt().ticks())),
                SettlementProvisionProcess::reducePrepared,
                (state, subject, intent, transition) -> {
                    if (!state.humanPopulation().provisions().containsKey(intent.causeSubjectId()) || !subject.equals(intent.causeSubjectId())) {
                        throw new IllegalArgumentException("settlement provision consumption transition lacks settlement ownership");
                    }
                    return reducePopulationConsumption(state, intent, transition);
                }, PhysicalIntentLifecycleRetirementPolicy.of(
                        (state, command, intent, transition) -> new CommandPlan.Accepted(
                                SettlementProvisionProcess.planTransition(state, intent, transition, command.submittedAt().ticks())),
                        (state, subject, intent, transition) -> {
                            if (!state.humanPopulation().provisions().containsKey(intent.causeSubjectId()) || !subject.equals(intent.causeSubjectId())) {
                                throw new IllegalArgumentException("settlement provision consumption retirement lacks settlement ownership");
                            }
                            return reducePopulationConsumption(state, intent, transition);
                }), intent -> FencedRecoveryAsset.EFFECT,
                retirementAccount(PhysicalIntentLifecycleOwner.SETTLEMENT_PROVISION));
    }

    private static PhysicalIntentRetirementAccount retirementAccount(PhysicalIntentLifecycleOwner owner) {
        return PhysicalIntentRetirementAccount.declared(owner,
                java.util.EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class),
                (before, command, intent, transition) -> retirementFacts(before, command, intent, transition, owner),
                (before, intent, transition, binding) -> PhysicalIntentRetirementAccount.requireSameDeclaredAccount(binding,
                        retirementFacts(before, binding.continuation(), intent, transition, owner)),
                (before, after, intent, transition, binding) -> {
                    if (intent.lifecycleOwner() != owner) throw new IllegalArgumentException("population retirement account owner mismatch");
                    SubjectId itemId = populationCommitment(before, intent, owner);
                    ExactItemStack beforeItem = before.inventory().items().get(itemId);
                    if (beforeItem == null) throw new IllegalArgumentException("population retirement account has no exact pre-state item commitment");
                    if (after != before && transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED) {
                        ExactItemStack afterItem = after.inventory().items().get(itemId);
                        if (afterItem != null && afterItem.count() >= beforeItem.count()) {
                            throw new IllegalArgumentException("population retirement account did not consume its exact committed item");
                        }
                    }
                    if (after != before && transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                            && !beforeItem.equals(after.inventory().items().get(itemId))) {
                        throw new IllegalArgumentException("population retirement account changed its exact item across an ambiguous recovery");
                    }
                });
    }

    private static PhysicalIntentRetirementAccount.Binding retirementFacts(FrontierWorldState state, FrontierCommand command,
                                                                            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                            PhysicalIntentTransition transition, PhysicalIntentLifecycleOwner owner) {
        var continuation = command == null ? new PhysicalIntentRetirementAccount.CheckedNone<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>(PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION)
                : command.scheduleBinding().<PhysicalIntentRetirementAccount.Obligation<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>>map(bound -> new PhysicalIntentRetirementAccount.Exact<>(bound.action().id()))
                .orElseGet(() -> new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION));
        return retirementFacts(state, continuation, intent, transition, owner);
    }

    private static PhysicalIntentRetirementAccount.Binding retirementFacts(FrontierWorldState state,
                                                                            PhysicalIntentRetirementAccount.Obligation<io.farfrontier.palemirror.frontier.v3.api.ScheduleId> continuation,
                                                                            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                            PhysicalIntentTransition transition, PhysicalIntentLifecycleOwner owner) {
        return new PhysicalIntentRetirementAccount.Binding(owner, intent.id(),
                new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_APPLICABLE_RELATION), continuation,
                new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_LEASE_OR_CARRIER),
                new PhysicalIntentRetirementAccount.Exact<>(populationCommitment(state, intent, owner)), lateDisposition(transition));
    }

    /** Each exact-consumption owner names its item in its own retained aggregate; no subject-list search is authority. */
    private static SubjectId populationCommitment(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                  PhysicalIntentLifecycleOwner owner) {
        return switch (owner) {
            case POPULATION_MIGRATION -> {
                ResidentBirthJob birth = state.humanPopulation().birthJobs().get(intent.causeSubjectId());
                if (birth == null || !birth.consumptionIntentId().equals(intent.id())) throw new IllegalArgumentException("birth retirement has no exact retained permit");
                yield birth.foodItemId();
            }
            case MEDICAL_TREATMENT -> {
                MedicalEvacuationOperation medical = state.humanPopulation().medicalOperations().get(intent.causeSubjectId());
                if (medical == null || !medical.consumptionIntentId().equals(intent.id())) throw new IllegalArgumentException("medical retirement has no exact retained operation");
                yield medical.supplyItemId();
            }
            case SETTLEMENT_PROVISION -> {
                SettlementProvision provision = state.humanPopulation().provisions().get(intent.causeSubjectId());
                if (provision == null || provision.activeIntentId().filter(intent.id()::equals).isEmpty()) throw new IllegalArgumentException("provision retirement has no exact retained allocation");
                yield provision.currentOrActiveAllocation().itemId();
            }
            default -> throw new IllegalArgumentException("population retirement owner has no exact-consumption contract");
        };
    }

    private static PhysicalIntentRetirementAccount.LateDisposition lateDisposition(PhysicalIntentTransition transition) {
        return transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                ? PhysicalIntentRetirementAccount.LateDisposition.RETAIN_AMBIGUOUS_RECOVERY
                : PhysicalIntentRetirementAccount.LateDisposition.REJECT_STALE_ONCE;
    }

    /** Population-owned exact-consumption terminal reduction; no aggregate kind dispatch participates. */
    private static FrontierWorldState reducePopulationConsumption(FrontierWorldState state,
                                                                   io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                   PhysicalIntentTransition transition) {
        return PhysicalIntentTransitionStorage.reduce(state, intent, transition,
                FrontierPopulationProcessModule::confirmPopulationConsumption,
                PhysicalIntentTransitionStorage::recordUnknown);
    }

    private static FrontierWorldState confirmPopulationConsumption(FrontierWorldState state,
                                                                    io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                    PhysicalEffectObservation evidence,
                                                                    java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId,
                                                                            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents) {
        if (!(evidence instanceof ExactItemConsumedObservation consumed)) {
            throw new IllegalArgumentException("population consumption requires item observation evidence");
        }
        io.farfrontier.palemirror.frontier.v3.api.SubjectId itemId = intent.subjectIds().stream()
                .filter(id -> !id.equals(intent.causeSubjectId())).findFirst().orElseThrow();
        ExactItemStack item = state.inventory().items().get(itemId);
        if (!itemId.equals(consumed.itemId()) || item == null || item.count() != consumed.countBefore()
                || !(item.custody() instanceof InventoryCustody.ContainerSlot slot)) {
            throw new IllegalArgumentException("population consumption receipt does not match current stack");
        }
        ExactItemConsumptionStateSupport.Claim claim = ExactItemConsumptionStateSupport.claim(state, intent);
        if (!claim.item().equals(item) || !claim.containerId().equals(slot.containerId()) || claim.slot() != slot.slot()
                || claim.count() != consumed.consumedCount()) {
            throw new IllegalArgumentException("population consumption stack is not in an active owner container");
        }
        java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations =
                new java.util.LinkedHashMap<>(state.physicalObservations());
        observations.put(consumed.id(), consumed);
        FrontierWorldState consumedState = state.withChanges(FrontierWorldStateUpdate.begin().physicalIntents(intents)
                .physicalObservations(observations).inventory(state.inventory().consume(itemId, consumed.consumedCount())));
        if (state.humanPopulation().provisions().values().stream()
                .anyMatch(provision -> provision.activeIntentId().filter(intent.id()::equals).isPresent())) {
            return SettlementProvisionStateSupport.reducePhysicalConsumptionAfterInventory(consumedState, intent, consumed);
        }
        return consumedState;
    }
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof ResidentBorn) {
            return FrontierWorldCommandPlanner.rejected("resident birth is emitted only by a confirmed population permit");
        }
        if (command.payload() instanceof ResidentMigrated migration) {
            try { state.recordResidentMigration(migration); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(migration.destinationSettlementId(), migration)));
        }
        if (command.payload() instanceof ResidentTransitAdvanced advanced) {
            ResidentMigrationJourney journey = state.humanPopulation().migration(advanced.residentId());
            if (journey == null) return FrontierWorldCommandPlanner.rejected("HOT transit observation has no active migration journey");
            try { PopulationMigrationProcess.reduceHotAdvance(state, advanced); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(journey.originSettlementId(), advanced)));
        }
        if (command.payload() instanceof MedicalTreatmentSceneLeasePrepared prepared) {
            try {
                return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierMedicalTreatmentSceneSupport.owner(state,
                        FrontierSceneBehaviors.medicalTreatment(prepared.lease())), prepared)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof MedicalTreatmentSceneLeaseHandoff handoff) {
            try {
                return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierMedicalTreatmentSceneSupport.owner(state,
                        FrontierSceneBehaviors.medicalTreatment(handoff.lease())), handoff)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        return FrontierWorldCommandPlanner.rejected("population process does not admit command: " + command.payload().type());
    }

    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case ResidentBorn birth -> PopulationBirthProcess.reduceBorn(state, event.subject(), birth);
            case ResidentMigrated migration -> reduceMigrated(state, event.subject(), migration);
            case ResidentMigrationStarted started -> reduceStarted(state, event.subject(), started);
            case ResidentMigrationAdvanced advanced -> reduceAdvanced(state, event.subject(), advanced);
            case ResidentTransitAdvanced advanced -> reduceTransit(state, event.subject(), advanced);
            case ResidentMigrationBlocked blocked -> reduceBlocked(state, event.subject(), blocked);
            case ResidentMigrationResumed resumed -> reduceResumed(state, event.subject(), resumed);
            case ResidentBirthStarted started -> PopulationBirthProcess.reduceStarted(state, event.subject(), started);
            case ResidentBirthCancelled cancelled -> PopulationBirthProcess.reduceCancelled(state, event.subject(), cancelled);
            case LegacySettlementProvisionStarted started -> SettlementProvisionProcess.reduceLegacyStarted(state, event.subject(), started);
            case SettlementProvisionStarted started -> SettlementProvisionProcess.reduceStarted(state, event.subject(), started);
            case SettlementProvisionConsumed consumed -> SettlementProvisionProcess.reduceConsumed(state, event.subject(), consumed);
            case SettlementProvisionResolved resolved -> SettlementProvisionProcess.reduceResolved(state, event.subject(), resolved);
            case ResidentHealthTransition transition -> HumanHealthProcess.reduceResidentTransition(state, event.subject(), event.instant().ticks(), transition);
            case SettlementQuarantineTransition transition -> HumanHealthProcess.reduceQuarantineTransition(state, event.subject(), event.instant().ticks(), transition);
            case MedicalTreatmentStarted started -> MedicalTreatmentProcess.reduceStarted(state, event.subject(), started);
            case MedicalTreatmentTransition transition -> MedicalTreatmentProcess.reduceTransition(state, event.subject(), event.instant().ticks(), transition);
            case MedicalTreatmentSceneLeasePrepared prepared -> reduceMedicalScenePrepared(state, event.subject(), event, prepared);
            case MedicalTreatmentSceneLeaseHandoff handoff -> reduceMedicalSceneHandoff(state, event.subject(), event, handoff);
            default -> throw new IllegalArgumentException("population process does not own event: " + event.payload().type());
        };
    }

    private static FrontierWorldState reduceMigrated(FrontierWorldState state, SubjectId subject, ResidentMigrated migration) {
        if (!subject.equals(migration.destinationSettlementId())) throw new IllegalArgumentException("resident migration lacks destination settlement owner");
        return state.recordResidentMigration(migration);
    }

    private static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, ResidentMigrationStarted started) {
        if (!subject.equals(started.journey().originSettlementId())) throw new IllegalArgumentException("migration start lacks its origin settlement owner");
        return HumanPopulationStateSupport.startMigration(state, started.journey());
    }

    private static FrontierWorldState reduceAdvanced(FrontierWorldState state, SubjectId subject, ResidentMigrationAdvanced advanced) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(advanced.residentId());
        if (journey == null || !subject.equals(journey.originSettlementId())) throw new IllegalArgumentException("migration advance lacks its origin settlement owner");
        return HumanPopulationStateSupport.advanceMigration(state, advanced);
    }

    private static FrontierWorldState reduceTransit(FrontierWorldState state, SubjectId subject, ResidentTransitAdvanced advanced) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(advanced.residentId());
        if (journey == null || !subject.equals(journey.originSettlementId())) throw new IllegalArgumentException("HOT transit observation lacks its origin settlement owner");
        return PopulationMigrationProcess.reduceHotAdvance(state, advanced);
    }

    private static FrontierWorldState reduceBlocked(FrontierWorldState state, SubjectId subject, ResidentMigrationBlocked blocked) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(blocked.residentId());
        if (journey == null || !subject.equals(journey.originSettlementId())) throw new IllegalArgumentException("migration block lacks its origin settlement owner");
        return HumanPopulationStateSupport.blockMigration(state, blocked);
    }

    private static FrontierWorldState reduceResumed(FrontierWorldState state, SubjectId subject, ResidentMigrationResumed resumed) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(resumed.residentId());
        if (journey == null || !subject.equals(journey.originSettlementId())) throw new IllegalArgumentException("migration resume lacks its origin settlement owner");
        return HumanPopulationStateSupport.resumeMigration(state, resumed);
    }

    private static FrontierWorldState reduceMedicalScenePrepared(FrontierWorldState state, SubjectId subject, FrontierEvent event,
                                                                  MedicalTreatmentSceneLeasePrepared prepared) {
        SceneLease lease = prepared.lease();
        if (!subject.equals(FrontierMedicalTreatmentSceneSupport.owner(state, FrontierSceneBehaviors.medicalTreatment(lease)))
                || !lease.handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("medical scene lease does not match its retained treatment");
        }
        return state.prepareSceneLease(lease);
    }

    private static FrontierWorldState reduceMedicalSceneHandoff(FrontierWorldState state, SubjectId subject, FrontierEvent event,
                                                                 MedicalTreatmentSceneLeaseHandoff handoff) {
        SceneLease lease = handoff.lease();
        if (!subject.equals(FrontierMedicalTreatmentSceneSupport.owner(state, FrontierSceneBehaviors.medicalTreatment(lease)))
                || !lease.handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("medical scene hand-off does not match its retained treatment");
        }
        return state.handoffAmbientScene(new SceneLeaseHandoff(lease, handoff.ambientMembers()));
    }
}
