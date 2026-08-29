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
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePreparationObservation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loaded-chunk, crash-safe preparation of a whole fixed field; it never adopts or rewrites a foreign cell. */
final class FrontierV3ResourceSiteExecutor {
    private static final Map<FrontierV3ServerRuntime<?, ?>, Integer> STAGE_CURSORS = new IdentityHashMap<>();
    enum BlockBreakObservation { UNMANAGED, ACCEPTED, REJECTED }
    enum StageProjectionResult { CURRENT, UPDATED, CONFLICT, DEFERRED }

    private FrontierV3ResourceSiteExecutor() { }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { STAGE_CURSORS.remove(runtime); }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        state.physicalIntents().values().stream().sorted(Comparator.comparing(PhysicalIntent::id))
                .filter(intent -> intent.kind() == PhysicalIntentKind.RESOURCE_SITE_PREPARATION)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                .findFirst().ifPresent(intent -> execute(level, runtime, state, intent));
        projectOneGrowthStage(level, runtime, state);
    }

    static BlockBreakObservation observeBlockBreak(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ServerLevel level,
                                                   BlockPos position, String cause) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return BlockBreakObservation.REJECTED;
        Target target = target(state, position);
        if (target == null) return BlockBreakObservation.UNMANAGED;
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(target.site().id());
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE) return BlockBreakObservation.UNMANAGED;
        if (!matches(level, target.site(), claim.stage())) {
            recordConflict(runtime, ledger, target.site(), firstMismatch(level, target.site(), claim.stage()).orElse(canonical(position)), cause);
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

    static boolean blocksNativeCropGrowth(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ServerLevel level, BlockPos position) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return false;
        Target target = target(state, position); if (target == null || !target.site().cropSlots().contains(canonical(position))) return false;
        return blocksNativeCropGrowth(level, FrontierV3ResourceSiteLedger.get(level), target.site(), position);
    }

    static boolean blocksNativeCropGrowth(ServerLevel level, FrontierV3ResourceSiteLedger ledger, ResourceSite site, BlockPos position) {
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id());
        return claim != null && claim.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE && site.cropSlots().contains(canonical(position))
                && level.getBlockState(position).equals(crop(claim.stage()));
    }

    private static void projectOneGrowthStage(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        List<ResourceSiteLifecycle> candidates = state.resourceSites().sites().values().stream().filter(FrontierV3ResourceSiteExecutor::projectsGrowthStage)
                .sorted(Comparator.comparing(ResourceSiteLifecycle::siteId)).toList();
        if (candidates.isEmpty()) return;
        int index = Math.floorMod(STAGE_CURSORS.getOrDefault(runtime, 0), candidates.size());
        STAGE_CURSORS.put(runtime, (index + 1) % candidates.size()); ResourceSiteLifecycle lifecycle = candidates.get(index);
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(lifecycle.siteId()); FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        StageProjectionResult result = projectStage(level, ledger, site, lifecycle.growthStage());
        if (result == StageProjectionResult.CONFLICT) {
            FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id()); int observedStage = claim == null ? lifecycle.growthStage() : claim.stage();
            recordConflict(runtime, ledger, site, firstMismatch(level, site, observedStage).orElse(site.cropSlots().getFirst()), "observed:resource-site-stage");
        }
    }

    static StageProjectionResult projectStage(ServerLevel level, FrontierV3ResourceSiteLedger ledger, ResourceSite site, int desiredStage) {
        if (desiredStage < 0 || desiredStage > 7) throw new IllegalArgumentException("resource-site crop stage is invalid");
        if (!loaded(level, site)) return StageProjectionResult.DEFERRED;
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id());
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE) return StageProjectionResult.CONFLICT;
        if (!matches(level, site, claim.stage())) return StageProjectionResult.CONFLICT;
        if (claim.stage() == desiredStage) return StageProjectionResult.CURRENT;
        for (BlockPosition crop : site.cropSlots()) level.setBlock(minecraft(crop), crop(desiredStage), 3);
        if (!matches(level, site, desiredStage)) return StageProjectionResult.CONFLICT;
        ledger.updateStage(site.id(), desiredStage); return StageProjectionResult.UPDATED;
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
    static boolean contains(ResourceSite site, BlockPos position) {
        return java.util.stream.Stream.concat(site.cropSlots().stream(), site.soilSlots().stream()).anyMatch(slot -> minecraft(slot).equals(position));
    }
    static boolean loaded(ServerLevel level, ResourceSite site) {
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
    private static Optional<BlockPosition> firstMismatch(ServerLevel level, ResourceSite site, int stage) {
        return java.util.stream.Stream.concat(site.soilSlots().stream().filter(soil -> !level.getBlockState(minecraft(soil)).is(Blocks.FARMLAND)),
                site.cropSlots().stream().filter(crop -> !level.getBlockState(minecraft(crop)).equals(crop(stage)))).findFirst();
    }
    private static boolean projectsGrowthStage(ResourceSiteLifecycle lifecycle) {
        return lifecycle.phase() == ResourceSitePhase.GROWING || lifecycle.phase() == ResourceSitePhase.READY;
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
    static void recordConflict(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierV3ResourceSiteLedger ledger,
                                       ResourceSite site, BlockPosition position, String cause) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId id = new CommandId("executor:resource-site-conflict-r" + checkpoint.revision().value() + "-p" + minecraft(position).asLong());
        recordConflict(runtime, ledger, site, position, cause, id);
    }
    static boolean recordConflict(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierV3ResourceSiteLedger ledger,
                                  ResourceSite site, BlockPosition position, String cause, CommandId id) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandResult result = runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), new ResourceSiteConflictObserved(site.id(), position, cause)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (!(result instanceof CommandResult.Accepted)) return false;
        ledger.conflict(site.id()); return true;
    }

    record Target(ResourceSite site, PhysicalIntent intent) { }
}
