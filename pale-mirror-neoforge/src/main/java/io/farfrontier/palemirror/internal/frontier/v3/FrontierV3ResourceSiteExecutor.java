package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteConflictObserved;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePreparationObservation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Loaded-chunk, crash-safe preparation of a whole fixed field; it never adopts or rewrites a foreign cell. */
final class FrontierV3ResourceSiteExecutor {
    enum BlockBreakObservation { UNMANAGED, ACCEPTED, REJECTED }

    private FrontierV3ResourceSiteExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        state.physicalIntents().values().stream().sorted(Comparator.comparing(PhysicalIntent::id))
                .filter(intent -> intent.kind() == PhysicalIntentKind.RESOURCE_SITE_PREPARATION)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                .findFirst().ifPresent(intent -> execute(level, runtime, state, intent));
    }

    static BlockBreakObservation observeBlockBreak(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ServerLevel level,
                                                   BlockPos position, String cause) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return BlockBreakObservation.REJECTED;
        Target target = target(state, position);
        if (target == null) return BlockBreakObservation.UNMANAGED;
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(target.site().id());
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE || !matches(level, target.site(), 0)) {
            if (claim != null) ledger.conflict(target.site().id());
            return BlockBreakObservation.UNMANAGED;
        }
        try {
            CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
            CommandId id = new CommandId("executor:resource-site-break-r" + checkpoint.revision().value() + "-p" + position.asLong());
            CommandResult result = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                    FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id),
                    new ResourceSiteConflictObserved(target.site().id(), canonical(position), cause))).orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
            if (!(result instanceof CommandResult.Accepted)) return BlockBreakObservation.REJECTED;
            ledger.conflict(target.site().id());
            return BlockBreakObservation.ACCEPTED;
        } catch (RuntimeException rejected) {
            return BlockBreakObservation.REJECTED;
        }
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(state, intent);
        if (target == null) { if (intent.status() == PhysicalIntentStatus.RUNNING) unknown(runtime, intent.id(), "target-conflict"); return; }
        if (!loaded(level, target.site())) return;
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(target.site().id());
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(level, runtime, ledger, target, claim); return; }
        if (claim != null) { unknown(runtime, intent.id(), "reserved-before-running"); return; }
        if (!baseline(level, target.site())) { unknown(runtime, intent.id(), "foreign-baseline"); return; }
        ledger.reserve(target.site().id(), intent.id());
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!placeWholeField(level, target.site())) { ledger.conflict(target.site().id()); unknown(runtime, intent.id(), "partial-write"); return; }
        ledger.activate(target.site().id()); confirm(runtime, intent, target.site());
    }

    private static void inspectRunning(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                       FrontierV3ResourceSiteLedger ledger, Target target, FrontierV3ResourceSiteLedger.Claim claim) {
        if (claim != null && claim.intentId().equals(target.intent().id()) && claim.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE
                && matches(level, target.site(), 0)) {
            confirm(runtime, target.intent(), target.site()); return;
        }
        if (claim != null) ledger.conflict(target.site().id());
        unknown(runtime, target.intent().id(), "restart-postcondition-conflict");
    }

    static boolean placeWholeField(ServerLevel level, ResourceSite site) {
        if (!baseline(level, site)) return false;
        for (BlockPosition soil : site.soilSlots()) level.setBlock(minecraft(soil), Blocks.FARMLAND.defaultBlockState(), 3);
        for (BlockPosition crop : site.cropSlots()) level.setBlock(minecraft(crop), crop(0), 3);
        return matches(level, site, 0);
    }

    private static void confirm(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, ResourceSite site) {
        ResourceSitePreparationObservation receipt = new ResourceSitePreparationObservation(
                new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')), intent.id(), site.id(), 64, 64);
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt), "confirmed")) {
            throw new IllegalStateException("resource-site preparation confirmation was rejected");
        }
    }

    private static Target target(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_PREPARATION || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED) return null;
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(intent.causeSubjectId());
        return site == null ? null : new Target(site, intent);
    }
    private static Target target(FrontierWorldState state, BlockPos position) {
        return FrontierResourceSitePlan.compile(state.bootstrap()).values().stream().filter(site -> contains(site, position)).findFirst()
                .map(site -> new Target(site, null)).orElse(null);
    }
    private static boolean contains(ResourceSite site, BlockPos position) {
        return java.util.stream.Stream.concat(site.cropSlots().stream(), site.soilSlots().stream()).anyMatch(slot -> minecraft(slot).equals(position));
    }
    private static boolean loaded(ServerLevel level, ResourceSite site) {
        return java.util.stream.Stream.concat(site.cropSlots().stream(), site.soilSlots().stream()).map(FrontierV3ResourceSiteExecutor::minecraft).allMatch(level::hasChunkAt);
    }
    static boolean baseline(ServerLevel level, ResourceSite site) {
        return site.cropSlots().stream().allMatch(crop -> level.getBlockState(minecraft(crop)).isAir())
                && site.soilSlots().stream().allMatch(soil -> {
                    BlockState state = level.getBlockState(minecraft(soil));
                    return (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)) && !level.getBlockState(minecraft(soil).below()).isAir();
                });
    }
    static boolean matches(ServerLevel level, ResourceSite site, int stage) {
        return site.soilSlots().stream().allMatch(soil -> level.getBlockState(minecraft(soil)).is(Blocks.FARMLAND))
                && site.cropSlots().stream().allMatch(crop -> level.getBlockState(minecraft(crop)).equals(crop(stage)));
    }
    private static BlockState crop(int stage) { return Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, stage); }
    private static BlockPos minecraft(BlockPosition position) { return new BlockPos(position.x(), position.y(), position.z()); }
    private static BlockPosition canonical(BlockPos position) { return new BlockPosition(position.getX(), position.getY(), position.getZ()); }

    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, String phase) {
        transition(runtime, id, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase);
    }
    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, PhysicalIntentStatus status,
                                      Optional<io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation> observation, String phase) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId command = new CommandId("executor:resource-site-" + phase + "-" + id.value().replace(':', '-'));
        CommandResult result = runtime.submit(new FrontierCommand(1, command, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), new PhysicalIntentTransition(id, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        return result instanceof CommandResult.Accepted;
    }

    record Target(ResourceSite site, PhysicalIntent intent) { }
}
