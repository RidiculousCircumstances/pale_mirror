package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.ArrayList;
import java.util.List;

/** Bounded deterministic COLD progression of one exact nutrient across hive store organs. */
final class HiveNutrientTransferProcess {
    private static final long STEP_TICKS = 20L;
    private HiveNutrientTransferProcess() { }

    static ScheduledAction advance(HiveNutrientTransfer transfer, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:hive-nutrient-transfer-" + suffix(transfer.id())), new SimInstant(dueAt), 0,
                transfer.id(), "frontier.hive.nutrient.transfer.progress", 1);
    }

    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        HiveNutrientTransfer transfer = HiveNutrientTransferStateSupport.requireActive(state, action.subject());
        try {
            HiveNutrientTransferStateSupport.validateColdEndpoints(state, transfer);
        } catch (IllegalArgumentException blocked) {
            return List.of(new ProposedEvent(transfer.hiveId(), new HiveNutrientTransferBlocked(transfer.id(), blockReason(state, transfer))));
        }
        int nextCursor = transfer.cursor() + 1;
        if (nextCursor < transfer.corridor().size() - 1) {
            return List.of(new ProposedEvent(transfer.hiveId(), new HiveNutrientTransferAdvanced(transfer.id(), nextCursor)),
                    schedule(advance(transfer, action.dueAt().ticks() + STEP_TICKS)));
        }
        HiveNutrientReceipt receipt = new HiveNutrientReceipt(transfer.id(), transfer.hiveId(), transfer.cargoId(), transfer.itemId(), transfer.sourceSlot(), transfer.targetSlot());
        return List.of(new ProposedEvent(transfer.hiveId(), new HiveNutrientTransferAdvanced(transfer.id(), nextCursor)),
                new ProposedEvent(transfer.hiveId(), new HiveNutrientTransferCompleted(receipt)),
                schedule(HiveGrowthProcess.start(task(state, transfer), action.dueAt().ticks() + 1L)));
    }

    static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, HiveNutrientTransfer transfer) {
        if (!subject.equals(transfer.hiveId())) throw new IllegalArgumentException("hive nutrient transfer departure lacks hive ownership");
        return HiveNutrientTransferStateSupport.start(state, transfer);
    }

    static FrontierWorldState reduceAdvanced(FrontierWorldState state, SubjectId subject, HiveNutrientTransferAdvanced advanced) {
        HiveNutrientTransfer transfer = HiveNutrientTransferStateSupport.requireActive(state, advanced.transferId());
        if (!subject.equals(transfer.hiveId())) throw new IllegalArgumentException("hive nutrient cursor lacks hive ownership");
        return HiveNutrientTransferStateSupport.advance(state, advanced.transferId(), advanced.cursor());
    }

    static FrontierWorldState reduceCompleted(FrontierWorldState state, SubjectId subject, HiveNutrientTransferCompleted completed) {
        HiveNutrientTransfer transfer = HiveNutrientTransferStateSupport.requireActive(state, completed.receipt().transferId());
        if (!subject.equals(transfer.hiveId())) throw new IllegalArgumentException("hive nutrient receipt lacks hive ownership");
        return HiveNutrientTransferStateSupport.complete(state, completed.receipt());
    }

    static FrontierWorldState reduceBlocked(FrontierWorldState state, SubjectId subject, HiveNutrientTransferBlocked blocked) {
        HiveNutrientTransfer transfer = HiveNutrientTransferStateSupport.requireActive(state, blocked.transferId());
        if (!subject.equals(transfer.hiveId())) throw new IllegalArgumentException("hive nutrient block lacks hive ownership");
        return HiveNutrientTransferStateSupport.block(state, blocked.transferId(), blocked.reason());
    }

    static HiveNutrientTransfer create(FrontierWorldState state, StrategicTask task, ExactItemStack item,
                                       SubjectId targetStore, int targetSlot) {
        if (!(item.custody() instanceof InventoryCustody.ContainerSlot sourceSlot)) throw new IllegalArgumentException("hive nutrient source must be one store slot");
        List<BlockPosition> corridor = corridor(state.inventory().surfaces().get(sourceSlot.containerId()).position(), state.inventory().surfaces().get(targetStore).position());
        String idSuffix = task.id().value().replace(':', '-');
        return new HiveNutrientTransfer(new SubjectId("transfer:hive-nutrient-" + idSuffix), state.bootstrap().hive().id(), task.id(),
                sourceSlot.containerId(), sourceSlot, targetStore, new InventoryCustody.ContainerSlot(targetStore, targetSlot),
                new SubjectId("cargo:hive-nutrient-" + idSuffix), item.id(), corridor, 0, HiveNutrientTransferPhase.IN_TRANSIT, java.util.Optional.empty());
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
        if (state.inventory().surfaces().get(transfer.sourceStoreId()).status() != ContainerSurfaceStatus.UNMATERIALIZED
                || state.inventory().surfaces().get(transfer.targetStoreId()).status() != ContainerSurfaceStatus.UNMATERIALIZED) {
            return HiveNutrientTransferBlockReason.ENDPOINT_MATERIALIZED;
        }
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
    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }
}
