package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Food owner reads the identified pre-loot body; never walks, feeds or reconstructs it. */
final class FrontierV3ResidentMealDeathResources {
    private static final String DROP_RECEIPT_KEY = "pmv3_retired_meal_drop_v1";
    private FrontierV3ResidentMealDeathResources() { }

    static FrontierV3ActorDeathResourceComposition.AfterFatality prepare(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, ActorBodyId id) {
        var state = runtime.decodedState().orElseThrow();
        var meal = state.humanPopulation().meals().get(id.actorId());
        if (meal == null || !(body instanceof Villager)
                || !ActorBodyAuthority.current(state, id.actorId()).equals(id)) return () -> { };
        ItemStack beforeLoot = FrontierV3ActorResourceSlots.get(body, meal.inventorySlot()).copy();
        var preparedEffect = prepareEffect(level, runtime, body, id, state, meal);
        // The indexed dying body is the positive source witness. An absent corpse after
        // loot/unload is deliberately not accepted by this port.
        return () -> {
            preparedEffect.settle();
            settlePortion(level, runtime, body, id, meal, beforeLoot);
        };
    }

    private static FrontierV3ActorDeathResourceComposition.AfterFatality prepareEffect(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, ActorBodyId id,
            FrontierWorldState state, ResidentMeal meal) {
        if (meal.pendingPhysicalStep().isEmpty()) return () -> { };
        Villager worker = (Villager) body;
        var step = meal.pendingPhysicalStep().orElseThrow();
        var actual = FrontierV3ActorResourceSlots.get(body, meal.inventorySlot());
        if (step.phase() == ResidentMeal.Phase.CONSUME) {
            // This is LivingDeathEvent at the head of die(), BEFORE loot/removal. It is not
            // an empty corpse/lookup observed after disappearance. A nonempty pocket proves
            // no consumption and remains a separate carried-resource obligation.
            var held = FrontierV3ResidentMealItems.heldPortion(state, meal);
            int remainingCount = held.quantity() - meal.portion().quantity();
            if (remainingCount == 0 ? !actual.isEmpty() : actual.getCount() != remainingCount
                    || !ItemStack.isSameItemSameComponents(actual, FrontierV3ResidentMealItems.stack(held))) return () -> { };
            var remainder = remainingCount == 0 ? List.<FungiblePhysicalObservation.Stack>of()
                    : List.of(new FungiblePhysicalObservation.Stack(FrontierV3ActorResourceSlots.address(
                            meal.residentId(), worker, meal.inventorySlot()), held.itemKind(), remainingCount));
            var receipt = new ResidentMealResourceEffectObserved(id, meal.executionId(), step,
                    ResidentMealResourceEffectObserved.Outcome.CONSUMPTION_APPLIED, remainder, List.of());
            return () -> submit(level, runtime, receipt);
        }
        var surface = state.inventory().surfaces().get(meal.depotId());
        if (surface == null || !ReferenceContainerCustody.hasLiveCustody(state, meal.depotId())
                || ReferenceContainerCustody.blocksCanonicalUse(state, meal.depotId())) return () -> { };
        var position = new BlockPos(surface.position().x(), surface.position().y(), surface.position().z());
        if (!level.hasChunkAt(position)) return () -> { };
        var chest = FrontierV3ContainerSurfaceExecutor.activeChest(level, position, meal.depotId());
        if (chest == null) return () -> { };
        var order = ResidentMealProcess.takeOrder(state, meal);
        var slices = MaterialSourceSelection.select(state.inventory().fungibleResources(), order);
        if (slices.isEmpty() || slices.stream().anyMatch(slice -> slice.epoch() != step.sourceEpoch())
                || !ResidentMealPhysicalStep.sourceCounts(slices).equals(step.sourceCounts())) return () -> { };
        var transfer = new FrontierV3ActorItemTransfer.FungibleStep(order, chest, worker, worker.getUUID(), slices, -1);
        var source = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest, state, meal.depotId());
        ResidentMealResourceEffectObserved receipt;
        if (actual.isEmpty() && transfer.before()) {
            receipt = new ResidentMealResourceEffectObserved(id, meal.executionId(), step,
                    ResidentMealResourceEffectObserved.Outcome.TAKE_UNAPPLIED, source, List.of());
        } else if (transfer.after() && FrontierV3ResidentMealItems.matches(actual, meal.portion())) {
            var held = new FungiblePhysicalObservation.Stack(FrontierV3ActorResourceSlots.address(
                    meal.residentId(), worker, meal.inventorySlot()), meal.portion().itemKind(), meal.portion().quantity());
            receipt = new ResidentMealResourceEffectObserved(id, meal.executionId(), step,
                    ResidentMealResourceEffectObserved.Outcome.TAKE_APPLIED, source, List.of(held));
        } else return () -> { }; // Ambiguous evidence stays local; never rewrite a chest/pocket.
        return () -> {
            if (submit(level, runtime, receipt) && receipt.outcome() == ResidentMealResourceEffectObserved.Outcome.TAKE_APPLIED
                    && !FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedMutation(runtime, meal.depotId(), chest))
                throw new IllegalStateException("retired meal take lacks its next exact depot replica boundary");
        };
    }

    private static void settlePortion(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Mob body, ActorBodyId id, ResidentMeal original, ItemStack beforeLoot) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        var retained = state.humanPopulation().mealResourceObligations().get(id.actorId());
        if (retained == null || !retained.body().equals(id) || !retained.executionId().equals(original.executionId())
                || retained.custodyState() == ResidentMealResourceObligation.CustodyState.SOURCE_TAKE_PENDING) return;
        var bindings = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(retained.actorAccountId())).toList();
        var expectedPocket = FrontierV3ActorResourceSlots.address(id.actorId(), body, retained.inventorySlot());
        if (bindings.size() != 1 || !bindings.getFirst().address().equals(expectedPocket)
                || !bindings.getFirst().lotQuantities().equals(state.inventory().fungibleResources().accounts()
                        .get(retained.actorAccountId()).lotQuantities())) return;
        var stock = new FoodPortion(retained.portion().itemKind(), retained.portion().nutritionPerItem(), bindings.getFirst().lotQuantities());
        var actual = FrontierV3ActorResourceSlots.get(body, retained.inventorySlot());
        if (beforeLoot.isEmpty() && actual.isEmpty()) {
            submitDisposition(level, runtime, new ResidentMealPortionDispositionObserved(id, retained.executionId(),
                    bindings.getFirst().authorityEpoch(), ResidentMealPortionDispositionObserved.Outcome.MISSING_BEFORE_LOOT,
                    Optional.empty()));
            return;
        }
        if (!FrontierV3ResidentMealItems.matches(beforeLoot, stock)
                || !ItemStack.matches(beforeLoot, actual)) return;
        UUID carrier = dropId(state, retained);
        // The already durable retired obligation is the before-effect fence. Never repeat
        // insertion after an absent lookup; this source can only use its positively held stack.
        if (level.getEntity(carrier) != null) return;
        ItemEntity drop = new ItemEntity(level, body.getX(), body.getY() + 0.25D, body.getZ(), beforeLoot.copy());
        drop.setUUID(carrier);
        var receipt = new ResidentMealPortionDispositionObserved(id, retained.executionId(),
                bindings.getFirst().authorityEpoch(), ResidentMealPortionDispositionObserved.Outcome.WORLD_DROP,
                Optional.of(carrier));
        drop.getPersistentData().putByteArray(DROP_RECEIPT_KEY,
                io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs().encode(receipt));
        if (!level.addFreshEntity(drop)) return; // Source remains intact; no fictional drop receipt.
        FrontierV3ActorResourceSlots.set(body, retained.inventorySlot(), ItemStack.EMPTY);
        if (level.getEntity(carrier) != drop || drop.isRemoved()
                || !FrontierV3ResidentMealItems.matches(drop.getItem(), stock)) return;
        submitDisposition(level, runtime, receipt);
    }

    /** Existing resource observation reconciles only unresolved portions, never the population.
     * A saved exact drop can complete a death-before-receipt gap; absence cannot replay insertion. */
    static boolean reconcileOneDrop(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    FrontierWorldState state) {
        for (var retained : state.humanPopulation().mealResourceObligations().values()) {
            if (retained.custodyState() == ResidentMealResourceObligation.CustodyState.SOURCE_TAKE_PENDING) continue;
            UUID carrier = dropId(state, retained);
            var account = state.inventory().fungibleResources().accounts().get(retained.actorAccountId());
            if (account == null) continue;
            var stock = new FoodPortion(retained.portion().itemKind(), retained.portion().nutritionPerItem(), account.lotQuantities());
            if (!(level.getEntity(carrier) instanceof ItemEntity drop) || drop.isRemoved()
                    || !FrontierV3ResidentMealItems.matches(drop.getItem(), stock)
                    || !drop.getPersistentData().contains(DROP_RECEIPT_KEY, net.minecraft.nbt.Tag.TAG_BYTE_ARRAY)) continue;
            ResidentMealPortionDispositionObserved receipt;
            try {
                receipt = (ResidentMealPortionDispositionObserved)
                        io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs().decode(
                                "frontier.resident_meal_portion_disposition_observed", drop.getPersistentData().getByteArray(DROP_RECEIPT_KEY));
            } catch (IllegalArgumentException invalid) { continue; }
            if (receipt.outcome() != ResidentMealPortionDispositionObserved.Outcome.WORLD_DROP
                    || !receipt.worldCarrier().equals(Optional.of(carrier)) || !receipt.body().equals(retained.body())
                    || !receipt.executionId().equals(retained.executionId())) continue;
            if (submitDisposition(level, runtime, receipt)) return true;
        }
        return false;
    }

    private static UUID dropId(FrontierWorldState state, ResidentMealResourceObligation retained) {
        return UUID.nameUUIDFromBytes((state.bootstrap().worldId().value() + ":retired-meal:"
                + retained.body().actorId().value() + ":" + retained.body().physicalEpoch() + ":"
                + retained.executionId().generation()).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean submitDisposition(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                             ResidentMealPortionDispositionObserved receipt) {
        var result = FrontierV3CommandSubmission.submit(runtime, "resident-meal-retired-portion",
                receipt.body().actorId().value(), receipt);
        FrontierV3DiagnosticTrace.record(level.getServer(), "resident-meal:" + receipt.body().actorId().value(),
                "resident-meal-retired-portion", receipt.body().actorId(), result);
        return result instanceof CommandResult.Accepted;
    }

    private static boolean submit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                  ResidentMealResourceEffectObserved receipt) {
        var result = FrontierV3CommandSubmission.submit(runtime, "resident-meal-retired-effect",
                receipt.body().actorId().value(), receipt);
        FrontierV3DiagnosticTrace.record(level.getServer(), "resident-meal:" + receipt.body().actorId().value(),
                "resident-meal-retired-effect", receipt.body().actorId(), result);
        return result instanceof CommandResult.Accepted;
    }
}
