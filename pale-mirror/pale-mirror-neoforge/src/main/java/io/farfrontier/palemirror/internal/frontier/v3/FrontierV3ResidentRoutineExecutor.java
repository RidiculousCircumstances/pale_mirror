package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.routine.*;
import io.farfrontier.palemirror.internal.network.ResidentRoutinePresentationPayload;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Bounded HOT admission and cosmetic observation. Common movement alone walks every admitted
 * stroll.
 */
final class FrontierV3ResidentRoutineExecutor {
  private record Stamp(long epoch, long generation, long sequence, long sentAt) {}

  private static final class Session {
    Map<SubjectId, AmbientActorLease> leases = Map.of();
    final Set<SubjectId> hot = new TreeSet<>();
    List<SubjectId> roster = List.of();
    int cursor;
    long next;
    final Map<Mob, Stamp> presented = new WeakHashMap<>();

    void synchronize(FrontierWorldState s) {
      boolean[] changed = {false};
      ImmutableMapChanges.forEachIdentityChangedKey(
          leases,
          s.ambientLeases(),
          id -> {
            var lease = s.ambientLeases().get(id);
            boolean eligible =
                lease != null
                    && lease.status() == AmbientLeaseStatus.HOT
                    && s.humanPopulation().resident(id) != null;
            if (eligible) changed[0] |= hot.add(id);
            else changed[0] |= hot.remove(id);
          });
      leases = s.ambientLeases();
      if (changed[0]) {
        roster = List.copyOf(hot);
        cursor = roster.isEmpty() ? 0 : Math.floorMod(cursor, roster.size());
      }
    }
  }

  private static final Map<ServerLevel, Session> SESSIONS = new WeakHashMap<>();

  static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
    var s = runtime.decodedState().orElseThrow();
    var rules = s.bootstrap().ruleset().residentLife().routines();
    if (!rules.enabled()) return;
    var session = SESSIONS.computeIfAbsent(level, k -> new Session());
    long now = level.getGameTime();
    if (now < session.next) return;
    session.next = now + rules.admissionInterval();
    session.synchronize(s);
    long tick = runtime.executionView().orElseThrow().instant().ticks();
    // At most the finite previously displayed cohort, never a population/entity census.
    var shown = session.presented.entrySet().iterator();
    while (shown.hasNext()) {
      var entry = shown.next();
      var body = entry.getKey();
      var stamp = entry.getValue();
      var declaration = FrontierV3ActorCarrierComposition.declaredBy(body);
      var row =
          declaration.isEmpty()
              ? null
              : s.routines().active().get(declaration.orElseThrow().actorId());
      if (row == null
          || row.phase() != ResidentRoutine.Phase.PAUSING
          || row.execution().orElseThrow().generation() != stamp.generation()
          || !body.isAlive()
          || !FrontierV3SurfaceObservation.at(body, row.goal())
          || s.combat().evasions().containsKey(row.actor())) {
        if (!body.isRemoved())
          send(
              level,
              body,
              new ResidentRoutinePresentationPayload(
                  body.getId(),
                  body.getUUID(),
                  stamp.epoch(),
                  stamp.generation(),
                  Math.max(now + 1, stamp.sequence() + 1),
                  Optional.empty(),
                  0,
                  1));
        shown.remove();
      }
    }
    for (var row : s.routines().active().values()) {
      if (row.phase() != ResidentRoutine.Phase.PAUSING || tick >= row.phaseAt() + row.pauseTicks())
        continue;
      var raw = level.getEntity(ActorBodyId.entityId(s.bootstrap().worldId(), row.actor()));
      if (!(raw instanceof Mob body)
          || !body.isAlive()
          || body.isPassenger()
          || !FrontierV3SurfaceObservation.at(body, row.goal())) continue;
      FrontierV3ActorActuation authority;
      try {
        authority =
            FrontierV3ActorActuation.capture(
                s, body, row.execution().orElseThrow(), runtime::decodedState);
      } catch (IllegalArgumentException stale) {
        continue;
      }
      if (!authority.current(body)) continue;
      FrontierV3GoalNavigation.stop(body, authority);
      var kind = row.kind();
      int age = (int) Math.min(2400, Math.max(0, tick - row.phaseAt()));
      if (kind == ResidentRoutine.Kind.GREET) {
        var neighbours =
            level.getEntitiesOfClass(
                Villager.class,
                body.getBoundingBox().inflate(6),
                other ->
                    other != body
                        && other.isAlive()
                        && FrontierV3ActorCarrierComposition.declaredBy(other)
                            .filter(d -> d.kind() == ActorKind.RESIDENT)
                            .isPresent());
        var peer =
            neighbours.stream()
                .sorted(Comparator.comparingDouble(body::distanceToSqr))
                .limit(4)
                .filter(body::hasLineOfSight)
                .findFirst();
        if (peer.isPresent()) {
          var other = peer.orElseThrow();
          body.setYHeadRot(
              (float)
                  (Math.toDegrees(
                          Math.atan2(other.getZ() - body.getZ(), other.getX() - body.getX()))
                      - 90));
        } else
          kind =
              ResidentRoutine.Kind
                  .LOOK_AROUND; // No visible neighbour means no greeting presentation.
      }
      if (kind == ResidentRoutine.Kind.LOOK_AROUND || kind == ResidentRoutine.Kind.STROLL)
        body.setYHeadRot(body.getYRot() + (float) Math.sin(age * .035) * 50);
      var prior = session.presented.get(body);
      long sequence = Math.max(now + 1, prior == null ? 1 : prior.sequence() + 1);
      session.presented.put(
          body,
          new Stamp(
              authority.id().body().physicalEpoch(),
              authority.id().execution().generation(),
              sequence,
              now));
      send(
          level,
          body,
          new ResidentRoutinePresentationPayload(
              body.getId(),
              body.getUUID(),
              authority.id().body().physicalEpoch(),
              authority.id().execution().generation(),
              sequence,
              Optional.of(kind),
              age,
              (int) Math.min(40, row.phaseAt() + row.pauseTicks() - tick)));
    }
    int visits = Math.min(rules.candidateVisits(), session.roster.size());
    for (int index = 0; index < visits; index++) {
      var actor = session.roster.get(session.cursor);
      session.cursor = (session.cursor + 1) % session.roster.size();
      s = runtime.decodedState().orElseThrow();
      if (!ResidentRoutines.eligible(s, actor, tick)) continue;
      var raw = level.getEntity(ActorBodyId.entityId(s.bootstrap().worldId(), actor));
      if (!(raw instanceof Mob body) || !body.isAlive() || body.isPassenger()) continue;
      if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, body)) continue;
      s = runtime.decodedState().orElseThrow();
      var retained = s.actorExecutions().actors().get(actor);
      if (retained == null || retained.current().isEmpty()) continue;
      FrontierV3ActorActuation authority;
      try {
        authority =
            FrontierV3ActorActuation.capture(
                s, body, retained.current().orElseThrow(), runtime::decodedState);
      } catch (IllegalArgumentException stale) {
        continue;
      }
      var kind = ResidentRoutines.nextKind(s, actor);
      var targets = ResidentRoutines.targets(s, actor, kind);
      for (var target : targets) {
        if (!FrontierV3LoadedWorld.ready(level, new BlockPos(target.x(), target.y(), target.z()))
            || !FrontierV3SemanticMovement.targetGeometryIsNavigable(level, body, target)) continue;
        FrontierV3CommandSubmission.propose(
            runtime,
            "resident-routine",
            actor.value(),
            new RoutineEvent.Requested(authority.id(), kind, target));
        return; // One candidate command per cadence.
      }
    }
  }

  private static void send(
      ServerLevel level, Mob actor, ResidentRoutinePresentationPayload payload) {
    for (var player : level.players())
      if (player.distanceToSqr(actor) <= 128 * 128
          && NetworkRegistry.hasChannel(player.connection, payload.type().id()))
        PacketDistributor.sendToPlayer(player, payload);
  }

  private FrontierV3ResidentRoutineExecutor() {}
}
