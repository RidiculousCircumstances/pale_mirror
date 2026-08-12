package io.farfrontier.palemirror.visuals.threat;

import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VisualEntityTypes {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE,
            PaleMirrorVisualsMod.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<ThreatHeartEntity>> THREAT_HEART = TYPES.register(
            "threat_heart", () -> EntityType.Builder.<ThreatHeartEntity>of(ThreatHeartEntity::new, MobCategory.MISC)
                    .sized(1.5F, 1.8F).clientTrackingRange(64).updateInterval(2).fireImmune().build("threat_heart"));

    private VisualEntityTypes() { }
    public static void register(IEventBus bus) { TYPES.register(bus); }
}
