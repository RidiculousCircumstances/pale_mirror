package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;

import java.util.LinkedHashMap;
import java.util.Map;

/** Service-work-owned endpoint rule: one medic-held exact reagent changes one observed infection cell. */
public final class SettlementServiceDecontaminationStateSupport {
    /** Domain-owned distinction between retained future work and a malformed effect request. */
    public enum ExecutionEligibility { INVALID, DEFERRED, READY }

    private SettlementServiceDecontaminationStateSupport() { }

    public static boolean owns(FrontierWorldState state, PhysicalIntent intent) {
        return intent.lifecycleOwner() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_SERVICE_DECONTAMINATION;
    }

    /** Snapshot/WAL receipt validation after the effect has already completed its retained work. */
    static void validateIntentForRecoveredReceipt(PhysicalIntent intent, DecontaminationObservation observation,
                                                  Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, SettlementServiceWork> works) {
        SettlementServiceWork work = works.get(intent.causeSubjectId());
        if (work == null || work.kind() != SettlementServiceWorkKind.DECONTAMINATION || !intent.id().equals(work.endpointIntentId())
                || !intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.serviceDecontamination(work.id(), work.workerId(), work.inputItemId()))
                || !observation.intentId().equals(intent.id()) || !observation.itemId().equals(work.inputItemId())
                || !(work.target() instanceof SettlementServiceTarget.Infection target) || !observation.cell().equals(target.cell())) {
            throw new IllegalArgumentException("service decontamination receipt has foreign retained work subjects");
        }
    }

    public static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.status() == PhysicalIntentStatus.RUNNING || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            validateRetainedEffect(state, intent);
            return;
        }
        validateRetainedSubjects(state, intent);
        SettlementServiceWork work = state.serviceWorks().get(intent.causeSubjectId());
        if (intent.status() != PhysicalIntentStatus.PREPARED || work.phase() != SettlementServiceWorkPhase.EFFECT_READY)
            throw new IllegalArgumentException("new decontamination needs its prepared effect-ready owner");
        var execution = SettlementServiceExecutionAuthority.current(state, work);
        ActorBodyAuthority.requireActuation(state, new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(
                ActorBodyAuthority.current(state, work.workerId()), execution));
        SceneLease lease = FrontierSettlementServiceWorkSceneSupport.requireHotLease(state, work, findHotLease(state, work));
        if (!lease.memberBody(state.actorLocations(), work.workerId()).equals(work.workStation().standingBody()))
            throw new IllegalArgumentException("service decontamination worker is not at its retained work station");
    }

    /** A started effect can settle from exact resource/target evidence without a scene or live actuator. */
    public static void validateRetainedEffect(FrontierWorldState state, PhysicalIntent intent) {
        validateRetainedSubjects(state, intent);
        var work = state.serviceWorks().get(intent.causeSubjectId());
        if ((intent.status() != PhysicalIntentStatus.RUNNING && intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)
                || (work.phase() != SettlementServiceWorkPhase.EFFECT_READY
                    && work.phase() != SettlementServiceWorkPhase.UNKNOWN_AFTER_RESTART
                    && work.phase() != SettlementServiceWorkPhase.BLOCKED))
            throw new IllegalArgumentException("retained decontamination has no possibly applied exact effect");
    }

    public static void validateFatalityAbandonment(FrontierWorldState state, PhysicalIntent intent) {
        var work = state.serviceWorks().get(intent.causeSubjectId());
        if (!owns(state, intent) || work == null || work.kind() != SettlementServiceWorkKind.DECONTAMINATION
                || !intent.id().equals(work.endpointIntentId())
                || !intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.serviceDecontamination(
                        work.id(), work.workerId(), work.inputItemId()))
                || (intent.status() != PhysicalIntentStatus.RUNNING && intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)
                || work.phase() != SettlementServiceWorkPhase.BLOCKED
                || state.actorLocations().get(work.workerId()).condition().status() != ActorLifeStatus.DEAD)
            throw new IllegalArgumentException("service effect abandonment needs its exact retired dead owner");
    }

    private static void validateRetainedSubjects(FrontierWorldState state, PhysicalIntent intent) {
        var work = state.serviceWorks().get(intent.causeSubjectId());
        if (!owns(state, intent) || work == null || work.kind() != SettlementServiceWorkKind.DECONTAMINATION
                || !intent.id().equals(work.endpointIntentId()) || !(work.target() instanceof SettlementServiceTarget.Infection target)
                || !intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.serviceDecontamination(
                        work.id(), work.workerId(), work.inputItemId())))
            throw new IllegalArgumentException("service decontamination has foreign exact subjects");
        ExactItemStack item = state.inventory().items().get(work.inputItemId());
        if (item == null || !item.itemKind().equals(DecontaminationPolicy.REAGENT)
                || !item.custody().equals(new InventoryCustody.Actor(work.workerId())) || !state.infection().containsKey(target.cell())) {
            throw new IllegalArgumentException("service decontamination lost its exact held reagent or infection target");
        }
    }

    /**
     * The endpoint intent is reserved with the exact service work, before that work reaches
     * its physical effect station.  Only the retained effect-ready/recovery phase permits a
     * materializer to inspect or execute it; earlier phases are ordinary deferral.
     */
    public static ExecutionEligibility executionEligibility(FrontierWorldState state, PhysicalIntent intent) {
        if (!owns(state, intent)) return ExecutionEligibility.INVALID;
        SettlementServiceWork work = state.serviceWorks().get(intent.causeSubjectId());
        if (work == null || work.kind() != SettlementServiceWorkKind.DECONTAMINATION || !intent.id().equals(work.endpointIntentId())
                || !intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.serviceDecontamination(work.id(), work.workerId(), work.inputItemId()))) {
            return ExecutionEligibility.INVALID;
        }
        boolean ready = work.phase() == SettlementServiceWorkPhase.EFFECT_READY && intent.status() == PhysicalIntentStatus.PREPARED;
        boolean recovering = (intent.status() == PhysicalIntentStatus.RUNNING || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)
                && (work.phase() == SettlementServiceWorkPhase.EFFECT_READY || work.phase() == SettlementServiceWorkPhase.UNKNOWN_AFTER_RESTART
                    || work.phase() == SettlementServiceWorkPhase.BLOCKED);
        if (!ready && !recovering) return ExecutionEligibility.DEFERRED;
        try {
            validateIntent(state, intent);
            return ExecutionEligibility.READY;
        } catch (IllegalArgumentException invalid) {
            return ExecutionEligibility.INVALID;
        }
    }

    /** Closed DECONTAMINATION receipt dispatch; service work never leaks a branch into the world aggregate. */
    public static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, PhysicalEffectObservation evidence,
                                              Map<PhysicalIntentId, PhysicalIntent> intents) {
        if (!(evidence instanceof DecontaminationObservation observation)) throw new IllegalArgumentException("decontamination requires observation evidence");
        if (!owns(state, intent)) return DecontaminationStateSupport.complete(state, intent, observation, intents);
        validateIntent(state, intent);
        SettlementServiceWork work = state.serviceWorks().get(intent.causeSubjectId());
        InfectionCell cell = ((SettlementServiceTarget.Infection) work.target()).cell(); FixedRatio current = state.infection().get(cell);
        if (!observation.itemId().equals(work.inputItemId()) || !observation.cell().equals(cell) || observation.priorRaw() != current.value().raw()) {
            throw new IllegalArgumentException("service decontamination receipt does not match retained item and target");
        }
        long remaining = Math.max(0L, Math.subtractExact(observation.priorRaw(), state.bootstrap().ruleset().rates().decontaminationReduction().raw()));
        if (observation.remainingRaw() != remaining) throw new IllegalArgumentException("service decontamination receipt has invalid reduction");
        intents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(observation.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations()); observations.put(observation.id(), observation);
        Map<InfectionCell, FixedRatio> infection = new LinkedHashMap<>(state.infection());
        if (remaining == 0L) infection.remove(cell); else infection.put(cell, new FixedRatio(new FixedScalar(remaining)));
        Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, SettlementServiceWork> works = new LinkedHashMap<>(state.serviceWorks());
        // A positive late effect settles resources/target, never revives an abandoned activity.
        boolean retired = work.phase() == SettlementServiceWorkPhase.BLOCKED;
        works.put(work.id(), retired ? work : work.withPhase(SettlementServiceWorkPhase.COMPLETED, 0));
        Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases());
        leases.replaceAll((id, lease) -> FrontierSceneBehaviors.isServiceWork(lease)
                && FrontierSceneBehaviors.serviceWork(lease).workId().equals(work.id())
                && (lease.status() == SceneLeaseStatus.HOT || lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART)
                ? lease.withStatus(SceneLeaseStatus.DRAINING) : lease);
        return state.withChanges(FrontierWorldStateUpdate.begin().infection(infection).inventory(state.inventory().consume(work.inputItemId(), 1))
                .serviceWorks(works).physicalIntents(intents).physicalObservations(observations).sceneLeases(leases)
                .actorExecutions(retired ? state.actorExecutions() : SettlementServiceExecutionAuthority.retired(state, work)));
    }

    public static FrontierWorldState unknown(FrontierWorldState state, PhysicalIntent intent, Map<PhysicalIntentId, PhysicalIntent> intents) {
        SettlementServiceWork work = state.serviceWorks().get(intent.causeSubjectId());
        if (work == null || !intent.id().equals(work.endpointIntentId()) || work.kind() != SettlementServiceWorkKind.DECONTAMINATION) {
            throw new IllegalArgumentException("service decontamination unknown transition has no exact retained work");
        }
        intents.put(intent.id(), intent.withRecoveryUnknown(PhysicalIntentRecoveryDiagnosticProducer.SETTLEMENT_SERVICE_DECONTAMINATION.stamp(intent)));
        Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, SettlementServiceWork> works = new LinkedHashMap<>(state.serviceWorks());
        if (work.phase().active()) works.put(work.id(), work.withPhase(SettlementServiceWorkPhase.UNKNOWN_AFTER_RESTART, 0));
        Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases());
        leases.replaceAll((id, lease) -> FrontierSceneBehaviors.isServiceWork(lease)
                && FrontierSceneBehaviors.serviceWork(lease).workId().equals(work.id()) && lease.status() == SceneLeaseStatus.HOT
                ? lease.withStatus(SceneLeaseStatus.UNKNOWN_AFTER_RESTART) : lease);
        return state.withChanges(FrontierWorldStateUpdate.begin().serviceWorks(works).physicalIntents(intents).sceneLeases(leases));
    }

    private static io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId findHotLease(FrontierWorldState state, SettlementServiceWork work) {
        return state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.HOT)
                .filter(FrontierSceneBehaviors::isServiceWork).filter(lease -> FrontierSceneBehaviors.serviceWork(lease).workId().equals(work.id()))
                .map(SceneLease::id).sorted().findFirst().orElseThrow(() -> new IllegalArgumentException("service decontamination has no HOT work lease"));
    }
}
