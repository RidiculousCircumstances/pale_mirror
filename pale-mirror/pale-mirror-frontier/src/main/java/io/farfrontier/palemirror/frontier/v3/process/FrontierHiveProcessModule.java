package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;
import java.util.Set;

/** Exact owner for hive perception, doctrine, growth and combat facts. */
final class FrontierHiveProcessModule implements FrontierWorldProcessModule {
    @Override public List<PhysicalIntentLifecycleCapability> physicalIntentLifecycleCapabilities() {
        return List.of(explosionCapability(), nutrientTransferCapability(), hiveGrowthCapability(),
                sceneStrikeCapability(PhysicalIntentLifecycleOwner.ROUTE_ENGAGEMENT),
                settlementAssaultCapability());
    }

    private static PhysicalIntentLifecycleCapability explosionCapability() {
        return new FunctionalPhysicalIntentLifecycleCapability(PhysicalIntentLifecycleOwner.HIVE_MOBILIZATION,
                Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EXPLOSION),
                (state, command, prepared) -> {
                    try {
                        ExplosionStateSupport.validateIntent(state, prepared.intent());
                        return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), prepared)));
                    } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
                },
                (state, command, intent, transition) -> new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), transition))),
                (state, subject, intent) -> {
                    if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("explosion intent must be prepared by the hive");
                    ExplosionStateSupport.validateIntent(state, intent);
                    return state.preparePhysicalIntent(intent);
                },
                (state, subject, intent, transition) -> {
                    if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("explosion transition lacks hive ownership");
                    return reduceExplosionTransition(state, intent, transition);
                }, PhysicalIntentLifecycleRetirementPolicy.of(
                        (state, command, intent, transition) -> new CommandPlan.Accepted(List.of(
                                new ProposedEvent(state.bootstrap().hive().id(), transition))),
                        (state, subject, intent, transition) -> {
                            if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("explosion retirement lacks hive ownership");
                            return reduceExplosionTransition(state, intent, transition);
                        }), intent -> FencedRecoveryAsset.EFFECT,
                retirementAccount(PhysicalIntentLifecycleOwner.HIVE_MOBILIZATION));
    }

    private static PhysicalIntentLifecycleCapability nutrientTransferCapability() {
        return new FunctionalPhysicalIntentLifecycleCapability(PhysicalIntentLifecycleOwner.HIVE_NUTRIENT_TRANSFER,
                Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE,
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL),
                (state, command, prepared) -> {
                    var intent = prepared.intent();
                    boolean owns = state.bootstrap().hive().id().equals(intent.causeSubjectId())
                            && state.hiveColony().nutrientTransfers().containsKey(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.TRANSFER));
                    return owns ? new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), prepared)))
                            : FrontierWorldCommandPlanner.rejected("hive nutrient endpoint has no retained transfer");
                },
                (state, command, intent, transition) -> new CommandPlan.Accepted(HiveNutrientTransferProcess.planTransition(
                        state, intent, transition, command.submittedAt().ticks())),
                (state, subject, intent) -> {
                    if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("hive nutrient endpoint intent must be prepared by the hive");
                    return state.preparePhysicalIntent(intent);
                },
                (state, subject, intent, transition) -> {
                    if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("hive nutrient endpoint transition lacks hive ownership");
                    return reduceNutrientTransition(state, intent, transition);
                }, PhysicalIntentLifecycleRetirementPolicy.of(
                        (state, command, intent, transition) -> new CommandPlan.Accepted(HiveNutrientTransferProcess.planTransition(
                                state, intent, transition, command.submittedAt().ticks())),
                        (state, subject, intent, transition) -> {
                            if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("hive nutrient endpoint retirement lacks hive ownership");
                            return reduceNutrientTransition(state, intent, transition);
                        }), intent -> FencedRecoveryAsset.CARGO,
                retirementAccount(PhysicalIntentLifecycleOwner.HIVE_NUTRIENT_TRANSFER));
    }

    private static PhysicalIntentLifecycleCapability hiveGrowthCapability() {
        return new FunctionalPhysicalIntentLifecycleCapability(PhysicalIntentLifecycleOwner.HIVE_GROWTH,
                Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EXACT_ITEM_CONSUMPTION),
                (state, command, prepared) -> FrontierWorldCommandPlanner.rejected("physical executor cannot prepare hive growth consumption"),
                (state, command, intent, transition) -> new CommandPlan.Accepted(HiveGrowthProcess.planTransition(
                        state, intent, transition, command.submittedAt().ticks())),
                HiveGrowthProcess::reducePrepared,
                (state, subject, intent, transition) -> {
                    HiveGrowthJob job = state.hiveColony().growthJobs().get(intent.causeSubjectId());
                    if (job == null || !subject.equals(job.hiveId())) throw new IllegalArgumentException("hive growth consumption transition lacks hive ownership");
                    return reduceHiveGrowthConsumption(state, intent, transition);
                }, PhysicalIntentLifecycleRetirementPolicy.of(
                        (state, command, intent, transition) -> new CommandPlan.Accepted(HiveGrowthProcess.planTransition(
                                state, intent, transition, command.submittedAt().ticks())),
                        (state, subject, intent, transition) -> {
                            HiveGrowthJob job = state.hiveColony().growthJobs().get(intent.causeSubjectId());
                            if (job == null || !subject.equals(job.hiveId())) throw new IllegalArgumentException("hive growth consumption retirement lacks hive ownership");
                            return reduceHiveGrowthConsumption(state, intent, transition);
                        }), intent -> FencedRecoveryAsset.EFFECT,
                retirementAccount(PhysicalIntentLifecycleOwner.HIVE_GROWTH));
    }

    private static PhysicalIntentLifecycleCapability sceneStrikeCapability(PhysicalIntentLifecycleOwner owner) {
        return new FunctionalPhysicalIntentLifecycleCapability(owner,
                Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SCENE_STRIKE),
                (state, command, prepared) -> {
                    try {
                        SceneStrikeStateSupport.validateIntent(state, prepared.intent());
                        return new CommandPlan.Accepted(List.of(new ProposedEvent(SceneStrikeStateSupport.owner(state, prepared.intent()), prepared)));
                    } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
                },
                (state, command, intent, transition) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SceneStrikeStateSupport.owner(state, intent), transition))),
                (state, subject, intent) -> {
                    SceneStrikeStateSupport.validateIntent(state, intent);
                    if (!subject.equals(SceneStrikeStateSupport.owner(state, intent))) throw new IllegalArgumentException("scene strike must be prepared by its exact scene owner");
                    return state.preparePhysicalIntent(intent);
                },
                (state, subject, intent, transition) -> {
                    if (!subject.equals(SceneStrikeStateSupport.owner(state, intent))) throw new IllegalArgumentException("scene strike transition lacks exact scene ownership");
                    return reduceSceneStrikeTransition(state, intent, transition);
                }, PhysicalIntentLifecycleRetirementPolicy.of(
                        (state, command, intent, transition) -> new CommandPlan.Accepted(List.of(
                                new ProposedEvent(SceneStrikeStateSupport.owner(state, intent), transition))),
                        (state, subject, intent, transition) -> {
                            if (!subject.equals(SceneStrikeStateSupport.owner(state, intent))) throw new IllegalArgumentException("scene strike retirement lacks exact scene ownership");
                            return reduceSceneStrikeTransition(state, intent, transition);
                        }), intent -> FencedRecoveryAsset.EFFECT,
                retirementAccount(owner));
    }

    private static PhysicalIntentLifecycleCapability settlementAssaultCapability() {
        return new FunctionalPhysicalIntentLifecycleCapability(PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT,
                Set.of(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SCENE_STRIKE,
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_ISSUE,
                        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_RETURN),
                (state, command, prepared) -> planAssaultPreparation(state, prepared),
                (state, command, intent, transition) -> planAssaultTransition(state, intent, transition),
                FrontierHiveProcessModule::reduceAssaultPreparation,
                FrontierHiveProcessModule::reduceAssaultTransition,
                PhysicalIntentLifecycleRetirementPolicy.of(
                        (state, command, intent, transition) -> planAssaultTransition(state, intent, transition),
                        FrontierHiveProcessModule::reduceAssaultTransition), intent -> FencedRecoveryAsset.EFFECT,
                retirementAccount(PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT));
    }

    private static PhysicalIntentRetirementAccount retirementAccount(PhysicalIntentLifecycleOwner owner) {
        return PhysicalIntentRetirementAccount.declared(owner,
                java.util.EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class),
                FrontierHiveProcessModule::bindRetirement,
                FrontierHiveProcessModule::verifyRetirementBinding,
                (before, after, intent, transition, binding) -> {
                    if (intent.lifecycleOwner() != owner) throw new IllegalArgumentException("hive retirement account owner mismatch");
                    if (after == before) return;
                    if (transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) return;
                    if (intent.lifecycleOwner() == PhysicalIntentLifecycleOwner.HIVE_GROWTH) {
                        HiveGrowthJob job = before.hiveColony().growthJobs().get(intent.causeSubjectId());
                        if (job == null) throw new IllegalArgumentException("hive growth retirement lacks its exact pre-state job");
                        if (job.inputHold() instanceof HiveGrowthInputHold.Exact) {
                            SubjectId item = ((PhysicalIntentRetirementAccount.Exact<SubjectId>) binding.commitment()).value();
                            if (after.inventory().items().containsKey(item)) throw new IllegalArgumentException("hive growth did not consume its exact input");
                        } else if (job.inputHold() instanceof HiveGrowthInputHold.FungibleCold held
                                && after.inventory().fungibleResources().claims().containsKey(held.claimId())) {
                            throw new IllegalArgumentException("hive growth did not consume its exact fungible claim");
                        }
                    }
                    if (intent.lifecycleOwner() == PhysicalIntentLifecycleOwner.HIVE_NUTRIENT_TRANSFER) {
                        HiveNutrientTransfer beforeTransfer = transfer(before, intent);
                        HiveNutrientTransfer afterTransfer = after.hiveColony().nutrientTransfers().get(beforeTransfer.id());
                        boolean terminalArrival = intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL;
                        if ((afterTransfer == null && !terminalArrival)
                                || (afterTransfer != null && afterTransfer.phase() == beforeTransfer.phase())) {
                            throw new IllegalArgumentException("hive nutrient retirement did not advance its exact transfer");
                        }
                    }
                    if (intent.lifecycleOwner() == PhysicalIntentLifecycleOwner.HIVE_MOBILIZATION
                            && !before.hiveColony().mobilizations().equals(after.hiveColony().mobilizations())) {
                        throw new IllegalArgumentException("hive explosion retirement changed a mobilization roster outside its exact effect boundary");
                    }
                    if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SCENE_STRIKE
                            && transition.observation().isEmpty()) throw new IllegalArgumentException("scene strike retirement lacks exact effect evidence");
                });
    }

    private static PhysicalIntentRetirementAccount.Binding bindRetirement(FrontierWorldState before, FrontierCommand command,
                                                                            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                            PhysicalIntentTransition transition) {
        var continuation = command == null ? new PhysicalIntentRetirementAccount.CheckedNone<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>(io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION)
                : command.scheduleBinding().<PhysicalIntentRetirementAccount.Obligation<io.farfrontier.palemirror.frontier.v3.api.ScheduleId>>map(binding -> new PhysicalIntentRetirementAccount.Exact<>(binding.action().id()))
                .orElseGet(() -> new PhysicalIntentRetirementAccount.CheckedNone<>(io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION));
        return retirementFacts(before, continuation, intent, transition);
    }

    private static void verifyRetirementBinding(FrontierWorldState before, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                PhysicalIntentTransition transition, PhysicalIntentRetirementAccount.Binding binding) {
        PhysicalIntentRetirementAccount.requireSameDeclaredAccount(binding,
                retirementFacts(before, binding.continuation(), intent, transition));
    }

    /** Each hive endpoint names its authoritative aggregate directly; no roster/cargo is inferred. */
    private static PhysicalIntentRetirementAccount.Binding retirementFacts(FrontierWorldState state,
            PhysicalIntentRetirementAccount.Obligation<io.farfrontier.palemirror.frontier.v3.api.ScheduleId> continuation,
            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent, PhysicalIntentTransition transition) {
        var noneRelations = new PhysicalIntentRetirementAccount.CheckedNone<List<FrontierDomainRelationships.Edge>>(PhysicalIntentRetirementProof.Absence.NO_APPLICABLE_RELATION);
        var noneSubject = new PhysicalIntentRetirementAccount.CheckedNone<io.farfrontier.palemirror.frontier.v3.api.SubjectId>(PhysicalIntentRetirementProof.Absence.NO_LEASE_OR_CARRIER);
        PhysicalIntentRetirementAccount.Obligation<List<FrontierDomainRelationships.Edge>> relations = noneRelations;
        PhysicalIntentRetirementAccount.Obligation<io.farfrontier.palemirror.frontier.v3.api.SubjectId> carrier = noneSubject;
        PhysicalIntentRetirementAccount.Obligation<io.farfrontier.palemirror.frontier.v3.api.SubjectId> commitment = new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_RESOURCE_COMMITMENT);
        switch (intent.lifecycleOwner()) {
            case HIVE_MOBILIZATION -> {
                if (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EXPLOSION) throw new IllegalArgumentException("hive mobilization has foreign intent kind");
                // An explosion is an effect-only endpoint, but it is still pinned to the one hive.
                ExplosionStateSupport.validateIntent(state, intent);
                carrier = new PhysicalIntentRetirementAccount.Exact<>(state.bootstrap().hive().id());
            }
            case HIVE_NUTRIENT_TRANSFER -> {
                HiveNutrientTransfer transfer = transfer(state, intent);
                carrier = new PhysicalIntentRetirementAccount.Exact<>(transfer.cargoId());
                commitment = new PhysicalIntentRetirementAccount.Exact<>(transfer.itemId());
            }
            case HIVE_GROWTH -> {
                HiveGrowthJob job = state.hiveColony().growthJobs().get(intent.causeSubjectId());
                if (job == null || !job.consumptionIntentId().equals(intent.id())) throw new IllegalArgumentException("hive growth retirement lacks its exact job");
                if (job.inputHold() instanceof HiveGrowthInputHold.Exact
                        && !state.inventory().items().containsKey(job.consumedItemId())) {
                    throw new IllegalArgumentException("hive growth retirement lost its exact input");
                }
                if (job.inputHold() instanceof HiveGrowthInputHold.FungibleCold held
                        && (!state.inventory().fungibleResources().claims().containsKey(held.claimId())
                        || state.inventory().fungibleResources().accounts().get(held.accountId()) == null
                        || state.inventory().fungibleResources().accounts().get(held.accountId()).claimQuantities().getOrDefault(held.claimId(), 0) != 64)) {
                    throw new IllegalArgumentException("hive growth retirement lost its exact fungible claim");
                }
                carrier = new PhysicalIntentRetirementAccount.Exact<>(job.nestId());
                commitment = new PhysicalIntentRetirementAccount.Exact<>(job.consumedItemId());
            }
            case ROUTE_ENGAGEMENT, SETTLEMENT_ASSAULT -> {
                if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SCENE_STRIKE) {
                    SceneStrikeStateSupport.validateIntent(state, intent);
                    carrier = new PhysicalIntentRetirementAccount.Exact<>(intent.causeSubjectId());
                    commitment = new PhysicalIntentRetirementAccount.Exact<>(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ATTACKER));
                } else {
                    SubjectId item = intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.EQUIPMENT);
                    if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_ISSUE) EquipmentIssueStateSupport.validateIntent(state, intent);
                    else if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_RETURN) EquipmentReturnStateSupport.validateIntent(state, intent);
                    else throw new IllegalArgumentException("settlement assault has foreign intent kind");
                    carrier = new PhysicalIntentRetirementAccount.Exact<>(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.DEFENDER)); commitment = new PhysicalIntentRetirementAccount.Exact<>(item);
                }
            }
            default -> throw new IllegalArgumentException("hive retirement account received foreign owner");
        }
        return new PhysicalIntentRetirementAccount.Binding(intent.lifecycleOwner(), intent.id(), relations, continuation, carrier, commitment,
                transition.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                        ? PhysicalIntentRetirementAccount.LateDisposition.RETAIN_AMBIGUOUS_RECOVERY : PhysicalIntentRetirementAccount.LateDisposition.REJECT_STALE_ONCE);
    }

    private static HiveNutrientTransfer transfer(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        HiveNutrientTransfer transfer = state.hiveColony().nutrientTransfers().get(
                intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.TRANSFER));
        if (transfer == null || !transfer.endpointIntentId().filter(intent.id()::equals).isPresent()) {
            throw new IllegalArgumentException("hive nutrient retirement lacks its exact transfer");
        }
        return transfer;
    }

    private static CommandPlan planAssaultPreparation(FrontierWorldState state, PhysicalIntentPrepared prepared) {
        if (prepared.intent().kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SCENE_STRIKE) {
            try {
                SceneStrikeStateSupport.validateIntent(state, prepared.intent());
                return new CommandPlan.Accepted(List.of(new ProposedEvent(SceneStrikeStateSupport.owner(state, prepared.intent()), prepared)));
            } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        return FrontierWorldCommandPlanner.rejected("physical executor cannot prepare settlement-assault equipment");
    }

    private static CommandPlan planAssaultTransition(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                      PhysicalIntentTransition transition) {
        if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SCENE_STRIKE) {
            return new CommandPlan.Accepted(List.of(new ProposedEvent(SceneStrikeStateSupport.owner(state, intent), transition)));
        }
        if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_ISSUE) EquipmentIssueStateSupport.validateIntent(state, intent);
        else EquipmentReturnStateSupport.validateIntent(state, intent);
        return new CommandPlan.Accepted(List.of(new ProposedEvent(intent.causeSubjectId(), transition)));
    }

    private static FrontierWorldState reduceAssaultPreparation(FrontierWorldState state,
                                                                io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SCENE_STRIKE) {
            SceneStrikeStateSupport.validateIntent(state, intent);
            if (!subject.equals(SceneStrikeStateSupport.owner(state, intent))) throw new IllegalArgumentException("scene strike must be prepared by its exact scene owner");
        } else if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_ISSUE) {
            EquipmentIssueStateSupport.validateIntent(state, intent);
            if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("equipment issue must be prepared by its settlement");
        } else {
            EquipmentReturnStateSupport.validateIntent(state, intent);
            if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("equipment return must be prepared by its settlement");
        }
        return state.preparePhysicalIntent(intent);
    }

    private static FrontierWorldState reduceAssaultTransition(FrontierWorldState state,
                                                               io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                               io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                               PhysicalIntentTransition transition) {
        if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SCENE_STRIKE) {
            if (!subject.equals(SceneStrikeStateSupport.owner(state, intent))) throw new IllegalArgumentException("scene strike transition lacks exact scene ownership");
        } else if (!subject.equals(intent.causeSubjectId())) {
            throw new IllegalArgumentException("settlement-assault equipment transition lacks settlement ownership");
        }
        return intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SCENE_STRIKE
                ? reduceSceneStrikeTransition(state, intent, transition)
                : PhysicalIntentTransitionStorage.reduce(state, intent, transition,
                (currentState, current, evidence, intents) -> HumanEquipmentStateSupport.complete(currentState, current, evidence,
                        new java.util.LinkedHashMap<>(intents)), PhysicalIntentTransitionStorage::recordUnknown);
    }

    private static FrontierWorldState reduceExplosionTransition(FrontierWorldState state,
                                                                 io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                 PhysicalIntentTransition transition) {
        return PhysicalIntentTransitionStorage.reduce(state, intent, transition,
                (currentState, current, evidence, intents) -> {
                    if (!(evidence instanceof ExplosionObservation explosion)) {
                        throw new IllegalArgumentException("explosion requires post-impact observation evidence");
                    }
                    return ExplosionStateSupport.complete(currentState, current, explosion, new java.util.LinkedHashMap<>(intents));
                }, PhysicalIntentTransitionStorage::recordUnknown);
    }

    private static FrontierWorldState reduceNutrientTransition(FrontierWorldState state,
                                                                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                PhysicalIntentTransition transition) {
        return PhysicalIntentTransitionStorage.reduce(state, intent, transition,
                (currentState, current, evidence, intents) -> HiveNutrientTransferStateSupport.completeEndpoint(currentState, current,
                        evidence, new java.util.LinkedHashMap<>(intents)),
                (currentState, current, intents) -> HiveNutrientTransferStateSupport.unknownEndpoint(currentState, current,
                        new java.util.LinkedHashMap<>(intents)));
    }

    private static FrontierWorldState reduceHiveGrowthConsumption(FrontierWorldState state,
                                                                   io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                   PhysicalIntentTransition transition) {
        return PhysicalIntentTransitionStorage.reduce(state, intent, transition,
                (currentState, current, evidence, intents) -> {
                    if (evidence instanceof FungibleResourceConsumedObservation consumed) {
                        HiveGrowthStateSupport.FungibleConsumption consumedState = HiveGrowthStateSupport.consumeObservedFungible(
                                currentState, current.causeSubjectId(), consumed);
                        java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations =
                                new java.util.LinkedHashMap<>(currentState.physicalObservations());
                        observations.put(consumed.id(), consumed);
                        return currentState.withChanges(FrontierWorldStateUpdate.begin().physicalIntents(intents)
                                .physicalObservations(observations).inventory(consumedState.inventory()).hiveColony(consumedState.colony()));
                    }
                    if (!(evidence instanceof ExactItemConsumedObservation consumed)) {
                        throw new IllegalArgumentException("hive growth consumption requires exact or fungible item observation evidence");
                    }
                    io.farfrontier.palemirror.frontier.v3.api.SubjectId itemId = current.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ITEM);
                    ExactItemStack item = currentState.inventory().items().get(itemId);
                    if (!itemId.equals(consumed.itemId()) || item == null || item.count() != consumed.countBefore()
                            || !(item.custody() instanceof InventoryCustody.ContainerSlot slot)) {
                        throw new IllegalArgumentException("hive growth consumption receipt does not match current stack");
                    }
                    ExactItemConsumptionStateSupport.Claim claim = ExactItemConsumptionStateSupport.claim(currentState, current);
                    if (!claim.item().equals(item) || !claim.containerId().equals(slot.containerId()) || claim.slot() != slot.slot()
                            || claim.count() != consumed.consumedCount()) {
                        throw new IllegalArgumentException("hive growth consumption stack is not in an active owner container");
                    }
                    java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations =
                            new java.util.LinkedHashMap<>(currentState.physicalObservations());
                    observations.put(consumed.id(), consumed);
                    return currentState.withChanges(FrontierWorldStateUpdate.begin().physicalIntents(intents).physicalObservations(observations)
                            .inventory(currentState.inventory().consume(itemId, consumed.consumedCount()))
                            .hiveColony(currentState.hiveColony().consumeTransferredNutrient(current.causeSubjectId(), itemId)));
                }, PhysicalIntentTransitionStorage::recordUnknown);
    }

    private static FrontierWorldState reduceSceneStrikeTransition(FrontierWorldState state,
                                                                   io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                                   PhysicalIntentTransition transition) {
        return PhysicalIntentTransitionStorage.reduce(state, intent, transition,
                (currentState, current, evidence, intents) -> {
                    if (!(evidence instanceof SceneStrikeObservation strike)) {
                        throw new IllegalArgumentException("scene strike requires exact hit evidence");
                    }
                    SceneStrikeStateSupport.validateObservation(currentState, current, strike);
                    java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations =
                            new java.util.LinkedHashMap<>(currentState.physicalObservations());
                    observations.put(strike.id(), strike);
                    java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ActorLocation> actors =
                            new java.util.LinkedHashMap<>(currentState.actorLocations());
                    if (strike.targetHealthAfter().compareTo(io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO) > 0) {
                        ActorLocation target = actors.get(strike.targetId());
                        actors.put(strike.targetId(), new ActorLocation(target.body(), target.condition().withHealth(strike.targetHealthAfter())));
                    }
                    return currentState.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).physicalIntents(intents)
                            .physicalObservations(observations)).withStrategicPlans(currentState.strategicPlans().afterConfirmedHotStrike(current));
                }, PhysicalIntentTransitionStorage::recordUnknown);
    }
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof ScoutPatrolAdvanced advanced) {
            try { HiveScoutPatrolProcess.reduce(state, state.bootstrap().hive().id(), advanced); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), advanced)));
        }
        if (command.payload() instanceof ScoutPatrolLeaseRecovered recovered) {
            try { HiveScoutPatrolProcess.reduceLeaseRecovered(state, state.bootstrap().hive().id(), recovered); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), recovered)));
        }
        if (command.payload() instanceof HotScoutOperationObserved observed) {
            try { HivePerceptionProcess.reduceHot(state, state.bootstrap().hive().id(), observed); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            HiveOperationKnowledge.Sighting sighting = new HiveOperationKnowledge.Sighting(observed.operationId(), observed.scoutId(),
                    observed.seenCarrierPosition(), observed.observedAt());
            return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), observed),
                    new ProposedEvent(state.bootstrap().hive().id(), new ScheduleEffect.Created(
                            StrategicObjectiveProcess.interceptOpportunity(state.bootstrap().hive().id(), sighting,
                                    Math.addExact(command.submittedAt().ticks(), 1L))))));
        }
        if (command.payload() instanceof HiveMobilizationReleaseStarted started) {
            try { HiveMobilizationProcess.reduceReleaseStarted(state, state.bootstrap().hive().id(), started); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), started)));
        }
        if (command.payload() instanceof HiveMobilizationCocoonReleased released) {
            try { HiveMobilizationProcess.reduceCocoonReleased(state, state.bootstrap().hive().id(), released); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            HiveMobilization mobilization = state.hiveColony().mobilizations().get(released.mobilizationId());
            boolean finalRelease = mobilization.releasedMemberIds().size() + 1 == mobilization.memberIds().size();
            List<ProposedEvent> events = new java.util.ArrayList<>(List.of(new ProposedEvent(state.bootstrap().hive().id(), released)));
            if (finalRelease) events.add(new ProposedEvent(state.bootstrap().hive().id(), new ScheduleEffect.Created(
                    HiveMobilizationProcess.assemblyProgress(mobilization.id(), Math.addExact(command.submittedAt().ticks(),
                            state.bootstrap().ruleset().cadence().migrationStepInterval())))));
            return new CommandPlan.Accepted(List.copyOf(events));
        }
        if (command.payload() instanceof HiveMobilizationAssemblyAdvanced advanced) {
            try { HiveMobilizationProcess.reduceAssemblyAdvanced(state, state.bootstrap().hive().id(), advanced); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            HiveMobilization mobilization = state.hiveColony().mobilizations().get(advanced.mobilizationId());
            HiveTaskAssembly next = mobilization.assembly().orElseThrow().advance(advanced.bioformId());
            List<ProposedEvent> events = new java.util.ArrayList<>(List.of(new ProposedEvent(state.bootstrap().hive().id(), advanced)));
            if (next.complete()) events.addAll(HiveSettlementAssaultProcess.planAssemblyDeparture(state, mobilization, next, command.submittedAt().ticks()));
            return new CommandPlan.Accepted(List.copyOf(events));
        }
        if (command.payload() instanceof HiveMobilizationReturnAdvanced advanced) {
            try { HiveMobilizationProcess.reduceReturnAdvanced(state, state.bootstrap().hive().id(), advanced); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), advanced)));
        }
        if (command.payload() instanceof HiveMobilizationConflicted conflicted) {
            try { HiveMobilizationProcess.reduceConflicted(state, state.bootstrap().hive().id(), conflicted); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), conflicted)));
        }
        if (command.payload() instanceof DeferredAftermathResolved resolved) {
            DeferredAftermath aftermath = state.deferredAftermath().entries().get(resolved.aftermathId());
            if (aftermath == null || !aftermath.ownerId().equals(state.bootstrap().hive().id())) {
                return FrontierWorldCommandPlanner.rejected("deferred aftermath has no exact hive owner");
            }
            try { state.deferredAftermath().resolve(resolved.aftermathId(), resolved.expectedEpoch(), resolved.observationAt(), resolved.expectedCursor(), resolved.authorityRevision(), resolved.result()); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(aftermath.ownerId(), resolved)));
        }
        if (command.payload() instanceof SettlementAssaultFormationObserved observed) {
            SettlementAssault assault = state.strategicPlans().settlementAssaults().get(observed.assaultId());
            if (assault == null) return FrontierWorldCommandPlanner.rejected("expedition formation observation has no assault");
            try { HiveSettlementAssaultProcess.reduceFormationObserved(state, assault.hiveId(), observed); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(assault.hiveId(), observed)));
        }
        if (command.payload() instanceof SettlementAssaultMarchIssueObserved observed) {
            SettlementAssault assault = state.strategicPlans().settlementAssaults().get(observed.assaultId());
            if (assault == null) return FrontierWorldCommandPlanner.rejected("expedition march issue has no assault");
            try { HiveSettlementAssaultProcess.reduceMarchIssueObserved(state, assault.hiveId(), observed); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(assault.hiveId(), observed)));
        }
        return FrontierWorldCommandPlanner.rejected("hive process does not admit command: " + command.payload().type());
    }

    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case InfectionChanged changed -> state.withInfection(changed.cell(), changed.intensity());
            case HiveGrowthStarted started -> HiveGrowthProcess.reduceStarted(state, event.subject(), started);
            case HiveGrowthBiomassConsumed consumed -> HiveGrowthProcess.reduceConsumed(state, event.subject(), consumed);
            case HiveGrowthCompleted completed -> HiveGrowthProcess.reduceCompleted(state, event.subject(), completed);
            case HiveGrowthBlocked blocked -> HiveGrowthProcess.reduceBlocked(state, event.subject(), blocked);
            case HiveNutrientTransferStarted started -> HiveNutrientTransferProcess.reduceStarted(state, event.subject(), started.transfer());
            case HiveNutrientTransferAdvanced advanced -> HiveNutrientTransferProcess.reduceAdvanced(state, event.subject(), advanced);
            case HiveNutrientTransferCompleted completed -> HiveNutrientTransferProcess.reduceCompleted(state, event.subject(), completed);
            case HiveNutrientTransferBlocked blocked -> HiveNutrientTransferProcess.reduceBlocked(state, event.subject(), blocked);
            case HiveNutrientTransferEndpointPrepared prepared -> HiveNutrientTransferProcess.reduceEndpointPrepared(state, event.subject(), prepared);
            case HiveMobilizationStarted started -> HiveMobilizationProcess.reduceStarted(state, event.subject(), started);
            case HiveMobilizationReleaseStarted started -> HiveMobilizationProcess.reduceReleaseStarted(state, event.subject(), started);
            case HiveMobilizationCocoonReleased released -> HiveMobilizationProcess.reduceCocoonReleased(state, event.subject(), released);
            case HiveMobilizationAssemblyAdvanced advanced -> HiveMobilizationProcess.reduceAssemblyAdvanced(state, event.subject(), advanced);
            case HiveMobilizationReturnAdvanced advanced -> HiveMobilizationProcess.reduceReturnAdvanced(state, event.subject(), advanced);
            case HiveMobilizationDeparted departed -> HiveMobilizationProcess.reduceDeparted(state, event.subject(), departed);
            case HiveMobilizationConflicted conflicted -> HiveMobilizationProcess.reduceConflicted(state, event.subject(), conflicted);
            case HiveOperationObserved observed -> HivePerceptionProcess.reduce(state, event.subject(), observed);
            case HiveTerritoryObserved observed -> HiveTerritoryPerceptionProcess.reduce(state, event.subject(), observed);
            case HiveSettlementObserved observed -> HiveSettlementPerceptionProcess.reduce(state, event.subject(), observed);
            case HiveDoctrineSelected selected -> HiveDoctrineProcess.reduce(state, event.subject(), selected);
            case HotScoutOperationObserved observed -> HivePerceptionProcess.reduceHot(state, event.subject(), observed);
            case ScoutPatrolAdvanced advanced -> HiveScoutPatrolProcess.reduce(state, event.subject(), advanced);
            case ScoutPatrolLeaseRecovered recovered -> HiveScoutPatrolProcess.reduceLeaseRecovered(state, event.subject(), recovered);
            case RouteEngagementStarted started -> HiveRouteEngagementProcess.reduceStarted(state, event.subject(), started);
            case RouteEngagementAttackerAdvanced advanced -> HiveRouteEngagementProcess.reduceAdvanced(state, event.subject(), advanced);
            case RouteEngagementTransition transition -> HiveRouteEngagementProcess.reduceTransition(state, event.subject(), transition);
            case RouteEngagementStrike strike -> HiveRouteEngagementProcess.reduceStrike(state, event.subject(), strike);
            case RouteEngagementResolved resolved -> HiveRouteEngagementProcess.reduceResolved(state, event.subject(), resolved);
            case RouteEngagementCommandAuthorityChanged changed -> HiveRouteEngagementProcess.reduceCommandAuthorityChanged(state, event.subject(), changed);
            case SettlementAssaultStarted started -> HiveSettlementAssaultProcess.reduceStarted(state, event.subject(), started);
            case SettlementAssaultAttackerAdvanced advanced -> HiveSettlementAssaultProcess.reduceAdvanced(state, event.subject(), advanced);
            case SettlementAssaultFormationObserved observed -> HiveSettlementAssaultProcess.reduceFormationObserved(state, event.subject(), observed);
            case SettlementAssaultMarchIssueObserved observed -> HiveSettlementAssaultProcess.reduceMarchIssueObserved(state, event.subject(), observed);
            case SettlementAssaultTransition transition -> HiveSettlementAssaultProcess.reduceTransition(state, event.subject(), transition);
            case SettlementAssaultStrike strike -> HiveSettlementAssaultProcess.reduceStrike(state, event.subject(), strike);
            case SettlementAssaultResolved resolved -> HiveSettlementAssaultProcess.reduceResolved(state, event.subject(), resolved);
            case DeferredAftermathPrepared prepared -> reduceAftermathPrepared(state, event.subject(), prepared);
            case DeferredAftermathResolved resolved -> reduceAftermathResolved(state, event.subject(), resolved);
            default -> throw new IllegalArgumentException("hive process does not own event: " + event.payload().type());
        };
    }

    private static FrontierWorldState reduceAftermathPrepared(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                               DeferredAftermathPrepared prepared) {
        if (!subject.equals(state.bootstrap().hive().id()) || !prepared.aftermath().ownerId().equals(subject)) {
            throw new IllegalArgumentException("deferred aftermath preparation lacks hive ownership");
        }
        DeferredAftermath aftermath = prepared.aftermath();
        String assaultId = aftermath.provenance().startsWith("captive-bomber-strike:")
                ? aftermath.provenance().substring("captive-bomber-strike:".length()) : "";
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(new io.farfrontier.palemirror.frontier.v3.api.SubjectId(assaultId));
        // The preparation event follows the exact strike in the same canonical transaction.
        // A same-actor or same-provenance substitute cannot cross this epoch/cause fence.
        if (assault == null || assault.nextStrikeEpoch() != aftermath.expectedEpoch() + 1L
                || aftermath.causeId().value().isBlank()
                || aftermath.cells().stream().anyMatch(cell -> cell.expectedMaterial() == null)) {
            throw new IllegalArgumentException("deferred aftermath preparation has stale or substituted causal authority");
        }
        boolean exactCause = assault.combatantAttackerIds().stream()
                .anyMatch(attacker -> aftermath.causeId().equals(SettlementAssaultCauseIdentity.strike(assault.id(), attacker, aftermath.expectedEpoch())));
        if (!exactCause) throw new IllegalArgumentException("deferred aftermath preparation has stale or substituted causal authority");
        FrontierWorldState preparedState = state.withChanges(FrontierWorldStateUpdate.begin()
                .deferredAftermath(state.deferredAftermath().prepare(aftermath)));
        // The COLD strike's semantic loss is committed with its exact cause.  Later natural
        // loading only reconciles the retained physical footprint; it must never decide whether
        // the causal consequence happened.
        return FrontierWorldPhysicalDeltaSupport.recordAll(preparedState, aftermath.cells().stream().map(cell ->
                new PhysicalDelta(cell.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                        java.util.Optional.of(cell.semanticTarget()), java.util.Optional.of(cell.expectedPart()), aftermath.causeId().value())).toList());
    }
    private static FrontierWorldState reduceAftermathResolved(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                               DeferredAftermathResolved resolved) {
        DeferredAftermath current = state.deferredAftermath().entries().get(resolved.aftermathId());
        if (current == null || !subject.equals(current.ownerId())) throw new IllegalArgumentException("deferred aftermath resolution lacks exact owner");
        DeferredAftermathState next = state.deferredAftermath().resolve(resolved.aftermathId(), resolved.expectedEpoch(), resolved.observationAt(), resolved.expectedCursor(), resolved.authorityRevision(), resolved.result());
        DeferredAftermathCell cell = current.cellAt(resolved.expectedCursor());
        if (resolved.result() == DeferredAftermathCellStatus.REALIZED && (cell == null || cell.status() != DeferredAftermathCellStatus.RUNNING)) {
            throw new IllegalArgumentException("realized aftermath has no running exact cell");
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().deferredAftermath(next));
    }
}
