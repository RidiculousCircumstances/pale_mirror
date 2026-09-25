package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** A stopped-world, before-effect eligibility check; no runtime absence inference. */
record FrontierV3OfflineFirstAdmissionRecoveryPlan(FrontierV3ActorFirstAdmission pending,
                                                    FrontierV3ActorOwnerBinding exactOwner) {
    static FrontierV3OfflineFirstAdmissionRecoveryPlan create(FrontierWorldState state, SubjectId actor,
            FrontierV3OfflineActorAbsence.Proof absence, FrontierV3AmbientCarrierLedger ledger) {
        var lease = state.ambientLeases().get(actor);
        var location = state.actorLocations().get(actor);
        var first = ledger.firstAdmission(actor).orElse(null);
        if (lease == null || lease.status() != AmbientLeaseStatus.PREPARED || location == null
                || location.condition().status() != ActorLifeStatus.ALIVE || !location.body().equals(lease.handoffBody())
                || first == null || first.phase() != FrontierV3ActorFirstAdmission.Phase.PENDING
                || !first.identity().entityId().equals(absence.actorUuid())
                || !first.identity().entityId().equals(SceneLease.deterministicEntityId(state.bootstrap().worldId(), actor))
                || ledger.hasCarrier(actor) || ledger.pendingAdoption(actor).isPresent()
                || ledger.pendingHandoff(actor).isPresent() || ledger.departure(actor).isPresent()
                || ledger.ambientDeparture(actor).isPresent() || ledger.hasDepartureConflict(actor))
            throw new IllegalArgumentException("first-body recovery needs one exact pending, absent ambient owner");

        boolean resident = state.humanPopulation().resident(actor) != null;
        var bioformLifecycle = state.hiveColony().bioformLifecycles().get(actor);
        boolean bioform = bioformLifecycle != null && bioformLifecycle.phase() == BioformLifecyclePhase.ACTIVE;
        if (resident == bioform || state.sceneLeases().values().stream().anyMatch(scene ->
                scene.members().stream().anyMatch(member -> member.actorId().equals(actor)))
                || state.productionJobs().values().stream().anyMatch(job -> job.workerId().equals(actor))
                || state.serviceWorks().values().stream().anyMatch(work -> work.workerId().equals(actor))
                || state.resourceSites().sites().values().stream().anyMatch(site -> site.activeWork()
                    .filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                    .map(job -> job.workerId().equals(actor)).orElse(false))
                || state.operations().values().stream().anyMatch(operation -> operation.participantIds().contains(actor))
                || state.routeConstructions().values().stream().anyMatch(work -> work.engineeringTeam()
                    .map(team -> team.memberIds().contains(actor)).orElse(false))
                || state.routeMaintenances().values().stream().anyMatch(work -> work.team().memberIds().contains(actor))
                || state.hiveColony().mobilizations().values().stream().anyMatch(work -> work.memberIds().contains(actor)))
            throw new IllegalArgumentException("first-body recovery cannot cross another work or physical owner");

        var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state, actor,
                FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, absence.actorUuid(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, lease.revision(), 1L);
        var owner = FrontierV3ActorOwnerBinding.ambient(declaration);
        if (!first.identity().matches(declaration) || !first.attempt().orElseThrow().equals(owner))
            throw new IllegalArgumentException("first-body recovery does not match the attempted owner");
        return new FrontierV3OfflineFirstAdmissionRecoveryPlan(first, owner);
    }
}
