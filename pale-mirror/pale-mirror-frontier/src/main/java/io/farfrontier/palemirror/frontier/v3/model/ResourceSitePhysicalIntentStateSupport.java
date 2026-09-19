package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole;

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
public final class ResourceSitePhysicalIntentStateSupport {
    private ResourceSitePhysicalIntentStateSupport() { }

    /** Traversal borrows a PREPARED effect request but never begins that effect before F0.2. */
    static boolean admitsTraversal(PhysicalIntent intent) {
        return intent != null && intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST
                && (intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING);
    }

    static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST) {
            validateHarvestBinding(state.resourceSites().site(intent.causeSubjectId()), intent); return;
        }
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_PREPARATION) return;
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId());
        ResourceSitePreparationJob job = preparation(lifecycle, intent.id());
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(lifecycle.siteId());
        FixedPosition expectedOrigin = new FixedPosition(FixedScalar.whole(site.cropSlots().getFirst().x()), FixedScalar.whole(site.cropSlots().getFirst().y()), FixedScalar.whole(site.cropSlots().getFirst().z()));
        if (intent.status() != PhysicalIntentStatus.PREPARED || !intent.roles().equals(PhysicalIntentRoleBinding.sitePreparation(job.siteId(), job.id()))
                || !intent.origin().equals(expectedOrigin) || intent.radiusBlocks() != 0 || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED) {
            throw new IllegalArgumentException("resource-site preparation intent does not exactly match its prepared field");
        }
    }

    static boolean ownsNonterminalSubject(ResourceSiteState sites, SubjectId subject) {
        return sites.sites().values().stream().anyMatch(lifecycle -> lifecycle.phase() == ResourceSitePhase.HARVESTING && (lifecycle.siteId().equals(subject)
                || jobId(lifecycle.siteId()).equals(subject) || lifecycle.activeWork().map(ResourceSiteWork::id).filter(subject::equals).isPresent()
                || lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .map(ResourceSiteHarvestJob::outputItemId).filter(subject::equals).isPresent())
                || lifecycle.harvestLineage().filter(ResourceSiteHarvestLineage::receiptPending).map(lineage ->
                lifecycle.siteId().equals(subject) || lineage.predecessorJobId().equals(subject) || lineage.workerId().equals(subject)
                        || lineage.outputItemId().equals(subject)).orElse(false));
    }

    /** The terminal field disposition retains exact causal ownership without a resumable intent. */
    static boolean ownsPreparedConflictIntent(ResourceSiteState sites, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_HARVEST
                || (intent.status() != PhysicalIntentStatus.CONFLICTED && intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)) return false;
        ResourceSiteLifecycle lifecycle = sites.sites().get(intent.causeSubjectId());
        if (lifecycle == null || lifecycle.phase() != ResourceSitePhase.CONFLICT) return false;
        ResourceSiteHarvestLineage deferred = lifecycle.harvestLineage().filter(ResourceSiteHarvestLineage::receiptPending)
                .filter(lineage -> lineage.predecessorIntentId().equals(intent.id())).orElse(null);
        if (deferred != null) return matchesDeferredHarvestBinding(lifecycle, intent, deferred);
        try {
            validateHarvestBinding(lifecycle, intent);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, ResourceSitePreparationObservation receipt,
                                       Map<PhysicalIntentId, PhysicalIntent> nextIntents) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId());
        ResourceSitePreparationJob job = preparation(lifecycle, intent.id());
        validateReceipt(intent, receipt);
        if (!receipt.siteId().equals(job.siteId())) throw new IllegalArgumentException("resource-site preparation receipt has a foreign site");
        nextIntents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations()); observations.put(receipt.id(), receipt);
        return replace(state, state.resourceSites().replace(lifecycle.prepared()), nextIntents, observations);
    }

    public static FrontierWorldState completeHarvest(FrontierWorldState state, PhysicalIntent intent, ResourceSiteHarvestObservation receipt,
                                              Map<PhysicalIntentId, PhysicalIntent> nextIntents) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId());
        ResourceSiteHarvestLineage deferred = lifecycle.harvestLineage().filter(ResourceSiteHarvestLineage::receiptPending)
                .filter(lineage -> lineage.predecessorIntentId().equals(intent.id())).orElse(null);
        if (deferred != null) return completeDeferredHarvest(state, intent, receipt, nextIntents, lifecycle, deferred);
        ResourceSiteHarvestJob job = harvest(lifecycle, intent.id());
        if (!job.progress().complete()) throw new IllegalArgumentException("resource-site harvest receipt cannot precede every observed crop");
        validateHarvestReceipt(state.bootstrap(), intent, receipt); if (!receipt.siteId().equals(job.siteId()) || !receipt.workerId().equals(job.workerId())) {
            throw new IllegalArgumentException("resource-site harvest receipt has a foreign site or worker");
        }
        if (!receipt.output().id().equals(job.outputItemId()) || !receipt.output().custody().equals(job.outputSlot())) {
            throw new IllegalArgumentException("resource-site harvest receipt has a foreign output slot");
        }
        nextIntents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations()); observations.put(receipt.id(), receipt);
        return replace(state, state.resourceSites().replace(lifecycle.harvested()), state.inventory().store(receipt.output()), nextIntents, observations);
    }

    private static FrontierWorldState completeDeferredHarvest(FrontierWorldState state, PhysicalIntent intent, ResourceSiteHarvestObservation receipt,
                                                              Map<PhysicalIntentId, PhysicalIntent> nextIntents, ResourceSiteLifecycle lifecycle,
                                                              ResourceSiteHarvestLineage lineage) {
        if (!receipt.siteId().equals(lifecycle.siteId()) || !receipt.workerId().equals(lineage.workerId())
                || !receipt.output().id().equals(lineage.outputItemId()) || !receipt.output().custody().equals(lineage.outputSlot())
                || receipt.output().count() != 64 || !receipt.output().itemKind().equals("minecraft:wheat")) {
            throw new IllegalArgumentException("deferred resource-site harvest receipt has a foreign exact binding");
        }
        ExactItemStack owned = state.inventory().items().get(lineage.outputItemId());
        // A true late observation of the historical wheat effect is still evidence, but once
        // an exact COLD successor owns that wheat it must not require resurrecting the consumed
        // stack.  The reference-container owner reconciles the actual chest to that current
        // successor result after this receipt.
        if (!receipt.output().equals(owned) && !lineage.composedIntoCanonicalSuccessor(state)) {
            throw new IllegalArgumentException("deferred resource-site harvest receipt does not match its already-owned exact output");
        }
        nextIntents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations()); observations.put(receipt.id(), receipt);
        return replace(state, state.resourceSites().replace(lifecycle.confirmDeferredHarvestReceipt(intent.id())),
                state.inventory(), nextIntents, observations);
    }

    public static FrontierWorldState conflict(FrontierWorldState state, PhysicalIntent intent, Map<PhysicalIntentId, PhysicalIntent> nextIntents) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId());
        if (lifecycle.phase() == ResourceSitePhase.DESTROYED) return replace(state, state.resourceSites(), nextIntents, state.physicalObservations());
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_PREPARATION) preparation(lifecycle, intent.id());
        else if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST) {
            ResourceSiteHarvestLineage deferred = lifecycle.harvestLineage().filter(ResourceSiteHarvestLineage::receiptPending)
                    .filter(lineage -> lineage.predecessorIntentId().equals(intent.id())).orElse(null);
            if (deferred != null) {
                // Semantic completion and its exact depot custody already happened in COLD.
                // A missing/foreign later physical surface therefore belongs to this one
                // materialization intent, not to the renewable field or its successor.
                InventoryConflict receiptConflict = new InventoryConflict(
                        new SubjectId("conflict:resource-site-deferred-" + intent.id().value().replace(':', '-')),
                        deferred.outputItemId(), deferred.outputSlot().containerId(), deferred.outputSlot().slot(), InventoryConflictKind.MISSING);
                return replace(state, state.resourceSites(), state.inventory().recordConflict(receiptConflict), nextIntents, state.physicalObservations());
            }
            harvest(lifecycle, intent.id());
        } else throw new IllegalArgumentException("resource-site conflict has a foreign physical intent");
        ResourceSite site = FrontierResourceSitePlan.compile(state.bootstrap()).get(lifecycle.siteId());
        ResourceSiteConflictObserved conflict = new ResourceSiteConflictObserved(lifecycle.siteId(), site.cropSlots().getFirst(),
                ResourceSiteConflictReason.RECOVERY_UNRESOLVED, ResourceSiteConflictSource.PHYSICAL_INTENT_RECOVERY);
        return replace(state, state.resourceSites().replace(lifecycle.conflicted(ResourceSiteConflictDisposition.recovery(site.cropSlots().getFirst(),
                ResourceSiteConflictIncidents.first(lifecycle, conflict)))),
                nextIntents, state.physicalObservations());
    }

    static void validateState(ResourceSiteState sites, Map<PhysicalIntentId, PhysicalIntent> intents,
                              Map<PhysicalObservationId, PhysicalEffectObservation> observations) {
        for (ResourceSiteLifecycle lifecycle : sites.sites().values()) {
            if (lifecycle.phase() == ResourceSitePhase.UNPREPARED && lifecycle.activeWork().isEmpty()) continue;
            PhysicalIntent intent = intents.get(intentId(lifecycle.siteId()));
            if (intent == null) {
                // COLD preparation is a canonical event.  Its loaded-world field is a deferred
                // desired-state projection, not a physical-intent prerequisite for food.
                if (lifecycle.phase() == ResourceSitePhase.UNPREPARED && lifecycle.activeWork().isPresent()) continue;
                if (lifecycle.phase() == ResourceSitePhase.CONFLICT || lifecycle.phase() == ResourceSitePhase.DESTROYED
                        || lifecycle.phase() == ResourceSitePhase.GROWING || lifecycle.phase() == ResourceSitePhase.READY
                        || lifecycle.phase() == ResourceSitePhase.HARVESTING) continue;
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
            if (lifecycle.phase() == ResourceSitePhase.HARVESTING) validateHarvestState(sites, intents, observations, lifecycle);
            lifecycle.harvestLineage().filter(ResourceSiteHarvestLineage::receiptPending)
                    .ifPresent(lineage -> validateDeferredHarvestState(intents, lifecycle, lineage));
        }
    }

    static void validateReceipt(PhysicalIntent intent, ResourceSitePreparationObservation receipt) {
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_PREPARATION || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED
                || !intent.id().equals(receipt.intentId()) || !intent.causeSubjectId().equals(receipt.siteId())
                || !intent.roles().equals(PhysicalIntentRoleBinding.sitePreparation(receipt.siteId(), jobId(receipt.siteId())))) {
            throw new IllegalArgumentException("resource-site preparation receipt does not match its exact field intent");
        }
    }

    static void validateHarvestReceipt(FrontierBootstrap bootstrap, PhysicalIntent intent, ResourceSiteHarvestObservation receipt) {
        ResourceSite site = FrontierResourceSitePlan.compile(bootstrap).get(intent.causeSubjectId()); ExactItemStack output = receipt.output();
        if (site == null || intent.kind() != PhysicalIntentKind.RESOURCE_SITE_HARVEST || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED
                || !intent.id().equals(receipt.intentId()) || !intent.causeSubjectId().equals(receipt.siteId())
                || !intent.roles().require(PhysicalIntentSubjectRole.WORKER).equals(receipt.workerId()) || !intent.roles().require(PhysicalIntentSubjectRole.OUTPUT_ITEM).equals(output.id())
                || !output.economicOwnerId().equals(site.settlementId()) || !output.itemKind().equals("minecraft:wheat") || output.count() != 64
                || !(output.custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(FrontierWorldState.depotId(site.settlementId()))) {
            throw new IllegalArgumentException("resource-site harvest receipt does not match its exact output claim");
        }
    }

    private static void validateHarvestState(ResourceSiteState sites, Map<PhysicalIntentId, PhysicalIntent> intents,
                                             Map<PhysicalObservationId, PhysicalEffectObservation> observations, ResourceSiteLifecycle lifecycle) {
        ResourceSiteHarvestJob job = lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("harvesting resource site has no harvest job"));
        PhysicalIntent intent = intents.get(job.intentId()); if (intent == null) return;
        validateHarvestBinding(lifecycle, intent);
        if (intent.status() == PhysicalIntentStatus.CONFIRMED) {
            PhysicalEffectObservation observation = observations.get(intent.postconditionObservationId().orElseThrow());
            if (!(observation instanceof ResourceSiteHarvestObservation)) throw new IllegalArgumentException("harvest intent has a foreign receipt");
        }
    }

    private static void validateDeferredHarvestState(Map<PhysicalIntentId, PhysicalIntent> intents, ResourceSiteLifecycle lifecycle,
                                                     ResourceSiteHarvestLineage lineage) {
        PhysicalIntent intent = intents.get(lineage.predecessorIntentId());
        if (intent == null || !matchesDeferredHarvestBinding(lifecycle, intent, lineage)
                || (lifecycle.phase() == ResourceSitePhase.CONFLICT ? intent.status() != PhysicalIntentStatus.CONFLICTED
                && intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                : intent.status() != PhysicalIntentStatus.PREPARED && intent.status() != PhysicalIntentStatus.RUNNING
                && intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)) {
            throw new IllegalArgumentException("resource-site deferred harvest receipt lacks its exact prepared or running intent");
        }
    }

    private static boolean matchesDeferredHarvestBinding(ResourceSiteLifecycle lifecycle, PhysicalIntent intent, ResourceSiteHarvestLineage lineage) {
        return intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST && intent.causeSubjectId().equals(lifecycle.siteId())
                && intent.roles().equals(PhysicalIntentRoleBinding.siteHarvest(lifecycle.siteId(), lineage.predecessorJobId(), lineage.workerId(), lineage.outputItemId()))
                && intent.postcondition() == PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED;
    }

    private static void validateHarvestBinding(ResourceSiteLifecycle lifecycle, PhysicalIntent intent) {
        ResourceSiteHarvestJob job = harvest(lifecycle, intent.id());
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_HARVEST || !intent.causeSubjectId().equals(lifecycle.siteId())
                || !intent.roles().equals(PhysicalIntentRoleBinding.siteHarvest(job.siteId(), job.id(), job.workerId(), job.outputItemId()))
                || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED) {
            throw new IllegalArgumentException("resource-site harvest intent does not bind its active job");
        }
    }

    private static ResourceSitePreparationJob preparation(ResourceSiteLifecycle lifecycle, PhysicalIntentId intentId) {
        return lifecycle.activeWork().filter(ResourceSitePreparationJob.class::isInstance).map(ResourceSitePreparationJob.class::cast)
                .filter(job -> job.intentId().equals(intentId)).orElseThrow(() -> new IllegalArgumentException("resource-site preparation has no matching active work"));
    }

    private static ResourceSiteHarvestJob harvest(ResourceSiteLifecycle lifecycle, PhysicalIntentId intentId) {
        return lifecycle.activeWork().filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .filter(job -> job.intentId().equals(intentId)).orElseThrow(() -> new IllegalArgumentException("resource-site harvest has no matching active work"));
    }

    private static SubjectId jobId(SubjectId siteId) { return new SubjectId("job:site-prepare-" + siteId.value().substring("site:".length())); }
    private static PhysicalIntentId intentId(SubjectId siteId) { return new PhysicalIntentId("intent:site-prepare-" + siteId.value().substring("site:".length())); }
    private static FrontierWorldState replace(FrontierWorldState state, ResourceSiteState sites, Map<PhysicalIntentId, PhysicalIntent> intents,
                                              Map<PhysicalObservationId, PhysicalEffectObservation> observations) {
        return replace(state, sites, state.inventory(), intents, observations);
    }
    private static FrontierWorldState replace(FrontierWorldState state, ResourceSiteState sites, ExactInventory inventory, Map<PhysicalIntentId, PhysicalIntent> intents,
                                              Map<PhysicalObservationId, PhysicalEffectObservation> observations) {
        return state.withChanges(FrontierWorldStateUpdate.begin().resourceSites(sites).inventory(inventory).physicalIntents(intents)
                .physicalObservations(observations));
    }
}
