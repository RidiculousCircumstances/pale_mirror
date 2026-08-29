package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Observes a real blast against an active field before reducing it to one durable site conflict. */
final class FrontierV3ResourceSiteExplosionExecutor {
    private static final int MAX_RECONCILIATIONS_PER_TICK = 4;

    private FrontierV3ResourceSiteExplosionExecutor() { }

    static boolean captureExternal(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, List<BlockPos> affected) {
        return capture(level, runtime, affected, Optional.empty());
    }

    static boolean captureManaged(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId intentId,
                                  List<BlockPos> affected) {
        return capture(level, runtime, affected, Optional.of(intentId));
    }

    private static boolean capture(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, List<BlockPos> affected,
                                   Optional<PhysicalIntentId> managedIntent) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return false;
        Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ResourceSite> sites = FrontierResourceSitePlan.compile(state.bootstrap());
        FrontierV3ResourceSiteExplosionLedger ledger = FrontierV3ResourceSiteExplosionLedger.get(level);
        return managedIntent.map(intent -> ledger.captureManaged(level, level.getGameTime(), intent, affected, List.copyOf(sites.values()),
                        FrontierV3ResourceSiteLedger.get(level)))
                .orElseGet(() -> ledger.captureExternal(level, level.getGameTime(), affected, List.copyOf(sites.values()), FrontierV3ResourceSiteLedger.get(level)));
    }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierV3ResourceSiteExplosionLedger ledger = FrontierV3ResourceSiteExplosionLedger.get(level);
        for (int count = 0; count < MAX_RECONCILIATIONS_PER_TICK; count++) {
            Optional<FrontierV3ResourceSiteExplosionLedger.Ready> ready = ledger.nextReady(level.getGameTime());
            if (ready.isEmpty()) return;
            FrontierV3ResourceSiteExplosionLedger.Ready value = ready.orElseThrow(); FrontierWorldState state = runtime.decodedState().orElse(null);
            if (state == null) return;
            ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(value.candidate().siteId());
            if (site == null) throw new IllegalStateException("resource-site explosion references an unknown canonical site");
            BlockPos witness = new BlockPos(value.candidate().witness().x(), value.candidate().witness().y(), value.candidate().witness().z());
            if (!FrontierV3ResourceSiteExecutor.contains(site, witness)) throw new IllegalStateException("resource-site explosion witness is outside its canonical field");
            if (!FrontierV3ResourceSiteExecutor.loaded(level, site)) return;
            FrontierV3ResourceSiteLedger.Claim claim = FrontierV3ResourceSiteLedger.get(level).claim(site.id());
            if (claim == null || claim.status() == FrontierV3ResourceSiteLedger.Status.CONFLICT
                    || state.resourceSites().site(site.id()).phase() == io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase.CONFLICT
                    || state.resourceSites().site(site.id()).phase() == io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase.DESTROYED) {
                ledger.resolve(value); continue;
            }
            if (FrontierV3ResourceSiteExecutor.matches(level, site, value.candidate().expectedStage())) { ledger.resolve(value); continue; }
            CommandId command = FrontierV3CommandIds.resourceSiteExplosionConflict(value.effectId(), site.id());
            if (!FrontierV3ResourceSiteExecutor.recordConflict(runtime, FrontierV3ResourceSiteLedger.get(level), site,
                    value.candidate().witness(), "explosion:" + value.effectId(), command)) return;
            ledger.resolve(value);
        }
    }

    /** Resource-site effects have their own site-level reconciliation and must not also become generic scars. */
    static Set<Long> activeOwnedCells(ServerLevel level, FrontierWorldState state) {
        FrontierV3ResourceSiteLedger claims = FrontierV3ResourceSiteLedger.get(level);
        return FrontierResourceSitePlan.compile(state.bootstrap()).values().stream().filter(site -> {
            FrontierV3ResourceSiteLedger.Claim claim = claims.claim(site.id());
            return claim != null && claim.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE
                    && FrontierV3ResourceSiteExecutor.loaded(level, site) && FrontierV3ResourceSiteExecutor.matches(level, site, claim.stage());
        }).flatMap(site -> java.util.stream.Stream.concat(site.soilSlots().stream(), site.cropSlots().stream()))
                .map(position -> new BlockPos(position.x(), position.y(), position.z()).asLong()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
