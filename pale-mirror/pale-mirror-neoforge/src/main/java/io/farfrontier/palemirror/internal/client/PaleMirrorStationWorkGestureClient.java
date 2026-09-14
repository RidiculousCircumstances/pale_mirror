package io.farfrontier.palemirror.internal.client;

import io.farfrontier.palemirror.internal.network.StationWorkGesturePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Client projection of a server-declared station gesture.
 *
 * <p>Minecraft's ordinary animate packet declares a start but carries no cancellation; a NoAI
 * retained body can consequently keep that pose into its next owned edge. The same
 * presentation-only channel also retains the server-declared travel phase until the next
 * station/release cue, so a client never labels real movement with an older polling receipt.
 * It cannot move a body, choose a target, or alter a lease.</p>
 */
public final class PaleMirrorStationWorkGestureClient {
    private static final int DURATION_TICKS = 6;
    private static final Map<Integer, Cue> cues = new HashMap<>();
    private static long clientTick;

    private PaleMirrorStationWorkGestureClient() { }

    public static void receive(StationWorkGesturePayload payload) {
        Entity entity = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(payload.entityId());
        if (!(entity instanceof LivingEntity living)) return;
        if (payload.active()) {
            // A newly joined player can receive an ordinary entity spawn/interpolation update
            // and a station gesture in the same network burst.  Do not make that actor swing
            // while the client is still resolving its already-authoritative arrival; the server
            // phase and body remain untouched, and a normal settled work cue still plays.
            cues.put(payload.entityId(), new Cue(clientTick + DURATION_TICKS, payload.dutyPhase(), true,
                    payload.dutyPhase().startsWith("HARVESTING:"), null, 0));
        } else if (payload.dutyPhase().isEmpty()) {
            clear(living); cues.remove(payload.entityId());
        } else {
            clear(living);
            cues.put(payload.entityId(), new Cue(Long.MAX_VALUE, payload.dutyPhase(), false,
                    payload.dutyPhase().startsWith("HARVESTING:"), null, 0));
        }
    }

    public static void tick() {
        clientTick++;
        Minecraft minecraft = Minecraft.getInstance();
        cues.entrySet().removeIf(entry -> {
            Cue cue = entry.getValue();
            Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(entry.getKey());
            if (cue.pendingStationPresentation() && entity instanceof LivingEntity living) {
                Vec3 position = living.position();
                boolean unchanged = cue.lastPosition() != null && cue.lastPosition().distanceToSqr(position) <= 1.0E-6D;
                int settledTicks = unchanged ? cue.settledTicks() + 1 : 0;
                if (settledTicks >= 2) {
                    cue = new Cue(cue.expiresAt(), cue.dutyPhase(), cue.gesture(), false, position, settledTicks);
                    if (cue.gesture() && cue.expiresAt() > clientTick) living.swing(InteractionHand.MAIN_HAND, false);
                } else cue = new Cue(cue.expiresAt(), cue.dutyPhase(), cue.gesture(), true, position, settledTicks);
                entry.setValue(cue);
            }
            // Minecraft's ordinary swing packet has no cancellation acknowledgement.  Keep a
            // received retained travel duty visually travel-only until its next station cue;
            // this clears only the stale render pose and never changes the replicated body.
            if (!cue.gesture() && cue.dutyPhase().startsWith("TRAVELLING:") && entity instanceof LivingEntity living) clear(living);
            if (cue.expiresAt() > clientTick) return false;
            if (cue.gesture() && entity instanceof LivingEntity living) clear(living);
            return true;
        });
    }

    /** Server-authored phase for the current cue, never inferred from pose or velocity. */
    public static String observedDutyPhase(int entityId) {
        Cue cue = cues.get(entityId);
        // This is only the join/arrival render hand-off, not a route decision: until the
        // received body is visually stationary, it must not be presented as harvesting.
        if (cue != null && cue.pendingStationPresentation()) return "TRAVELLING:PREPARED";
        return cue != null && cue.expiresAt() > clientTick ? cue.dutyPhase() : null;
    }

    public static void clear() { cues.clear(); clientTick = 0L; }

    private static void clear(LivingEntity living) {
        living.swinging = false; living.swingTime = 0; living.attackAnim = 0.0F; living.oAttackAnim = 0.0F;
    }

    private record Cue(long expiresAt, String dutyPhase, boolean gesture, boolean pendingStationPresentation,
                       Vec3 lastPosition, int settledTicks) { }
}
