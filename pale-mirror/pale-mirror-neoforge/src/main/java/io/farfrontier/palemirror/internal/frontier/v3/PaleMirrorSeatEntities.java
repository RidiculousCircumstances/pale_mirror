package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.*;
import java.util.Optional;

/** Optional persistent furniture carrier. Handcrafted's ordinary player seats remain untouched. */
public final class PaleMirrorSeatEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, PaleMirrorMod.MOD_ID);
    private static DeferredHolder<EntityType<?>, EntityType<FrontierV3SeatEntity>> seat;
    public static void register(IEventBus bus) {
        if (!ModList.get().isLoaded("handcrafted")) return;
        FrontierV3SeatProvider.register(io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingSeat.Kind.HANDCRAFTED_CHAIR, new FrontierV3HandcraftedSeating());
        seat = ENTITIES.register("resident_seat", () -> EntityType.Builder.<FrontierV3SeatEntity>of(FrontierV3SeatEntity::new, MobCategory.MISC)
                .sized(1, 1).fireImmune().noSummon().clientTrackingRange(8).updateInterval(20).build("pale_mirror:resident_seat"));
        ENTITIES.register(bus);
    }
    static EntityType<FrontierV3SeatEntity> require() {
        if (seat == null) throw new IllegalStateException("Handcrafted seat integration is unavailable");
        return seat.get();
    }
    public static Optional<EntityType<?>> rendererType() { return seat == null ? Optional.empty() : Optional.of(seat.get()); }
    public static boolean isResidentSeat(Entity entity) { return entity != null && seat != null && entity.getType() == seat.get(); }
    private PaleMirrorSeatEntities() { }
}
