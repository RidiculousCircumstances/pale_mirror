package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Projects a COLD-held bakery batch only when a genuinely new scene body is admitted. */
final class FrontierV3BakeryHandProjection {
    private FrontierV3BakeryHandProjection() { }

    static boolean prepareNew(FrontierWorldState state, SceneLease lease, SceneMember member, Mob body) {
        if (!FrontierSceneBehaviors.isProductionWork(lease)) return true;
        ProductionJob job = state.productionJobs().get(FrontierSceneBehaviors.productionWork(lease).jobId());
        if (job == null || job.bakeryWork().isEmpty() || !job.workerId().equals(member.actorId())) return true;
        return projectNew(state, job, body);
    }

    static boolean prepareAmbientNew(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId actorId,
                                     Mob body) {
        ProductionJob job = bakeryForActor(state, actorId);
        return job == null || !(job.inputHold() instanceof ProductionInputHold.Materialized) || projectNew(state, job, body);
    }

    static boolean matchesAmbient(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId actorId,
                                  Mob body) {
        ProductionJob job = bakeryForActor(state, actorId);
        return job == null || matchesJob(state, job, body);
    }

    private static ProductionJob bakeryForActor(FrontierWorldState state,
                                                io.farfrontier.palemirror.frontier.v3.api.SubjectId actorId) {
        return state.productionJobs().values().stream().filter(job -> job.bakeryWork().isPresent()
                && job.workerId().equals(actorId)).reduce((left, right) -> {
                    throw new IllegalArgumentException("one baker has concurrent production jobs");
                }).orElse(null);
    }

    private static boolean projectNew(FrontierWorldState state, ProductionJob job, Mob body) {
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        if (work.phase() != BakeryWorkState.Phase.STATION_LOAD
                && work.phase() != BakeryWorkState.Phase.DEPOT_DELIVERY) return true;
        if (!body.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return false;
        ItemStack held;
        if (job.inputHold() instanceof ProductionInputHold.Materialized) {
            ExactItemStack item = state.inventory().items().get(work.phase() == BakeryWorkState.Phase.STATION_LOAD
                    ? job.consumedItemId() : job.outputItemId());
            if (item == null || !item.custody().equals(new InventoryCustody.Actor(job.workerId()))) return false;
            held = FrontierV3CargoHandoffExecutor.materializedStack(item);
        } else {
            CustodyAccount account = state.inventory().fungibleResources().accounts().get(work.actorAccountId());
            if (account == null || !account.custody().equals(new ResourceCustody.Actor(job.workerId()))
                    || state.inventory().fungibleResources().bindings().values().stream()
                    .anyMatch(binding -> binding.accountId().equals(account.id()))) return false;
            String kind = work.phase() == BakeryWorkState.Phase.STATION_LOAD ? "minecraft:wheat" : "minecraft:bread";
            if (account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum() != job.outputCount()
                    || account.lotQuantities().keySet().stream().anyMatch(id ->
                    !state.inventory().fungibleResources().lots().get(id).itemKind().equals(kind))) return false;
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(kind));
            if (item == Items.AIR) return false;
            held = new ItemStack(item, job.outputCount());
        }
        body.setItemSlot(EquipmentSlot.MAINHAND, held);
        return true;
    }

    static boolean matchesCurrent(FrontierWorldState state, SceneLease lease, SceneMember member, Mob body) {
        if (!FrontierSceneBehaviors.isProductionWork(lease)) return true;
        ProductionJob job = state.productionJobs().get(FrontierSceneBehaviors.productionWork(lease).jobId());
        if (job == null || job.bakeryWork().isEmpty() || !job.workerId().equals(member.actorId())) return true;
        return matchesJob(state, job, body);
    }

    private static boolean matchesJob(FrontierWorldState state, ProductionJob job, Mob body) {
        BakeryWorkState work = job.bakeryWork().orElseThrow();
        ItemStack observed = body.getItemBySlot(EquipmentSlot.MAINHAND);
        if (work.phase() != BakeryWorkState.Phase.STATION_LOAD
                && work.phase() != BakeryWorkState.Phase.DEPOT_DELIVERY) return observed.isEmpty();
        if (job.inputHold() instanceof ProductionInputHold.Materialized) {
            ExactItemStack item = state.inventory().items().get(work.phase() == BakeryWorkState.Phase.STATION_LOAD
                    ? job.consumedItemId() : job.outputItemId());
            return item != null && item.custody().equals(new InventoryCustody.Actor(job.workerId()))
                    && FrontierV3CargoHandoffExecutor.exactMatch(observed, item);
        }
        String kind = work.phase() == BakeryWorkState.Phase.STATION_LOAD ? "minecraft:wheat" : "minecraft:bread";
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(kind));
        return item != Items.AIR && observed.getCount() == job.outputCount()
                && ItemStack.isSameItemSameComponents(observed, new ItemStack(item, job.outputCount()));
    }
}
