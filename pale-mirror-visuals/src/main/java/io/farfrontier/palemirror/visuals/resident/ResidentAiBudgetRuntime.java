package io.farfrontier.palemirror.visuals.resident;

import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import io.farfrontier.palemirror.visuals.runtime.AuthoredVisualProvider;
import io.farfrontier.palemirror.visuals.runtime.VisualServerConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Keeps the complete roster visible while granting expensive vanilla AI only to the highest-value nearby actors. */
@EventBusSubscriber(modid = PaleMirrorVisualsMod.MOD_ID)
public final class ResidentAiBudgetRuntime {
    private ResidentAiBudgetRuntime() { }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 10 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) reconcile(level);
    }

    private static void reconcile(ServerLevel level) {
        int radius = VisualServerConfig.ACTIVE_RESIDENT_AI_RADIUS.get();
        double radiusSquared = (double) radius * radius;
        ArrayList<Candidate> residents = new ArrayList<>();
        for (var region : AuthoredVisualProvider.INSTANCE.markers().discovered(
                level.dimension().location().toString())) for (var seed : region.residents()) {
            var entity = level.getEntity(UUID.fromString(seed.residentId()));
            if (!(entity instanceof Villager villager)) continue;
            double nearest = level.players().stream().filter(player -> !player.isSpectator())
                    .mapToDouble(villager::distanceToSqr).min().orElse(Double.POSITIVE_INFINITY);
            residents.add(new Candidate(villager, seed.cohort(), nearest));
        }
        residents.sort(Comparator.comparingInt((Candidate value) -> priority(value.cohort()))
                .thenComparingDouble(Candidate::distanceSquared)
                .thenComparing(value -> value.villager().getUUID()));
        int remaining = VisualServerConfig.ACTIVE_RESIDENT_AI_LIMIT.get();
        for (Candidate candidate : residents) {
            boolean active = remaining > 0 && candidate.distanceSquared() <= radiusSquared;
            if (active) remaining--;
            if (candidate.villager().isNoAi() == active) candidate.villager().setNoAi(!active);
        }
    }

    private static int priority(String cohort) {
        return switch (cohort) { case "GUARDS" -> 0; case "SPECIALISTS" -> 1; case "WORKERS" -> 2; default -> 3; };
    }

    private record Candidate(Villager villager, String cohort, double distanceSquared) { }
}
