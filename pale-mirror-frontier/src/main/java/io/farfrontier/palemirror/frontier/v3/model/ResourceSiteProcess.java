package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.List;

/** Advances a confirmed prepared field by COLD server time; it neither inspects nor changes Minecraft. */
final class ResourceSiteProcess {
    static final long WHEAT_STAGE_INTERVAL = 3_000L;
    private ResourceSiteProcess() { }

    static ScheduledAction nextGrowth(ResourceSiteLifecycle lifecycle, long dueAt) {
        if (lifecycle.phase() != ResourceSitePhase.GROWING) throw new IllegalArgumentException("only growing resource sites can schedule growth");
        return new ScheduledAction(new ScheduleId("schedule:resource-site-growth-" + lifecycle.siteId().value().substring("site:".length())
                + "-" + lifecycle.growthEpoch() + "-" + lifecycle.growthStage()), new SimInstant(dueAt), 0, lifecycle.siteId(),
                "frontier.resource_site.growth", 1);
    }

    static List<ProposedEvent> planGrowth(FrontierWorldState state, ScheduledAction action) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(action.subject());
        if (lifecycle.phase() != ResourceSitePhase.GROWING || !action.id().equals(nextGrowth(lifecycle, action.dueAt().ticks()).id())) return List.of();
        ResourceSiteGrowthAdvanced advanced = new ResourceSiteGrowthAdvanced(lifecycle.siteId(), lifecycle.growthEpoch(), lifecycle.growthStage());
        ResourceSiteLifecycle next = lifecycle.advanceGrowth();
        if (next.phase() == ResourceSitePhase.READY) return List.of(new ProposedEvent(lifecycle.siteId(), advanced));
        return List.of(new ProposedEvent(lifecycle.siteId(), advanced), new ProposedEvent(lifecycle.siteId(), new ScheduleEffect.Created(nextGrowth(next,
                Math.addExact(action.dueAt().ticks(), WHEAT_STAGE_INTERVAL)))));
    }

    static FrontierWorldState reduceGrowth(FrontierWorldState state, SubjectId subject, ResourceSiteGrowthAdvanced advanced) {
        if (!subject.equals(advanced.siteId())) throw new IllegalArgumentException("resource-site growth has a foreign event owner");
        ResourceSiteLifecycle current = state.resourceSites().site(advanced.siteId());
        if (current.phase() != ResourceSitePhase.GROWING || current.growthEpoch() != advanced.growthEpoch() || current.growthStage() != advanced.growthStage()) {
            throw new IllegalArgumentException("resource-site growth event is stale or invalid");
        }
        return state.withResourceSites(state.resourceSites().replace(current.advanceGrowth()));
    }
}
