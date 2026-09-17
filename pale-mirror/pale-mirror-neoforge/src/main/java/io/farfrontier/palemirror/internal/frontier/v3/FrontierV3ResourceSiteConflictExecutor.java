package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteConflictReason;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteConflictObserved;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteConflictSource;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;

/** One canonical conflict admission and its bounded diagnostic incident, shared by every resource-site adapter. */
final class FrontierV3ResourceSiteConflictExecutor {
    private FrontierV3ResourceSiteConflictExecutor() { }

    static boolean recordConflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                  FrontierV3ResourceSiteLedger ledger, ResourceSite site, BlockPosition position,
                                  ResourceSiteConflictReason reason, ResourceSiteConflictSource source, CommandId id) {
        CommandResult result = submitConflict(runtime, site, position, reason, source, id);
        if (!(result instanceof CommandResult.Accepted)) return false;
        ledger.conflict(site.id());
        FrontierV3DiagnosticTrace.record(level.getServer(), incidentTrace(site),
                "resource_site_conflict:" + source.name().toLowerCase(Locale.ROOT), site.id(), result);
        return true;
    }

    static void recordLifecycleConflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                        FrontierV3ResourceSiteLedger ledger, ResourceSite site, BlockPosition position,
                                        ResourceSiteConflictReason reason, FrontierV3ResourceSiteExecutor.LifecycleConflictOrigin origin) {
        recordLifecycleConflict(level, runtime, ledger, site, position, reason, origin, "NOT_EVALUATED", "NOT_EVALUATED");
    }

    static void recordLifecycleConflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                        FrontierV3ResourceSiteLedger ledger, ResourceSite site, BlockPosition position,
                                        ResourceSiteConflictReason reason, FrontierV3ResourceSiteExecutor.LifecycleConflictOrigin origin,
                                        String admission) {
        recordLifecycleConflict(level, runtime, ledger, site, position, reason, origin, admission, "NOT_EVALUATED");
    }

    static void recordLifecycleConflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                        FrontierV3ResourceSiteLedger ledger, ResourceSite site, BlockPosition position,
                                        ResourceSiteConflictReason reason, FrontierV3ResourceSiteExecutor.LifecycleConflictOrigin origin,
                                        String admission, String claimState) {
        FrontierWorldState state = runtime.decodedState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site.id());
        FrontierV3ResourceSiteLedger.Claim claim = ledger.claim(site.id());
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState()
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        String originId = origin.name().toLowerCase(Locale.ROOT).replace('_', '-');
        CommandId id = new CommandId("executor:resource-site-conflict-" + originId + "-r" + checkpoint.revision().value()
                + "-p" + minecraft(position).asLong());
        CommandResult result = submitConflict(runtime, site, position, reason, lifecycleConflictSource(origin), id);
        if (!(result instanceof CommandResult.Accepted)) return;
        ledger.conflict(site.id());
        FrontierV3DiagnosticTrace.record(level.getServer(), incidentTrace(site), lifecycleConflictTraceKind(origin, lifecycle, claim, reason, admission, claimState), site.id(), result);
    }

    /** Package-visible formatter seam for the bounded causal incident contract. */
    static String lifecycleConflictTraceKind(FrontierV3ResourceSiteExecutor.LifecycleConflictOrigin origin,
                                             ResourceSiteLifecycle lifecycle, FrontierV3ResourceSiteLedger.Claim claim,
                                             ResourceSiteConflictReason reason) {
        return lifecycleConflictTraceKind(origin, lifecycle, claim, reason, "NOT_EVALUATED");
    }

    static String lifecycleConflictTraceKind(FrontierV3ResourceSiteExecutor.LifecycleConflictOrigin origin,
                                             ResourceSiteLifecycle lifecycle, FrontierV3ResourceSiteLedger.Claim claim,
                                             ResourceSiteConflictReason reason, String admission) {
        return lifecycleConflictTraceKind(origin, lifecycle, claim, reason, admission, "NOT_EVALUATED");
    }

    static String lifecycleConflictTraceKind(FrontierV3ResourceSiteExecutor.LifecycleConflictOrigin origin,
                                             ResourceSiteLifecycle lifecycle, FrontierV3ResourceSiteLedger.Claim claim,
                                             ResourceSiteConflictReason reason, String admission, String claimState) {
        String claimValue = claimValue(claim);
        return "resource_site_conflict:" + origin.name().toLowerCase(Locale.ROOT)
                + ":owner=resource-site-lifecycle:claim=" + claimValue
                + ":expected=" + lifecycle.phase().name().toLowerCase(Locale.ROOT)
                + "-e" + lifecycle.growthEpoch() + "-s" + lifecycle.growthStage()
                + ":observed=" + reason.name().toLowerCase(Locale.ROOT)
                + ":admission=" + admission.toLowerCase(Locale.ROOT)
                + ":claim_state=" + claimState.toLowerCase(Locale.ROOT)
                + ":disposition=terminal-repair-required";
    }

    /**
     * The stable facility tuple alone cannot explain a restart conflict while a bounded writer
     * is active. Retain its immutable transition facts without making that mutable cursor a
     * second ownership identity or a desired-state repair instruction.
     */
    private static String claimValue(FrontierV3ResourceSiteLedger.Claim claim) {
        if (claim == null) return "none";
        String value = claim.status().name().toLowerCase(Locale.ROOT) + "-s" + claim.stage() + "-h" + claim.harvestedCropSlots();
        FrontierV3ResourceSiteLedger.ProjectionTransition projection = claim.projection();
        if (projection == null) return value + "-pnone";
        return value + "-p" + projection.mode().name().toLowerCase(Locale.ROOT)
                + "-fs" + projection.fromStage() + "-fh" + projection.fromHarvestedCropSlots()
                + "-ts" + projection.targetStage() + "-th" + projection.targetHarvestedCropSlots()
                + "-n" + projection.nextWrite() + "-c" + projection.writeCount()
                + "-src" + traceValue(projection.source());
    }

    private static String traceValue(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /** Restart observation is an ambiguity boundary, while a live growth mismatch is an invariant failure. */
    static ResourceSiteConflictSource lifecycleConflictSource(FrontierV3ResourceSiteExecutor.LifecycleConflictOrigin origin) {
        return switch (origin) {
            case ORDINARY_GROWTH -> ResourceSiteConflictSource.LIFECYCLE_RECONCILIATION;
            case RESTART_RECONCILIATION -> ResourceSiteConflictSource.RESTART_RECONCILIATION;
        };
    }

    static boolean recordPlayerConflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                        FrontierV3ResourceSiteLedger ledger, ResourceSite site, BlockPosition position, String cause) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState()
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId id = new CommandId("executor:resource-site-conflict-r" + checkpoint.revision().value() + "-p" + minecraft(position).asLong());
        CommandResult result = submitConflict(runtime, site, position, ResourceSiteConflictReason.PLAYER_REMOVED_MANAGED_CELL,
                ResourceSiteConflictSource.PLAYER_WORLD_OBSERVATION, id);
        if (result instanceof CommandResult.Accepted) {
            ledger.conflict(site.id());
            FrontierV3DiagnosticTrace.record(level.getServer(), incidentTrace(site), "resource_site_conflict:player_world_observation", site.id(), result);
        } else if (result instanceof CommandResult.Rejected rejected) {
            PaleMirrorMod.LOGGER.info("PMV3_PLAYER_BREAK resource-disposition={} detail={}", rejected.rejection().code(), rejected.rejection().detail());
        }
        return result instanceof CommandResult.Accepted;
    }

    private static CommandResult submitConflict(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ResourceSite site,
                                                BlockPosition position, ResourceSiteConflictReason reason,
                                                ResourceSiteConflictSource source, CommandId id) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState()
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        return runtime.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id),
                new ResourceSiteConflictObserved(site.id(), position, reason, source)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
    }

    private static String incidentTrace(ResourceSite site) {
        return "conflict:incident:resource-site:" + site.id().value().substring("site:".length());
    }

    private static BlockPos minecraft(BlockPosition position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }
}
