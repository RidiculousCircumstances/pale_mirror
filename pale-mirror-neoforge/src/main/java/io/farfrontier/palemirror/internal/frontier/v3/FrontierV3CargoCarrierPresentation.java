package io.farfrontier.palemirror.internal.frontier.v3;

import com.mojang.math.Transformation;
import io.farfrontier.palemirror.frontier.v3.model.CargoBatch;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLabels;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.MinecartChest;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * One local, non-owning caption for the exact physical cargo carrier of a HOT operation.
 *
 * <p>The cart remains the sole materialized cargo surface. This Display is its passenger,
 * therefore moves and disappears with that real cart and can never authorize delivery,
 * recovery, block writes, or canonical mutation.</p>
 */
final class FrontierV3CargoCarrierPresentation {
    private static final String LEASE_KEY = "pale_mirror_frontier_v3_cargo_caption_lease";
    private static final String CARGO_KEY = "pale_mirror_frontier_v3_cargo_caption_cargo";
    // A villager is nearly two blocks tall. Keep the moving two-line caption above the
    // convoy's visible bodies, rather than letting the carrier's passengers occlude it.
    private static final float BODY_CLEARANCE = 2.75F, CAPTION_SCALE = 1.1F;

    private FrontierV3CargoCarrierPresentation() { }

    static void ensure(ServerLevel level, FrontierWorldState state, SceneLease lease, MinecartChest carrier) {
        UUID id = id(lease); Entity existing = level.getEntity(id);
        if (existing != null) return;
        CargoBatch cargo = state.inventory().cargo().get(lease.cargoId());
        if (cargo == null) return;
        Display.TextDisplay caption = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        caption.setUUID(id); caption.setPos(carrier.position()); configure(caption, FrontierSceneLabels.cargo(state, cargo));
        caption.getPersistentData().putString(LEASE_KEY, lease.id().value());
        caption.getPersistentData().putString(CARGO_KEY, lease.cargoId().value());
        // A failed visual admission never changes the carrier's canonical/physical ownership.
        if (level.addFreshEntity(caption)) caption.startRiding(carrier, true);
    }

    static void discard(Entity carrier, SceneLease lease) {
        carrier.getPassengers().stream().filter(passenger -> owned(passenger, lease)).forEach(Entity::discard);
        if (carrier.level() instanceof ServerLevel level) {
            Entity detached = level.getEntity(id(lease));
            if (owned(detached, lease)) detached.discard();
        }
    }

    static boolean attached(Entity carrier, SceneLease lease) {
        return carrier.getPassengers().stream().anyMatch(passenger -> owned(passenger, lease));
    }

    private static boolean owned(Entity entity, SceneLease lease) {
        return entity instanceof Display.TextDisplay && !entity.isRemoved() && entity.getUUID().equals(id(lease))
                && lease.id().value().equals(entity.getPersistentData().getString(LEASE_KEY))
                && lease.cargoId().value().equals(entity.getPersistentData().getString(CARGO_KEY));
    }

    private static UUID id(SceneLease lease) {
        return UUID.nameUUIDFromBytes(("pale-mirror-frontier-v3-cargo-caption:" + lease.id().value()).getBytes(StandardCharsets.UTF_8));
    }

    private static void configure(Display.TextDisplay display, String text) {
        CompoundTag data = display.saveWithoutId(new CompoundTag());
        Component content = Component.literal(text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        data.putString(Display.TextDisplay.TAG_TEXT, Component.Serializer.toJson(content, display.registryAccess()));
        data.putInt("line_width", 176); data.putByte("text_opacity", (byte) 0xFF); data.putInt("background", 0xB0000000);
        data.putBoolean("shadow", true); data.putBoolean("see_through", false); data.putString("alignment", "center"); data.putFloat("view_range", 1.0F);
        data.putFloat("width", 7.0F); data.putFloat("height", 2.5F); data.putInt("glow_color_override", 0xFFAA00); data.putBoolean("Glowing", true);
        Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE, new Transformation(new Vector3f(0.0F, BODY_CLEARANCE, 0.0F), new Quaternionf(), new Vector3f(CAPTION_SCALE), new Quaternionf()))
                .ifSuccess(value -> data.put("transformation", value));
        Display.BillboardConstraints.CODEC.encodeStart(NbtOps.INSTANCE, Display.BillboardConstraints.CENTER).ifSuccess(value -> data.put("billboard", value));
        Brightness.CODEC.encodeStart(NbtOps.INSTANCE, Brightness.FULL_BRIGHT).ifSuccess(value -> data.put("brightness", value));
        display.load(data); display.setNoGravity(true); display.setCustomName(Component.literal(text)); display.setCustomNameVisible(false);
    }
}
