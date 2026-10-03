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
        ResourceSite site = state.resourceSite(lifecycle.siteId());
        FixedPosition expectedOrigin = new FixedPosition(FixedScalar.whole(site.cropSlots().getFirst().x()), FixedScalar.whole(site.cropSlots().getFirst().y()), FixedScalar.whole(site.cropSlots().getFirst().z()));
        if (intent.status() != PhysicalIntentStatus.PREPARED || !intent.roles().equals(PhysicalIntentRoleBinding.sitePreparation(job.siteId(), job.id()))
                || !intent.origin().equals(expectedOrigin) || intent.radiusBlocks() != 0 || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED) {
            throw new IllegalArgumentException("resource-site preparation intent does not exactly match its prepared field");
        }
    }

    static boolean ownsNonterminalSubject(ResourceSiteState sites, SubjectId subject) {
        return sites.sites().values().stream().anyMatch(lifecycle -> lifecycle.phase() == ResourceSitePhase.UNPREPARED
                && lifecycle.preparationWork()
                .map(job -> job.siteId().equals(subject) || job.id().equals(subject)).orElse(false)
                || lifecycle.phase() == ResourceSitePhase.HARVESTING && (lifecycle.siteId().equals(subject)
                || lifecycle.harvestJobs().containsKey(subject)
                || lifecycle.harvestJobs().values().stream()
                .anyMatch(job -> job.actorAccountId().equals(subject) || job.depotAccountId().equals(subject)
                        || job.outputItemId().equals(subject)))
                || lifecycle.harvestLineages().values().stream().filter(ResourceSiteHarvestLineage::receiptPending).anyMatch(lineage ->
                lifecycle.siteId().equals(subject) || lineage.predecessorJobId().equals(subject) || lineage.workerId().equals(subject)
                        || lineage.actorAccountId().equals(subject) || lineage.depotAccountId().equals(subject)
                        || lineage.outputItemId().equals(subject)));
    }

    /** The terminal field disposition retains exact causal ownership without a resumable intent. */
    static boolean ownsPreparedConflictIntent(ResourceSiteState sites, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_HARVEST
                || (intent.status() != PhysicalIntentStatus.CONFLICTED && intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)) return false;
        ResourceSiteLifecycle lifecycle = sites.sites().get(intent.causeSubjectId());
        if (lifecycle == null || lifecycle.phase() != ResourceSitePhase.CONFLICT) return false;
        ResourceSiteHarvestLineage deferred = lifecycle.harvestLineage(intent.id()).filter(ResourceSiteHarvestLineage::receiptPending)
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
        ResourceSite site = state.resourceSite(job.siteId());
        if (receipt.preparedSoilSlots() != site.soilSlots().size()
                || receipt.preparedCropSlots() != site.cropSlots().size())
            throw new IllegalArgumentException("resource-site preparation receipt has a partial or foreign field layout");
        nextIntents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations()); observations.put(receipt.id(), receipt);
        return replace(state, state.resourceSites().replace(lifecycle.prepared(),
                ResourceFieldCycle.seeded(job.siteId(), state.resourceSites().cycle(job.siteId()).layout(), 1L)), nextIntents, observations);
    }

    public static FrontierWorldState completeHarvest(FrontierWorldState state, PhysicalIntent intent, ResourceSiteHarvestObservation receipt,
                                              Map<PhysicalIntentId, PhysicalIntent> nextIntents) {
        // The current field reducer already credits each positive cell to its one fungible
        // actor part. This old receipt would mint a second exact 64-stack (including after a
        // zero/partial cycle), so it is no longer an admissible transition in fresh schema.
        // A typed per-part hand/depot receipt must replace it before HOT completion is live.
        throw new IllegalArgumentException("legacy exact-stack field harvest receipt is retired");
    }

    /**
     * One canonical publication of the observed handoff, job retirement, next field epoch and
     * HOT scene drain. The active job's reserved chest slot is released only in this successor.
     */
    public static FrontierWorldState completeHarvest(FrontierWorldState state, PhysicalIntent intent,
                                                     ResourceSiteHarvestDeliveryObservation receipt,
                                                     Map<PhysicalIntentId, PhysicalIntent> nextIntents) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId());
        ResourceSiteHarvestJob job = harvest(lifecycle, intent.id());
        ResourceSite site = state.resourceSite(job.siteId());
        ResourceFieldCycle cycle = state.resourceSites().cycle(job.siteId());
        if (intent.status() != PhysicalIntentStatus.RUNNING
                || !job.progress().complete() || !cycle.pendingPlayerBreaks().isEmpty()
                || !receipt.intentId().equals(intent.id()) || !receipt.siteId().equals(job.siteId())
                || !receipt.jobId().equals(job.id()) || !receipt.workerId().equals(job.workerId())
                || !receipt.actorAccountId().equals(job.actorAccountId())
                || !receipt.depotAccountId().equals(job.depotAccountId())) {
            throw new IllegalArgumentException("field delivery does not close its exact accounted job and intent");
        }
        SceneLease lease = FrontierResourceSiteHarvestSceneSupport.requireHotLease(state, job, receipt.leaseId());
        if (!lease.members().getFirst().entityId().equals(receipt.entityId())
                || lease.revision() != receipt.actorEpoch()) {
            throw new IllegalArgumentException("field delivery lacks its exact HOT farmer body and epoch");
        }
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || !ResourceSiteHarvestGoal.actorAtDepot(state, job))
            throw new IllegalArgumentException("field delivery lacks an observed legal depot service station");
        int carried = ResourceSiteHarvestCargo.quantity(state, job);
        if (carried != job.undeliveredYieldQuantity()
                || carried != receipt.harvestedQuantity()) {
            throw new IllegalArgumentException("field delivery cannot skip a completed bounded yield part");
        }
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        if (carried == 0) {
            if (resources.accounts().containsKey(job.actorAccountId()))
                throw new IllegalArgumentException("zero-yield field delivery retains actor-held wheat");
        } else {
            resources = ResourceSiteHarvestCargo.deliverObserved(state, job, receipt.entityId(),
                    receipt.actorEpoch(), receipt.depotEpoch(), receipt.depotStacks());
        }
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.put(receipt.id(), receipt);
        Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> scenes = new LinkedHashMap<>(state.sceneLeases());
        scenes.put(lease.id(), lease.withStatus(SceneLeaseStatus.DRAINING));
        ResourceFieldCycle successor = cycle;
        FrontierWorldState published = state.withChanges(FrontierWorldStateUpdate.begin()
                .resourceSites(state.resourceSites().replace(lifecycle.harvestedAt(ResourceSiteHarvestGoal.current(state, job), actor.body(),
                        ResourceSiteHarvestHistoryRetention.reclaimable(state, lifecycle, job.workerId()))
                        .withPlantReadiness(successor), successor))
                .inventory(state.inventory().withFungibleResources(resources))
                .physicalIntents(nextIntents).physicalObservations(observations).sceneLeases(scenes)
                .actorExecutions(ActorExecutionComposition.LIFECYCLE.retire(state, job.workerId(),
                        io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.FIELD_HARVEST, job.id())));
        if (carried == 0) return published;
        SubjectId containerId = job.outputSlot().containerId();
        if (!ReferenceContainerCustody.canonicalFingerprint(published, containerId).equals(receipt.depotFingerprint())) {
            throw new IllegalArgumentException("field delivery observed a different complete depot postcondition");
        }
        return ReferenceContainerCustody.closeConfirmedMutation(published,
                ReferenceContainerCustody.confirmedMutationTransition(published, containerId,
                        receipt.emittedCanonicalRevision()));
    }

    /** Confirms the already-owned COLD depot image; it neither transfers nor issues wheat. */
    public static FrontierWorldState completeDeferredHarvest(FrontierWorldState state, PhysicalIntent intent,
                                                              ResourceSiteHarvestDeferredObservation receipt,
                                                              Map<PhysicalIntentId, PhysicalIntent> nextIntents) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(receipt.siteId());
        ResourceSiteHarvestLineage lineage = lifecycle.harvestLineage(intent.id())
                .filter(ResourceSiteHarvestLineage::receiptPending)
                .filter(value -> value.predecessorIntentId().equals(intent.id()))
                .orElseThrow(() -> new IllegalArgumentException("deferred harvest has no exact pending lineage"));
        ResourceSite site = state.resourceSite(receipt.siteId());
        if (intent.status() != PhysicalIntentStatus.RUNNING || site == null
                || !intent.causeSubjectId().equals(receipt.siteId())
                || intent.kind() != PhysicalIntentKind.RESOURCE_SITE_HARVEST
                || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED
                || !intent.id().equals(receipt.intentId())
                || !intent.roles().equals(PhysicalIntentRoleBinding.siteHarvest(receipt.siteId(), receipt.jobId(),
                        lineage.workerId(), lineage.actorAccountId(), lineage.depotAccountId()))
                || !lineage.predecessorJobId().equals(receipt.jobId())
                || lineage.completedGrowthEpoch() != receipt.completedGrowthEpoch()
                || !lineage.outputSlot().containerId().equals(receipt.containerId())
                || !receipt.containerId().equals(FrontierWorldState.depotId(site.settlementId()))
                || lineage.causality().hotLeaseIds().isEmpty()
                || state.sceneLeases().values().stream().anyMatch(lease -> FrontierSceneBehaviors.isResourceSiteHarvest(lease)
                        && FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId().equals(receipt.siteId())
                        && FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(receipt.jobId())
                        && lease.status() != SceneLeaseStatus.CLOSED)) {
            throw new IllegalArgumentException("deferred harvest receipt lacks its completed HOT/COLD owner");
        }
        SubjectId depot = receipt.containerId();
        PhysicalReplicaRecord replica = state.replicaCustody().replicas().get(depot);
        PhysicalCustodyLease custody = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(depot));
        if (replica == null || replica.state() != PhysicalReplicaState.OBSERVED_CURRENT
                || !replica.semanticKind().equals(ReferenceContainerCustody.semanticKind(state, depot))
                || replica.emittedCanonicalRevision() != receipt.emittedCanonicalRevision()
                || replica.replicaRevision() != receipt.replicaRevision()
                || !replica.fingerprint().equals(receipt.depotFingerprint())
                || !replica.provenance().equals(receipt.depotProvenance())
                || !receipt.depotFingerprint().equals(ReferenceContainerCustody.canonicalFingerprint(state, depot))
                || !receipt.depotProvenance().equals(ReferenceContainerCustody.provenance(depot))
                || custody == null || custody.status() != PhysicalCustodyLeaseStatus.ACQUIRED
                || !custody.objectId().equals(depot) || !custody.providerId().equals(ReferenceContainerCustody.PROVIDER_ID)
                || custody.authorityEpoch() != receipt.custodyEpoch()
                || custody.expectedCanonicalRevision() != replica.emittedCanonicalRevision()
                || custody.expectedReplicaRevision() != replica.replicaRevision()) {
            throw new IllegalArgumentException("deferred harvest receipt lacks its current owned physical depot");
        }
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.put(receipt.id(), receipt);
        return replace(state, state.resourceSites().replace(lifecycle.confirmDeferredHarvestReceipt(intent.id(), receipt.id())),
                nextIntents, observations);
    }

    /** Confirms one full physical handoff while retaining the same farmer, job and RUNNING intent. */
    public static FrontierWorldState deliverHarvestBatch(FrontierWorldState state,
                                                        ResourceSiteHarvestBatchDelivered delivered) {
        ResourceSiteHarvestDeliveryObservation receipt = delivered.receipt();
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(receipt.siteId());
        ResourceSiteHarvestJob job = harvest(lifecycle, receipt.intentId());
        ResourceSite site = state.resourceSite(job.siteId());
        ResourceFieldCycle cycle = state.resourceSites().cycle(job.siteId());
        PhysicalIntent intent = state.physicalIntents().get(job.intentId());
        if (intent == null || intent.status() != PhysicalIntentStatus.RUNNING
                || !job.returningForBatch() || job.progress().complete()
                || job.deliveredYieldQuantity() != delivered.deliveredYieldBefore()
                || !cycle.pendingPlayerBreaks().isEmpty() || ResourceSiteHarvestCargo.quantity(state, job) != 64
                || !receipt.jobId().equals(job.id()) || !receipt.workerId().equals(job.workerId())
                || !receipt.actorAccountId().equals(job.actorAccountId())
                || !receipt.depotAccountId().equals(job.depotAccountId())
                || receipt.harvestedQuantity() != 64 || job.batchSuccessorSlot().isEmpty()
                || state.physicalObservations().containsKey(receipt.id()))
            throw new IllegalArgumentException("field batch delivery lacks its exact pending full hand and intent");
        validateHarvestDeliveryReceipt(state.bootstrap(), intent, receipt);
        SceneLease lease = FrontierResourceSiteHarvestSceneSupport.requireHotLease(state, job, receipt.leaseId());
        if (!lease.members().getFirst().entityId().equals(receipt.entityId())
                || lease.revision() != receipt.actorEpoch())
            throw new IllegalArgumentException("field batch delivery lacks its exact HOT farmer body and epoch");
        ActorLocation actor = state.actorLocations().get(job.workerId());
        if (actor == null || !ResourceSiteHarvestGoal.actorAtDepot(state, job))
            throw new IllegalArgumentException("field batch delivery lacks its retained depot station");
        InventoryCustody.ContainerSlot nextSlot = job.batchSuccessorSlot().orElseThrow();
        FungibleResourceLedger resources = ResourceSiteHarvestCargo.deliverObserved(state, job,
                receipt.entityId(), receipt.actorEpoch(), receipt.depotEpoch(), receipt.depotStacks());
        FrontierWorldState published = state.withChanges(FrontierWorldStateUpdate.begin()
                .resourceSites(state.resourceSites().replace(lifecycle.deliverFullHarvestBatch(job, nextSlot,
                        java.util.Optional.of(delivered), state.resourceSites().cycle(job.siteId()), actor.supportingSurface())))
                .inventory(state.inventory().withFungibleResources(resources)));
        if (!ReferenceContainerCustody.canonicalFingerprint(published, job.outputSlot().containerId())
                .equals(receipt.depotFingerprint()))
            throw new IllegalArgumentException("field batch delivery observed a different complete depot postcondition");
        return ReferenceContainerCustody.closeConfirmedMutation(published,
                ReferenceContainerCustody.confirmedMutationTransition(published, job.outputSlot().containerId(),
                        receipt.emittedCanonicalRevision()));
    }

    public static FrontierWorldState conflict(FrontierWorldState state, PhysicalIntent intent, Map<PhysicalIntentId, PhysicalIntent> nextIntents) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(intent.causeSubjectId());
        if (lifecycle.phase() == ResourceSitePhase.DESTROYED) return replace(state, state.resourceSites(), nextIntents, state.physicalObservations());
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_PREPARATION) preparation(lifecycle, intent.id());
        else if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST) {
            ResourceSiteHarvestLineage deferred = lifecycle.harvestLineage(intent.id()).filter(ResourceSiteHarvestLineage::receiptPending)
                    .filter(lineage -> lineage.predecessorIntentId().equals(intent.id())).orElse(null);
            if (deferred != null) {
                // Semantic completion and its exact depot custody already happened in COLD.
                // A missing/foreign later physical surface therefore belongs to this one
                // materialization intent, not to the renewable field or its successor.
                InventoryConflict receiptConflict = InventoryDiagnosticProducer.RESOURCE_SITE_DEFERRED_RECEIPT_MISSING.create(
                        new SubjectId("conflict:resource-site-deferred-" + intent.id().value().replace(':', '-')),
                        deferred.outputItemId(), deferred.outputSlot().containerId(), deferred.outputSlot().slot());
                return replace(state, state.resourceSites(), state.inventory().recordConflict(receiptConflict), nextIntents, state.physicalObservations());
            }
            harvest(lifecycle, intent.id());
        } else throw new IllegalArgumentException("resource-site conflict has a foreign physical intent");
        ResourceSite site = state.resourceSite(lifecycle.siteId());
        ResourceSiteConflictObserved conflict = new ResourceSiteConflictObserved(lifecycle.siteId(), site.cropSlots().getFirst(),
                ResourceSiteDiagnosticProducer.PHYSICAL_INTENT_RECOVERY);
        return replace(state, state.resourceSites().replace(lifecycle.conflicted(ResourceSiteConflictDisposition.recovery(site.cropSlots().getFirst(),
                ResourceSiteConflictIncidents.first(lifecycle, conflict)))),
                nextIntents, state.physicalObservations());
    }

    static void validateState(ResourceSiteState sites, Map<PhysicalIntentId, PhysicalIntent> intents,
                              Map<PhysicalObservationId, PhysicalEffectObservation> observations) {
        for (ResourceSiteLifecycle lifecycle : sites.sites().values()) {
            if (lifecycle.phase() == ResourceSitePhase.UNPREPARED && !lifecycle.hasWork()) continue;
            PhysicalIntent intent = intents.get(intentId(lifecycle.siteId()));
            if (intent == null) {
                // COLD preparation is a canonical event.  Its loaded-world field is a deferred
                // desired-state projection, not a physical-intent prerequisite for food.
                if (lifecycle.phase() == ResourceSitePhase.UNPREPARED && lifecycle.hasWork()) continue;
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
            lifecycle.harvestLineages().values().stream().filter(ResourceSiteHarvestLineage::receiptPending)
                    .forEach(lineage -> validateDeferredHarvestState(intents, lifecycle, lineage));
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

    static void validateHarvestDeliveryReceipt(FrontierBootstrap bootstrap, PhysicalIntent intent,
                                               ResourceSiteHarvestDeliveryObservation receipt) {
        ResourceSite site = FrontierResourceSitePlan.compile(bootstrap).get(receipt.siteId());
        if (site == null || intent.kind() != PhysicalIntentKind.RESOURCE_SITE_HARVEST
                || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED
                || !intent.id().equals(receipt.intentId()) || !intent.causeSubjectId().equals(receipt.siteId())
                || !intent.roles().equals(PhysicalIntentRoleBinding.siteHarvest(receipt.siteId(), receipt.jobId(),
                        receipt.workerId(), receipt.actorAccountId(), receipt.depotAccountId()))
                || !receipt.depotAccountId().equals(ReferenceContainerCustody.scopeId(
                        FrontierWorldState.depotId(site.settlementId())))
                || receipt.depotStacks().stream().anyMatch(stack -> !(stack.address()
                        instanceof PhysicalStackAddress.ContainerSlot slot)
                        || !slot.slot().containerId().equals(FrontierWorldState.depotId(site.settlementId())))) {
            throw new IllegalArgumentException("field delivery receipt has a foreign intent, account or depot surface");
        }
    }

    static void validateDeferredHarvestReceipt(FrontierBootstrap bootstrap, PhysicalIntent intent,
                                               ResourceSiteHarvestDeferredObservation receipt) {
        ResourceSite site = FrontierResourceSitePlan.compile(bootstrap).get(receipt.siteId());
        if (site == null || intent.kind() != PhysicalIntentKind.RESOURCE_SITE_HARVEST
                || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED
                || !intent.id().equals(receipt.intentId()) || !intent.causeSubjectId().equals(receipt.siteId())
                || !intent.roles().require(PhysicalIntentSubjectRole.RESOURCE_SITE_JOB).equals(receipt.jobId())
                || !receipt.containerId().equals(FrontierWorldState.depotId(site.settlementId()))
                || !intent.roles().require(PhysicalIntentSubjectRole.RESOURCE_DESTINATION_ACCOUNT)
                .equals(ReferenceContainerCustody.scopeId(receipt.containerId()))
                || !receipt.depotProvenance().equals(ReferenceContainerCustody.provenance(receipt.containerId()))) {
            throw new IllegalArgumentException("retained deferred harvest receipt has a foreign intent or depot");
        }
    }

    private static void validateHarvestState(ResourceSiteState sites, Map<PhysicalIntentId, PhysicalIntent> intents,
                                             Map<PhysicalObservationId, PhysicalEffectObservation> observations, ResourceSiteLifecycle lifecycle) {
        for (ResourceSiteHarvestJob job : lifecycle.harvestJobs().values()) {
        PhysicalIntent intent = intents.get(job.intentId());
        if (intent == null) {
            if (job.lastConfirmedBatch().isPresent())
                throw new IllegalArgumentException("confirmed intermediate field batch lost its owning intent");
            continue;
        }
        validateHarvestBinding(lifecycle, intent);
        if (job.lastConfirmedBatch().isPresent()) {
            ResourceSiteHarvestDeliveryObservation batch = job.lastConfirmedBatch().orElseThrow().receipt();
            if (intent.status() != PhysicalIntentStatus.RUNNING
                    && intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                    || observations.containsKey(batch.id()))
                throw new IllegalArgumentException("intermediate field receipt must remain under its running job, not a terminal observation");
        }
        if (intent.status() == PhysicalIntentStatus.CONFIRMED) {
            PhysicalEffectObservation observation = observations.get(intent.postconditionObservationId().orElseThrow());
            if (!(observation instanceof ResourceSiteHarvestObservation)) throw new IllegalArgumentException("harvest intent has a foreign receipt");
        }
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
                && intent.roles().equals(PhysicalIntentRoleBinding.siteHarvest(lifecycle.siteId(), lineage.predecessorJobId(), lineage.workerId(),
                lineage.actorAccountId(), lineage.depotAccountId()))
                && intent.postcondition() == PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED;
    }

    private static void validateHarvestBinding(ResourceSiteLifecycle lifecycle, PhysicalIntent intent) {
        ResourceSiteHarvestJob job = harvest(lifecycle, intent.id());
        if (intent.kind() != PhysicalIntentKind.RESOURCE_SITE_HARVEST || !intent.causeSubjectId().equals(lifecycle.siteId())
                || !intent.roles().equals(PhysicalIntentRoleBinding.siteHarvest(job.siteId(), job.id(), job.workerId(),
                job.actorAccountId(), job.depotAccountId()))
                || intent.postcondition() != PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED) {
            throw new IllegalArgumentException("resource-site harvest intent does not bind its active job");
        }
    }

    private static ResourceSitePreparationJob preparation(ResourceSiteLifecycle lifecycle, PhysicalIntentId intentId) {
        return lifecycle.preparationWork()
                .filter(job -> job.intentId().equals(intentId)).orElseThrow(() -> new IllegalArgumentException("resource-site preparation has no matching active work"));
    }

    private static ResourceSiteHarvestJob harvest(ResourceSiteLifecycle lifecycle, PhysicalIntentId intentId) {
        return lifecycle.harvestJobs().values().stream().filter(job -> job.intentId().equals(intentId))
                .reduce((left, right) -> { throw new IllegalArgumentException("duplicate harvest intent owner"); }).orElseThrow(() -> new IllegalArgumentException("resource-site harvest has no matching active work"));
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
