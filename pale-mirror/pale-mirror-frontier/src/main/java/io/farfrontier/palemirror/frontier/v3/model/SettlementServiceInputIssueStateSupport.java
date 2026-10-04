package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;

/** Exact custody owner for a depot-to-retained-service-worker hand-off. */
public final class SettlementServiceInputIssueStateSupport {
    /** Domain-owned distinction between an invalid request and one waiting for its retained route. */
    public enum ExecutionEligibility { INVALID, DEFERRED, READY }

    private SettlementServiceInputIssueStateSupport() { }

    public static boolean owns(PhysicalIntent intent) {
        return intent.kind() == PhysicalIntentKind.SETTLEMENT_SERVICE_INPUT_ISSUE;
    }

    public static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        validateRetainedInput(state, intent);
        SettlementServiceWork work = state.serviceWorks().get(intent.causeSubjectId());
        if (work.phase() != SettlementServiceWorkPhase.INPUT_ISSUE_PENDING)
            throw new IllegalArgumentException("retired or recovering service input cannot authorize a new take");
        SettlementServiceExecutionAuthority.current(state, work);
        ActorLocation worker = state.actorLocations().get(work.workerId());
        if (worker == null || worker.condition().status() != ActorLifeStatus.ALIVE
                || !worker.supportingSurface().equals(work.inputStation()))
            throw new IllegalArgumentException("service input issue worker has not reached its source station");
    }

    /** An inspected source-present/empty-hand retry still needs the same active worker/station. */
    public static void validateUnappliedRetry(FrontierWorldState state, PhysicalIntent intent) {
        validateRetainedInput(state, intent);
        var work = state.serviceWorks().get(intent.causeSubjectId());
        if (!work.phase().active() || (intent.status() != PhysicalIntentStatus.RUNNING
                && intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)
                || work.inputTraversalCursor() != work.inputTraversal().linearCorridorSurfaces().size() - 1)
            throw new IllegalArgumentException("input retry needs its exact active reached source boundary");
        SettlementServiceExecutionAuthority.current(state, work);
        var worker = state.actorLocations().get(work.workerId());
        if (worker == null || worker.condition().status() != ActorLifeStatus.ALIVE
                || !worker.supportingSurface().equals(work.inputStation()))
            throw new IllegalArgumentException("input retry needs its living worker at the exact source station");
    }

    /** Exact effect settlement survives body departure; it cannot issue a new physical take. */
    public static void validateRetainedInput(FrontierWorldState state, PhysicalIntent intent) {
        if (!owns(intent)) throw new IllegalArgumentException("foreign service input effect");
        SubjectId workId = intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.SETTLEMENT_SERVICE_WORK);
        SettlementServiceWork work = state.serviceWorks().get(workId);
        if (work == null || !intent.id().equals(work.inputIssueIntentId()) || !intent.causeSubjectId().equals(workId)
                || !intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.serviceInputIssue(workId, work.workerId(), work.inputItemId()))
                || work.phase() != SettlementServiceWorkPhase.INPUT_ISSUE_PENDING
                && !((work.phase() == SettlementServiceWorkPhase.BLOCKED || work.phase() == SettlementServiceWorkPhase.UNKNOWN_AFTER_RESTART)
                    && (intent.status() == PhysicalIntentStatus.RUNNING || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART))) {
            throw new IllegalArgumentException("service input issue must bind its exact pending or possibly applied retained work");
        }
        ExactItemStack item = state.inventory().items().get(work.inputItemId());
        ContainerSurface surface = state.inventory().surfaces().get(work.inputSource().containerId());
        if (item == null || !item.custody().equals(work.inputSource()) || surface == null
                || surface.status() != ContainerSurfaceStatus.ACTIVE) {
            throw new IllegalArgumentException("service input issue has lost its worker, source station or exact source stack");
        }
    }

    /**
     * Establishes whether the retained physical hand-off may be considered by a Minecraft
     * executor.  A service work atomically reserves its input intent before the worker has
     * traversed to the source station; that normal earlier phase is a deferral, never a
     * canonical conflict.
     */
    public static ExecutionEligibility executionEligibility(FrontierWorldState state, PhysicalIntent intent) {
        if (!owns(intent)) return ExecutionEligibility.INVALID;
        SubjectId workId = intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.SETTLEMENT_SERVICE_WORK);
        SettlementServiceWork work = state.serviceWorks().get(workId);
        if (work == null || !intent.id().equals(work.inputIssueIntentId()) || !intent.causeSubjectId().equals(workId)
                || !intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.serviceInputIssue(workId, work.workerId(), work.inputItemId()))) {
            return ExecutionEligibility.INVALID;
        }
        if (intent.status() == PhysicalIntentStatus.RUNNING || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            try {
                validateRetainedInput(state, intent);
                return ExecutionEligibility.READY; // Inspect retained effects even after leaving the station.
            } catch (IllegalArgumentException invalid) { return ExecutionEligibility.INVALID; }
        }
        if (work.phase() != SettlementServiceWorkPhase.INPUT_ISSUE_PENDING) return ExecutionEligibility.DEFERRED;
        // Departure retains the input reservation but may require an approach back to the source.
        // That is spatial deferral, not loss of resource identity or permission to issue remotely.
        ActorLocation worker = state.actorLocations().get(work.workerId());
        if (work.spatial().pending() && worker != null && worker.condition().status() == ActorLifeStatus.ALIVE
                && !worker.supportingSurface().equals(work.inputStation())) return ExecutionEligibility.DEFERRED;
        try {
            validateIntent(state, intent);
            return ExecutionEligibility.READY;
        } catch (IllegalArgumentException invalid) {
            return ExecutionEligibility.INVALID;
        }
    }

    public static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent,
                                       SettlementServiceInputIssueObservation receipt,
                                       Map<PhysicalIntentId, PhysicalIntent> nextIntents) {
        validateRetainedInput(state, intent);
        SettlementServiceWork work = state.serviceWorks().get(receipt.workId());
        ExactItemStack item = state.inventory().items().get(receipt.itemId());
        if (work == null || !receipt.intentId().equals(intent.id()) || !receipt.workerId().equals(work.workerId())
                || !receipt.itemId().equals(work.inputItemId()) || item == null || !receipt.sourceSlot().equals(work.inputSource())
                || !item.custody().equals(work.inputSource())) {
            throw new IllegalArgumentException("service input-issue receipt does not match exact canonical custody");
        }
        nextIntents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt.id())));
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.put(receipt.id(), receipt);
        Map<SubjectId, SettlementServiceWork> works = new LinkedHashMap<>(state.serviceWorks());
        // Confirmed custody is independent of a retired actor execution. Never revive BLOCKED.
        SettlementServiceWork issued = work.phase() == SettlementServiceWorkPhase.BLOCKED ? work : work.withInputIssued();
        ActorLocation worker = state.actorLocations().get(work.workerId());
        if (issued.phase().active() && worker != null && worker.condition().status() == ActorLifeStatus.ALIVE
                && !worker.supportingSurface().equals(FrontierSettlementServiceWorkSceneSupport.semanticSurface(issued)))
            issued = issued.withSpatial(SettlementServiceJourneyKnowledge.checkpoint(state, issued, worker.supportingSurface()));
        works.put(work.id(), issued);
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().moveObservedItem(work.inputItemId(), work.inputSource(), new InventoryCustody.Actor(work.workerId())))
                .serviceWorks(works).physicalIntents(nextIntents).physicalObservations(observations));
    }

    /** Input uncertainty changes its own owner phase atomically, not just the intent bytes. */
    public static FrontierWorldState unknown(FrontierWorldState state, PhysicalIntent intent, Map<PhysicalIntentId, PhysicalIntent> intents) {
        validateRetainedInput(state, intent);
        var work = state.serviceWorks().get(intent.causeSubjectId());
        var works = new LinkedHashMap<>(state.serviceWorks());
        if (work.phase().active()) works.put(work.id(), work.withPhase(SettlementServiceWorkPhase.UNKNOWN_AFTER_RESTART, work.completedWorkTicks()));
        return state.withChanges(FrontierWorldStateUpdate.begin().serviceWorks(works).physicalIntents(intents));
    }

    public static SettlementServiceInputIssueObservation requireReceipt(PhysicalEffectObservation evidence) {
        if (evidence instanceof SettlementServiceInputIssueObservation issue) return issue;
        throw new IllegalArgumentException("service input issue requires exact hand-off evidence");
    }

    static void validateReceiptForRecovery(PhysicalIntent intent, SettlementServiceInputIssueObservation receipt) {
        if (!owns(intent) || !intent.id().equals(receipt.intentId())
                || !intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.serviceInputIssue(receipt.workId(), receipt.workerId(), receipt.itemId()))) {
            throw new IllegalArgumentException("service input-issue receipt has foreign exact subjects");
        }
    }
}
