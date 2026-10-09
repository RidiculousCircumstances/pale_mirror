package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Mining effects only. The common controllers alone own bodies, routes, inventories and source authority. */
final class FrontierV3ExtractionExecutor {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private FrontierV3ExtractionExecutor() { }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        for (var job : state.extractionSites().work().values().stream().filter(value -> !value.terminal())
                .sorted(Comparator.comparing(ExtractionWork::id)).toList()) {
            if (progress(level, runtime, state, job)) return;
        }
    }
    private static boolean progress(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, ExtractionWork job) {
        var actor = job.execution().actorId();
        var entity = level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(), actor));
        if (entity == null) return savedDeparture(level, runtime, state, job);
        if (!(entity instanceof Villager worker) || !worker.isAlive()
                || !FrontierV3ActorBodyController.recognizesRecordedBody(level, state, worker)) return false;
        var lease = state.ambientLeases().get(actor);
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT || ActorExecutionCoordinator.sceneOwns(state, actor)) return false;
        var custody = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(job.carriedAccountId())).toList();
        int carried = ExtractionWorkAuthority.carried(state, job);
        if (carried > 0 && custody.isEmpty() && job.pending().isEmpty()
                && job.execution().equals(state.actorExecutions().current(ActorActivityKind.EXTRACTION).get(actor))
                && FrontierV3ActorCarryProjection.witnessed(state, actor, worker)) {
            // Binding requires this exact current actuator, not the suspended work owner.
            var body = ActorBodyAuthority.current(state, actor);
            return submit(level, runtime, job.id(), "hand-materialized", new ExtractionHandCustodyObserved(job.id(),
                    new ActorActuationId(body, job.execution()), ExtractionHandCustodyObserved.Boundary.MATERIALIZED,
                    ExtractionPhysicalStateSupport.hand(state, job, carried)));
        }
        FrontierV3ActorActuation actuation;
        try { actuation = FrontierV3ActorActuation.capture(state, worker, job.execution(), runtime::decodedState); }
        catch (IllegalArgumentException inactive) { return false; }
        if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, worker) || !actuation.current(worker)) return false;
        state = runtime.decodedState().orElseThrow();
        if (!job.equals(state.extractionSites().work().get(job.id())) || state.actorMovements().containsKey(actor)
                || !FrontierV3SurfaceObservation.at(worker, job.movementOrder(ExtractionWorkAuthority.site(state, job)).legalStations().getFirst()))
            return false;
        var step = job.pending().orElse(null);
        if (step == null) {
            long tick = runtime.checkpointImage().orElseThrow().instant().ticks();
            if (!ResidentActivityCoordinator.ordinaryWorkPermitted(state, actor, tick)) return false;
            try {
                step = prepare(level, state, job, worker, actuation, tick);
                if (step == null) return false;
                return submit(level, runtime, job.id(), "effect-prepared", new ExtractionHotPrepared(job.id(), step));
            } catch (IllegalArgumentException unavailable) {
                LOGGER.debug("PMV3 mining preparation waiting job={} phase={} reason={}", job.id(), job.phase(), unavailable.getMessage());
                return false;
            }
        }
        try {
            var receipt = apply(level, state, job, step, worker, actuation);
            if (receipt == null) return false;
            boolean committed = submit(level, runtime, job.id(), "effect-observed", receipt);
            if (!committed) return false;
            var current = runtime.decodedState().orElseThrow();
            if (step instanceof ExtractionPhysicalStep.BlockWork block) {
                var target = job.target().orElseThrow();
                var cell = current.extractionSites().deposits().get(job.siteId()).cells().get(target.key().cell());
                var declared = new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE, job.siteId(),
                        WorksiteBlock.Role.RESOURCE, target.key().cell()), block.extraction().target(), cell.revision(), cell.knownBlock());
                var ledger = FrontierV3GrayboxLedger.get(level);
                ledger.worksite(new FrontierV3WorksiteBlockWitness(declared, block.extraction().definition().before(),
                        FrontierV3WorksiteBlockWitness.Phase.SETTLED));
                ledger.persist(level);
            } else {
                var container = ExtractionWorkAuthority.site(state, job).containerId();
                var physical = FrontierV3PhysicalContainer.loaded(level, current, container).orElseThrow();
                if (!FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedContainerMutation(runtime, container, physical))
                    throw new IllegalStateException("confirmed mining storage effect lacks its successor replica boundary");
            }
            FrontierV3ActorCarryProjection.rememberConfirmed(runtime.decodedState().orElseThrow(), actor, worker);
            return true;
        } catch (IllegalArgumentException changed) {
            // A retained ambiguous effect is never converted into a timeout unlock or replay.
            LOGGER.warn("PMV3 mining effect unresolved job={} revision={} phase={} step={} reason={}",
                    job.id(), job.revision(), job.phase(), step, changed.getMessage());
            return false;
        }
    }
    private static ExtractionPhysicalStep prepare(ServerLevel level, FrontierWorldState state, ExtractionWork job,
            Villager worker, FrontierV3ActorActuation actuation, long tick) {
        var observation = new ActorHotObservation(actuation.id(), job.revision());
        var site = ExtractionWorkAuthority.site(state, job);
        if (job.phase() == ExtractionWork.Phase.EXTRACT) {
            if (job.labour().isEmpty() || job.labour().orElseThrow().completedAt(tick) < job.labour().orElseThrow().requiredMilliWork()) return null;
            var target = job.target().orElseThrow(); var cell = site.layout().require(target.key().cell());
            var region = new ExtractionRegion(site.id(), Math.floorDiv(cell.source().x(), 16), Math.floorDiv(cell.source().z(), 16));
            var custody = state.replicaCustody().custodyByScope().get(region.scopeId());
            var witness = FrontierV3GrayboxLedger.get(level).worksite(new BlockPos(cell.source().x(), cell.source().y(), cell.source().z()));
            if (custody == null || custody.status() != PhysicalCustodyLeaseStatus.ACQUIRED || witness == null
                    || witness.phase() != FrontierV3WorksiteBlockWitness.Phase.SETTLED
                    || witness.declaration().revision() != target.revision()) return null;
            var tool = state.inventory().items().get(job.toolId());
            if (tool == null || !FrontierV3ExactItemPresentation.exactMatch(worker.getMainHandItem(), tool)) return null;
            int carried = ExtractionWorkAuthority.carried(state, job);
            if (!plain(worker.getOffhandItem(), job.outputKind(), carried)) return null;
            var extraction = new FrontierV3MinecraftBlockExtraction(level, worker, actuation).prepare(
                    ExtractionPhysicalStateSupport.operation(job), job.execution(), cell.source(), cell.definition());
            return new ExtractionPhysicalStep.BlockWork(observation, extraction, carried, custody.authorityEpoch());
        }
        var physical = FrontierV3PhysicalContainer.loaded(level, state, site.containerId()).orElse(null);
        if (physical == null || !ReferenceContainerCustody.hasOperationalCustody(state, site.containerId())
                || !ServiceAccessCoordinator.available(state, ExtractionServiceAccess.identity(state, job))) return null;
        long epoch = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(site.containerId())).authorityEpoch();
        if (job.phase() == ExtractionWork.Phase.STORE) {
            var order = ExtractionWorkEffects.cargo(state, job);
            var source = MaterialSourcePreparation.review(state, order).requireReady();
            var target = ContainerMaterialDestination.selectWhole(state, site.containerId(), job.outputKind(),
                    ((ActorContainerItemOrder.Portion.Fungible) order.portion()).lotQuantities(), Optional.empty()).orElse(null);
            if (target == null) return null;
            var transfer = new ActorItemTransferStep(observation, source, epoch, target.slot(), target.before());
            var physicalStep = new FrontierV3ActorItemTransfer.FungibleStep(order, physical, worker, worker.getUUID(), source, target.slot(), target.before());
            return physicalStep.before() ? new ExtractionPhysicalStep.Cargo(transfer) : null;
        }
        var order = ExtractionWorkEffects.equipment(state, job); var slot = order.exactSlot();
        if (job.phase() == ExtractionWork.Phase.TAKE_TOOL
                ? !worker.getMainHandItem().isEmpty() || !FrontierV3ExactItemPresentation.exactMatch(physical.inventory().getItem(slot.slot()),
                    ((ActorContainerItemOrder.Portion.Exact) order.portion()).item())
                : !physical.inventory().getItem(slot.slot()).isEmpty() || !FrontierV3ExactItemPresentation.exactMatch(worker.getMainHandItem(),
                    ((ActorContainerItemOrder.Portion.Exact) order.portion()).item())) return null;
        return new ExtractionPhysicalStep.Equipment(observation, slot, epoch);
    }
    private static ExtractionHotObserved apply(ServerLevel level, FrontierWorldState state, ExtractionWork job,
            ExtractionPhysicalStep step, Villager worker, FrontierV3ActorActuation actuation) {
        if (!step.observation().actuation().equals(actuation.id()) || step.observation().scopeRevision() != job.revision())
            throw new IllegalArgumentException("mining retained effect has a stale body/owner fence");
        switch (step) {
            case ExtractionPhysicalStep.BlockWork block -> {
                var source = new BlockPos(block.extraction().target().x(), block.extraction().target().y(), block.extraction().target().z());
                if (!level.hasChunkAt(source)) return null;
                int resultCount = block.carriedBefore() + block.extraction().output().getFirst().quantity();
                var adapter = new FrontierV3MinecraftBlockExtraction(level, worker, actuation);
                var ledger = FrontierV3GrayboxLedger.get(level); var witness = ledger.worksite(source);
                var key = new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE, job.siteId(),
                        WorksiteBlock.Role.RESOURCE, job.target().orElseThrow().key().cell());
                if (witness == null || !witness.declaration().key().equals(key)
                        || witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL
                        || witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL_PENDING)
                    throw new IllegalArgumentException("mining source has external or missing physical provenance");
                boolean sourceAfter = FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(source)).equals(block.extraction().definition().after());
                boolean handAfter = plain(worker.getOffhandItem(), job.outputKind(), resultCount);
                if (sourceAfter && witness.phase() != FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED)
                    throw new IllegalArgumentException("source postcondition alone is not proof of this worker's extraction");
                if (witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED
                        && !witness.effectOperation().equals(Optional.of(block.extraction().operationId())))
                    throw new IllegalArgumentException("source block-half receipt belongs to a different operation");
                var external = witness.externalSuccessor();
                if (external.isPresent() && !external.orElseThrow().equals(FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(source))))
                    throw new IllegalArgumentException("split mining successor lacks the current captured external fact");
                if (!sourceAfter || !handAfter) {
                    // The exact intent may have saved its block half before its hand half.
                    // Completing that retained split does not evaluate loot or mint twice.
                    if (!handAfter && !plain(worker.getOffhandItem(), job.outputKind(), block.carriedBefore()))
                        throw new IllegalArgumentException("mining output hand matches neither retained preimage nor postimage");
                    if (!sourceAfter && external.isEmpty()) {
                        var result = new BlockExtractionPort.Result[1];
                        FrontierV3WorksiteWrites.apply(key, () -> {
                            result[0] = adapter.apply(block.extraction());
                            return result[0] == BlockExtractionPort.Result.APPLIED;
                        });
                        if (result[0] != BlockExtractionPort.Result.APPLIED)
                            throw new IllegalArgumentException("retained mining block effect refused: " + result[0]);
                        ledger.worksite(new FrontierV3WorksiteBlockWitness(witness.declaration(), witness.before(),
                                FrontierV3WorksiteBlockWitness.Phase.EFFECT_BLOCK_APPLIED, Optional.of(block.extraction().operationId())));
                        ledger.persist(level); // Causal block-half receipt before completing its held-resource half.
                    }
                    if (!handAfter) worker.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(job.outputKind())), resultCount));
                }
                if (!FrontierV3MinecraftBlockExtraction.describe(level.getBlockState(source)).equals(external.orElse(block.extraction().definition().after()))
                        || !plain(worker.getOffhandItem(), job.outputKind(), resultCount))
                    throw new IllegalArgumentException("mining effect has no exact complete block/hand postcondition");
                worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                return new ExtractionHotObserved(job.id(), step, List.of(), List.of(ExtractionPhysicalStateSupport.hand(state, job, resultCount)), external);
            }
            case ExtractionPhysicalStep.Equipment equipment -> {
                var site = ExtractionWorkAuthority.site(state, job);
                var position = new BlockPos(site.layout().container().x(), site.layout().container().y(), site.layout().container().z());
                var chest = FrontierV3ContainerSurfaceExecutor.activeChest(level, position, site.containerId());
                if (chest == null) return null;
                var expected = state.inventory().items().get(job.toolId()); boolean take = job.phase() == ExtractionWork.Phase.TAKE_TOOL;
                boolean after = take ? chest.getItem(equipment.slot().slot()).isEmpty() && FrontierV3ExactItemPresentation.exactMatch(worker.getMainHandItem(), expected)
                        : worker.getMainHandItem().isEmpty() && FrontierV3ExactItemPresentation.exactMatch(chest.getItem(equipment.slot().slot()), expected);
                if (!after && !(take ? FrontierV3ActorItemTransfer.take(chest, worker, expected, equipment.slot(), EquipmentSlot.MAINHAND)
                        : FrontierV3ActorItemTransfer.place(chest, worker, expected, equipment.slot(), EquipmentSlot.MAINHAND)))
                    throw new IllegalArgumentException("equipment source/destination no longer matches its retained intent");
                return new ExtractionHotObserved(job.id(), step, List.of(), List.of());
            }
            case ExtractionPhysicalStep.Cargo cargo -> {
                var site = ExtractionWorkAuthority.site(state, job);
                var physical = FrontierV3PhysicalContainer.loaded(level, state, site.containerId()).orElse(null);
                if (physical == null) return null;
                var order = ExtractionWorkEffects.cargo(state, job); var transfer = cargo.transfer();
                var adapter = new FrontierV3ActorItemTransfer.FungibleStep(order, physical, worker, worker.getUUID(),
                        transfer.source(), transfer.destinationSlot(), transfer.destinationBefore());
                if (!adapter.after() && (!adapter.before() || !adapter.apply() || !adapter.after()))
                    throw new IllegalArgumentException("mining storage effect matches neither unapplied nor applied evidence");
                return new ExtractionHotObserved(job.id(), step, List.of(),
                        FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(physical.inventory(), state, site.containerId()));
            }
        }
    }
    private static boolean plain(ItemStack stack, String kind, int count) {
        return count == 0 ? stack.isEmpty() : stack.getCount() == count
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(kind)
                && ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem(), count));
    }
    private static boolean savedDeparture(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, ExtractionWork job) {
        if (job.pending().isPresent() || !ExtractionWorkAuthority.physicalCargo(state, job)) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var departure = ledger.bodyDeparture(job.execution().actorId()).orElse(null);
        int carried = ExtractionWorkAuthority.carried(state, job);
        if (departure == null || !departure.current(state) || !ledger.savedBodyDeparture(departure)
                || !departure.offhand().equals(Optional.of(new FrontierV3ActorBodyDeparture.HandStack(job.outputKind(), carried)))) return false;
        return submit(level, runtime, job.id(), "hand-saved-departure", new ExtractionHandCustodyObserved(job.id(),
                new ActorActuationId(new ActorBodyId(job.execution().actorId(), departure.identity().epoch()), job.execution()),
                ExtractionHandCustodyObserved.Boundary.SAVED_DEPARTURE, ExtractionPhysicalStateSupport.hand(state, job, carried)));
    }
    private static boolean submit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            SubjectId id, String operation, FrontierPayload payload) {
        var result = FrontierV3CommandSubmission.submit(runtime, "mining-" + operation, id.value(), payload);
        FrontierV3DiagnosticTrace.record(level.getServer(), "extraction:" + id.value(), operation, id, result);
        return result instanceof CommandResult.Accepted;
    }
}
