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
    private SettlementServiceInputIssueStateSupport() { }

    public static boolean owns(PhysicalIntent intent) {
        return intent.kind() == PhysicalIntentKind.SETTLEMENT_SERVICE_INPUT_ISSUE;
    }

    public static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (!owns(intent)) return;
        if (intent.subjectIds().size() != 3) throw new IllegalArgumentException("service input issue requires work, worker and exact item");
        SubjectId workId = intent.subjectIds().getFirst();
        SettlementServiceWork work = state.serviceWorks().get(workId);
        if (work == null || !intent.id().equals(work.inputIssueIntentId()) || !intent.causeSubjectId().equals(workId)
                || !intent.subjectIds().equals(java.util.List.of(workId, work.workerId(), work.inputItemId()))
                || work.phase() != SettlementServiceWorkPhase.INPUT_ISSUE_PENDING) {
            throw new IllegalArgumentException("service input issue must bind the exact retained work at its source station");
        }
        ActorLocation worker = state.actorLocations().get(work.workerId());
        ExactItemStack item = state.inventory().items().get(work.inputItemId());
        ContainerSurface surface = state.inventory().surfaces().get(work.inputSource().containerId());
        if (worker == null || worker.condition().status() != ActorLifeStatus.ALIVE || !worker.supportingSurface().equals(work.inputStation())
                || item == null || !item.custody().equals(work.inputSource()) || surface == null
                || surface.status() != ContainerSurfaceStatus.ACTIVE) {
            throw new IllegalArgumentException("service input issue has lost its worker, source station or exact source stack");
        }
    }

    static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent,
                                       SettlementServiceInputIssueObservation receipt,
                                       Map<PhysicalIntentId, PhysicalIntent> nextIntents) {
        validateIntent(state, intent);
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
        works.put(work.id(), work.withInputIssued());
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().moveObservedItem(work.inputItemId(), work.inputSource(), new InventoryCustody.Actor(work.workerId())))
                .serviceWorks(works).physicalIntents(nextIntents).physicalObservations(observations));
    }

    static SettlementServiceInputIssueObservation requireReceipt(PhysicalEffectObservation evidence) {
        if (evidence instanceof SettlementServiceInputIssueObservation issue) return issue;
        throw new IllegalArgumentException("service input issue requires exact hand-off evidence");
    }

    static void validateReceiptForRecovery(PhysicalIntent intent, SettlementServiceInputIssueObservation receipt) {
        if (!owns(intent) || !intent.id().equals(receipt.intentId())
                || !intent.subjectIds().equals(java.util.List.of(receipt.workId(), receipt.workerId(), receipt.itemId()))) {
            throw new IllegalArgumentException("service input-issue receipt has foreign exact subjects");
        }
    }
}
