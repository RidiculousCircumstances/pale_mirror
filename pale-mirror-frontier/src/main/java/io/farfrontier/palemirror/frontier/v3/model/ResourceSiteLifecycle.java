package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Mutable canonical facts for one fixed resource site, excluding Minecraft blocks and item custody. */
public record ResourceSiteLifecycle(SubjectId siteId, ResourceSitePhase phase, long growthEpoch, int growthStage,
                                    Optional<ResourceSiteWork> activeWork) {
    public static final int MATURE_STAGE = 7;

    public ResourceSiteLifecycle {
        Objects.requireNonNull(siteId, "resource-site lifecycle id"); Objects.requireNonNull(phase, "resource-site phase");
        activeWork = Optional.ofNullable(activeWork).orElse(Optional.empty());
        if (!siteId.value().startsWith("site:") || growthEpoch < 0L || growthStage < 0 || growthStage > MATURE_STAGE) {
            throw new IllegalArgumentException("resource-site lifecycle value is invalid");
        }
        activeWork.ifPresent(work -> {
            if (!siteId.equals(work.siteId())) throw new IllegalArgumentException("resource-site work must belong to its lifecycle site");
        });
        switch (phase) {
            case UNPREPARED -> {
                if (growthEpoch != 0L || growthStage != 0 || activeWork.filter(ResourceSitePreparationJob.class::isInstance).isEmpty() && activeWork.isPresent()) {
                    throw new IllegalArgumentException("unprepared resource site may retain only its initial preparation work");
                }
            }
            case GROWING -> {
                if (growthEpoch == 0L || growthStage >= MATURE_STAGE || activeWork.isPresent()) throw new IllegalArgumentException("growing resource site state is invalid");
            }
            case READY -> {
                if (growthEpoch == 0L || growthStage != MATURE_STAGE || activeWork.isPresent()) throw new IllegalArgumentException("ready resource site state is invalid");
            }
            case HARVESTING -> {
                if (growthEpoch == 0L || growthStage != MATURE_STAGE || activeWork.filter(ResourceSiteHarvestJob.class::isInstance).isEmpty()) {
                    throw new IllegalArgumentException("harvesting resource site must retain one mature harvest job");
                }
            }
            case CONFLICT, DESTROYED -> {
                if (activeWork.isPresent()) throw new IllegalArgumentException("terminal resource-site condition cannot retain active work");
            }
        }
    }

    public static ResourceSiteLifecycle unprepared(SubjectId siteId) { return new ResourceSiteLifecycle(siteId, ResourceSitePhase.UNPREPARED, 0L, 0, Optional.empty()); }
    public ResourceSiteLifecycle preparing(ResourceSitePreparationJob job) {
        if (phase != ResourceSitePhase.UNPREPARED || activeWork.isPresent()) throw new IllegalStateException("resource site is not available for preparation");
        return new ResourceSiteLifecycle(siteId, phase, growthEpoch, growthStage, Optional.of(job));
    }
    public ResourceSiteLifecycle prepared() {
        if (phase != ResourceSitePhase.UNPREPARED || activeWork.filter(ResourceSitePreparationJob.class::isInstance).isEmpty()) throw new IllegalStateException("resource site has no active preparation");
        return new ResourceSiteLifecycle(siteId, ResourceSitePhase.GROWING, 1L, 0, Optional.empty());
    }
    public ResourceSiteLifecycle advanceGrowth() {
        if (phase != ResourceSitePhase.GROWING) throw new IllegalStateException("resource site is not growing");
        int nextStage = Math.addExact(growthStage, 1);
        return new ResourceSiteLifecycle(siteId, nextStage == MATURE_STAGE ? ResourceSitePhase.READY : ResourceSitePhase.GROWING,
                growthEpoch, nextStage, Optional.empty());
    }
    public ResourceSiteLifecycle harvesting(ResourceSiteHarvestJob job) {
        if (phase != ResourceSitePhase.READY) throw new IllegalStateException("resource site is not ready for harvest");
        return new ResourceSiteLifecycle(siteId, ResourceSitePhase.HARVESTING, growthEpoch, growthStage, Optional.of(job));
    }
    public ResourceSiteLifecycle harvested() {
        if (phase != ResourceSitePhase.HARVESTING) throw new IllegalStateException("resource site has no active harvest");
        return new ResourceSiteLifecycle(siteId, ResourceSitePhase.GROWING, Math.addExact(growthEpoch, 1L), 0, Optional.empty());
    }
    public ResourceSiteLifecycle conflicted() {
        if (phase == ResourceSitePhase.DESTROYED) throw new IllegalStateException("destroyed resource site cannot become a conflict");
        return new ResourceSiteLifecycle(siteId, ResourceSitePhase.CONFLICT, growthEpoch, growthStage, Optional.empty());
    }
    public ResourceSiteLifecycle destroyed() { return new ResourceSiteLifecycle(siteId, ResourceSitePhase.DESTROYED, growthEpoch, growthStage, Optional.empty()); }
}
