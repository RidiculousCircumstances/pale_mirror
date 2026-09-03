package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BioformLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.HiveCocoonPlan;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilization;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationCocoonReleased;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflictReason;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflicted;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationReleaseStarted;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStatus;
import io.farfrontier.palemirror.frontier.v3.model.HiveOrgan;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Loaded-world executor for the exact cocoon-release half of hive mobilization.
 *
 * <p>It records {@code RELEASING} durably before removing one block. A restart or changed
 * block during that non-replayable interval becomes a visible conflict; it never infers a
 * released body from absence. The matching pure confirmation alone advances lifecycle/body
 * state, so this executor has no actor creation authority.</p>
 */
final class FrontierV3HiveMobilizationExecutor {
    /** A loaded world distinguishes no projection yet from a changed previously owned cocoon. */
    enum CocoonProjection { PENDING, PRESENT, CONFLICT }

    private FrontierV3HiveMobilizationExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        state.hiveColony().mobilizations().values().stream().sorted(Comparator.comparing(HiveMobilization::id))
                .filter(mobilization -> mobilization.status() == HiveMobilizationStatus.WAKING
                        || mobilization.status() == HiveMobilizationStatus.RELEASING)
                .findFirst().ifPresent(mobilization -> advance(level, runtime, state, mobilization));
    }

    /**
     * Read-only reason why one exact waking group can or cannot cross its physical boundary.
     * This is deliberately separate from {@link #advance}: observing the gate must never cause
     * a cocoon to open or a command to enter the WAL.
     */
    static Readiness readiness(ServerLevel level, FrontierWorldState state, SubjectId mobilizationId) {
        HiveMobilization mobilization = state.hiveColony().mobilizations().get(mobilizationId);
        if (mobilization == null) return Readiness.notFound();
        if (mobilization.status() != HiveMobilizationStatus.WAKING && mobilization.status() != HiveMobilizationStatus.RELEASING) {
            return Readiness.inactive(mobilization);
        }
        SubjectId bioformId = mobilization.status() == HiveMobilizationStatus.RELEASING
                ? mobilization.releasingMemberId().orElse(null)
                : mobilization.memberIds().get(mobilization.releasedMemberIds().size());
        if (bioformId == null) return Readiness.invalid(mobilization, "missing_releasing_member");
        BioformLifecycle lifecycle = state.hiveColony().bioformLifecycles().get(bioformId);
        if (lifecycle == null || lifecycle.homeSlot().isEmpty()) return Readiness.invalid(mobilization, "missing_cocoon_home");
        HiveOrgan hibernaculum = Stream.concat(state.bootstrap().hive().organs().stream(), state.hiveColony().addedOrgans().values().stream())
                .filter(organ -> organ.id().equals(lifecycle.homeSlot().orElseThrow().hibernaculumId())).findFirst().orElse(null);
        if (hibernaculum == null) return Readiness.invalid(mobilization, "missing_hibernaculum");
        var position = HiveCocoonPlan.cocoonCell(hibernaculum, lifecycle.homeSlot().orElseThrow());
        BlockPos minecraftPosition = new BlockPos(position.x(), position.y(), position.z());
        FrontierV3PhysicalDemand.Readiness demand = FrontierV3PhysicalDemand.readiness(level,
                minecraftPosition);
        CocoonProjection projection = cocoonProjection(FrontierV3GrayboxLedger.get(level).claim(minecraftPosition), bioformId,
                level.getBlockState(minecraftPosition).equals(FrontierV3GrayboxExecutor.material(GrayboxMaterial.HIVE_COCOON)));
        return new Readiness("ok", mobilization.status().name(), bioformId.value(), position.x(), position.y(), position.z(),
                demand.chunkLoaded(), demand.ordinaryPlayerNearby(), demand.runnable(), projection.name());
    }

    private static void advance(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, HiveMobilization mobilization) {
        SubjectId bioformId = mobilization.status() == HiveMobilizationStatus.RELEASING
                ? mobilization.releasingMemberId().orElseThrow()
                : mobilization.memberIds().get(mobilization.releasedMemberIds().size());
        BioformLifecycle lifecycle = state.hiveColony().bioformLifecycles().get(bioformId);
        if (lifecycle == null || lifecycle.homeSlot().isEmpty()) {
            submitConflict(level, runtime, state, mobilization, HiveMobilizationConflictReason.COCOON_CHANGED); return;
        }
        HiveOrgan hibernaculum = Stream.concat(state.bootstrap().hive().organs().stream(), state.hiveColony().addedOrgans().values().stream())
                .filter(organ -> organ.id().equals(lifecycle.homeSlot().orElseThrow().hibernaculumId())).findFirst().orElse(null);
        if (hibernaculum == null) { submitConflict(level, runtime, state, mobilization, HiveMobilizationConflictReason.COCOON_CHANGED); return; }
        var modelPosition = HiveCocoonPlan.cocoonCell(hibernaculum, lifecycle.homeSlot().orElseThrow());
        BlockPos position = new BlockPos(modelPosition.x(), modelPosition.y(), modelPosition.z());
        if (!FrontierV3PhysicalDemand.exists(level, position)) return;
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        FrontierV3GrayboxLedger.Claim claim = ledger.claim(position);
        CocoonProjection projection = cocoonProjection(claim, bioformId,
                level.getBlockState(position).equals(FrontierV3GrayboxExecutor.material(GrayboxMaterial.HIVE_COCOON)));
        if (mobilization.status() == HiveMobilizationStatus.WAKING) {
            // Player demand can load a chunk before the bounded projector has made its first
            // pass. No provenance means no v3 object existed yet, so it is pending work rather
            // than a player/world loss. Once a claim exists, every mismatch is a real conflict.
            if (projection == CocoonProjection.PENDING) return;
            if (projection == CocoonProjection.CONFLICT) {
                submitConflict(level, runtime, state, mobilization, HiveMobilizationConflictReason.COCOON_CHANGED);
                return;
            }
            submit(level, runtime, state, "hive-mobilization-release-start", mobilization.id(),
                    new HiveMobilizationReleaseStarted(mobilization.id()));
            return;
        }
        if (projection != CocoonProjection.PRESENT) {
            // The durable RELEASING state proves an effect was requested, not that an air block
            // is ours. A missing or altered loaded block is therefore a conflict, including a
            // restart between effect and receipt.
            submitConflict(level, runtime, state, mobilization, HiveMobilizationConflictReason.COCOON_CHANGED);
            return;
        }
        if (!level.setBlock(position, Blocks.AIR.defaultBlockState(), 3) || !level.getBlockState(position).isAir()) return;
        boolean accepted = submit(level, runtime, state, "hive-mobilization-cocoon-released", mobilization.id(),
                new HiveMobilizationCocoonReleased(mobilization.id(), bioformId));
        if (accepted) ledger.retire(position, claim.owner(), claim.material(), claim.semanticPart());
    }

    /**
     * A null claim is deliberately pending: no v3 block has existed there yet, so it cannot be
     * evidence of a player/world loss. A retained claim makes the opposite assertion durable.
     */
    static CocoonProjection cocoonProjection(FrontierV3GrayboxLedger.Claim claim, SubjectId bioformId, boolean expectedBlock) {
        if (claim == null) return CocoonProjection.PENDING;
        if (!claim.conflicted() && claim.owner().equals(bioformId.value())
                && claim.material().equals(GrayboxMaterial.HIVE_COCOON.name())
                && claim.semanticPart().equals(GrayboxSemanticPart.COCOON.name()) && expectedBlock) {
            return CocoonProjection.PRESENT;
        }
        return CocoonProjection.CONFLICT;
    }

    private static void submitConflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                       FrontierWorldState state, HiveMobilization mobilization, HiveMobilizationConflictReason reason) {
        submit(level, runtime, state, "hive-mobilization-conflict", mobilization.id(), new HiveMobilizationConflicted(mobilization.id(), reason));
    }

    private static boolean submit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                  String action, SubjectId subject, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        var checkpoint = runtime.canonicalState().orElse(null);
        if (checkpoint == null) return false;
        CommandId id = new CommandId("executor:" + action + "-r" + checkpoint.revision().value() + "-" + subject.value().replace(':', '-'));
        CommandResult result = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload)).orElse(null);
        return result instanceof CommandResult.Accepted;
    }

    record Readiness(String status, String mobilizationStatus, String nextMember, int x, int y, int z,
                     boolean chunkLoaded, boolean ordinaryPlayerNearby, boolean runnable, String detail) {
        private static Readiness notFound() { return new Readiness("not_found", "", "", 0, 0, 0, false, false, false, ""); }
        private static Readiness inactive(HiveMobilization value) {
            return new Readiness("inactive", value.status().name(), "", 0, 0, 0, false, false, false, "");
        }
        private static Readiness invalid(HiveMobilization value, String detail) {
            return new Readiness("invalid", value.status().name(), "", 0, 0, 0, false, false, false, detail);
        }
    }
}
