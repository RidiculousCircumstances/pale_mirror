package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Read-only safe-point assessment. The owning process still performs the actual pause. */
public record ResidentWorkYield(SubjectId residentId, HumanAssignment assignment, Status status) {
    public enum Status {
        READY, SCENE_OR_AMBIENT_AUTHORITY, CARRYING_RESOURCE, PENDING_PHYSICAL_EFFECT,
        OWNER_SAFETY_HOLD
    }

    public ResidentWorkYield {
        Objects.requireNonNull(residentId, "yield resident");
        Objects.requireNonNull(assignment, "yield assignment");
        Objects.requireNonNull(status, "yield status");
        if (!residentId.equals(assignment.residentId()))
            throw new IllegalArgumentException("yield assessment must name its exact resident");
    }

    public boolean ready() { return status == Status.READY; }

    /** Every current work kind is deliberate; an unadapted family cannot accidentally yield. */
    public static ResidentWorkYield assess(FrontierWorldState state, HumanAssignment assignment) {
        Objects.requireNonNull(state, "yield state");
        SubjectId resident = assignment.residentId();
        AmbientActorLease ambient = state.ambientLeases().get(resident);
        // A completed meal still owns the HOT lease until the activity hand-off retargets it.
        // That transient goal is not a work-scene or cargo claim and may safely choose
        // another meal when one bread has not relieved the resident's whole deficit.
        boolean safeAmbient = ambient != null && ambient.status() == AmbientLeaseStatus.HOT
                && (ambient.goal() == AmbientGoalKind.PATROL || ambient.goal() == AmbientGoalKind.WORK
                    || ambient.goal() == AmbientGoalKind.MEAL);
        if ((!FrontierSceneAdmission.available(state, java.util.List.of(resident)) && !safeAmbient)
                || state.sceneLeases().values().stream().anyMatch(lease -> lease.retainsMemberCustody(resident)))
            return new ResidentWorkYield(resident, assignment, Status.SCENE_OR_AMBIENT_AUTHORITY);
        // Exact actor custody also includes durable equipment; it is not an occupied work
        // hand. The bounded fungible actor account is the current farmer/baker cargo hand.
        if (state.inventory().fungibleResources().accounts().values().stream().anyMatch(account ->
                account.custody().equals(new ResourceCustody.Actor(resident))))
            return new ResidentWorkYield(resident, assignment, Status.CARRYING_RESOURCE);
        Status status = switch (assignment.kind()) {
            case IDLE -> Status.READY;
            case PRODUCTION -> production(state, assignment.ownerId().orElseThrow());
            case FIELD_HARVEST -> harvest(state, assignment.ownerId().orElseThrow());
            case CARGO_TRANSPORT, ESCORT, ROUTE_PATROL, SETTLEMENT_DEFENCE,
                    ENGINEERING_RECOVERY, SETTLEMENT_SERVICE, MEDICAL_EVACUATION,
                    TRANSIT -> Status.OWNER_SAFETY_HOLD;
        };
        return new ResidentWorkYield(resident, assignment, status);
    }

    private static Status production(FrontierWorldState state, SubjectId owner) {
        ProductionJob job = state.productionJobs().get(owner);
        if (job == null)
            throw new IllegalArgumentException("production yield lost its exact job");
        // The production assignment covers bakery and non-bakery jobs. A non-bakery
        // production owner has no declared suspend/resume seam in this cut.
        if (job.bakeryWork().isEmpty()) return Status.OWNER_SAFETY_HOLD;
        return job.bakeryWork().orElseThrow().pendingPhysicalStep().isPresent()
                ? Status.PENDING_PHYSICAL_EFFECT : Status.READY;
    }

    private static Status harvest(FrontierWorldState state, SubjectId owner) {
        ResourceSiteHarvestJob job = state.resourceSites().sites().values().stream()
                .map(ResourceSiteLifecycle::activeWork).flatMap(java.util.Optional::stream)
                .filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .filter(candidate -> candidate.id().equals(owner)).findFirst().orElseThrow(
                        () -> new IllegalArgumentException("harvest yield lost its exact field job"));
        return job.progress().hasPendingCrop() || state.resourceSites().hasPendingWorldChange(job.siteId())
                ? Status.PENDING_PHYSICAL_EFFECT : Status.READY;
    }
}
