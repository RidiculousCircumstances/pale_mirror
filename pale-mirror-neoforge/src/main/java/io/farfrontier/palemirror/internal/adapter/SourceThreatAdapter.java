package io.farfrontier.palemirror.internal.adapter;

import java.util.List;
import java.util.Optional;

import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.GatePlanRef;
import io.farfrontier.palemirror.internal.world.SourceGatePartRef;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Full black-box source boundary. It may create or inspect only PM-registered
 * representations; canonical gate progression remains in the domain.
 */
public interface SourceThreatAdapter extends ThreatActorAdapter {
    default List<String> commandAliases() { return List.of(source().value()); }
    default SourceOverlayPalette overlayPalette() { return SourceOverlayPalette.baselineOnly(); }
    default Optional<GatePlanRef> gatePlan(FacilityState facility, long worldSeed) { return Optional.empty(); }
    default Optional<SourceGateLayout> gateLayout(TestMineRecord site, FacilityState facility) { return Optional.empty(); }
    default ActorOperationResult ensureGatePart(ServerLevel level, TestMineRecord site, String jobId,
                                                SourceGatePartRef part) {
        return ActorOperationResult.unavailable("Source adapter does not provide gate materialization");
    }
    default ActorOperationResult removeGatePart(ServerLevel level, TestMineRecord site, String slotId) {
        return ActorOperationResult.unavailable("Source adapter does not provide gate cleanup");
    }
    default boolean matchesGatePart(Entity entity) { return false; }
    default boolean matchesOwnedGatePart(Entity entity, TestMineRecord site, String slotId) { return false; }
    default Optional<String> gatePartAt(ServerLevel level, TestMineRecord site, BlockPos position) { return Optional.empty(); }
    default void onGatePartObservedDestroyed(TestMineRecord site, String slotId) { }
    default ActorDamageResult receiveGateDamage(ServerLevel level, TestMineRecord site, Entity entity,
                                                SourceGatePartRef part, DamageSource source, float amount) {
        return ActorDamageResult.passThrough();
    }
    default void onServerStarted(MinecraftServer server) { }
    default List<PreparableReloadListener> reloadListeners() { return List.of(); }
    default void registerBuiltInPacks(AddPackFindersEvent event) { }
    default boolean rejectsUnmanagedEntity(Entity entity) { return false; }
    default Optional<BlockedSourceItem> classifyExcludedItem(ItemStack stack) { return Optional.empty(); }
}
