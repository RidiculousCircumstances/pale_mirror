package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.VisualStateProjection;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;

/** Sparse diegetic cues at the receiving depot; Atlas remains the explanatory layer. */
final class SettlementStateCueRuntime {
    void reconcile(ServerLevel level, VisualStateProjection projection,
                   Collection<AuthoredRegionSeed> regions, long cueTick) {
        if (cueTick % 20L != 0L) return;
        AuthoredRegionSeed region = regions.stream().filter(seed ->
                projection.objectId().equals(seed.planId() + "_mine")).findFirst().orElse(null);
        if (region == null) return;
        BlockPos depot = new BlockPos(region.receivingDepot().x(), region.receivingDepot().y() + 3,
                region.receivingDepot().z());
        if (!level.hasChunkAt(depot)) return;
        if (projection.alternateDispatch().equals("OPERATIONAL")) {
            BlockPos dispatch = new BlockPos(region.alternateMineSite().loadingEndpoint().x(),
                    region.alternateMineSite().loadingEndpoint().y() + 2,
                    region.alternateMineSite().loadingEndpoint().z());
            ItemParticleOption cargo = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.RAW_IRON));
            if (level.hasChunkAt(dispatch)) level.sendParticles(cargo, dispatch.getX() + 0.5, dispatch.getY(),
                    dispatch.getZ() + 0.5, 18, 1.2, 0.5, 1.2, 0.08);
            level.sendParticles(cargo, depot.getX() + 0.5, depot.getY(), depot.getZ() + 0.5,
                    18, 1.2, 0.5, 1.2, 0.08);
        }
        if (projection.crisis().equals("CRITICAL") || projection.economy().equals("UNAVAILABLE")) {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, depot.getX() + 0.5, depot.getY(), depot.getZ() + 0.5,
                    3, 0.6, 0.25, 0.6, 0.01);
            if (cueTick % 60L == 0L) level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    depot.getX() + 0.5, depot.getY() - 1, depot.getZ() + 0.5, 2, 1.5, 0.3, 1.5, 0.0);
            return;
        }
        if (projection.crisis().equals("RECOVERING") || projection.operation().equals("RECOVERING")) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, depot.getX() + 0.5, depot.getY() - 1,
                    depot.getZ() + 0.5, 3, 1.5, 0.5, 1.5, 0.0);
            return;
        }
        int prosperity = parse(projection.development());
        if (prosperity >= 50 && cueTick % 40L == 0L) {
            level.sendParticles(ParticleTypes.COMPOSTER, depot.getX() + 0.5, depot.getY() - 1,
                    depot.getZ() + 0.5, 2, 1.2, 0.3, 1.2, 0.0);
        }
    }

    private static int parse(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return 0; }
    }
}
