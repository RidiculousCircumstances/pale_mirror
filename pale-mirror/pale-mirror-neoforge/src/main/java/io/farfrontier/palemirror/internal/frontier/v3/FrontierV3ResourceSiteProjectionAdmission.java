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
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteConflictReason;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestLineage;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgress;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePreparationObservation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ResourceSiteLedger.ProjectionMode;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ResourceSiteExecutor.*;

/** Exact physical and canonical admission rules for bounded field projection. */
final class FrontierV3ResourceSiteProjectionAdmission {
    private FrontierV3ResourceSiteProjectionAdmission() { }

    static String projectionSource(FrontierWorldState state, ResourceSite site) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site.id());
        return lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .map(job -> job.id().value()).or(() -> lifecycle.harvestLineage().filter(ResourceSiteHarvestLineage::receiptPending)
                        .map(lineage -> lineage.predecessorJobId().value()))
                .orElse("growth:" + site.id().value() + ":e" + lifecycle.growthEpoch());
    }
    static List<FieldWrite> transitionWrites(ResourceSite site, FrontierV3ResourceSiteLedger.Claim claim,
                                                     int desiredStage, int completedCropSlots, boolean successorRegrowth) {
        List<FieldWrite> writes = new java.util.ArrayList<>();
        if (claim.stage() != desiredStage) {
            for (int index = 0; index < site.cropSlots().size(); index++) writes.add(new FieldWrite(site.cropSlots().get(index),
                    desiredStage == ResourceSiteLifecycle.MATURE_STAGE && index < completedCropSlots ? crop(0) : crop(desiredStage)));
        } else if (desiredStage == ResourceSiteLifecycle.MATURE_STAGE) {
            if (successorRegrowth) {
                int restore = successorRegrowthRestoreSlots(claim.harvestedCropSlots(), completedCropSlots);
                for (int index = claim.harvestedCropSlots() - 1; index >= claim.harvestedCropSlots() - restore; index--) {
                    writes.add(new FieldWrite(site.cropSlots().get(index), crop(ResourceSiteLifecycle.MATURE_STAGE)));
                }
            } else for (int index = claim.harvestedCropSlots(); index < completedCropSlots; index++)
                writes.add(new FieldWrite(site.cropSlots().get(index), crop(0)));
        }
        return List.copyOf(writes);
    }
    static int successorRegrowthRestoreSlots(int predecessorHarvestedCropSlots, int successorCompletedCropSlots) {
        if (predecessorHarvestedCropSlots < 0 || predecessorHarvestedCropSlots > ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS
                || successorCompletedCropSlots < 0 || successorCompletedCropSlots > predecessorHarvestedCropSlots) {
            throw new IllegalArgumentException("successor regrowth cursor is invalid");
        }
        return predecessorHarvestedCropSlots - successorCompletedCropSlots;
    }
    /** Same-stage mature regrowth must restore the old seedling prefix, not perform a zero-write advance. */
    static boolean restoresMaturePredecessor(FrontierV3ResourceSiteLedger.Claim claim, int desiredStage,
                                             int completedCropSlots, boolean admittedRegrowth) {
        return admittedRegrowth && claim.stage() == ResourceSiteLifecycle.MATURE_STAGE
                && desiredStage == ResourceSiteLifecycle.MATURE_STAGE && claim.harvestedCropSlots() > completedCropSlots;
    }

    static boolean awaitingHarvest(ResourceSiteLifecycle lifecycle) {
        return lifecycle.phase() == ResourceSitePhase.GROWING || lifecycle.phase() == ResourceSitePhase.READY;
    }
    static boolean resetsTerminalHarvestClaim(FrontierV3ResourceSiteLedger.Claim claim, int completedCropSlots,
                                              boolean successorRegrowth, boolean confirmedHarvestRegrowth) {
        return claim.stage() == ResourceSiteLifecycle.MATURE_STAGE
                && claim.harvestedCropSlots() == ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS
                && claim.harvestedCropSlots() > completedCropSlots
                && (successorRegrowth || confirmedHarvestRegrowth);
    }
    static boolean allowsOwnedStageCatchUp(FrontierV3ResourceSiteLedger.Claim claim, int desiredStage,
                                           int completedCropSlots) {
        return claim != null && claim.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE
                && claim.harvestedCropSlots() == 0 && completedCropSlots == 0
                && claim.stage() >= 0 && claim.stage() < desiredStage
                && desiredStage <= ResourceSiteLifecycle.MATURE_STAGE;
    }

    /**
     * Re-entry may rebuild a missing physical ownership witness only for a complete current
     * surface whose exact terminal lineage still names its canonical successor.  This is not
     * a permissive recovery of a damaged field: callers must provide an exact whole-surface
     * observation and a composed successor relation.
     */
    static boolean allowsComposedTerminalLedgerRehydration(ResourceSiteLifecycle lifecycle, int desiredStage,
                                                            int completedCropSlots, boolean exactCurrentSurface,
                                                            boolean composedCanonicalSuccessor) {
        if (lifecycle == null || completedCropSlots != 0 || desiredStage != lifecycle.growthStage()
                || !exactCurrentSurface || !composedCanonicalSuccessor) return false;
        if (lifecycle.phase() != ResourceSitePhase.GROWING && lifecycle.phase() != ResourceSitePhase.READY) return false;
        return lifecycle.harvestLineage().map(lineage -> lineage.outputReceiptResolved()
                && lineage.completedGrowthEpoch() + 1L == lifecycle.growthEpoch()).orElse(false);
    }
    /**
     * The restart dispatcher must not route an exact composed terminal predecessor through
     * the legacy preparation-intent reconciler: that reconciler correctly rejects a missing
     * claim, whereas this narrower case owns an already-confirmed successor and its complete
     * physical predecessor surface.  Keep the absent-claim condition explicit so no active
     * or damaged field can borrow this recovery authority.
     */
    static boolean admitsMissingComposedTerminalRehydration(FrontierV3ResourceSiteLedger.Claim claim,
                                                             ResourceSiteLifecycle lifecycle, int desiredStage,
                                                             int completedCropSlots, boolean exactCurrentSurface,
                                                             boolean composedCanonicalSuccessor) {
        return claim == null && allowsComposedTerminalLedgerRehydration(lifecycle, desiredStage, completedCropSlots,
                exactCurrentSurface, composedCanonicalSuccessor);
    }
    static boolean admitsMissingComposedGrowthPredecessor(FrontierV3ResourceSiteLedger.Claim claim,
                                                           ResourceSiteLifecycle lifecycle, int desiredStage,
                                                           int completedCropSlots, int predecessorStage,
                                                           boolean exactPredecessorSurface,
                                                           boolean composedCanonicalSuccessor) {
        return claim == null && predecessorStage >= 0 && predecessorStage < desiredStage
                && allowsComposedTerminalLedgerRehydration(lifecycle, desiredStage, completedCropSlots,
                exactPredecessorSurface, composedCanonicalSuccessor);
    }
    /**
     * Finds only a whole physical growth stage strictly preceding the desired successor
     * stage.  A mixed crop surface is deliberately not normalized: it is an unknown write and
     * remains a local conflict.  Walking down from the desired stage preserves the nearest
     * exact predecessor if COLD advanced more than one canonical stage before re-entry.
     */
    static int exactComposedGrowthPredecessorStage(ServerLevel level, ResourceSite site, int desiredStage) {
        for (int stage = desiredStage - 1; stage >= 0; stage--) {
            if (matches(level, site, stage)) return stage;
        }
        return -1;
    }
    static String claimPhysicalState(ServerLevel level, FrontierV3ResourceSiteLedger ledger, ResourceSite site, FrontierV3ResourceSiteLedger.Claim claim) {
        String physical;
        if (claim == null) physical = "MISSING_CLAIM";
        else if (matchesClaim(level, site, claim)) physical = "EXACT_CLAIM";
        else if (baseline(level, site)) physical = "NEUTRAL_BASELINE";
        else if (matchesHarvestProgress(level, site, ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS)) physical = "TERMINAL_HARVEST_RECEIPT";
        else {
            physical = null;
            for (int stage = 0; stage <= ResourceSiteLifecycle.MATURE_STAGE; stage++) {
                if (matches(level, site, stage)) { physical = "COMPLETE_STAGE_" + stage; break; }
            }
            if (physical == null) physical = "FOREIGN_OR_DAMAGED_" + cropSurface(level, site);
        }
        FrontierV3ResourceSiteLedger.NativeGrowthFence fence = ledger.nativeGrowthFence(site.id());
        return physical + "_NATIVE_FENCE_" + (fence == null ? "NOT_OBSERVED" : fence.source() + "_A" + fence.observedAge()
                + "_S" + fence.claimStage() + "_P" + fence.position().x() + "_" + fence.position().y() + "_" + fence.position().z());
    }
    static String cropSurface(ServerLevel level, ResourceSite site) {
        if (!matchesInfrastructure(level, site)) return "INFRASTRUCTURE_MISMATCH";
        int[] ages = new int[ResourceSiteLifecycle.MATURE_STAGE + 1]; int air = 0; int other = 0;
        for (BlockPosition slot : site.cropSlots()) {
            BlockState observed = level.getBlockState(minecraft(slot));
            if (observed.isAir()) air++;
            else if (observed.is(Blocks.WHEAT)) ages[observed.getValue(CropBlock.AGE)]++;
            else other++;
        }
        StringBuilder surface = new StringBuilder("CROPS");
        for (int age = 0; age < ages.length; age++) surface.append("_A").append(age).append('_').append(ages[age]);
        return surface.append("_AIR_").append(air).append("_OTHER_").append(other).toString();
    }
    static boolean isExactSuccessorRegrowth(ServerLevel level, FrontierWorldState state, ResourceSite site, int desiredStage,
                                                    int completedCropSlots, FrontierV3ResourceSiteLedger.Claim claim) {
        if (claim == null || desiredStage != ResourceSiteLifecycle.MATURE_STAGE
                || claim.stage() != ResourceSiteLifecycle.MATURE_STAGE
                || claim.harvestedCropSlots() <= completedCropSlots
                || !matchesClaim(level, site, claim)) return false;
        // A canonical successor authorizes a transition of its exact owned predecessor,
        // never an overwrite of arbitrary blocks. Validate before the first batch too;
        // persisted-prefix validation only protects subsequent batches/recovery.
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site.id());
        return lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast)
                .map(job -> job.progress().completedCropSlots() == completedCropSlots).orElse(false);
    }
    static boolean isExactTerminalPredecessor(ServerLevel level, FrontierWorldState state, ResourceSite site,
                                                      int desiredStage, int completedCropSlots, FrontierV3ResourceSiteLedger.Claim claim) {
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || desiredStage != ResourceSiteLifecycle.MATURE_STAGE || completedCropSlots <= 0
                || !matchesHarvestProgress(level, site, ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS)) return false;
        return state.resourceSites().site(site.id()).activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast)
                .map(job -> job.progress().completedCropSlots() == completedCropSlots).orElse(false);
    }
    static boolean isExactConfirmedHarvestRegrowth(ServerLevel level, FrontierWorldState state, ResourceSite site,
                                                           int desiredStage, int completedCropSlots,
                                                           FrontierV3ResourceSiteLedger.Claim claim) {
        return confirmedHarvestRegrowthAdmission(level, state, site, desiredStage, completedCropSlots, claim)
                == ConfirmedHarvestRegrowthAdmission.ADMITTED;
    }
    /**
     * A COLD terminal has already atomically composed its exact output and retired its
     * PREPARED intent.  The durable owned field can still be the older HOT prefix: after a
     * restart it is lawful to replace that exact prefix with the current growth stage, but only
     * when the immediately preceding lineage proves that canonical terminal hand-off.
     */
    static boolean isExactComposedTerminalRegrowth(ResourceSiteLifecycle lifecycle, int desiredStage,
                                                    int completedCropSlots, FrontierV3ResourceSiteLedger.Claim claim,
                                                    boolean exactPhysicalPredecessor) {
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || claim.stage() != ResourceSiteLifecycle.MATURE_STAGE || claim.harvestedCropSlots() <= 0
                || completedCropSlots != 0 || !exactPhysicalPredecessor) return false;
        return awaitingHarvest(lifecycle) && desiredStage == lifecycle.growthStage()
                && lifecycle.harvestLineage().map(ResourceSiteHarvestLineage::outputReceiptResolved).orElse(false);
    }
    static boolean isExactDeferredHarvestReceipt(ServerLevel level, FrontierWorldState state, ResourceSite site,
                                                          int desiredStage, int completedCropSlots,
                                                          FrontierV3ResourceSiteLedger.Claim claim) {
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || claim.stage() != ResourceSiteLifecycle.MATURE_STAGE
                || claim.harvestedCropSlots() != ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS
                || !matchesHarvestProgress(level, site, ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS)) return false;
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site.id());
        return matchesCanonicalProjectionTarget(lifecycle, desiredStage, completedCropSlots)
                && lifecycle.harvestLineage().filter(ResourceSiteHarvestLineage::receiptPending)
                .filter(lineage -> !lineage.composedIntoCanonicalSuccessor(state)).isPresent();
    }
    /** Current projection demand does not retire an earlier unresolved physical receipt. */
    static boolean matchesCanonicalProjectionTarget(ResourceSiteLifecycle lifecycle, int desiredStage, int completedCropSlots) {
        if (lifecycle == null || desiredStage != lifecycle.growthStage()) return false;
        if (awaitingHarvest(lifecycle)) return completedCropSlots == 0;
        return lifecycle.phase() == ResourceSitePhase.HARVESTING && lifecycle.activeWork()
                .filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .map(job -> job.progress().completedCropSlots() == completedCropSlots).orElse(false);
    }
    static ConfirmedHarvestRegrowthAdmission confirmedHarvestRegrowthAdmission(ServerLevel level, FrontierWorldState state,
                                                                                         ResourceSite site, int desiredStage,
                                                                                         int completedCropSlots,
                                                                                         FrontierV3ResourceSiteLedger.Claim claim) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site.id());
        boolean confirmedReceipt = hasConfirmedHarvestReceipt(state, site.id());
        return classifyConfirmedHarvestRegrowth(claim, lifecycle, desiredStage, completedCropSlots,
                matchesHarvestProgress(level, site, ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS), confirmedReceipt);
    }
    static boolean hasConfirmedHarvestReceipt(FrontierWorldState state, SubjectId siteId) {
        return state.physicalIntents().values().stream().anyMatch(intent -> intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST
                && intent.status() == PhysicalIntentStatus.CONFIRMED && intent.causeSubjectId().equals(siteId));
    }
    static ConfirmedHarvestRegrowthAdmission classifyConfirmedHarvestRegrowth(FrontierV3ResourceSiteLedger.Claim claim,
                                                                                ResourceSiteLifecycle lifecycle,
                                                                                int desiredStage, int completedCropSlots,
                                                                                boolean exactTerminalPhysicalReceipt,
                                                                                boolean confirmedHarvestReceipt) {
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                || claim.stage() != ResourceSiteLifecycle.MATURE_STAGE
                || claim.harvestedCropSlots() != ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS) {
            return ConfirmedHarvestRegrowthAdmission.MISSING_ACTIVE_TERMINAL_CLAIM;
        }
        if (!awaitingHarvest(lifecycle) || desiredStage != lifecycle.growthStage() || completedCropSlots != 0) {
            return ConfirmedHarvestRegrowthAdmission.LIFECYCLE_NOT_INITIAL_GROWTH;
        }
        if (!exactTerminalPhysicalReceipt) return ConfirmedHarvestRegrowthAdmission.PHYSICAL_RECEIPT_MISMATCH;
        return confirmedHarvestReceipt ? ConfirmedHarvestRegrowthAdmission.ADMITTED
                : ConfirmedHarvestRegrowthAdmission.MISSING_CONFIRMED_RECEIPT;
    }
    static boolean isExactInterruptedStageZeroProjection(ServerLevel level, FrontierWorldState state, ResourceSite site,
                                                                 int desiredStage, int completedCropSlots,
                                                                 FrontierV3ResourceSiteLedger.Claim claim) {
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE || claim.stage() != 0
                || desiredStage != ResourceSiteLifecycle.MATURE_STAGE || completedCropSlots <= 0
                || !matchesInfrastructure(level, site)) return false;
        if (!matches(level, site, 0)) return false;
        return state.resourceSites().site(site.id()).activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast)
                .map(job -> job.progress().completedCropSlots() == completedCropSlots).orElse(false);
    }
    static boolean isExactUncommittedCurrentHarvest(ServerLevel level, FrontierWorldState state, ResourceSite site,
                                                            int desiredStage, int completedCropSlots,
                                                            FrontierV3ResourceSiteLedger.Claim claim) {
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE
                ) return false;
        return exactCurrentHarvestJob(level, state, site, desiredStage, completedCropSlots).isPresent();
    }
    static Optional<ResourceSiteHarvestJob> exactCurrentHarvestJob(ServerLevel level, FrontierWorldState state, ResourceSite site,
                                                                             int desiredStage, int completedCropSlots) {
        if (desiredStage != ResourceSiteLifecycle.MATURE_STAGE || completedCropSlots <= 0
                || !matchesHarvestProgress(level, site, completedCropSlots)) return Optional.empty();
        return namedCurrentHarvestJob(state, site, desiredStage, completedCropSlots);
    }
    static Optional<ResourceSiteHarvestJob> namedCurrentHarvestJob(FrontierWorldState state, ResourceSite site,
                                                                            int desiredStage, int completedCropSlots) {
        if (desiredStage != ResourceSiteLifecycle.MATURE_STAGE || completedCropSlots <= 0) return Optional.empty();
        return state.resourceSites().site(site.id()).activeWork().filter(ResourceSiteHarvestJob.class::isInstance)
                .map(ResourceSiteHarvestJob.class::cast)
                .filter(job -> job.progress().completedCropSlots() == completedCropSlots);
    }
    static Optional<ResourceSiteHarvestJob> exactUnmaterializedColdHarvest(ServerLevel level, FrontierWorldState state,
                                                                                     ResourceSite site, int desiredStage,
                                                                                     int completedCropSlots) {
        if (!blankManagedSurface(level, site)) return Optional.empty();
        return namedCurrentHarvestJob(state, site, desiredStage, completedCropSlots).filter(job -> {
            PhysicalIntent intent = state.physicalIntents().get(job.intentId());
            return intent != null && intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST
                    && intent.status() == PhysicalIntentStatus.PREPARED && intent.causeSubjectId().equals(site.id())
                    && intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.siteHarvest(site.id(), job.id(), job.workerId(),
                    job.actorAccountId(), job.depotAccountId()));
        });
    }
    static boolean blankManagedSurface(ServerLevel level, ResourceSite site) {
        return loaded(level, site) && site.managedSlots().stream().allMatch(slot -> level.getBlockState(minecraft(slot)).isAir());
    }
}
