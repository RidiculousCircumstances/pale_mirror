package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.Container;

import java.util.List;
import java.util.Optional;

/** One prepared bakery effect: inspect pre/post, apply once, then report actual loaded custody. */
final class FrontierV3BakeryPhysicalEffect {
    private FrontierV3BakeryPhysicalEffect() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                     FrontierWorldState state, SceneLease lease, ProductionJob job, Villager worker) {
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        if (work.phase() == BakeryWorkState.Phase.STATION_LOAD && work.pendingPhysicalStep().isEmpty()
                && BakeryStationOperation.loadReadiness(state, job) != ProductionStationProcessing.StartReadiness.READY) return;
        if (work.phase() == BakeryWorkState.Phase.DEPOT_PICKUP
                && work.block().map(value -> value.reason() == BakeryWorkBlock.Reason.SOURCE_CHANGED).orElse(false)
                && state.inventory().fungibleResources().claims().values().stream()
                    .noneMatch(claim -> claim.claimantId().equals(job.id()))) return;
        SubjectId containerId = work.phase() == BakeryWorkState.Phase.DEPOT_PICKUP
                || work.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY
                ? BakeryStorage.current(job) : station(state, work).containerId();
        if (work.pendingPhysicalStep().isEmpty()
                ? !ReferenceContainerCustody.hasOperationalCustody(state, containerId)
                : !ReferenceContainerCustody.hasLiveCustody(state, containerId)
                || ReferenceContainerCustody.blocksCanonicalUse(state, containerId)) return;
        ContainerSurface surface = state.inventory().surfaces().get(containerId);
        if (surface == null || !level.hasChunkAt(new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()))) return;
        var physical = FrontierV3PhysicalContainer.loaded(level, state, containerId).orElse(null);
        if (physical == null) return;
        var chest = physical.inventory();
        if (materializeColdHand(level, runtime, state, lease, job, worker)) return;
        int destinationSlot = switch (work.phase()) {
            case DEPOT_PICKUP, STATION_UNLOAD -> -1;
            case STATION_LOAD -> station(state, work).inputSlot();
            case PROCESSING -> station(state, work).outputSlot();
            case DEPOT_DELIVERY -> work.pendingPhysicalStep().map(BakeryPhysicalStep::destinationSlot)
                    .orElseGet(() -> ProductionOutputCapacity.deliverySlot(state, job).orElse(-1));
            case DELIVERED -> throw new IllegalStateException("delivered bakery work cannot prepare another effect");
        };
        if (destinationSlot < 0 && (work.phase() == BakeryWorkState.Phase.STATION_LOAD
                || work.phase() == BakeryWorkState.Phase.PROCESSING
                || work.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY)) {
            block(level, runtime, lease, job, new BakeryWorkBlock(BakeryWorkBlock.Reason.DESTINATION_OCCUPIED,
                    containerId, -1, "minecraft:air", 0));
            return;
        }
        Shape shape;
        try { shape = shape(state, lease, job, worker, physical, containerId, destinationSlot); }
        catch (UnboundContainerSource waitingForObservation) { return; }
        catch (IllegalArgumentException invalid) {
            block(level, runtime, lease, job, new BakeryWorkBlock(BakeryWorkBlock.Reason.MACHINE_UNAVAILABLE,
                    containerId, -1, "minecraft:air", 0));
            return;
        }
        BakeryPhysicalStep pending = work.pendingPhysicalStep().orElse(null);
        if (pending == null) {
            if (!shape.before()) {
                block(level, runtime, lease, job, shape.preconditionBlock());
                return;
            }
            if (work.block().isPresent()) {
                clearBlock(level, runtime, lease, job);
                return;
            }
            FrontierV3CommandSubmission.submit(runtime, "bakery-effect-prepare", lease.id().value(),
                    new BakeryHotEffectPrepared(job.id(), lease.id(), work.phase(), destinationSlot));
            return;
        }
        if (!pending.leaseId().equals(lease.id()) || pending.destinationSlot() != destinationSlot) return;
        // A rejected post-effect receipt is non-replayable. Keep the physical
        // evidence and pending step stable for explicit reconciliation.
        if (work.block().map(value -> value.reason() == BakeryWorkBlock.Reason.AMBIGUOUS_EFFECT).orElse(false)) return;
        var participants = participants(shape);
        var declaration = declaration(shape, participants);
        var effect = FrontierV3MinecraftOperation.apply(level, declaration, participants, shape::before,
                () -> { shape.apply(); return true; }, shape::after);
        if (effect.status() == FrontierV3MinecraftOperation.Status.WAITING_DURABILITY) return;
        if (effect.complete()) {
            observe(level, runtime, lease, job, containerId, effect.ticket(), shape.observation());
        } else if (effect.status() == FrontierV3MinecraftOperation.Status.NOT_APPLIED && shape.canAbortUnappliedDelivery()) {
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "bakery_delivery_aborted", lease,
                    FrontierV3CommandSubmission.submit(runtime, "bakery-delivery-abort", lease.id().value(), shape.cancellation()));
        } else {
            block(level, runtime, lease, job, new BakeryWorkBlock(BakeryWorkBlock.Reason.AMBIGUOUS_EFFECT,
                    containerId, destinationSlot, "minecraft:air", 0));
        }
    }

    /** Capture the existing boundary before loot changes the hand. Never execute a transfer here. */
    static Optional<FrontierV3OperationAftermath> deathEffect(ServerLevel level, FrontierWorldState state,
            ProductionJob job, Villager worker, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId identity) {
        var work = job.bakeryWork().orElseThrow(); var pending = work.pendingPhysicalStep().orElse(null);
        // Station-only effects are not cancelled by a body outcome. An unresolved
        // legacy exact recipe remains retained; it is not an unused hand transfer.
        if (pending == null || pending.phase() == BakeryWorkState.Phase.PROCESSING) return Optional.empty();
        var lease = state.sceneLeases().get(pending.leaseId());
        if (lease == null || lease.members().size() != 1 || !lease.members().getFirst().entityId().equals(worker.getUUID())
                || !identity.body().actorId().equals(job.workerId()) || !identity.execution().activityOwnerId().equals(job.id()))
            throw new IllegalArgumentException("bakery death has no exact retained transfer scene");
        var container = work.phase() == BakeryWorkState.Phase.DEPOT_PICKUP || work.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY
                ? BakeryStorage.current(job) : station(state, work).containerId();
        var physical = FrontierV3PhysicalContainer.loaded(level, state, container).orElse(null);
        if (physical == null) return Optional.empty();
        var shape = shape(state, lease, job, worker, physical, container, pending.destinationSlot());
        boolean applied = shape.after();
        if (!applied && !shape.before()) return Optional.empty();
        var effect = applied ? shape.observation() : new BakeryHotEffectObserved(job.id(), lease.id(), work.phase(),
                BakeryWorkGoal.current(state, job).station().standingBody(), shape.sourceEpoch(), shape.destinationEpoch(), List.of(), List.of());
        var ports = participants(shape);
        return FrontierV3OperationAftermath.capture(level, declaration(shape, ports), ports,
                new BakeryRetiredEffectObserved(identity, effect, applied), applied, applied ? shape::after : shape::before);
    }

    private static List<FrontierV3MinecraftOperation.Participant> participants(Shape shape) {
        return shape.phase() == BakeryWorkState.Phase.PROCESSING ? List.of(shape.physical().persistence().get())
                : List.of(shape.physical().persistence().get(), FrontierV3OperationEntityParticipant.current(shape.worker(),
                    ActorBodyAuthority.current(shape.state(), shape.job().workerId()).physicalEpoch()));
    }
    private static FrontierV3OperationReceipt.Declaration declaration(Shape shape, List<FrontierV3MinecraftOperation.Participant> participants) {
        String preparation = FrontierV3OperationConfirmation.fingerprint(new BakeryHotEffectPrepared(shape.job().id(),
                shape.lease().id(), shape.phase(), shape.destinationSlot()));
        String identity = preparation + ":" + shape.sourceEpoch() + ":" + shape.destinationEpoch() + ":" + shape.kind();
        return new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.BAKERY, shape.job().id(), identity, identity,
                participants.stream().map(FrontierV3MinecraftOperation.Participant::identity).collect(java.util.stream.Collectors.toSet()));
    }

    private static void observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                SceneLease lease, ProductionJob job, SubjectId containerId,
                                java.util.UUID ticket, BakeryHotEffectObserved receipt) {
        if (!FrontierV3OperationConfirmation.submit(level, runtime, ticket, receipt)) {
            // The chest/hand may already contain the effect. Keep the prepared
            // step and its exact worker local; do not quarantine unrelated sites.
            block(level, runtime, lease, job, new BakeryWorkBlock(BakeryWorkBlock.Reason.AMBIGUOUS_EFFECT,
                    containerId, receipt.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY
                    ? job.bakeryWork().orElseThrow().pendingPhysicalStep().orElseThrow().destinationSlot() : -1,
                    "minecraft:air", 0));
            return;
        }
    }

    static void block(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                      SceneLease lease, ProductionJob job, BakeryWorkBlock reason) {
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        if (work.block().equals(Optional.of(reason))) return;
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "bakery_block_" + reason.reason(), lease,
                FrontierV3CommandSubmission.submit(runtime, "bakery-hot-block", lease.id().value(),
                        new BakeryHotBlockChanged(job.id(), lease.id(), work.phase(), Optional.of(reason))));
    }

    static void clearBlock(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                           SceneLease lease, ProductionJob job) {
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        if (work.block().isEmpty()) return;
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "bakery_block_cleared", lease,
                FrontierV3CommandSubmission.submit(runtime, "bakery-hot-block-clear", lease.id().value(),
                        new BakeryHotBlockChanged(job.id(), lease.id(), work.phase(), Optional.empty())));
    }

    private static final class UnboundContainerSource extends IllegalArgumentException {
        UnboundContainerSource() { super("reference container has not yet published its fungible layout"); }
    }

    private static boolean materializeColdHand(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                               FrontierWorldState state, SceneLease lease, ProductionJob job, Villager worker) {
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        if (job.inputHold() instanceof ProductionInputHold.Materialized
                || work.phase() != BakeryWorkState.Phase.STATION_LOAD
                && work.phase() != BakeryWorkState.Phase.DEPOT_DELIVERY) return false;
        FungibleResourceLedger ledger = state.inventory().fungibleResources();
        if (ledger.bindings().values().stream().anyMatch(binding -> binding.accountId().equals(work.actorAccountId())))
            return false;
        String kind = BakeryCargo.kind(job);
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(kind));
        ItemStack hand = worker.getItemBySlot(EquipmentSlot.MAINHAND);
        if (item == Items.AIR || hand.getCount() != BakeryCargo.quantity(job)
                || !ItemStack.isSameItemSameComponents(hand, new ItemStack(item, BakeryCargo.quantity(job)))) {
            block(level, runtime, lease, job, observedBlock(BakeryWorkBlock.Reason.HAND_MISMATCH,
                    work.phase() == BakeryWorkState.Phase.STATION_LOAD
                            ? station(state, work).containerId() : BakeryStorage.current(job), -1, hand));
            return true;
        }
        if (work.block().isPresent()) {
            clearBlock(level, runtime, lease, job);
            return true;
        }
        var stack = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(job.workerId(),
                lease.members().getFirst().entityId(), ActorContainerItemOrder.Hand.MAIN), kind, BakeryCargo.quantity(job));
        var observed = new BakeryHotHandMaterialized(job.id(), lease.id(), work.actorAccountId(),
                Math.max(1L, lease.revision()), stack);
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "bakery_cold_hand_materialized", lease,
                FrontierV3CommandSubmission.submit(runtime, "bakery-cold-hand-materialized", lease.id().value(), observed));
        return true;
    }

    private static Shape shape(FrontierWorldState state, SceneLease lease, ProductionJob job, Villager worker,
                               FrontierV3PhysicalContainer physical, SubjectId containerId, int destinationSlot) {
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        boolean exact = job.inputHold() instanceof ProductionInputHold.Materialized;
        ActorContainerItemOrder order = work.phase() == BakeryWorkState.Phase.PROCESSING
                ? null : io.farfrontier.palemirror.frontier.v3.process.BakeryProcess.currentItemOrder(state, job);
        String kind = BakeryCargo.kind(job);
        ExactItemStack exactSource = exact ? state.inventory().items().get(work.phase() == BakeryWorkState.Phase.STATION_UNLOAD
                || work.phase() == BakeryWorkState.Phase.DEPOT_DELIVERY ? job.outputItemId() : job.consumedItemId()) : null;
        ExactItemStack exactOutput = exact && work.phase() == BakeryWorkState.Phase.PROCESSING
                ? new ExactItemStack(job.outputItemId(), job.rights().resourceOwner().id(), job.outputItemKind(), job.outputCount(),
                new InventoryCustody.ContainerSlot(containerId, destinationSlot)) : null;
        if (exact && exactSource == null) throw new IllegalArgumentException("bakery physical source item absent");
        List<MaterialSourceSelection.Slice> slices = exact ? List.of() : sourceSlices(state, lease, job, order, kind);
        long sourceEpoch = slices.isEmpty() ? 0L : slices.getFirst().epoch();
        long destinationEpoch = exact ? 0L : (work.phase() == BakeryWorkState.Phase.DEPOT_PICKUP
                || work.phase() == BakeryWorkState.Phase.STATION_UNLOAD ? Math.max(1L, lease.revision())
                : state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(containerId)).authorityEpoch());
        return new Shape(state, lease, job, worker, physical, containerId, destinationSlot, order,
                kind, exactSource, exactOutput, slices, sourceEpoch, destinationEpoch);
    }

    private static List<MaterialSourceSelection.Slice> sourceSlices(FrontierWorldState state, SceneLease lease, ProductionJob job,
                                            ActorContainerItemOrder order, String kind) {
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        SubjectId account = switch (work.phase()) {
            case DEPOT_PICKUP -> work.sourceAccountId();
            case STATION_LOAD, DEPOT_DELIVERY -> work.actorAccountId();
            case PROCESSING, STATION_UNLOAD -> work.stationAccountId();
            case DELIVERED -> throw new IllegalStateException("delivered bakery work has no physical source");
        };
        if (state.inventory().fungibleResources().bindings().values().stream()
                .noneMatch(value -> value.accountId().equals(account))) {
            if (work.phase() == BakeryWorkState.Phase.DEPOT_PICKUP
                    || work.phase() == BakeryWorkState.Phase.STATION_UNLOAD
                    || work.phase() == BakeryWorkState.Phase.PROCESSING)
                throw new UnboundContainerSource();
            throw new IllegalArgumentException("bakery actor hand is not bound to its current scene");
        }
        List<MaterialSourceSelection.Slice> slices = order == null
                ? MaterialSourceSelection.select(state.inventory().fungibleResources(),
                        account, kind, job.inputQuantities())
                : MaterialSourceSelection.select(state.inventory().fungibleResources(), order);
        for (MaterialSourceSelection.Slice source : slices) {
            switch (source.address()) {
                case PhysicalStackAddress.ContainerSlot value -> {
                    if (!value.slot().containerId().equals(work.phase() == BakeryWorkState.Phase.DEPOT_PICKUP
                            ? BakeryStorage.current(job) : station(state, work).containerId()))
                        throw new IllegalArgumentException("bakery source is bound to another container");
                }
                case PhysicalStackAddress.ActorHand value -> {
                    if (!value.actorId().equals(job.workerId()) || !value.entityId().equals(lease.members().getFirst().entityId()))
                        throw new IllegalArgumentException("bakery actor hand belongs to another body");
                }
                default -> throw new IllegalArgumentException("bakery source is not a declared chest or hand");
            }
        }
        return slices;
    }

    private static ProductionStationSpec station(FrontierWorldState state, BakeryWorkState work) {
        return state.inventory().containers().values().stream().flatMap(container -> container.productionStation().stream())
                .filter(value -> value.id().equals(work.stationId())).findFirst().orElseThrow();
    }

    private static BakeryWorkBlock observedBlock(BakeryWorkBlock.Reason reason, SubjectId containerId,
                                                  int slot, ItemStack actual) {
        return new BakeryWorkBlock(reason, containerId, slot,
                actual.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(actual.getItem()).toString(),
                actual.getCount());
    }

    private record Shape(FrontierWorldState state, SceneLease lease, ProductionJob job, Villager worker,
                         FrontierV3PhysicalContainer physical, SubjectId containerId, int destinationSlot,
                         ActorContainerItemOrder order, String kind,
                         ExactItemStack exactSource, ExactItemStack exactOutput,
                         List<MaterialSourceSelection.Slice> slices, long sourceEpoch, long destinationEpoch) {
        BakeryWorkState.Phase phase() { return job.bakeryWork().orElseThrow().phase(); }
        Container chest() { return physical.inventory(); }
        ItemStack hand() { return worker.getItemBySlot(EquipmentSlot.MAINHAND); }
        ItemStack source(MaterialSourceSelection.Slice slice) {
            return switch (slice.address()) {
                case PhysicalStackAddress.ContainerSlot address -> chest().getItem(address.slot().slot());
                case PhysicalStackAddress.ActorHand ignored -> hand();
                default -> throw new IllegalArgumentException("bakery source is not a chest or hand");
            };
        }
        int sourceSlot(MaterialSourceSelection.Slice slice) {
            return slice.address() instanceof PhysicalStackAddress.ContainerSlot address ? address.slot().slot() : -1;
        }
        FrontierV3ActorItemTransfer.FungibleStep transfer() {
            return new FrontierV3ActorItemTransfer.FungibleStep(order, physical, worker,
                    lease.members().getFirst().entityId(), slices, destinationSlot, 0);
        }
        FrontierV3StationRecipeEffect recipeEffect() {
            var station = station(state, job.bakeryWork().orElseThrow());
            ItemStack input, output;
            if (exactSource != null) {
                if (!exactSource.custody().equals(new InventoryCustody.ContainerSlot(containerId, station.inputSlot())))
                    throw new IllegalArgumentException("station input lacks its declared physical port");
                input = FrontierV3ExactItemPresentation.materializedStack(exactSource);
                output = FrontierV3ExactItemPresentation.materializedStack(exactOutput);
            } else {
                if (slices.size() != 1 || sourceSlot(slices.getFirst()) != station.inputSlot())
                    throw new IllegalArgumentException("station recipe input must occupy its declared port");
                input = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(kind)), slices.getFirst().before());
                output = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(job.outputItemKind())), job.outputCount());
            }
            return new FrontierV3StationRecipeEffect(chest(), station,
                    state.bootstrap().ruleset().production().require(job.bakeryWork().orElseThrow().recipeId()),
                    input, job.inputCount(), output);
        }
        boolean plain(ItemStack stack, String itemKind, int quantity) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemKind));
            return item != Items.AIR && ItemStack.isSameItemSameComponents(stack, new ItemStack(item, quantity))
                    && stack.getCount() == quantity;
        }
        boolean sourceMatches(boolean after) {
            for (MaterialSourceSelection.Slice slice : slices) {
                int expected = slice.before() - (after ? slice.moved() : 0);
                if (expected == 0 ? !source(slice).isEmpty() : !plain(source(slice), kind, expected)) return false;
            }
            return true;
        }
        boolean before() {
            if (exactSource != null) return exactBefore();
            return switch (phase()) {
                case DEPOT_PICKUP, STATION_UNLOAD, STATION_LOAD, DEPOT_DELIVERY -> transfer().before();
                case PROCESSING -> recipeEffect().before();
                case DELIVERED -> false;
            };
        }
        BakeryWorkBlock preconditionBlock() {
            boolean sourceInHand = phase() == BakeryWorkState.Phase.STATION_LOAD
                    || phase() == BakeryWorkState.Phase.DEPOT_DELIVERY;
            if (exactSource != null) {
                if (sourceInHand && !FrontierV3ExactItemPresentation.exactMatch(hand(), exactSource))
                    return observedBlock(BakeryWorkBlock.Reason.HAND_MISMATCH, containerId, -1, hand());
                if (!sourceInHand) {
                    int slot = ((InventoryCustody.ContainerSlot) exactSource.custody()).slot();
                    if (!FrontierV3ExactItemPresentation.exactMatch(chest().getItem(slot), exactSource))
                        return observedBlock(BakeryWorkBlock.Reason.SOURCE_CHANGED, containerId, slot, chest().getItem(slot));
                }
            } else for (MaterialSourceSelection.Slice slice : slices) {
                int slot = sourceSlot(slice);
                if (!plain(source(slice), kind, slice.before()))
                    return observedBlock(sourceInHand ? BakeryWorkBlock.Reason.HAND_MISMATCH
                            : BakeryWorkBlock.Reason.SOURCE_CHANGED, containerId, slot, source(slice));
            }
            if (phase() == BakeryWorkState.Phase.DEPOT_PICKUP || phase() == BakeryWorkState.Phase.STATION_UNLOAD)
                return observedBlock(BakeryWorkBlock.Reason.HAND_MISMATCH, containerId, -1, hand());
            return observedBlock(BakeryWorkBlock.Reason.DESTINATION_OCCUPIED, containerId,
                    destinationSlot, chest().getItem(destinationSlot));
        }
        boolean after() {
            if (exactSource != null) return exactAfter();
            return switch (phase()) {
                case DEPOT_PICKUP, STATION_UNLOAD, STATION_LOAD, DEPOT_DELIVERY -> transfer().after();
                case PROCESSING -> recipeEffect().after();
                case DELIVERED -> false;
            };
        }
        boolean canAbortUnappliedDelivery() {
            return phase() == BakeryWorkState.Phase.DEPOT_DELIVERY && !chest().getItem(destinationSlot).isEmpty()
                    && (exactSource != null ? FrontierV3ExactItemPresentation.exactMatch(hand(), exactSource)
                        : transfer().unappliedDestinationOccupied());
        }
        BakeryHotDeliveryAborted cancellation() {
            if (!canAbortUnappliedDelivery()) throw new IllegalStateException("cannot cancel a possibly applied delivery");
            return new BakeryHotDeliveryAborted(job.id(), lease.id(), destinationSlot, sourceEpoch,
                    new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(job.workerId(),
                            lease.members().getFirst().entityId(), ActorContainerItemOrder.Hand.MAIN), kind, job.outputCount()),
                    exactSource == null ? Optional.empty() : Optional.of(exactSource.id()),
                    observedBlock(BakeryWorkBlock.Reason.DESTINATION_OCCUPIED, containerId, destinationSlot, chest().getItem(destinationSlot)));
        }
        boolean exactBefore() {
            return switch (phase()) {
                case DEPOT_PICKUP, STATION_UNLOAD -> FrontierV3ExactItemPresentation.exactMatch(chest().getItem(((InventoryCustody.ContainerSlot) exactSource.custody()).slot()), exactSource)
                        && hand().isEmpty();
                case STATION_LOAD, DEPOT_DELIVERY -> FrontierV3ExactItemPresentation.exactMatch(hand(), exactSource)
                        && chest().getItem(destinationSlot).isEmpty();
                case PROCESSING -> recipeEffect().before();
                case DELIVERED -> false;
            };
        }
        boolean exactAfter() {
            return switch (phase()) {
                case DEPOT_PICKUP, STATION_UNLOAD -> chest().getItem(((InventoryCustody.ContainerSlot) exactSource.custody()).slot()).isEmpty()
                        && FrontierV3ExactItemPresentation.exactMatch(hand(), exactSource);
                case STATION_LOAD, DEPOT_DELIVERY -> hand().isEmpty()
                        && FrontierV3ExactItemPresentation.exactMatch(chest().getItem(destinationSlot), exactSource);
                case PROCESSING -> recipeEffect().after();
                case DELIVERED -> false;
            };
        }
        void apply() {
            if (!before()) throw new IllegalStateException("bakery physical precondition disappeared before effect");
            if (exactSource != null) {
                switch (phase()) {
                    case DEPOT_PICKUP, STATION_UNLOAD -> FrontierV3ActorItemTransfer.take(chest(), worker, exactSource,
                            (InventoryCustody.ContainerSlot) exactSource.custody(), EquipmentSlot.MAINHAND);
                    case STATION_LOAD, DEPOT_DELIVERY -> FrontierV3ActorItemTransfer.place(chest(), worker, exactSource,
                            new InventoryCustody.ContainerSlot(containerId, destinationSlot), EquipmentSlot.MAINHAND);
                    case PROCESSING -> recipeEffect().apply();
                    case DELIVERED -> throw new IllegalStateException("delivered bakery work has no effect");
                }
            } else {
                switch (phase()) {
                    case DEPOT_PICKUP, STATION_UNLOAD, STATION_LOAD, DEPOT_DELIVERY -> {
                        if (!transfer().apply()) throw new IllegalStateException("declared actor material transfer lost its physical postcondition");
                    }
                    case PROCESSING -> recipeEffect().apply();
                    case DELIVERED -> throw new IllegalStateException("delivered bakery work has no effect");
                }
            }
        }
        BakeryHotEffectObserved observation() {
            if (!after()) throw new IllegalStateException("bakery physical postcondition was not observed");
            List<FungiblePhysicalObservation.Stack> remaining = List.of(), destination = List.of();
            if (exactSource == null) {
                if (phase() == BakeryWorkState.Phase.DEPOT_PICKUP || phase() == BakeryWorkState.Phase.STATION_UNLOAD)
                    remaining = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest(), state, containerId);
                if (phase() == BakeryWorkState.Phase.STATION_LOAD || phase() == BakeryWorkState.Phase.DEPOT_DELIVERY)
                    destination = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest(), state, containerId);
                else if (phase() == BakeryWorkState.Phase.PROCESSING)
                    destination = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest(), state, containerId);
                else destination = List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(
                        job.workerId(), lease.members().getFirst().entityId(), ActorContainerItemOrder.Hand.MAIN),
                        kind, BakeryCargo.quantity(job)));
            }
            return new BakeryHotEffectObserved(job.id(), lease.id(), phase(),
                    BakeryWorkGoal.current(state, job).station().standingBody(), sourceEpoch, destinationEpoch,
                    remaining, destination);
        }
    }
}
