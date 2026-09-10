package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.ArrayList;
import java.util.List;

/** Bounded deterministic COLD progression of one exact nutrient across hive store organs. */
public final class HiveNutrientTransferProcess {
    private HiveNutrientTransferProcess() { }

    public static ScheduledAction advance(HiveNutrientTransfer transfer, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:hive-nutrient-transfer-" + suffix(transfer.id())), new SimInstant(dueAt), 0,
                transfer.id(), "frontier.hive.nutrient.transfer.progress", 1);
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        HiveNutrientTransfer transfer = HiveNutrientTransferStateSupport.requireTransit(state, action.subject());
        if (ReferenceContainerCustody.blocksCanonicalUse(state, transfer.sourceStoreId())
                || ReferenceContainerCustody.blocksCanonicalUse(state, transfer.targetStoreId())) {
            return List.of(new ProposedEvent(transfer.hiveId(), new HiveNutrientTransferBlocked(transfer.id(), HiveNutrientTransferBlockReason.CARGO_CUSTODY_LOST)));
        }
        try {
            HiveNutrientTransferStateSupport.validateTransit(state, transfer);
        } catch (IllegalArgumentException blocked) {
            return List.of(new ProposedEvent(transfer.hiveId(), new HiveNutrientTransferBlocked(transfer.id(), blockReason(state, transfer))));
        }
        int nextCursor = transfer.cursor() + 1;
        if (nextCursor < transfer.corridor().size() - 1) {
            return List.of(new ProposedEvent(transfer.hiveId(), new HiveNutrientTransferAdvanced(transfer.id(), nextCursor)),
                    schedule(advance(transfer, action.dueAt().ticks() + state.bootstrap().ruleset().cadence().hiveNutrientTransferStepInterval())));
        }
        if (ReferenceContainerCustody.hasConflict(state, transfer.targetStoreId())) {
            return List.of(new ProposedEvent(transfer.hiveId(), new HiveNutrientTransferBlocked(transfer.id(), HiveNutrientTransferBlockReason.CARGO_CUSTODY_LOST)));
        }
        if (ReferenceContainerCustody.hasLiveCustody(state, transfer.targetStoreId())) {
            HiveNutrientTransfer waiting = transfer.advanceTo(nextCursor).awaitArrival(arrivalIntentId(transfer));
            return List.of(new ProposedEvent(transfer.hiveId(), new HiveNutrientTransferAdvanced(transfer.id(), nextCursor)),
                    new ProposedEvent(transfer.hiveId(), new HiveNutrientTransferEndpointPrepared(waiting)),
                    new ProposedEvent(transfer.hiveId(), new PhysicalIntentPrepared(HiveNutrientTransferStateSupport.arrivalIntent(state, waiting))));
        }
        HiveNutrientReceipt receipt = new HiveNutrientReceipt(transfer.id(), transfer.hiveId(), transfer.cargoId(), transfer.itemId(), transfer.sourceSlot(), transfer.targetSlot());
        return List.of(new ProposedEvent(transfer.hiveId(), new HiveNutrientTransferAdvanced(transfer.id(), nextCursor)),
                new ProposedEvent(transfer.hiveId(), new HiveNutrientTransferCompleted(receipt)),
                schedule(HiveGrowthProcess.start(task(state, transfer), action.dueAt().ticks() + 1L)));
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, HiveNutrientTransfer transfer) {
        if (!subject.equals(transfer.hiveId())) throw new IllegalArgumentException("hive nutrient transfer departure lacks hive ownership");
        if (ReferenceContainerCustody.blocksCanonicalUse(state, transfer.sourceStoreId())
                || ReferenceContainerCustody.blocksCanonicalUse(state, transfer.targetStoreId())) throw new IllegalArgumentException("hive nutrient transfer cannot start across conflicted store evidence");
        return HiveNutrientTransferStateSupport.start(state, transfer);
    }

    public static FrontierWorldState reduceAdvanced(FrontierWorldState state, SubjectId subject, HiveNutrientTransferAdvanced advanced) {
        HiveNutrientTransfer transfer = HiveNutrientTransferStateSupport.requireTransit(state, advanced.transferId());
        if (!subject.equals(transfer.hiveId())) throw new IllegalArgumentException("hive nutrient cursor lacks hive ownership");
        return HiveNutrientTransferStateSupport.advance(state, advanced.transferId(), advanced.cursor());
    }

    public static FrontierWorldState reduceCompleted(FrontierWorldState state, SubjectId subject, HiveNutrientTransferCompleted completed) {
        HiveNutrientTransfer transfer = HiveNutrientTransferStateSupport.requireTransit(state, completed.receipt().transferId());
        if (!subject.equals(transfer.hiveId())) throw new IllegalArgumentException("hive nutrient receipt lacks hive ownership");
        if (ReferenceContainerCustody.blocksCanonicalUse(state, transfer.targetStoreId())) throw new IllegalArgumentException("hive nutrient receipt cannot use conflicted target store evidence");
        return HiveNutrientTransferStateSupport.complete(state, completed.receipt());
    }

    public static FrontierWorldState reduceBlocked(FrontierWorldState state, SubjectId subject, HiveNutrientTransferBlocked blocked) {
        HiveNutrientTransfer transfer = HiveNutrientTransferStateSupport.requireUnblocked(state, blocked.transferId());
        if (!subject.equals(transfer.hiveId())) throw new IllegalArgumentException("hive nutrient block lacks hive ownership");
        return HiveNutrientTransferStateSupport.block(state, blocked.transferId(), blocked.reason());
    }

    public static HiveNutrientTransfer create(FrontierWorldState state, StrategicTask task, ExactItemStack item,
                                       SubjectId targetStore, int targetSlot) {
        if (!(item.custody() instanceof InventoryCustody.ContainerSlot sourceSlot)) throw new IllegalArgumentException("hive nutrient source must be one store slot");
        if (ReferenceContainerCustody.blocksCanonicalUse(state, sourceSlot.containerId()) || ReferenceContainerCustody.blocksCanonicalUse(state, targetStore)) {
            throw new IllegalArgumentException("hive nutrient transfer cannot use conflicted store evidence");
        }
        List<BlockPosition> corridor = corridor(state.inventory().surfaces().get(sourceSlot.containerId()).position(), state.inventory().surfaces().get(targetStore).position());
        String idSuffix = task.id().value().replace(':', '-');
        SubjectId transferId = new SubjectId("transfer:hive-nutrient-" + idSuffix);
        boolean physicalDeparture = ReferenceContainerCustody.hasLiveCustody(state, sourceSlot.containerId());
        return new HiveNutrientTransfer(transferId, state.bootstrap().hive().id(), task.id(),
                sourceSlot.containerId(), sourceSlot, targetStore, new InventoryCustody.ContainerSlot(targetStore, targetSlot),
                new SubjectId("cargo:hive-nutrient-" + idSuffix), item.id(), corridor, 0,
                physicalDeparture ? HiveNutrientTransferPhase.DEPARTURE_PENDING : HiveNutrientTransferPhase.IN_TRANSIT,
                physicalDeparture ? java.util.Optional.of(departureIntentId(transferId)) : java.util.Optional.empty(), java.util.Optional.empty());
    }

    public static List<ProposedEvent> startEvents(FrontierWorldState state, HiveNutrientTransfer transfer, long dueAt) {
        List<ProposedEvent> events = new ArrayList<>(); events.add(new ProposedEvent(transfer.hiveId(), new HiveNutrientTransferStarted(transfer)));
        if (transfer.phase() == HiveNutrientTransferPhase.DEPARTURE_PENDING) {
            events.add(new ProposedEvent(transfer.hiveId(), new PhysicalIntentPrepared(HiveNutrientTransferStateSupport.departureIntent(state, transfer))));
        } else events.add(schedule(advance(transfer, dueAt + state.bootstrap().ruleset().cadence().hiveNutrientTransferStepInterval())));
        return List.copyOf(events);
    }

    public static List<ProposedEvent> planTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition, long now) {
        HiveNutrientTransfer transfer = state.hiveColony().nutrientTransfers().values().stream()
                .filter(value -> value.endpointIntentId().equals(java.util.Optional.of(intent.id()))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("hive endpoint intent has no retained transfer"));
        if (!intent.causeSubjectId().equals(transfer.hiveId()) || !intent.subjectIds().equals(List.of(transfer.id(), transfer.cargoId(), transfer.itemId())))
            throw new IllegalArgumentException("hive endpoint intent has foreign exact subjects");
        if (transition.status() == PhysicalIntentStatus.CONFIRMED) {
            List<ProposedEvent> events = new ArrayList<>(); events.add(new ProposedEvent(transfer.hiveId(), transition));
            if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE) events.add(schedule(advance(transfer,
                    now + state.bootstrap().ruleset().cadence().hiveNutrientTransferStepInterval())));
            else events.add(schedule(HiveGrowthProcess.start(task(state, transfer), now + 1L)));
            return List.copyOf(events);
        }
        return List.of(new ProposedEvent(transfer.hiveId(), transition));
    }

    public static FrontierWorldState reduceEndpointPrepared(FrontierWorldState state, SubjectId subject, HiveNutrientTransferEndpointPrepared prepared) {
        HiveNutrientTransfer transfer = HiveNutrientTransferStateSupport.requireTransit(state, prepared.transfer().id());
        if (!subject.equals(transfer.hiveId()) || !transfer.id().equals(prepared.transfer().id()) || prepared.transfer().phase() != HiveNutrientTransferPhase.ARRIVAL_PENDING)
            throw new IllegalArgumentException("hive nutrient arrival preparation has foreign ownership");
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .hiveColony(state.hiveColony().advanceNutrientTransferState(transfer.id(), prepared.transfer())));
    }

    private static StrategicTask task(FrontierWorldState state, HiveNutrientTransfer transfer) {
        StrategicTask task = state.strategicPlans().tasks().get(transfer.requesterTaskId());
        if (task == null || !task.ownerId().equals(transfer.hiveId()) || task.kind() != StrategicTaskKind.GROW_HIVE_ORGANISM
                || task.status() != StrategicTaskStatus.PENDING) {
            throw new IllegalArgumentException("hive nutrient receipt has no pending growth requester");
        }
        return task;
    }

    private static HiveNutrientTransferBlockReason blockReason(FrontierWorldState state, HiveNutrientTransfer transfer) {
        if (ReferenceContainerCustody.hasConflict(state, transfer.targetStoreId())) return HiveNutrientTransferBlockReason.CARGO_CUSTODY_LOST;
        if (state.inventory().itemAt(transfer.targetStoreId(), transfer.targetSlot().slot()).isPresent()) return HiveNutrientTransferBlockReason.TARGET_SLOT_UNAVAILABLE;
        return HiveNutrientTransferBlockReason.CARGO_CUSTODY_LOST;
    }

    private static List<BlockPosition> corridor(BlockPosition source, BlockPosition target) {
        ArrayList<BlockPosition> nodes = new ArrayList<>(); nodes.add(source);
        int x = source.x(), z = source.z();
        while (x != target.x()) { x += Integer.signum(target.x() - x) * Math.min(8, Math.abs(target.x() - x)); nodes.add(new BlockPosition(x, source.y(), z)); }
        while (z != target.z()) { z += Integer.signum(target.z() - z) * Math.min(8, Math.abs(target.z() - z)); nodes.add(new BlockPosition(x, source.y(), z)); }
        if (nodes.size() > HiveNutrientTransfer.MAX_CORRIDOR_NODES || !nodes.getLast().equals(target)) throw new IllegalArgumentException("hive organ corridor exceeds its bound");
        return List.copyOf(nodes);
    }

    private static String suffix(SubjectId id) { return id.value().substring("transfer:hive-nutrient-".length()); }
    private static PhysicalIntentId departureIntentId(SubjectId transferId) { return new PhysicalIntentId("intent:hive-nutrient-departure-" + suffix(transferId)); }
    private static PhysicalIntentId arrivalIntentId(HiveNutrientTransfer transfer) { return new PhysicalIntentId("intent:hive-nutrient-arrival-" + suffix(transfer.id())); }
    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }
}
