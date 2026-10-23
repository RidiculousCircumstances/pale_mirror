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
final class FrontierV3StationWorkHandProjection {
    private FrontierV3StationWorkHandProjection() { }

    static boolean prepareNew(FrontierWorldState state, SceneLease lease, SceneMember member, Mob body) {
        if (!FrontierSceneBehaviors.isProductionWork(lease)) return true;
        ProductionJob job = state.productionJobs().get(FrontierSceneBehaviors.productionWork(lease).jobId());
        if (job == null || job.stationWork().isEmpty() || !job.workerId().equals(member.actorId())) return true;
        return projectNew(state, job, body);
    }

    static boolean prepareAmbientNew(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId actorId,
                                     Mob body) {
        ProductionJob job = bakeryForActor(state, actorId);
        return job == null || !(StationWorkCargo.exact(state, job)) || projectNew(state, job, body);
    }

    static boolean matchesAmbient(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId actorId,
                                  Mob body) {
        ProductionJob job = bakeryForActor(state, actorId);
        return job == null || matchesJob(state, job, body);
    }

    private static ProductionJob bakeryForActor(FrontierWorldState state,
                                                io.farfrontier.palemirror.frontier.v3.api.SubjectId actorId) {
        return state.productionJobs().values().stream().filter(job -> job.stationWork().isPresent()
                && job.workerId().equals(actorId)).reduce((left, right) -> {
                    throw new IllegalArgumentException("one baker has concurrent production jobs");
                }).orElse(null);
    }

    private static boolean projectNew(FrontierWorldState state, ProductionJob job, Mob body) {
        StationWorkState work = job.stationWork().orElseThrow();
        if (!work.phase().carriesCargo()) return true;
        if (!body.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return false;
        ItemStack held;
        if (StationWorkCargo.exact(state, job)) {
            ExactItemStack item = state.inventory().items().get(StationWorkCargo.exactId(job));
            if (item == null || !item.custody().equals(new InventoryCustody.Actor(job.workerId()))) return false;
            held = FrontierV3ExactItemPresentation.materializedStack(item);
        } else {
            CustodyAccount account = state.inventory().fungibleResources().accounts().get(work.actorAccountId());
            if (account == null || !account.custody().equals(new ResourceCustody.Actor(job.workerId()))
                    || state.inventory().fungibleResources().bindings().values().stream()
                    .anyMatch(binding -> binding.accountId().equals(account.id()))) return false;
            String kind = StationWorkCargo.kind(job);
            if (account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum() != StationWorkCargo.quantity(job)
                    || account.lotQuantities().keySet().stream().anyMatch(id ->
                    !state.inventory().fungibleResources().lots().get(id).itemKind().equals(kind))) return false;
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(kind));
            if (item == Items.AIR) return false;
            held = new ItemStack(item, StationWorkCargo.quantity(job));
        }
        body.setItemSlot(EquipmentSlot.MAINHAND, held);
        return true;
    }

    static boolean matchesCurrent(FrontierWorldState state, SceneLease lease, SceneMember member, Mob body) {
        if (!FrontierSceneBehaviors.isProductionWork(lease)) return true;
        ProductionJob job = state.productionJobs().get(FrontierSceneBehaviors.productionWork(lease).jobId());
        if (job == null || job.stationWork().isEmpty() || !job.workerId().equals(member.actorId())) return true;
        return matchesJob(state, job, body);
    }

    private static boolean matchesJob(FrontierWorldState state, ProductionJob job, Mob body) {
        StationWorkState work = job.stationWork().orElseThrow();
        ItemStack observed = body.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!work.phase().carriesCargo()) return observed.isEmpty();
        if (StationWorkCargo.exact(state, job)) {
            ExactItemStack item = state.inventory().items().get(StationWorkCargo.exactId(job));
            return item != null && item.custody().equals(new InventoryCustody.Actor(job.workerId()))
                    && FrontierV3ExactItemPresentation.exactMatch(observed, item);
        }
        String kind = StationWorkCargo.kind(job);
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(kind));
        return item != Items.AIR && observed.getCount() == StationWorkCargo.quantity(job)
                && ItemStack.isSameItemSameComponents(observed, new ItemStack(item, StationWorkCargo.quantity(job)));
    }
}
