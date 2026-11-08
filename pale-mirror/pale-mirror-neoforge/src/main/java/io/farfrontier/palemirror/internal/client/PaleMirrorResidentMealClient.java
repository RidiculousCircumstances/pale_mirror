package io.farfrontier.palemirror.internal.client;

import io.farfrontier.palemirror.internal.frontier.v3.PaleMirrorSeatEntities;
import io.farfrontier.palemirror.internal.network.ResidentMealPresentationPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Bounded client-only drawing state. Icons never enter any Minecraft inventory. */
public final class PaleMirrorResidentMealClient {
    private record Cue(ResidentMealPresentationPayload payload, long received, ItemStack icon) { }
    record Frame(float phase, ItemStack icon) { }
    private static final Map<UUID, Cue> CUES = new HashMap<>();
    private static long clock;
    public static void receive(ResidentMealPresentationPayload payload) {
        var old = CUES.get(payload.body());
        if (old != null && (payload.epoch() < old.payload().epoch()
                || payload.epoch() == old.payload().epoch() && payload.sequence() <= old.payload().sequence())) return;
        if (old == null && CUES.size() >= 256) return;
        var icon = payload.food().map(id -> new ItemStack(BuiltInRegistries.ITEM.get(id))).orElse(ItemStack.EMPTY);
        CUES.put(payload.body(), new Cue(payload, clock, icon));
    }
    static Frame frame(Villager actor, float partial) {
        var cue = CUES.get(actor.getUUID());
        if (cue == null || !actor.isAlive() || cue.payload().entityId() != actor.getId() || cue.icon().isEmpty()
                || clock - cue.received() >= cue.payload().duration() || !PaleMirrorSeatEntities.isResidentSeat(actor.getVehicle())) return null;
        return new Frame(cue.payload().phase() + clock - cue.received() + partial, cue.icon());
    }
    static ItemStack display(Villager actor, HumanoidArm hand, ItemStack actual) {
        var frame = frame(actor, 0);
        return frame != null && hand == actor.getMainArm().getOpposite() ? frame.icon() : actual;
    }
    /** Read-only player/pilot presentation evidence, never simulation authority. */
    public static Optional<net.minecraft.resources.ResourceLocation> displayedFood(Villager actor) {
        var frame = frame(actor, 0);
        return frame == null ? Optional.empty() : Optional.of(BuiltInRegistries.ITEM.getKey(frame.icon().getItem()));
    }
    public static void tick() {
        clock++;
        if (Minecraft.getInstance().level == null) { clear(); return; }
        CUES.values().removeIf(cue -> clock - cue.received() >= cue.payload().duration());
    }
    public static void clear() { CUES.clear(); clock = 0; }
    private PaleMirrorResidentMealClient() { }
}
