package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validates and atomically completes the one-time owned-field preparation boundary. */
final class ResourceSitePhysicalIntentStateSupport {
    private ResourceSitePhysicalIntentStateSupport() { }

    static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_PREPARATION) return;
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId());
        ResourceSitePreparationJob job = preparation(lifecycle, intent.id());
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(lifecycle.siteId());
        FixedPosition expectedOrigin = new FixedPosition(FixedScalar.whole(site.cropSlots().getFirst().x()), FixedScalar.whole(site.cropSlots().getFirst().y()), FixedScalar.whole(site.cropSlots().getFirst().z()));
        if (intent.status() != PhysicalIntentStatus.PREPARED || !intent.subjectIds().equals(List.of(job.siteId(), job.id()))
                || !intent.origin().equals(expectedOrigin) || intent.radiusBlocks() != 0 || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED) {
            throw new IllegalArgumentException("resource-site preparation intent does not exactly match its prepared field");
        }
    }

    static boolean ownsNonterminalSubject(ResourceSiteState sites, SubjectId subject) {
        return sites.sites().values().stream().anyMatch(lifecycle -> lifecycle.siteId().equals(subject)
                || jobId(lifecycle.siteId()).equals(subject) || lifecycle.activeWork().map(ResourceSiteWork::id).filter(subject::equals).isPresent());
    }

    static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, ResourceSitePreparationObservation receipt,
                                       Map<PhysicalIntentId, PhysicalIntent> nextIntents) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId());
        ResourceSitePreparationJob job = preparation(lifecycle, intent.id());
        validateReceipt(intent, receipt);
        if (!receipt.siteId().equals(job.siteId())) throw new IllegalArgumentException("resource-site preparation receipt has a foreign site");
        nextIntents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations()); observations.put(receipt.id(), receipt);
        return replace(state, state.resourceSites().replace(lifecycle.prepared()), nextIntents, observations);
    }

    static FrontierWorldState conflict(FrontierWorldState state, PhysicalIntent intent, Map<PhysicalIntentId, PhysicalIntent> nextIntents) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId());
        if (lifecycle.phase() == ResourceSitePhase.DESTROYED) return replace(state, state.resourceSites(), nextIntents, state.physicalObservations());
        preparation(lifecycle, intent.id());
        return replace(state, state.resourceSites().replace(lifecycle.conflicted()), nextIntents, state.physicalObservations());
    }

    static void validateState(ResourceSiteState sites, Map<PhysicalIntentId, PhysicalIntent> intents,
                              Map<PhysicalObservationId, PhysicalEffectObservation> observations) {
        for (ResourceSiteLifecycle lifecycle : sites.sites().values()) {
            if (lifecycle.phase() == ResourceSitePhase.UNPREPARED && lifecycle.activeWork().isEmpty()) continue;
            PhysicalIntent intent = intents.get(intentId(lifecycle.siteId()));
            if (intent == null) {
                if (lifecycle.phase() == ResourceSitePhase.UNPREPARED && lifecycle.activeWork().isPresent()) continue;
                if (lifecycle.phase() == ResourceSitePhase.CONFLICT || lifecycle.phase() == ResourceSitePhase.DESTROYED) continue;
                throw new IllegalArgumentException("resource-site lifecycle lacks its preparation intent");
            }
            if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_PREPARATION || !intent.causeSubjectId().equals(lifecycle.siteId())) {
                throw new IllegalArgumentException("resource-site lifecycle has a foreign preparation intent");
            }
            if (lifecycle.phase() == ResourceSitePhase.UNPREPARED) {
                preparation(lifecycle, intent.id());
                if (intent.status() != PhysicalIntentStatus.PREPARED && intent.status() != PhysicalIntentStatus.RUNNING) throw new IllegalArgumentException("unprepared field has terminal preparation");
            } else if (lifecycle.phase() != ResourceSitePhase.CONFLICT && lifecycle.phase() != ResourceSitePhase.DESTROYED) {
                if (intent.status() != PhysicalIntentStatus.CONFIRMED) throw new IllegalArgumentException("prepared field lacks confirmed preparation");
                PhysicalEffectObservation observation = observations.get(intent.postconditionObservationId().orElseThrow());
                if (!(observation instanceof ResourceSitePreparationObservation receipt)) throw new IllegalArgumentException("prepared field lacks preparation receipt");
                validateReceipt(intent, receipt);
            }
        }
    }

    static void validateReceipt(PhysicalIntent intent, ResourceSitePreparationObservation receipt) {
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_PREPARATION || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED
                || !intent.id().equals(receipt.intentId()) || !intent.causeSubjectId().equals(receipt.siteId())
                || !intent.subjectIds().equals(List.of(receipt.siteId(), jobId(receipt.siteId())))) {
            throw new IllegalArgumentException("resource-site preparation receipt does not match its exact field intent");
        }
    }

    private static ResourceSitePreparationJob preparation(ResourceSiteLifecycle lifecycle, PhysicalIntentId intentId) {
        return lifecycle.activeWork().filter(ResourceSitePreparationJob.class::isInstance).map(ResourceSitePreparationJob.class::cast)
                .filter(job -> job.intentId().equals(intentId)).orElseThrow(() -> new IllegalArgumentException("resource-site preparation has no matching active work"));
    }

    private static SubjectId jobId(SubjectId siteId) { return new SubjectId("job:site-prepare-" + siteId.value().substring("site:".length())); }
    private static PhysicalIntentId intentId(SubjectId siteId) { return new PhysicalIntentId("intent:site-prepare-" + siteId.value().substring("site:".length())); }
    private static FrontierWorldState replace(FrontierWorldState state, ResourceSiteState sites, Map<PhysicalIntentId, PhysicalIntent> intents,
                                              Map<PhysicalObservationId, PhysicalEffectObservation> observations) {
        return new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(),
                state.contracts(), state.operations(), intents, observations, state.sceneLeases(), state.hiveColony(), state.structureDamage(), state.physicalDeltas(), state.ambientLeases(),
                state.routeConstructions(), state.routeTopology(), state.strategicPlans(), state.humanPopulation(), sites);
    }
}
