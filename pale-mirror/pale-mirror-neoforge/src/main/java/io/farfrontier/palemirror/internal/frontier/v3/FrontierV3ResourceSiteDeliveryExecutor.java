package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Completes a returned HOT farmer's single bounded hand-to-depot effect before generic chest drift audit. */
final class FrontierV3ResourceSiteDeliveryExecutor {
    private FrontierV3ResourceSiteDeliveryExecutor() { }
    private enum ChestState { BEFORE, AFTER, FOREIGN }

    /** A work-gate marker alone cannot authorize a chest effect: the exact owned body must occupy its declared port. */
    private static boolean workerAtDepot(ServerLevel level, FrontierWorldState state,
                                         ResourceSiteHarvestJob job, SceneLease scene) {
        if (!ResourceSiteHarvestGoal.actorAtDepot(state, job) || scene.members().size() != 1) return false;
        SceneMember member = scene.members().getFirst();
        var entity = level.getEntity(member.entityId());
        if (!(entity instanceof Mob worker) || !FrontierV3SceneExecutor.owned(entity, state, scene, member)) return false;
        SurfaceAnchor retained = state.actorLocations().get(job.workerId()).supportingSurface();
        return ResourceSiteHarvestGoal.current(state, job).arrivedAt(retained)
                && FrontierV3SemanticMovement.arrived(level, worker, retained);
    }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(level);
        var pending = ledger.pendingFieldDeliveries();
        // A pending chest may be unloaded for arbitrarily long. It owns only its site/depot;
        // it must not prevent an independent loaded farmer from reaching the same terminal.
        for (var witness : pending) {
            if (continueDelivery(level, runtime, state, ledger, witness)) return;
        }
        for (ResourceSiteHarvestJob job : state.resourceSites().sites().values().stream()
                .flatMap(site -> site.harvestJobs().values().stream()).sorted(Comparator.comparing(ResourceSiteHarvestJob::id)).toList()) {
            if (!ResourceSiteHarvestGoal.actorAtDepot(state, job)) continue;
            // New hand/chest effects require the same service turn as navigation.
            // Already prepared deliveries above settle their retained witness,
            // independently of subsequent admission decisions.
            if (!HarvestServiceAccess.available(state, job)) continue;
            if (ledger.fieldDelivery(job.siteId()) != null || ledger.fieldHandProjection(job.siteId()) != null) continue;
            var intent = state.physicalIntents().get(job.intentId());
            if (intent == null || intent.status() != PhysicalIntentStatus.PREPARED
                    && intent.status() != PhysicalIntentStatus.RUNNING) continue;
            SceneLease scene = state.sceneLeases().values().stream()
                    .filter(lease -> lease.status() == SceneLeaseStatus.HOT
                            && FrontierSceneBehaviors.isResourceSiteHarvest(lease)
                            && FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId().equals(job.siteId())
                            && FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(job.id()))
                    .findFirst().orElse(null);
            if (scene == null || scene.members().size() != 1) continue;
            if (!workerAtDepot(level, state, job, scene)) continue;
            ResourceFieldCycle cycle = state.resourceSites().cycle(job.siteId());
            boolean intermediate = job.returningForBatch();
            if ((!intermediate && !job.progress().complete()) || !cycle.pendingPlayerBreaks().isEmpty()) continue;
            // Delivery is fenced by the actor lot, scene, depot replica and
            // intent; the remote field's block-projection claim is not cargo
            // authority and may lag a completed COLD harvest.
            var hand = FrontierV3ActorHandObservation.observe(level, state, scene, job);
            int quantity = ResourceSiteHarvestCargo.quantity(state, job);
            if (quantity == 0) {
                if (hand.disposition() == FrontierV3ActorHandObservation.Disposition.EMPTY
                        && !state.inventory().fungibleResources().accounts().containsKey(job.actorAccountId())) {
                    if (startRunningAtReturn(runtime, job, scene, intent.status())) return;
                    confirm(runtime, state, job, scene, 0, 0L, List.of(), "not_applicable",
                            "witness:field-zero-" + job.id().value().substring("job:".length()));
                    return;
                }
                continue;
            }
            if (hand.disposition() != FrontierV3ActorHandObservation.Disposition.WHEAT
                    || hand.stack().orElseThrow().quantity() != quantity || quantity > 64) continue;
            var account = state.inventory().fungibleResources().accounts().get(job.actorAccountId());
            var binding = state.inventory().fungibleResources().bindings().values().stream()
                    .filter(value -> value.accountId().equals(job.actorAccountId())).toList();
            if (account == null || account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum() != quantity
                    || binding.size() != 1 || binding.getFirst().authorityEpoch() != scene.revision()
                    || !binding.getFirst().address().equals(hand.stack().orElseThrow().address())) continue;
            SubjectId depot = job.outputSlot().containerId();
            // A COLD-worked full part may first join HOT at the depot. Its intent is still
            // PREPARED: reserve the successor only after the durable RUNNING boundary, or
            // the reservation command would reject this otherwise valid handoff.
            if (intermediate && startRunningAtReturn(runtime, job, scene, intent.status())) return;
            if (intermediate) {
                var actor = state.actorLocations().get(job.workerId());
                if (actor == null) continue;
                if (job.batchSuccessorSlot().isEmpty()) {
                    var nextSlot = state.firstFreeContainerSlot(depot);
                    if (nextSlot.isPresent()) {
                        var prepared = new ResourceSiteHarvestBatchPrepared(job.siteId(), job.id(), scene.id(),
                                job.deliveredYieldQuantity(), new InventoryCustody.ContainerSlot(depot, nextSlot.orElseThrow()));
                        CommandResult result = FrontierV3CommandSubmission.submit(runtime, "field-batch-capacity", job.id().value(), prepared);
                        if (!(result instanceof CommandResult.Accepted))
                            FrontierV3ResourceSiteHarvestSceneExecutor.conflict(level, runtime, scene, "field-batch-next-route-unavailable");
                        return;
                    }
                }
            }
            if (ledger.hasPendingFieldDelivery(depot)) continue;
            PhysicalCustodyLease custody = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(depot));
            PhysicalReplicaRecord replica = state.replicaCustody().replicas().get(depot);
            if (custody == null || custody.status() != PhysicalCustodyLeaseStatus.ACQUIRED
                    || replica == null || replica.state() != PhysicalReplicaState.OBSERVED_CURRENT) continue;
            ChestBlockEntity chest = chest(level, state, depot);
            if (chest == null || classify(state, chest, depot, job.outputSlot().slot(), quantity, null) != ChestState.BEFORE
                    || !replica.fingerprint().equals(ReferenceContainerCustody.canonicalFingerprint(state, depot))
                    || !FrontierV3ReferenceContainerCustodyExecutor.observed(state, depot, chest).provenance()
                    .equals(replica.provenance())) continue;
            // A COLD-completed job may first become HOT only at this terminal station. Crop
            // work never crossed RUNNING in that case, but delivery still needs a durable
            // running boundary before its first non-replayable chest/hand effect.
            if (startRunningAtReturn(runtime, job, scene, intent.status())) return;
            String after = futureFingerprint(state, depot, job.outputSlot().slot(), quantity);
            var witness = new FrontierV3ResourceSiteDeliveryWitness(job.siteId(), job.id(), job.intentId(), job.workerId(),
                    scene.members().getFirst().entityId(), scene.id(), scene.revision(), depot,
                    job.outputSlot().slot(), quantity, custody.authorityEpoch(), replica.fingerprint(), after,
                    "witness:field-delivery-" + job.id().value().substring("job:".length()) + "-part-" + job.deliveredYieldQuantity(),
                    job.deliveredYieldQuantity(), intermediate,
                    intermediate ? job.batchSuccessorSlot().map(InventoryCustody.ContainerSlot::slot).orElse(-1) : -1);
            ledger.beginFieldDelivery(witness);
            ledger.persist(level);
            return;
        }
    }

    /** A returned COLD job has no physical farmer hand left to deliver a second time. */
    static void confirmDeferredOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        for (ResourceSiteHarvestLineage lineage : state.resourceSites().sites().values().stream()
                .flatMap(site -> site.harvestLineages().values().stream())
                .sorted(Comparator.comparing(ResourceSiteHarvestLineage::predecessorIntentId)).toList()) {
            if (!lineage.receiptPending() || lineage.composedIntoCanonicalSuccessor(state)
                    || lineage.causality().hotLeaseIds().isEmpty()) continue;
            var intent = state.physicalIntents().get(lineage.predecessorIntentId());
            if (intent == null || intent.status() != PhysicalIntentStatus.RUNNING) continue;
            SubjectId depot = lineage.outputSlot().containerId();
            if (FrontierV3ResourceSiteLedger.get(level).hasPendingFieldDelivery(depot)) continue;
            ContainerSurface surface = state.inventory().surfaces().get(depot);
            if (surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE) continue;
            BlockPos position = new BlockPos(surface.position().x(), surface.position().y(), surface.position().z());
            if (!level.hasChunkAt(position) || !level.shouldTickBlocksAt(position)) continue;
            ChestBlockEntity chest = FrontierV3CargoHandoffExecutor.activeChest(level,
                    new FrontierV3CargoHandoffExecutor.StoreTarget(position, depot));
            if (chest == null) continue;
            PhysicalReplicaRecord replica = state.replicaCustody().replicas().get(depot);
            PhysicalCustodyLease custody = state.replicaCustody().custodyByScope()
                    .get(ReferenceContainerCustody.scopeId(depot));
            if (replica == null || replica.state() != PhysicalReplicaState.OBSERVED_CURRENT
                    || custody == null || custody.status() != PhysicalCustodyLeaseStatus.ACQUIRED) continue;
            var observed = FrontierV3ReferenceContainerCustodyExecutor.observed(state, depot, chest);
            if (!observed.fingerprint().equals(replica.fingerprint())
                    || !observed.provenance().equals(replica.provenance())
                    || !observed.fingerprint().equals(ReferenceContainerCustody.canonicalFingerprint(state, depot))) continue;
            var receipt = new ResourceSiteHarvestDeferredObservation(
                    new PhysicalObservationId("observation:field-deferred-" + lineage.predecessorJobId().value().substring("job:".length())),
                    intent.id(), intent.causeSubjectId(), lineage.predecessorJobId(), depot, lineage.completedGrowthEpoch(),
                    custody.authorityEpoch(), replica.emittedCanonicalRevision(), replica.replicaRevision(),
                    observed.fingerprint(), observed.provenance());
            var nextIntents = new java.util.LinkedHashMap<>(state.physicalIntents());
            nextIntents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, Optional.of(receipt.id())));
            try {
                ResourceSitePhysicalIntentStateSupport.completeDeferredHarvest(state, intent, receipt, nextIntents);
            } catch (IllegalArgumentException notCurrent) {
                continue;
            }
            FrontierV3CommandSubmission.submit(runtime, "field-deferred-receipt", lineage.predecessorJobId().value(),
                    new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)));
            return;
        }
    }

    private static boolean startRunningAtReturn(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                ResourceSiteHarvestJob job, SceneLease scene,
                                                PhysicalIntentStatus status) {
        if (status != PhysicalIntentStatus.PREPARED) return false;
        FrontierV3CommandSubmission.submit(runtime, "field-delivery-running", scene.id().value(),
                new PhysicalIntentTransition(job.intentId(), PhysicalIntentStatus.RUNNING, Optional.empty()));
        return true;
    }

    private static boolean continueDelivery(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                         FrontierWorldState state, FrontierV3ResourceSiteLedger ledger,
                                         FrontierV3ResourceSiteDeliveryWitness witness) {
        var intent = state.physicalIntents().get(witness.intentId());
        if (witness.intermediate() && acceptedBatchObservation(state, witness)
                || !witness.intermediate() && intent != null && intent.status() == PhysicalIntentStatus.CONFIRMED) {
            ledger.retireFieldDelivery(witness); ledger.persist(level); return true;
        }
        if (intent == null || intent.status() != PhysicalIntentStatus.RUNNING) return false;
        var lifecycle = state.resourceSites().sites().get(witness.siteId());
        ResourceSiteHarvestJob job = lifecycle == null ? null : lifecycle.harvestJob(witness.jobId()).orElse(null);
        if (job == null || !ResourceSiteHarvestGoal.actorAtDepot(state, job)
                || job.returningForBatch() != witness.intermediate()
                || job.deliveredYieldQuantity() != witness.deliveredYieldBefore()
                || witness.intermediate() && job.batchSuccessorSlot().map(InventoryCustody.ContainerSlot::slot)
                .orElse(-1) != witness.successorSlot()
                || !job.outputSlot().containerId().equals(witness.containerId())
                || job.outputSlot().slot() != witness.slot()) return false;
        ResourceFieldCycle cycle = state.resourceSites().cycle(witness.siteId());
        if ((!witness.intermediate() && !job.progress().complete()) || !cycle.pendingPlayerBreaks().isEmpty()
                || ResourceSiteHarvestCargo.quantity(state, job) != witness.quantity()) return false;
        SceneLease scene = state.sceneLeases().get(witness.leaseId());
        if (scene == null || scene.status() != SceneLeaseStatus.HOT || scene.revision() != witness.actorEpoch()
                || scene.members().size() != 1 || !scene.members().getFirst().entityId().equals(witness.entityId())) return false;
        if (!workerAtDepot(level, state, job, scene)) return false;
        PhysicalCustodyLease custody = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(witness.containerId()));
        if (custody == null || custody.status() != PhysicalCustodyLeaseStatus.ACQUIRED
                || custody.authorityEpoch() != witness.depotEpoch()) return false;
        ChestBlockEntity chest = chest(level, state, witness.containerId());
        if (chest == null) return false;
        PhysicalReplicaRecord replica = state.replicaCustody().replicas().get(witness.containerId());
        if (replica == null) return false;
        if (replica.state() == PhysicalReplicaState.CONFLICT) {
            FrontierV3ResourceSiteHarvestSceneExecutor.conflict(level, runtime, scene, "field-delivery-depot-conflicted");
            return true;
        }
        if (replica.state() != PhysicalReplicaState.OBSERVED_CURRENT) return false;
        if (!replica.fingerprint().equals(witness.beforeFingerprint())) {
            FrontierV3ResourceSiteHarvestSceneExecutor.conflict(level, runtime, scene, "field-delivery-depot-epoch-diverged");
            return true;
        }
        if (!FrontierV3ReferenceContainerCustodyExecutor.observed(state, witness.containerId(), chest)
                .provenance().equals(replica.provenance())) {
            FrontierV3ReferenceContainerCustodyExecutor.reportForeignDuringFieldDelivery(runtime, state, replica, chest);
            FrontierV3ResourceSiteHarvestSceneExecutor.conflict(level, runtime, scene, "field-delivery-depot-provenance-foreign");
            return true;
        }
        var part = ResourceSiteHarvestCargo.part(state, job).orElse(null);
        var resources = state.inventory().fungibleResources();
        var account = resources.accounts().get(job.actorAccountId());
        var bindings = resources.bindings().values().stream()
                .filter(value -> value.accountId().equals(job.actorAccountId())).toList();
        if (part == null || part.quantity() != witness.quantity() || account == null
                || !account.custody().equals(new ResourceCustody.Actor(job.workerId()))
                || !account.lotQuantities().equals(java.util.Map.of(part.id(), part.quantity()))
                || !account.claimQuantities().isEmpty() || bindings.size() != 1
                || bindings.getFirst().authorityEpoch() != witness.actorEpoch()
                || !bindings.getFirst().address().equals(new PhysicalStackAddress.ActorHand(job.workerId(), witness.entityId()))) {
            FrontierV3ResourceSiteHarvestSceneExecutor.conflict(level, runtime, scene, "field-delivery-actor-lot-diverged");
            return true;
        }
        var hand = FrontierV3ActorHandObservation.observe(level, state, scene, job);
        ChestState chestState = classify(state, chest, witness.containerId(), witness.slot(), witness.quantity(), witness);
        boolean handBefore = hand.disposition() == FrontierV3ActorHandObservation.Disposition.WHEAT
                && hand.stack().orElseThrow().quantity() == witness.quantity();
        boolean handAfter = hand.disposition() == FrontierV3ActorHandObservation.Disposition.EMPTY;
        if (chestState == ChestState.FOREIGN) {
            FrontierV3ReferenceContainerCustodyExecutor.reportForeignDuringFieldDelivery(runtime, state, replica, chest);
            FrontierV3ResourceSiteHarvestSceneExecutor.conflict(level, runtime, scene, "field-delivery-chest-foreign");
            return true;
        }
        if (hand.disposition() == FrontierV3ActorHandObservation.Disposition.FOREIGN_ITEM
                || hand.disposition() == FrontierV3ActorHandObservation.Disposition.FOREIGN_OWNER
                || hand.disposition() == FrontierV3ActorHandObservation.Disposition.WHEAT && !handBefore) {
            FrontierV3ResourceSiteHarvestSceneExecutor.conflict(level, runtime, scene, "field-delivery-hand-foreign");
            return true;
        }
        if (hand.disposition() == FrontierV3ActorHandObservation.Disposition.UNAVAILABLE) return false;
        if (chestState == ChestState.BEFORE && handBefore) {
            chest.setItem(witness.slot(), new ItemStack(Items.WHEAT, witness.quantity())); chest.setChanged();
            return true;
        }
        if (chestState == ChestState.AFTER && handBefore) {
            if (level.getEntity(witness.entityId()) instanceof Mob farmer
                    && FrontierV3SceneExecutor.owned(farmer, state, scene, scene.members().getFirst())) {
                if (!FrontierV3VillagerHandMutation.setOffhand(farmer, ItemStack.EMPTY))
                    FrontierV3ResourceSiteHarvestSceneExecutor.conflict(level, runtime, scene,
                            "field-delivery-hand-clear-rejected");
                return true;
            }
            return false;
        }
        if (chestState == ChestState.BEFORE && handAfter) {
            FrontierV3ResourceSiteHarvestSceneExecutor.conflict(level, runtime, scene, "field-delivery-crash-split-ambiguous");
            return true;
        }
        if (chestState != ChestState.AFTER || !handAfter) return false;
        List<FungiblePhysicalObservation.Stack> stacks = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(
                chest, state, witness.containerId());
        confirm(runtime, state, job, scene, witness.quantity(), witness.depotEpoch(), stacks,
                witness.afterFingerprint(), witness.witnessId());
        FrontierWorldState current = runtime.decodedState().orElse(null);
        if (current != null && (witness.intermediate() && acceptedBatchObservation(current, witness)
                || !witness.intermediate() && current.physicalIntents().get(witness.intentId()) != null
                && current.physicalIntents().get(witness.intentId()).status() == PhysicalIntentStatus.CONFIRMED)) {
            ledger.retireFieldDelivery(witness); ledger.persist(level);
        }
        return true;
    }

    private static boolean acceptedBatchObservation(FrontierWorldState state,
                                                    FrontierV3ResourceSiteDeliveryWitness witness) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().sites().get(witness.siteId());
        ResourceSiteHarvestJob job = lifecycle == null ? null : lifecycle.harvestJob(witness.jobId()).orElse(null);
        if (job == null
                || job.deliveredYieldQuantity() != witness.deliveredYieldBefore() + 64) return false;
        return job.lastConfirmedBatch().filter(batch -> batch.deliveredYieldBefore() == witness.deliveredYieldBefore()
                && batch.receipt().id().equals(batchObservationId(witness.jobId(), witness.deliveredYieldBefore()))
                && batch.receipt().witnessId().equals(witness.witnessId())
                && batch.receipt().depotFingerprint().equals(witness.afterFingerprint())).isPresent();
    }

    private static PhysicalObservationId batchObservationId(SubjectId jobId, int deliveredBefore) {
        return ResourceSiteHarvestBatchDelivered.observationId(jobId, deliveredBefore);
    }

    private static void confirm(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                ResourceSiteHarvestJob job, SceneLease scene, int quantity, long depotEpoch,
                                List<FungiblePhysicalObservation.Stack> stacks, String fingerprint, String witnessId) {
        PhysicalCustodyLease custody = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(job.outputSlot().containerId()));
        long revision = quantity == 0 ? runtime.canonicalState().orElseThrow().revision().value()
                : Math.max(runtime.canonicalState().orElseThrow().revision().value(), custody.expectedCanonicalRevision() + 1L);
        var receipt = new ResourceSiteHarvestDeliveryObservation(job.returningForBatch()
                ? batchObservationId(job.id(), job.deliveredYieldQuantity()) : new PhysicalObservationId(
                "observation:field-delivery-" + job.id().value().substring("job:".length())),
                job.intentId(), job.siteId(), job.id(), job.workerId(), job.actorAccountId(), job.depotAccountId(),
                scene.id(), scene.members().getFirst().entityId(), scene.revision(), quantity, depotEpoch,
                stacks, fingerprint, revision, witnessId);
        CommandResult result = FrontierV3CommandSubmission.submit(runtime, "field-delivery", job.id().value(),
                job.returningForBatch() ? new ResourceSiteHarvestBatchDelivered(receipt, job.deliveredYieldQuantity())
                        : new PhysicalIntentTransition(job.intentId(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)));
        if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("field delivery was not durably accepted");
    }

    private static ChestBlockEntity chest(ServerLevel level, FrontierWorldState state, SubjectId depot) {
        ContainerSurface surface = state.inventory().surfaces().get(depot);
        if (surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE) return null;
        BlockPos position = new BlockPos(surface.position().x(), surface.position().y(), surface.position().z());
        if (!level.hasChunkAt(position) || !level.shouldTickBlocksAt(position)) return null;
        return FrontierV3CargoHandoffExecutor.activeChest(level,
                new FrontierV3CargoHandoffExecutor.StoreTarget(position, depot));
    }

    private static ChestState classify(FrontierWorldState state, ChestBlockEntity chest, SubjectId depot,
                                       int target, int quantity, FrontierV3ResourceSiteDeliveryWitness witness) {
        if (target < 0 || target >= chest.getContainerSize()
                || state.inventory().itemAt(depot, target).isPresent()
                || ReferenceContainerCustody.expectedFungibleSlot(state, depot, target).isPresent()) return ChestState.FOREIGN;
        List<ReferenceContainerCustody.ObservedSlot> observed = new ArrayList<>();
        var projectedSlots = ReferenceContainerCustody.expectedFungibleSlots(state, depot);
        ChestState targetState = ChestState.FOREIGN;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (slot == target) {
                if (stack.isEmpty()) { observed.add(ReferenceContainerCustody.ObservedSlot.empty(slot)); targetState = ChestState.BEFORE; }
                else if (stack.is(Items.WHEAT) && stack.getCount() == quantity && plain(stack)) {
                    observed.add(ReferenceContainerCustody.ObservedSlot.fungible(slot, "minecraft:wheat", quantity));
                    targetState = ChestState.AFTER;
                } else return ChestState.FOREIGN;
                continue;
            }
            ExactItemStack exact = state.inventory().itemAt(depot, slot).orElse(null);
            if (exact != null) {
                if (!FrontierV3CargoHandoffExecutor.exactMatch(stack, exact)) return ChestState.FOREIGN;
                observed.add(new ReferenceContainerCustody.ObservedSlot(slot, exact.id().value(), exact.itemKind(), exact.count()));
                continue;
            }
            var projected = projectedSlots.get(slot);
            if (projected == null) {
                if (!stack.isEmpty()) return ChestState.FOREIGN;
                observed.add(ReferenceContainerCustody.ObservedSlot.empty(slot));
            } else {
                if (stack.isEmpty() || !plain(stack)
                        || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(projected.itemKind())
                        || stack.getCount() != projected.quantity()) return ChestState.FOREIGN;
                observed.add(ReferenceContainerCustody.ObservedSlot.fungible(slot, projected.itemKind(), projected.quantity()));
            }
        }
        if (witness == null) return targetState == ChestState.BEFORE
                && ReferenceContainerCustody.observedFingerprint(state, depot, observed).equals(
                        ReferenceContainerCustody.canonicalFingerprint(state, depot)) ? ChestState.BEFORE : ChestState.FOREIGN;
        String fingerprint = ReferenceContainerCustody.observedFingerprint(state, depot, observed);
        return targetState == ChestState.BEFORE && fingerprint.equals(witness.beforeFingerprint())
                || targetState == ChestState.AFTER && fingerprint.equals(witness.afterFingerprint())
                ? targetState : ChestState.FOREIGN;
    }

    private static boolean plain(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem(), stack.getCount()));
    }

    private static String futureFingerprint(FrontierWorldState state, SubjectId depot, int target, int quantity) {
        List<ReferenceContainerCustody.ObservedSlot> next = new ArrayList<>();
        int size = state.inventory().containers().get(depot).slotCount();
        var projectedSlots = ReferenceContainerCustody.expectedFungibleSlots(state, depot);
        for (int slot = 0; slot < size; slot++) {
            if (slot == target) { next.add(ReferenceContainerCustody.ObservedSlot.fungible(slot, "minecraft:wheat", quantity)); continue; }
            ExactItemStack exact = state.inventory().itemAt(depot, slot).orElse(null);
            var fungible = projectedSlots.get(slot);
            next.add(exact != null ? new ReferenceContainerCustody.ObservedSlot(slot, exact.id().value(), exact.itemKind(), exact.count())
                    : fungible != null ? ReferenceContainerCustody.ObservedSlot.fungible(slot, fungible.itemKind(), fungible.quantity())
                    : ReferenceContainerCustody.ObservedSlot.empty(slot));
        }
        return ReferenceContainerCustody.observedFingerprint(state, depot, next);
    }
}
