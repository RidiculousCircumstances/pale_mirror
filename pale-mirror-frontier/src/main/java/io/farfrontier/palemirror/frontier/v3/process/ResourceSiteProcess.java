package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.List;

/** Advances a canonical field by COLD server time; projection never gates its food economy. */
public final class ResourceSiteProcess {
    /** The first canonical preparation occurs without requiring a loaded Minecraft chunk. */
    public static final long INITIAL_PREPARATION_TICK = 1L;
    public static final long WHEAT_STAGE_INTERVAL = 3_000L;
    public static final String PREPARATION_ACTION = "frontier.resource_site.prepare";
    private ResourceSiteProcess() { }

    public static ScheduledAction nextGrowth(ResourceSiteLifecycle lifecycle, long dueAt) {
        if (lifecycle.phase() != ResourceSitePhase.GROWING) throw new IllegalArgumentException("only growing resource sites can schedule growth");
        return new ScheduledAction(new ScheduleId("schedule:resource-site-growth-" + lifecycle.siteId().value().substring("site:".length())
                + "-" + lifecycle.growthEpoch() + "-" + lifecycle.growthStage()), new SimInstant(dueAt), 0, lifecycle.siteId(),
                "frontier.resource_site.growth", 1);
    }

    public static ScheduledAction preparation(SubjectId siteId, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:resource-site-prepare-" + siteId.value().substring("site:".length())), new SimInstant(dueAt), 0,
                siteId, PREPARATION_ACTION, 1);
    }

    public static List<ProposedEvent> planPreparation(FrontierWorldState state, ScheduledAction action) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(action.subject());
        if (lifecycle.phase() != ResourceSitePhase.UNPREPARED || lifecycle.activeWork().isPresent() || !action.id().equals(preparation(lifecycle.siteId(), action.dueAt().ticks()).id())) return List.of();
        String suffix = lifecycle.siteId().value().substring("site:".length()); ResourceSitePreparationJob job = new ResourceSitePreparationJob(
                new SubjectId("job:site-prepare-" + suffix), lifecycle.siteId(), new PhysicalIntentId("intent:site-prepare-" + suffix));
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(lifecycle.siteId()); BlockPosition origin = site.cropSlots().getFirst();
        ResourceSiteLifecycle prepared = lifecycle.preparing(job).prepared();
        return List.of(new ProposedEvent(lifecycle.siteId(), new ResourceSitePreparationStarted(job)), new ProposedEvent(lifecycle.siteId(), new ResourceSitePrepared(job)),
                new ProposedEvent(lifecycle.siteId(), new ScheduleEffect.Created(nextGrowth(prepared, Math.addExact(action.dueAt().ticks(), WHEAT_STAGE_INTERVAL)))));
    }

    public static FrontierWorldState reducePreparationStarted(FrontierWorldState state, SubjectId subject, ResourceSitePreparationStarted started) {
        ResourceSitePreparationJob job = started.job();
        if (!subject.equals(job.siteId())) throw new IllegalArgumentException("resource-site preparation has a foreign event owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
        return state.withResourceSites(state.resourceSites().replace(lifecycle.preparing(job)));
    }

    public static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, ResourceSitePrepared prepared) {
        ResourceSitePreparationJob job = prepared.job();
        if (!subject.equals(job.siteId())) throw new IllegalArgumentException("resource-site preparation completion has a foreign event owner");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
        ResourceSitePreparationJob active = lifecycle.activeWork().filter(ResourceSitePreparationJob.class::isInstance).map(ResourceSitePreparationJob.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("resource-site preparation completion has no active work"));
        if (!active.equals(job)) throw new IllegalArgumentException("resource-site preparation completion does not match active work");
        return state.withResourceSites(state.resourceSites().replace(lifecycle.prepared()));
    }

    public static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_PREPARATION || !subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("resource-site preparation intent is invalid");
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId());
        ResourceSitePreparationJob job = lifecycle.activeWork().filter(ResourceSitePreparationJob.class::isInstance).map(ResourceSitePreparationJob.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("resource-site preparation lacks active work"));
        if (!intent.id().equals(job.intentId()) || !intent.subjectIds().equals(List.of(job.siteId(), job.id())) || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED) {
            throw new IllegalArgumentException("resource-site preparation intent does not bind its active work");
        }
        return state.preparePhysicalIntent(intent);
    }

    public static List<ProposedEvent> planPreparationTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition, long now) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId());
        if (lifecycle.phase() == ResourceSitePhase.DESTROYED) {
            if (transition.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) throw new IllegalArgumentException("destroyed resource site can only retain unknown preparation evidence");
            return List.of(new ProposedEvent(lifecycle.siteId(), transition));
        }
        if (lifecycle.activeWork().filter(ResourceSitePreparationJob.class::isInstance).map(ResourceSitePreparationJob.class::cast)
                .filter(job -> job.intentId().equals(intent.id())).isEmpty()) throw new IllegalArgumentException("resource-site preparation transition has no active work");
        if (transition.status() == PhysicalIntentStatus.CONFIRMED) {
            return List.of(new ProposedEvent(lifecycle.siteId(), transition), new ProposedEvent(lifecycle.siteId(), new ScheduleEffect.Created(nextGrowth(lifecycle.prepared(), Math.addExact(now, WHEAT_STAGE_INTERVAL)))));
        }
        return List.of(new ProposedEvent(lifecycle.siteId(), transition));
    }

    public static List<ProposedEvent> planGrowth(FrontierWorldState state, ScheduledAction action) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(action.subject());
        if (lifecycle.phase() != ResourceSitePhase.GROWING || !action.id().equals(nextGrowth(lifecycle, action.dueAt().ticks()).id())) return List.of();
        ResourceSiteGrowthAdvanced advanced = new ResourceSiteGrowthAdvanced(lifecycle.siteId(), lifecycle.growthEpoch(), lifecycle.growthStage());
        ResourceSiteLifecycle next = lifecycle.advanceGrowth();
        if (next.phase() == ResourceSitePhase.READY) return List.of(new ProposedEvent(lifecycle.siteId(), advanced), new ProposedEvent(lifecycle.siteId(),
                new ScheduleEffect.Created(StrategicObjectiveProcess.resourceHarvestOpportunity(state, next, Math.addExact(action.dueAt().ticks(), 1L)))));
        return List.of(new ProposedEvent(lifecycle.siteId(), advanced), new ProposedEvent(lifecycle.siteId(), new ScheduleEffect.Created(nextGrowth(next,
                Math.addExact(action.dueAt().ticks(), WHEAT_STAGE_INTERVAL)))));
    }

    public static FrontierWorldState reduceGrowth(FrontierWorldState state, SubjectId subject, ResourceSiteGrowthAdvanced advanced) {
        if (!subject.equals(advanced.siteId())) throw new IllegalArgumentException("resource-site growth has a foreign event owner");
        ResourceSiteLifecycle current = state.resourceSites().site(advanced.siteId());
        if (current.phase() != ResourceSitePhase.GROWING || current.growthEpoch() != advanced.growthEpoch() || current.growthStage() != advanced.growthStage()) {
            throw new IllegalArgumentException("resource-site growth event is stale or invalid");
        }
        return state.withResourceSites(state.resourceSites().replace(current.advanceGrowth()));
    }

    public static FrontierWorldState reduceConflict(FrontierWorldState state, SubjectId subject, ResourceSiteConflictObserved conflict) {
        if (!subject.equals(conflict.siteId())) throw new IllegalArgumentException("resource-site conflict has a foreign event owner");
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(conflict.siteId());
        if (site == null || !site.managedSlots().contains(conflict.position())) {
            throw new IllegalArgumentException("resource-site conflict must name one exact field cell");
        }
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(conflict.siteId());
        if (lifecycle.phase() == ResourceSitePhase.DESTROYED || lifecycle.phase() == ResourceSitePhase.CONFLICT) return state;
        return state.withResourceSites(state.resourceSites().replace(lifecycle.conflicted()));
    }

    public static List<ProposedEvent> planConflict(FrontierWorldState state, ResourceSiteConflictObserved conflict) {
        reduceConflict(state, conflict.siteId(), conflict);
        return List.of(new ProposedEvent(conflict.siteId(), conflict));
    }
}
