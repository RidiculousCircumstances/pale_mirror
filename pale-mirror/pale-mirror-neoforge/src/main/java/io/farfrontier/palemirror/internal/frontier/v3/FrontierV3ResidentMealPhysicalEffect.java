package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.List;
import java.util.Map;

/** Loaded executor for the resident's retained meal; only a WAL-prepared step may mutate stacks. */
final class FrontierV3ResidentMealPhysicalEffect {
    private FrontierV3ResidentMealPhysicalEffect() { }

    static boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                        FrontierWorldState state, SubjectId residentId, Mob body, AmbientActorLease lease) {
        ResidentMeal meal = state.humanPopulation().meals().get(residentId);
        if (meal == null || lease.goal() != AmbientGoalKind.MEAL || lease.status() != AmbientLeaseStatus.HOT
                || !(body instanceof Villager worker) || !body.getUUID().equals(
                        SceneLease.deterministicEntityId(state.bootstrap().worldId(), residentId))
                || !FrontierV3SurfaceObservation.at(body, ResidentMealProcess.serviceSurface(state, meal))) return false;
        if (meal.phase() == ResidentMeal.Phase.TAKE) return take(level, runtime, state, meal, worker, lease);
        if (meal.phase() == ResidentMeal.Phase.CONSUME) return consume(level, runtime, state, meal, worker, lease);
        return false;
    }

    private static boolean take(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, ResidentMeal meal, Villager worker, AmbientActorLease lease) {
        ContainerSurface surface = state.inventory().surfaces().get(meal.depotId());
        if (surface == null) return false;
        BlockPos position = new BlockPos(surface.position().x(), surface.position().y(), surface.position().z());
        if (!level.hasChunkAt(position)) return false;
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.activeChest(level, position, meal.depotId());
        if (chest == null) return false;
        if (meal.pendingPhysicalStep().isEmpty()
                && !ReferenceContainerCustody.hasOperationalCustody(state, meal.depotId())) return false;
        if (meal.pendingPhysicalStep().isPresent()
                && (!ReferenceContainerCustody.hasLiveCustody(state, meal.depotId())
                    || ReferenceContainerCustody.blocksCanonicalUse(state, meal.depotId()))) return false;
        List<MaterialSourceSelection.Slice> slices;
        ActorContainerItemOrder order;
        try {
            order = ResidentMealProcess.takeOrder(state, meal);
            slices = MaterialSourceSelection.select(state.inventory().fungibleResources(), order);
        } catch (IllegalArgumentException changed) { return false; }
        if (slices.isEmpty() || slices.stream().anyMatch(slice -> !(slice.address() instanceof PhysicalStackAddress.ContainerSlot))) return false;
        var sourceCounts = ResidentMealPhysicalStep.sourceCounts(slices);
        var transfer = new FrontierV3ActorItemTransfer.FungibleStep(order, chest, worker,
                worker.getUUID(), slices, -1);
        ResidentMealPhysicalStep pending = meal.pendingPhysicalStep().orElse(null);
        if (pending == null) {
            if (!transfer.before()) return false;
            ResidentMealPhysicalStep step = new ResidentMealPhysicalStep(ResidentMeal.Phase.TAKE,
                    sourceCounts, 0, slices.getFirst().epoch(),
                    lease.revision(), lease.revision());
            return accepted(level, runtime, meal.residentId(), "resident-meal-take-prepare",
                    new ResidentMealHotEffectPrepared(meal.residentId(), step));
        }
        if (pending.phase() != ResidentMeal.Phase.TAKE || pending.ambientRevision() != lease.revision()
                || !pending.sourceCounts().equals(sourceCounts)
                || pending.sourceEpoch() != slices.getFirst().epoch()) return false;
        if (!transfer.after()) {
            if (!transfer.before() || !transfer.apply() || !transfer.after()) return false;
        }
        List<FungiblePhysicalObservation.Stack> remaining =
                FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest, state, meal.depotId());
        var held = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(
                meal.residentId(), worker.getUUID()), meal.portion().itemKind(), meal.portion().quantity());
        boolean applied = accepted(level, runtime, meal.residentId(), "resident-meal-take-observed",
                new ResidentMealHotEffectObserved(meal.residentId(), ResidentMeal.Phase.TAKE,
                        lease.revision(), lease.goalBody(), remaining, List.of(held)));
        if (applied && !FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedMutation(
                runtime, meal.depotId(), chest))
            throw new IllegalStateException("resident bread take lacks its next depot replica boundary");
        return applied;
    }

    private static boolean consume(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                   FrontierWorldState state, ResidentMeal meal, Villager worker, AmbientActorLease lease) {
        var ledger = state.inventory().fungibleResources();
        List<PhysicalStackBinding> bindings = ledger.bindings().values().stream()
                .filter(binding -> binding.accountId().equals(meal.actorAccountId())).toList();
        ItemStack actual = worker.getItemBySlot(EquipmentSlot.OFFHAND);
        boolean before = FrontierV3ResidentMealItems.matches(actual, meal.portion());
        if (bindings.isEmpty() && meal.pendingPhysicalStep().isEmpty() && before)
            return accepted(level, runtime, meal.residentId(), "resident-meal-hand-materialized",
                    new ResidentMealHotHandMaterialized(meal.residentId(), lease.revision(),
                            new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(
                                    meal.residentId(), worker.getUUID()), meal.portion().itemKind(), meal.portion().quantity())));
        if (bindings.size() != 1 || bindings.getFirst().quantity() != meal.portion().quantity()
                || !bindings.getFirst().lotQuantities().equals(meal.portion().lotQuantities())
                || !(bindings.getFirst().address() instanceof PhysicalStackAddress.ActorHand hand)
                || !hand.actorId().equals(meal.residentId()) || !hand.entityId().equals(worker.getUUID())) return false;
        ResidentMealPhysicalStep pending = meal.pendingPhysicalStep().orElse(null);
        if (pending == null) {
            if (!before) return false;
            return accepted(level, runtime, meal.residentId(), "resident-meal-consume-prepare",
                    new ResidentMealHotEffectPrepared(meal.residentId(), new ResidentMealPhysicalStep(
                            ResidentMeal.Phase.CONSUME, -1, meal.portion().quantity(), bindings.getFirst().authorityEpoch(),
                            0L, lease.revision())));
        }
        if (pending.phase() != ResidentMeal.Phase.CONSUME || pending.ambientRevision() != lease.revision()
                || pending.consumptionQuantity() != meal.portion().quantity()
                || pending.sourceEpoch() != bindings.getFirst().authorityEpoch()) return false;
        if (before) {
            worker.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            level.playSound(null, worker.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8F, 1.0F);
        } else if (!actual.isEmpty()) return false;
        return accepted(level, runtime, meal.residentId(), "resident-meal-consume-observed",
                new ResidentMealHotEffectObserved(meal.residentId(), ResidentMeal.Phase.CONSUME,
                        lease.revision(), lease.goalBody(), List.of(), List.of()));
    }

    private static boolean accepted(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    SubjectId residentId, String operation, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        CommandResult result = FrontierV3CommandSubmission.submit(runtime, operation, residentId.value(), payload);
        FrontierV3DiagnosticTrace.record(level.getServer(), "resident-meal:" + residentId.value(), operation, residentId, result);
        return result instanceof CommandResult.Accepted;
    }
}
