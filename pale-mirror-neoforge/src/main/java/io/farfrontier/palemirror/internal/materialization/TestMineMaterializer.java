package io.farfrontier.palemirror.internal.materialization;

import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.content.EncounterProfile;
import io.farfrontier.palemirror.internal.adapter.ActorOperationResult;
import io.farfrontier.palemirror.internal.world.MutableCell;
import io.farfrontier.palemirror.internal.world.SourceGatePartRef;
import io.farfrontier.palemirror.internal.world.GatePresentationRecord;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import io.farfrontier.palemirror.internal.world.WorldObjectLifecycle;
import io.farfrontier.palemirror.domain.ThreatTier;
import io.farfrontier.palemirror.domain.FacilityState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Executes at most one persisted operation per call. Every operation verifies
 * its physical postcondition before it is advanced in its job.
 */
public final class TestMineMaterializer {
    private static final int MAX_REGISTERED_BIOME_CELLS = 70;
    public boolean executeNext(ServerLevel level, TestMineRecord mine, FacilityState facility, MaterializationJob job) {
        if (!level.hasChunkAt(mine.anchor())) return false;
        if (job.state() == JobState.COMPLETED || job.state() == JobState.BLOCKED) return job.state() == JobState.COMPLETED;
        if (job.state() == JobState.PLANNED) job.start();
        MaterializationOperation operation = job.nextOperation();
        if (operation == null) {
            job.complete();
            return true;
        }
        if (operation.state() == OperationState.BLOCKED) {
            job.block(operation.lastError());
            return false;
        }
        if (operation.state() == OperationState.DEGRADED) {
            job.advanceOperation();
        } else if (operation.state() != OperationState.COMPLETED) {
            operation.start();
            if (operation.type() == MaterializationOperationType.ENSURE_SOURCE_ENCOUNTER_ACTOR) {
                ActorOperationResult result = ensureSourceActor(level, mine, facility, job.jobId(), operation.target());
                if (result.status() == ActorOperationResult.Status.MATERIALIZED) operation.complete();
                else {
                    operation.degrade(result.diagnostic());
                    mine.encounter().degrade(result.diagnostic());
                }
                job.advanceOperation();
                if (job.nextOperation() == null) completeJob(mine, job);
                return job.state() == JobState.COMPLETED;
            }
            if (operation.type() == MaterializationOperationType.REMOVE_SOURCE_ENCOUNTER_ACTOR) {
                ActorOperationResult result = removeSourceActor(level, mine, facility, operation.target());
                if (result.status() == ActorOperationResult.Status.MATERIALIZED) operation.complete();
                else {
                    operation.degrade(result.diagnostic());
                    mine.encounter().degrade(result.diagnostic());
                }
                job.advanceOperation();
                if (job.nextOperation() == null) completeJob(mine, job);
                return job.state() == JobState.COMPLETED;
            }
            if (operation.type() == MaterializationOperationType.ENSURE_SOURCE_GATE_PART) {
                ActorOperationResult result = ensureSourceGatePart(level, mine, facility, job.jobId(), operation.target());
                if (result.status() == ActorOperationResult.Status.MATERIALIZED) operation.complete();
                else {
                    operation.degrade(result.diagnostic());
                    mine.gate().degrade(result.diagnostic());
                }
                job.advanceOperation();
                if (job.nextOperation() == null) completeJob(mine, job);
                return job.state() == JobState.COMPLETED;
            }
            if (operation.type() == MaterializationOperationType.REMOVE_SOURCE_GATE_PART) {
                ActorOperationResult result = AdapterRegistry.sourceAdapter(facility.infectionSource()).removeGatePart(level, mine, operation.target());
                if (result.status() == ActorOperationResult.Status.MATERIALIZED) operation.complete();
                else {
                    operation.degrade(result.diagnostic());
                    mine.gate().degrade(result.diagnostic());
                }
                job.advanceOperation();
                if (job.nextOperation() == null) completeJob(mine, job);
                return job.state() == JobState.COMPLETED;
            }
            String error = execute(level, mine, facility, job, operation);
            if (error != null) {
                operation.block(error);
                job.block(error);
                return false;
            }
            operation.complete();
        }
        if (operation.state() == OperationState.COMPLETED) job.advanceOperation();
        if (job.nextOperation() == null) {
            completeJob(mine, job);
        }
        return job.state() == JobState.COMPLETED;
    }

    private String execute(ServerLevel level, TestMineRecord mine, FacilityState facility, MaterializationJob job,
                           MaterializationOperation operation) {
        return switch (operation.type()) {
            case ENSURE_OVERLAY -> ensureOverlay(level, mine, facility, operation.target());
            case ENSURE_PM_ANCHOR -> ensureAnchor(level, mine, job.jobId());
            case REMOVE_PM_ANCHOR -> removeAnchor(level, mine);
            case ENSURE_SOURCE_ENCOUNTER_ACTOR -> throw new IllegalStateException("Source actor operation must be handled as optional work");
            case REMOVE_SOURCE_ENCOUNTER_ACTOR -> throw new IllegalStateException("Source actor cleanup must be handled as optional work");
            case ENSURE_SOURCE_GATE_PART -> throw new IllegalStateException("Source gate operation must be handled by its adapter");
            case REMOVE_SOURCE_GATE_PART -> throw new IllegalStateException("Source gate cleanup must be handled by its adapter");
            case REMOVE_OVERLAY -> removeOverlay(level, mine);
        };
    }

    private String ensureOverlay(ServerLevel level, TestMineRecord mine, FacilityState facility, String tierName) {
        ThreatTier tier;
        try {
            tier = ThreatTier.valueOf(tierName);
        } catch (IllegalArgumentException failure) {
            return "Unknown infection biome tier " + tierName;
        }
        if (tier == ThreatTier.DORMANT) return "Infection biome cannot materialize at DORMANT tier";
        if (mine.mutableCells().size() > MAX_REGISTERED_BIOME_CELLS) {
            return "Test mine biome exceeds the bounded registered-cell budget";
        }
        for (MutableCell cell : mine.biomeCells()) {
            String desired = AdapterRegistry.sourceAdapter(facility.infectionSource()).overlayPalette().desiredBlock(cell, tier);
            boolean previouslyOwned = !cell.lastAppliedBlock().equals(cell.baselineBlock());
            boolean mustApply = !desired.equals(cell.baselineBlock());
            if (!previouslyOwned && !mustApply) continue;
            if (cell.conflicted()) return "Mutable cell " + cell.position() + " is conflicted";
            String current = blockId(level, cell.position());
            if (!current.equals(cell.baselineBlock()) && !current.equals(cell.lastAppliedBlock())) {
                cell.conflict();
                return "Mutable cell " + cell.position() + " was changed outside Pale Mirror";
            }
            String error = setBlock(level, cell.position(), desired);
            if (error != null) return error;
            cell.markApplied(desired);
        }
        return overlaysMatch(level, mine, facility, tier) ? null : "Infection biome postcondition failed";
    }

    private String ensureAnchor(ServerLevel level, TestMineRecord mine, String jobId) {
        if (!AdapterRegistry.vanillaAnchor().ensureAnchor(level, mine, jobId)) return "Could not create PM anchor";
        if (!AdapterRegistry.vanillaAnchor().hasAnchor(level, mine)) return "PM anchor postcondition failed";
        mine.object().setLifecycle(WorldObjectLifecycle.ACTIVE);
        return null;
    }

    private String removeAnchor(ServerLevel level, TestMineRecord mine) {
        AdapterRegistry.vanillaAnchor().removeAnchor(level, mine);
        return mine.anchorId() == null && !AdapterRegistry.vanillaAnchor().hasAnchor(level, mine)
                ? null : "PM anchor removal postcondition failed";
    }

    private ActorOperationResult ensureSourceActor(ServerLevel level, TestMineRecord mine, FacilityState facility,
                                                   String jobId, String slotId) {
        EncounterProfile.ActorSlot slot = mine.encounter().actors().stream()
                .filter(actor -> actor.slotId().equals(slotId))
                .findFirst()
                .map(actor -> new EncounterProfile.ActorSlot(actor.slotId(), actor.actorProfileId(),
                        io.farfrontier.palemirror.domain.ThreatTier.FOOTHOLD))
                .orElse(null);
        return slot == null ? ActorOperationResult.unavailable("Encounter profile has no persisted slot " + slotId)
                : AdapterRegistry.sourceAdapter(facility.infectionSource()).ensureActor(level, mine, jobId, slot);
    }

    private ActorOperationResult removeSourceActor(ServerLevel level, TestMineRecord mine, FacilityState facility, String slotId) {
        ActorOperationResult result = AdapterRegistry.sourceAdapter(facility.infectionSource()).removeActor(level, mine, slotId);
        if (result.status() != ActorOperationResult.Status.MATERIALIZED) {
            return result;
        }
        if (mine.encounter().actors().stream().allMatch(actor -> actor.status() != io.farfrontier.palemirror.internal.world.EncounterActorRef.Status.ACTIVE)) {
            mine.encounter().clean();
        }
        return result;
    }

    private ActorOperationResult ensureSourceGatePart(ServerLevel level, TestMineRecord mine, FacilityState facility,
                                                       String jobId, String slotId) {
        SourceGatePartRef part = mine.gate().part(slotId).orElse(null);
        return part == null ? ActorOperationResult.unavailable("Gate record has no persisted part " + slotId)
                : AdapterRegistry.sourceAdapter(facility.infectionSource()).ensureGatePart(level, mine, jobId, part);
    }

    private static void completeJob(TestMineRecord mine, MaterializationJob job) {
        job.complete();
        if (job.operations().stream().anyMatch(value -> value.type() == MaterializationOperationType.REMOVE_SOURCE_GATE_PART)) {
            mine.setGate(GatePresentationRecord.none());
        }
        mine.object().setLifecycle(job.operations().stream().anyMatch(value -> value.type() == MaterializationOperationType.ENSURE_PM_ANCHOR)
                ? WorldObjectLifecycle.ACTIVE : WorldObjectLifecycle.REPRESENTED);
    }

    private String removeOverlay(ServerLevel level, TestMineRecord mine) {
        for (MutableCell cell : mine.mutableCells()) {
            if (cell.lastAppliedBlock().equals(cell.baselineBlock())) continue;
            if (cell.conflicted()) return "Mutable cell " + cell.position() + " is conflicted";
            String current = blockId(level, cell.position());
            if (!current.equals(cell.lastAppliedBlock()) && !current.equals(cell.baselineBlock())) {
                cell.conflict();
                return "Mutable cell " + cell.position() + " was changed outside Pale Mirror";
            }
            String error = setBlock(level, cell.position(), cell.baselineBlock());
            if (error != null) return error;
            cell.markApplied(cell.baselineBlock());
        }
        return baselinesMatch(level, mine) ? null : "Overlay removal postcondition failed";
    }

    private static boolean overlaysMatch(ServerLevel level, TestMineRecord mine, FacilityState facility, ThreatTier tier) {
        return mine.biomeCells().stream().allMatch(cell -> {
            String desired = AdapterRegistry.sourceAdapter(facility.infectionSource()).overlayPalette().desiredBlock(cell, tier);
            boolean previouslyOwned = !cell.lastAppliedBlock().equals(cell.baselineBlock());
            boolean mustApply = !desired.equals(cell.baselineBlock());
            return !previouslyOwned && !mustApply || blockId(level, cell.position()).equals(desired);
        });
    }

    private static boolean baselinesMatch(ServerLevel level, TestMineRecord mine) {
        return mine.mutableCells().stream().allMatch(cell -> cell.lastAppliedBlock().equals(cell.baselineBlock())
                || blockId(level, cell.position()).equals(cell.baselineBlock()));
    }

    private static String setBlock(ServerLevel level, BlockPos position, String blockId) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId));
        if (block == Blocks.AIR && !"minecraft:air".equals(blockId)) return "Unknown palette block " + blockId;
        if (!blockId(level, position).equals(blockId)) level.setBlock(position, block.defaultBlockState(), 3);
        return blockId(level, position).equals(blockId) ? null : "Could not apply palette block " + blockId + " at " + position;
    }

    private static String blockId(ServerLevel level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
    }
}
