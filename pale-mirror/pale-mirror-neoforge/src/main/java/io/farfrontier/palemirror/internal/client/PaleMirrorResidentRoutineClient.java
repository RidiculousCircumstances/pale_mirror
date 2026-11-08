package io.farfrontier.palemirror.internal.client;

import io.farfrontier.palemirror.frontier.v3.model.routine.ResidentRoutine;
import io.farfrontier.palemirror.internal.network.ResidentRoutinePresentationPayload;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.Villager;

/** Bounded client overlay, expiring without a continuing current-owner pulse. */
public final class PaleMirrorResidentRoutineClient {
  private record Cue(ResidentRoutinePresentationPayload payload, long received) {}

  record Frame(ResidentRoutine.Kind kind, float age) {}

  private static final Map<UUID, Cue> CUES = new HashMap<>();
  private static long clock;

  public static void receive(ResidentRoutinePresentationPayload p) {
    var old = CUES.get(p.body());
    if (old != null
        && (p.epoch() < old.payload().epoch()
            || p.epoch() == old.payload().epoch()
                && (p.generation() < old.payload().generation()
                    || p.sequence() <= old.payload().sequence()))) return;
    if (old == null && CUES.size() >= 256) return;
    CUES.put(p.body(), new Cue(p, clock));
  }

  static Frame frame(Villager actor, float partial) {
    var cue = CUES.get(actor.getUUID());
    if (cue == null
        || !actor.isAlive()
        || actor.isPassenger()
        || cue.payload().entityId() != actor.getId()
        || cue.payload().kind().isEmpty()
        || clock - cue.received() >= cue.payload().duration()) return null;
    return new Frame(
        cue.payload().kind().orElseThrow(), cue.payload().age() + clock - cue.received() + partial);
  }

  public static String displayedKind(Villager actor) {
    var f = frame(actor, 0);
    return f == null ? "NONE" : f.kind().name();
  }

  public static void tick() {
    clock++;
    if (Minecraft.getInstance().level == null) {
      clear();
      return;
    }
    CUES.values().removeIf(c -> clock - c.received() >= c.payload().duration());
  }

  public static void clear() {
    CUES.clear();
    clock = 0;
  }

  private PaleMirrorResidentRoutineClient() {}
}
