package io.farfrontier.palemirror.internal.materialization;

import io.farfrontier.palemirror.PaleMirrorMod;
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
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
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
    /** Bounds neighbour updates while persisted MutableCell state acts as the crash-safe cursor. */
    static final int MAX_OVERLAY_RECONCILIATIONS_PER_INVOCATION = 16;

    public boolean executeNext(ServerLevel level, PaleMirrorSavedData data, TestMineRecord mine,
                               FacilityState facility, MaterializationJob job) {
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
            if (operation.state() == OperationState.PENDING) operation.start();
            if (operation.type() == MaterializationOperationType.ENSURE_SOURCE_ENCOUNTER_ACTOR) {
                ActorOperationResult result = ensureSourceActor(level, mine, facility, job.jobId(), operation.target());
                if (result.status() == ActorOperationResult.Status.MATERIALIZED) operation.complete();
                else {
                    operation.degrade(result.diagnostic());
                    mine.encounter().degrade(result.diagnostic());
                    PaleMirrorMod.LOGGER.warn("PM encounter actor materialization degraded for {} slot {}: {}",
                            mine.id().value(), operation.target(), result.diagnostic());
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
                    PaleMirrorMod.LOGGER.warn("PM encounter actor cleanup degraded for {} slot {}: {}",
                            mine.id().value(), operation.target(), result.diagnostic());
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
            ExecutionResult result = execute(level, data, mine, facility, job, operation);
            if (result.status() == ExecutionStatus.DEFERRED) return false;
            if (result.status() == ExecutionStatus.BLOCKED) {
                operation.block(result.diagnostic());
                job.block(result.diagnostic());
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

    private ExecutionResult execute(ServerLevel level, PaleMirrorSavedData data, TestMineRecord mine,
                                    FacilityState facility, MaterializationJob job,
                                    MaterializationOperation operation) {
        return switch (operation.type()) {
            case ENSURE_OVERLAY -> ensureOverlay(level, mine, facility, operation.target());
            case ENSURE_PM_ANCHOR -> ensureAnchor(level, data, mine, facility, job.jobId());
            case REMOVE_PM_ANCHOR -> removeAnchor(level, data, mine);
            case ENSURE_SOURCE_ENCOUNTER_ACTOR -> throw new IllegalStateException("Source actor operation must be handled as optional work");
            case REMOVE_SOURCE_ENCOUNTER_ACTOR -> throw new IllegalStateException("Source actor cleanup must be handled as optional work");
            case ENSURE_SOURCE_GATE_PART -> throw new IllegalStateException("Source gate operation must be handled by its adapter");
            case REMOVE_SOURCE_GATE_PART -> throw new IllegalStateException("Source gate cleanup must be handled by its adapter");
            case REMOVE_OVERLAY -> removeOverlay(level, mine);
            default -> ExecutionResult.blocked(
                    "Operation " + operation.type() + " is not supported by the test-mine executor");
        };
    }

    private ExecutionResult ensureOverlay(ServerLevel level, TestMineRecord mine, FacilityState facility,
                                          String tierName) {
        ThreatTier tier;
        try {
            tier = ThreatTier.valueOf(tierName);
        } catch (IllegalArgumentException failure) {
            return ExecutionResult.blocked("Unknown infection biome tier " + tierName);
        }
        if (tier == ThreatTier.DORMANT) {
            return ExecutionResult.blocked("Infection biome cannot materialize at DORMANT tier");
        }
        boolean deferred = false;
        int reconciled = 0;
        for (MutableCell cell : mine.biomeCells()) {
            String desired = AdapterRegistry.sourceAdapter(facility.infectionSource()).overlayPalette().desiredBlock(cell, tier);
            boolean activeOrOwned = !desired.equals(cell.baselineBlock())
                    || !cell.lastAppliedBlock().equals(cell.baselineBlock());
            if (!activeOrOwned) continue;
            // A reconciled cell is durable ownership evidence. It does not need its chunk
            // to remain loaded while another semantic slice is visited later.
            if (cell.baselineObserved() && cell.lastAppliedBlock().equals(desired)) continue;
            if (!level.hasChunkAt(cell.position())) {
                deferred = true;
                continue;
            }
            if (cell.conflicted()) {
                return ExecutionResult.blocked("Mutable cell " + cell.position() + " is conflicted");
            }
            String current = blockId(level, cell.position());
            if (!cell.baselineObserved()) {
                if (reconciled >= MAX_OVERLAY_RECONCILIATIONS_PER_INVOCATION) {
                    return ExecutionResult.deferred();
                }
                cell.observeBaseline(current);
                reconciled++;
                desired = AdapterRegistry.sourceAdapter(facility.infectionSource())
                        .overlayPalette().desiredBlock(cell, tier);
                if (cell.lastAppliedBlock().equals(desired)) continue;
            }
            // A crash can persist the physical postcondition before SavedData.
            // Adopt only the exact desired block; every other unknown change remains a conflict.
            if (current.equals(desired)) {
                if (!cell.lastAppliedBlock().equals(desired)) {
                    if (reconciled >= MAX_OVERLAY_RECONCILIATIONS_PER_INVOCATION) {
                        return ExecutionResult.deferred();
                    }
                    cell.markApplied(desired);
                    reconciled++;
                }
                continue;
            }
            if (!current.equals(cell.baselineBlock()) && !current.equals(cell.lastAppliedBlock())) {
                cell.conflict();
                return ExecutionResult.blocked(
                        "Mutable cell " + cell.position() + " was changed outside Pale Mirror");
            }
            if (reconciled >= MAX_OVERLAY_RECONCILIATIONS_PER_INVOCATION) {
                return ExecutionResult.deferred();
            }
            String error = setBlock(level, cell.position(), desired);
            if (error != null) return ExecutionResult.blocked(error);
            cell.markApplied(desired);
            reconciled++;
        }
        return deferred ? ExecutionResult.deferred() : ExecutionResult.applied();
    }

    private ExecutionResult ensureAnchor(ServerLevel level, PaleMirrorSavedData data, TestMineRecord mine,
                                         FacilityState facility, String jobId) {
        if (!level.hasChunkAt(mine.anchor())) return ExecutionResult.deferred();
        if (io.farfrontier.palemirror.internal.world.ProductProfilePreflight.coreOnly()) {
            if (!AdapterRegistry.vanillaAnchor().ensureAnchor(level, mine, jobId)) {
                return ExecutionResult.blocked("Could not create core-only PM anchor");
            }
            if (!AdapterRegistry.vanillaAnchor().hasAnchor(level, mine)) {
                return ExecutionResult.blocked("Core-only PM anchor postcondition failed");
            }
        } else {
            var provider = io.farfrontier.palemirror.api.PaleMirrorVisuals.provider().orElse(null);
            if (provider == null) {
                return ExecutionResult.blocked("Product profile has no visual threat-controller provider");
            }
            int stage = Math.max(1, Math.min(4, facility.threatTier().ordinal()));
            var projection = new io.farfrontier.palemirror.api.ThreatControllerProjection(mine.id().value(), jobId,
                    new io.farfrontier.palemirror.api.VisualPoint(mine.anchor().getX(), mine.anchor().getY(), mine.anchor().getZ()), stage);
            var result = provider.ensureThreatController(level, projection);
            if (result.status() != io.farfrontier.palemirror.api.ThreatControllerResult.Status.MATERIALIZED
                    || result.entityId() == null) return ExecutionResult.blocked(result.diagnostic().isBlank()
                    ? "Threat Heart materialization failed" : result.diagnostic());
            mine.setAnchorId(result.entityId());
            data.threatCombat().attachActor("pale_mirror", mine.id().value(), "controller", "heart",
                    "threat_heart_v1", result.entityId(), controllerHitPoints(facility.threatTier()));
        }
        mine.object().setLifecycle(WorldObjectLifecycle.ACTIVE);
        return ExecutionResult.applied();
    }

    private ExecutionResult removeAnchor(ServerLevel level, PaleMirrorSavedData data, TestMineRecord mine) {
        if (!level.hasChunkAt(mine.anchor())) return ExecutionResult.deferred();
        if (io.farfrontier.palemirror.internal.world.ProductProfilePreflight.coreOnly()) {
            AdapterRegistry.vanillaAnchor().removeAnchor(level, mine);
            return mine.anchorId() == null && !AdapterRegistry.vanillaAnchor().hasAnchor(level, mine)
                    ? ExecutionResult.applied()
                    : ExecutionResult.blocked("Core-only PM anchor removal postcondition failed");
        }
        var provider = io.farfrontier.palemirror.api.PaleMirrorVisuals.provider().orElse(null);
        if (provider == null) {
            return ExecutionResult.blocked("Product profile has no visual threat-controller provider");
        }
        var result = provider.removeThreatController(level, mine.id().value());
        if (result.status() == io.farfrontier.palemirror.api.ThreatControllerResult.Status.BLOCKED) {
            return ExecutionResult.blocked(result.diagnostic());
        }
        data.threatCombat().retireActor("pale_mirror", mine.id().value(), "controller", "heart");
        mine.setAnchorId(null);
        return ExecutionResult.applied();
    }

    private static int controllerHitPoints(ThreatTier tier) {
        return switch (tier) { case DORMANT, FOOTHOLD -> 24; case INFESTED -> 40; case SIEGE -> 64; case APEX -> 96; };
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

    private ExecutionResult removeOverlay(ServerLevel level, TestMineRecord mine) {
        boolean deferred = false;
        int reconciled = 0;
        for (MutableCell cell : mine.mutableCells()) {
            if (!cell.baselineObserved()) continue;
            if (cell.lastAppliedBlock().equals(cell.baselineBlock())) continue;
            if (!level.hasChunkAt(cell.position())) {
                deferred = true;
                continue;
            }
            if (cell.conflicted()) {
                return ExecutionResult.blocked("Mutable cell " + cell.position() + " is conflicted");
            }
            String current = blockId(level, cell.position());
            if (current.equals(cell.baselineBlock())) {
                if (reconciled >= MAX_OVERLAY_RECONCILIATIONS_PER_INVOCATION) {
                    return ExecutionResult.deferred();
                }
                cell.markApplied(cell.baselineBlock());
                reconciled++;
                continue;
            }
            if (!current.equals(cell.lastAppliedBlock()) && !current.equals(cell.baselineBlock())) {
                cell.conflict();
                return ExecutionResult.blocked(
                        "Mutable cell " + cell.position() + " was changed outside Pale Mirror");
            }
            if (reconciled >= MAX_OVERLAY_RECONCILIATIONS_PER_INVOCATION) {
                return ExecutionResult.deferred();
            }
            String error = setBlock(level, cell.position(), cell.baselineBlock());
            if (error != null) return ExecutionResult.blocked(error);
            cell.markApplied(cell.baselineBlock());
            reconciled++;
        }
        return deferred ? ExecutionResult.deferred() : ExecutionResult.applied();
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

    private enum ExecutionStatus { APPLIED, DEFERRED, BLOCKED }

    private record ExecutionResult(ExecutionStatus status, String diagnostic) {
        private static ExecutionResult applied() { return new ExecutionResult(ExecutionStatus.APPLIED, ""); }
        private static ExecutionResult deferred() { return new ExecutionResult(ExecutionStatus.DEFERRED, ""); }
        private static ExecutionResult blocked(String diagnostic) {
            return new ExecutionResult(ExecutionStatus.BLOCKED, diagnostic);
        }
    }
}
