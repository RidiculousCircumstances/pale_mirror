package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;
import java.util.Set;

/** Exact owner for resident demography, migration, provisioning and health facts. */
final class FrontierPopulationProcessModule implements FrontierWorldProcessModule {
    @Override public List<PhysicalIntentLifecycleCapability> physicalIntentLifecycleCapabilities() {
        return List.of(medicalConsumptionCapability(), provisionConsumptionCapability());
    }

    private static PhysicalIntentLifecycleCapability medicalConsumptionCapability() {
        return new FunctionalPhysicalIntentLifecycleCapability(PhysicalIntentLifecycleDeclaration.physical(PhysicalIntentLifecycleOwner.MEDICAL_TREATMENT,
                Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EXACT_ITEM_CONSUMPTION),
                Set.of(PhysicalIntentRoleSchema.MEDICAL_TREATMENT_CONSUMPTION)),
                (state, command, prepared) -> FrontierWorldCommandPlanner.rejected("physical executor cannot prepare medical consumption"),
                (state, command, intent, transition) -> {
                    if (!medicalConsumptionPermitted(state, intent, transition)) {
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
                    requireMedicalConsumptionPermission(state, intent, transition);
                    return reducePopulationConsumption(state, intent, transition);
                }, PhysicalIntentLifecycleRetirementPolicy.of(
                        (state, command, intent, transition) -> {
                            if (!medicalConsumptionPermitted(state, intent, transition)) {
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
                            requireMedicalConsumptionPermission(state, intent, transition);
                            return reducePopulationConsumption(state, intent, transition);
                        }), intent -> FencedRecoveryAsset.EFFECT,
                retirementAccount(PhysicalIntentLifecycleOwner.MEDICAL_TREATMENT), PhysicalIntentResolvedRetentionPolicy.confirmedReceiptWithoutRecovery(), PhysicalIntentRecoveryDiagnosticProducer.MEDICAL_TREATMENT);
    }

    private static boolean medicalConsumptionPermitted(FrontierWorldState state,
                                                       io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                       PhysicalIntentTransition transition) {
        return switch (transition.status()) {
            case RUNNING -> FrontierMedicalTreatmentSceneSupport.permitsCurrentConsumptionIntent(state, intent);
            case CONFIRMED -> FrontierMedicalTreatmentSceneSupport.permitsConsumptionReceipt(state, intent);
            default -> true;
        };
    }

    private static void requireMedicalConsumptionPermission(FrontierWorldState state,
                                                            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                            PhysicalIntentTransition transition) {
        if (!medicalConsumptionPermitted(state, intent, transition)) {
            throw new IllegalArgumentException("medical treatment consumption requires its current HOT infirmary scene");
        }
    }

    private static PhysicalIntentLifecycleCapability provisionConsumptionCapability() {
        // The wire owner remains declared so old bytes fail at their exact boundary instead
        // of being misrouted to medical consumption. No current world may prepare, advance or
        // replay settlement-wide ration consumption after resident meals own nutrition.
        return new FunctionalPhysicalIntentLifecycleCapability(PhysicalIntentLifecycleDeclaration.physical(PhysicalIntentLifecycleOwner.SETTLEMENT_PROVISION,
                Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EXACT_ITEM_CONSUMPTION),
                Set.of(PhysicalIntentRoleSchema.SETTLEMENT_PROVISION_CONSUMPTION)),
                (state, command, prepared) -> FrontierWorldCommandPlanner.rejected("settlement-wide provision is retired"),
                (state, command, intent, transition) -> FrontierWorldCommandPlanner.rejected("settlement-wide provision is retired"),
                (state, subject, intent) -> { throw new IllegalArgumentException("settlement-wide provision is retired"); },
                (state, subject, intent, transition) -> { throw new IllegalArgumentException("settlement-wide provision is retired"); },
                PhysicalIntentLifecycleRetirementPolicy.of(
                        (state, command, intent, transition) -> FrontierWorldCommandPlanner.rejected("settlement-wide provision is retired"),
                        (state, subject, intent, transition) -> { throw new IllegalArgumentException("settlement-wide provision is retired"); }),
                intent -> FencedRecoveryAsset.EFFECT,
                retirementAccount(PhysicalIntentLifecycleOwner.SETTLEMENT_PROVISION), PhysicalIntentResolvedRetentionPolicy.confirmedReceiptWithoutRecovery(), PhysicalIntentRecoveryDiagnosticProducer.SETTLEMENT_PROVISION);
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
        io.farfrontier.palemirror.frontier.v3.api.SubjectId itemId = intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ITEM);
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
        return consumedState;
    }
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof ResidentBorn) {
            return FrontierWorldCommandPlanner.rejected("resident birth is emitted only by its scheduled canonical commitment");
        }
        if (command.payload() instanceof ResidentMigrated migration) {
            try { state.recordResidentMigration(migration); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(migration.destinationSettlementId(), migration)));
        }
        if (command.payload() instanceof ResidentTransitAdvanced advanced) {
            ResidentMigrationJourney journey = state.humanPopulation().migration(advanced.residentId());
            if (journey == null) return FrontierWorldCommandPlanner.rejected("HOT transit observation has no active migration journey");
            try { PopulationMigrationProcess.reduceHotAdvance(state, advanced, command.submittedAt().ticks()); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(journey.originSettlementId(), advanced)));
        }
        if (command.payload() instanceof ResidentWorkModifiersChanged changed) {
            try {
                if (changed.atTick() != command.submittedAt().ticks())
                    throw new IllegalArgumentException("work modifiers must change at the current command tick");
                var next = ResidentWorkModifiersProcess.reduce(state, changed.residentId(), changed);
                var events = new java.util.ArrayList<ProposedEvent>();
                events.add(new ProposedEvent(changed.residentId(), changed));
                events.addAll(ActivityExecutionCapabilities.workStatsChanged(next,
                        HumanAssignmentProjection.compile(next).assignment(changed.residentId()), changed.atTick()));
                return new CommandPlan.Accepted(List.copyOf(events));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResidentMetabolismChanged changed) {
            if (command.submittedAt().ticks() != changed.atTick())
                return FrontierWorldCommandPlanner.rejected("metabolism edit must use its canonical submission instant");
            try { return new CommandPlan.Accepted(ResidentMetabolismProcess.plan(state, changed)); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResidentMealHotArrived arrived) {
            try { ResidentMealProcess.reduceHotArrived(state, arrived.residentId(), arrived); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(arrived.residentId(), arrived)));
        }
        if (command.payload() instanceof ResidentMealHotEffectPrepared prepared) {
            try { ResidentMealProcess.reduceHotPrepared(state, prepared.residentId(), prepared); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(prepared.residentId(), prepared)));
        }
        if (command.payload() instanceof ResidentMealHotEffectObserved observed) {
            try { return new CommandPlan.Accepted(ResidentMealProcess.planHotObserved(state, observed, command.submittedAt().ticks())); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResidentMealHotHandMaterialized observed) {
            try { ResidentMealProcess.reduceHotHandMaterialized(state, observed.residentId(), observed); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(observed.residentId(), observed)));
        }
        if (command.payload() instanceof ResidentMealHotHandReleased observed) {
            try { ResidentMealProcess.reduceHotHandReleased(state, observed.residentId(), observed); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(observed.residentId(), observed)));
        }
        if (command.payload() instanceof ResidentMealHotReturned returned) {
            try { return new CommandPlan.Accepted(ResidentMealProcess.planHotReturned(state, returned,
                    command.submittedAt().ticks())); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ResidentMealHotAccessCleared cleared) {
            try { ResidentMealProcess.reduceHotAccessCleared(state, cleared.residentId(), cleared); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(cleared.residentId(), cleared)));
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
            case ResidentTransitAdvanced advanced -> reduceTransit(state, event.subject(), advanced, event.instant().ticks());
            case ResidentMigrationBlocked blocked -> reduceBlocked(state, event.subject(), blocked);
            case ResidentMigrationResumed resumed -> reduceResumed(state, event.subject(), resumed);
            case ResidentBirthStarted started -> PopulationBirthProcess.reduceStarted(state, event.subject(), started);
            case LegacySettlementProvisionStarted ignored -> throw new IllegalArgumentException("legacy settlement ration event is retired by resident nutrition");
            case SettlementProvisionStarted ignored -> throw new IllegalArgumentException("settlement provision start is retired by resident nutrition");
            case SettlementProvisionConsumed ignored -> throw new IllegalArgumentException("settlement provision consumption is retired by resident nutrition");
            case SettlementProvisionResolved ignored -> throw new IllegalArgumentException("settlement provision resolution is retired by resident nutrition");
            case ResidentStarvationIntegrated integrated -> ResidentStarvationProcess.reduce(state, event.subject(), integrated);
            case ResidentNeedIntegrated integrated -> ResidentNeedProcess.reduce(state, event.subject(), integrated);
            case ResidentWorkModifiersChanged changed -> ResidentWorkModifiersProcess.reduce(state, event.subject(), changed);
            case ResidentMetabolismChanged changed -> ResidentMetabolismProcess.reduce(state, event.subject(), changed);
            case ResidentMealStarted started -> ResidentActivityProcess.reduceMealStarted(state, event.subject(), started);
            case ResidentMealColdStep step -> ResidentMealProcess.reduceColdStep(state, event.subject(), step);
            case ResidentMealHotArrived arrived -> ResidentMealProcess.reduceHotArrived(state, event.subject(), arrived);
            case ResidentMealHotEffectPrepared prepared -> ResidentMealProcess.reduceHotPrepared(state, event.subject(), prepared);
            case ResidentMealHotEffectObserved observed -> ResidentActivityProcess.reduceMealEffectObserved(state, event.subject(), observed, event.instant().ticks());
            case ResidentMealHotHandMaterialized observed -> ResidentMealProcess.reduceHotHandMaterialized(state, event.subject(), observed);
            case ResidentMealHotHandReleased observed -> ResidentMealProcess.reduceHotHandReleased(state, event.subject(), observed);
            case ResidentMealHotAccessCleared cleared -> ResidentMealProcess.reduceHotAccessCleared(state, event.subject(), cleared);
            case ResidentMealHotReturned returned -> ResidentActivityProcess.reduceMealReturned(state, event.subject(), returned,
                    event.instant().ticks());
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

    private static FrontierWorldState reduceTransit(FrontierWorldState state, SubjectId subject,
                                                     ResidentTransitAdvanced advanced, long atTick) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(advanced.residentId());
        if (journey == null || !subject.equals(journey.originSettlementId())) throw new IllegalArgumentException("HOT transit observation lacks its origin settlement owner");
        return PopulationMigrationProcess.reduceHotAdvance(state, advanced, atTick);
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
