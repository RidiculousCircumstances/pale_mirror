package io.farfrontier.palemirror.frontier.v3.model.routine;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingSpatialPlan;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;
import io.farfrontier.palemirror.frontier.v3.model.seating.*;
import io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingDefinition;

/**
 * Optional resident-owned purpose. Shared UAE, movement, geometry and body owners remain
 * authoritative.
 */
public final class ResidentRoutines {
  private static final List<ResidentRoutine.Kind> VARIETY =
      List.of(
          ResidentRoutine.Kind.STROLL,
          ResidentRoutine.Kind.LOOK_AROUND,
          ResidentRoutine.Kind.GREET,
          ResidentRoutine.Kind.STRETCH);
  private static final ImmutableInputView<Map<SubjectId, List<SurfaceAnchor>>> TARGETS =
      new ImmutableInputView<>();

  public static ResidentRoutine.Kind nextKind(FrontierWorldState s, SubjectId actor) {
    var row = s.routines().rows().get(actor);
    long sequence = row == null ? 1 : row.sequence() + 1;
    var variety = s.bootstrap().ruleset().residentLife().routines().seatedRest()
        ? List.of(ResidentRoutine.Kind.STROLL, ResidentRoutine.Kind.LOOK_AROUND, ResidentRoutine.Kind.GREET, ResidentRoutine.Kind.STRETCH, ResidentRoutine.Kind.SIT) : VARIETY;
    var selected = variety.get((int) Math.floorMod(actor.hashCode() + sequence, variety.size()));
    return selected == ResidentRoutine.Kind.SIT && seats(s, actor).isEmpty() ? ResidentRoutine.Kind.LOOK_AROUND : selected;
  }

  private static final ImmutableInputView<Map<SubjectId, List<SeatPlace>>> REST_PLACES = new ImmutableInputView<>();
  private static List<SeatPlace> authoredSeats(FrontierWorldState s, SubjectId homeId) {
    return REST_PLACES.get(List.of(s.bootstrap()), () -> {
      var result = new HashMap<SubjectId, List<SeatPlace>>();
      for (var home : s.bootstrap().settlements()) {
        var found = new ArrayList<SeatPlace>();
        for (var building : home.structures()) for (var seat : building.definition().seats().values())
          if (building.definition().zones().get(seat.zoneId()).use() == BuildingDefinition.ZoneUse.REST)
            found.add(SeatPlace.authored(home.id(), building, seat));
        result.put(home.id(), found.stream().sorted(Comparator.comparing(p -> p.id().buildingId().value() + ":" + p.id().localId())).toList());
      }
      return Map.copyOf(result);
    }).getOrDefault(homeId, List.of());
  }

  public static List<SeatPlace> seats(FrontierWorldState s, SubjectId actor) {
    var person = Objects.requireNonNull(s.humanPopulation().resident(actor));
    var places = authoredSeats(s, person.settlementId());
    var start = s.actorLocations().get(actor).supportingSurface();
    var execution = s.actorExecutions().next(actor, ActorActivityKind.LEISURE, actor);
    return places.stream().filter(p -> distance(start, p.approach()) <= s.bootstrap().ruleset().residentLife().routines().strollRadius())
        .filter(p -> s.humanPopulation().seats().free(p.id()))
        .filter(p -> SeatContacts.furnitureKnowledge(s, p) != SeatContacts.FurnitureKnowledge.MISSING)
        .filter(p -> !ServiceDestinationClaims.excludedFor(s, actor).contains(p.approach())
            && !ServiceDestinationClaims.excludedFor(s, actor).contains(p.egress()))
        .sorted(Comparator.comparingLong(p -> distance(start, p.approach())))
        .limit(8).filter(p -> SeatContacts.approachClear(s, SeatUse.reserve(execution, p, 0))).toList();
  }

  public static SeatUse use(FrontierWorldState s, ResidentRoutine row) {
    var use = s.humanPopulation().seats().uses().get(row.actor());
    if (use == null) throw new IllegalArgumentException("routine lost its exact seat reservation");
    if (!row.execution().equals(Optional.of(use.execution())) || !row.seat().equals(Optional.of(use.place().id()))
        || !row.home().equals(use.place().home())) throw new IllegalArgumentException("routine lost its exact seat reservation");
    return use;
  }

  /** Intent is independent of whether a seated body can already plan a meal route. */
  public static boolean finishing(FrontierWorldState s, ResidentRoutine row, long tick) {
    if (tick >= (row.phase() == ResidentRoutine.Phase.PAUSING ? row.phaseAt() + row.pauseTicks()
        : row.startedAt() + s.bootstrap().ruleset().residentLife().routines().walkingBudgetTicks())) return true;
    if (row.kind() != ResidentRoutine.Kind.SIT) return false;
    var person = s.humanPopulation().resident(row.actor());
    var nutrition = s.humanPopulation().nutrition(row.actor());
    return SeatContacts.furnitureKnowledge(s, use(s, row).place()) == SeatContacts.FurnitureKnowledge.MISSING
        || s.combat().evasions().containsKey(row.actor())
        || io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalAuthority.needsCare(s, row.actor())
        || nutrition.accrueThrough(Math.max(tick, nutrition.lastEvaluatedTick()), s.bootstrap().ruleset().residentLife(),
            person.characteristics().effectiveMetabolismPermille(tick)).wantsFood(s.bootstrap().ruleset().residentLife())
        || !HumanAssignmentProjection.compile(s).idle(row.actor())
        || use(s, row).phase() == SeatUse.Phase.RESERVED
            && (row.phase() == ResidentRoutine.Phase.PAUSING || use(s, row).carrierEpoch() > 1);
  }

  private static boolean destination(FrontierWorldState s, SubjectId actor, ResidentRoutine.Kind kind,
      SurfaceAnchor goal, Optional<SeatPlace.Id> seat) {
    return kind == ResidentRoutine.Kind.SIT
        ? seat.isPresent() && seats(s, actor).stream().anyMatch(p -> p.id().equals(seat.orElseThrow()) && p.approach().equals(goal))
        : seat.isEmpty() && targets(s, actor, kind).contains(goal);
  }

  public static boolean eligible(FrontierWorldState s, SubjectId actor, long tick) {
    var rules = s.bootstrap().ruleset().residentLife().routines();
    var row = s.routines().rows().get(actor);
    var person = s.humanPopulation().resident(actor);
    var body = s.actorLocations().get(actor);
    if (!rules.enabled()
        || person == null
        || body == null
        || body.condition().status() != ActorLifeStatus.ALIVE
        || row != null && (row.phase() != ResidentRoutine.Phase.CLOSED || tick < row.eligibleAt())
        || row == null
            && tick < rules.admissionInterval() * (1 + Math.floorMod(actor.hashCode(), 40))
        || s.routines().active().size() >= rules.activeLimit()
        || s.combat().evasions().containsKey(actor)
        || s.humanPopulation().meals().containsKey(actor)
        || s.humanPopulation().migration(actor) != null
        || s.actorMovements().containsKey(actor)
        || io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalAuthority.needsCare(
            s, actor)
        || ServiceAccessCoordinator.turnoverPoint(s, actor).isPresent()
            && nextKind(s, actor) != ResidentRoutine.Kind.SIT
            && authoredSeats(s, person.settlementId()).stream().noneMatch(p -> p.approach().equals(body.supportingSurface()))
        || ActorInventoryInteractionFences.pending(s, actor)
        || !HumanAssignmentProjection.compile(s).idle(actor)) return false;
    var current = s.actorExecutions().actors().get(actor);
    if (current != null
        && (current.suspended().isPresent()
            || current
                .current()
                .filter(e -> e.activityKind() != ActorActivityKind.PRESENCE)
                .isPresent())) return false;
    if (s.humanPopulation()
        .nutrition(actor)
        .accrueThrough(
            tick,
            s.bootstrap().ruleset().residentLife(),
            person.characteristics().effectiveMetabolismPermille(tick))
        .wantsFood(s.bootstrap().ruleset().residentLife())) return false;
    return ActorExecutionCoordinator.ordinaryWorkAdmission(s, actor).permitted()
        && ActorExecutionComposition.LIFECYCLE.mayBegin(s, actor);
  }

  public static List<SurfaceAnchor> targets(
      FrontierWorldState s, SubjectId actor, ResidentRoutine.Kind kind) {
    var person = Objects.requireNonNull(s.humanPopulation().resident(actor));
    var start = s.actorLocations().get(actor).supportingSurface();
    if (kind == ResidentRoutine.Kind.SIT) return seats(s, actor).stream().map(SeatPlace::approach).toList();
    if (kind != ResidentRoutine.Kind.STROLL) return List.of(start);
    var declared =
        TARGETS
            .get(
                List.of(s.bootstrap()),
                () -> {
                  var result = new HashMap<SubjectId, List<SurfaceAnchor>>();
                  for (var home : s.bootstrap().settlements()) {
                    var points = new LinkedHashSet<SurfaceAnchor>();
                    for (var building : home.structures())
                      for (var entry : BuildingSpatialPlan.of(building).publicEntrances())
                        points.addAll(entry.exteriorApproach());
                    result.put(home.id(), List.copyOf(points));
                  }
                  return Map.copyOf(result);
                })
            .getOrDefault(person.settlementId(), List.of());
    int radius = s.bootstrap().ruleset().residentLife().routines().strollRadius();
    var knowledge = knowledge(s, actor, start);
    // Entrances themselves are often no-parking aprons. Probe a fixed public
    // neighbourhood through the shared known geometry, without loading terrain.
    var candidates = new LinkedHashSet<SurfaceAnchor>(declared);
    for (int distance = 2; distance <= Math.min(radius, 8); distance += 2)
      for (var facing : FacilityFacing.values())
        candidates.addAll(
            knowledge.surfacesAt(
                start.x() + facing.x() * distance, start.z() + facing.z() * distance));
    return candidates.stream()
        .filter(
            p -> {
              long d = distance(start, p);
              return d >= 3 && d <= radius;
            })
        .sorted(
            Comparator.comparingLong((SurfaceAnchor p) -> distance(start, p))
                .thenComparingInt(SurfaceAnchor::x)
                .thenComparingInt(SurfaceAnchor::z))
        .limit(16)
        .filter(
            p ->
                PedestrianLocalDeparture.publicPosition(s, p)
                    && knowledge.clearStation(p)
                    && ServiceBoundaryComposition.turnoverPoint(s, p).isEmpty()
                    && !ServiceDestinationClaims.excludedFor(s, actor).contains(p))
        .limit(8)
        .toList();
  }

  private static long distance(SurfaceAnchor a, SurfaceAnchor b) {
    return Math.abs((long) a.x() - b.x())
        + Math.abs((long) a.y() - b.y())
        + Math.abs((long) a.z() - b.z());
  }

  public static KnownPedestrianRouteKnowledge knowledge(
      FrontierWorldState s, SubjectId actor, SurfaceAnchor start) {
    var row = s.routines().rows().get(actor);
    return PedestrianLocalDeparture.departure(s, start,
        row != null && row.phase() != ResidentRoutine.Phase.CLOSED && row.seat().isPresent()
            ? SeatContacts.passages(s, SeatContacts.place(s, row.home(), row.seat().orElseThrow())) : List.of()).forActor(s, actor);
  }

  public static ResidentRoutine require(FrontierWorldState s, ActorExecutionId e) {
    var row = s.routines().rows().get(e.actorId());
    if (e.activityKind() != ActorActivityKind.LEISURE
        || !e.activityOwnerId().equals(e.actorId())
        || row == null
        || !row.execution().equals(Optional.of(e)))
      throw new IllegalArgumentException("routine lost its explicit resident execution");
    return row;
  }

  public static ResidentRoutine requested(
      FrontierWorldState s, RoutineEvent.Requested request, long tick) {
    ActorBodyAuthority.requireActuation(s, request.actor());
    var actor = request.subject();
    if (!eligible(s, actor, tick)
        || request.kind() != nextKind(s, actor)
        || !destination(s, actor, request.kind(), request.goal(), request.seat()))
      throw new IllegalArgumentException(
          "routine admission lacks an idle, nourished, available person and declared destination");
    var rules = s.bootstrap().ruleset().residentLife().routines();
    var previous = s.routines().rows().get(actor);
    long sequence = previous == null ? 1 : Math.addExact(previous.sequence(), 1);
    var execution = s.actorExecutions().next(actor, ActorActivityKind.LEISURE, actor);
    return new ResidentRoutine(
        actor,
        s.humanPopulation().resident(actor).settlementId(),
        sequence,
        request.kind(),
        (request.kind() == ResidentRoutine.Kind.STROLL || request.kind() == ResidentRoutine.Kind.SIT)
            ? ResidentRoutine.Phase.WALKING
            : ResidentRoutine.Phase.PAUSING,
        Optional.of(execution),
        request.goal(),
        request.seat(),
        tick,
        tick,
        rules.pauseTicks() + Math.floorMod(actor.hashCode(), 4) * 20L,
        Math.addExact(tick, rules.cooldownTicks()));
  }

  public static FrontierWorldState started(FrontierWorldState s, ResidentRoutine row, long tick) {
    if (!eligible(s, row.actor(), tick)
        || row.kind() != nextKind(s, row.actor())
        || row.startedAt() != tick
        || !destination(s, row.actor(), row.kind(), row.goal(), row.seat()))
      throw new IllegalArgumentException("stale routine start");
    var e = row.execution().orElseThrow();
    var expected = s.actorExecutions().next(row.actor(), ActorActivityKind.LEISURE, row.actor());
    var rules = s.bootstrap().ruleset().residentLife().routines();
    var prior = s.routines().rows().get(row.actor());
    if (!expected.equals(e)
        || !row.home().equals(s.humanPopulation().resident(row.actor()).settlementId())
        || row.sequence() != (prior == null ? 1 : prior.sequence() + 1)
        || row.phaseAt() != tick
        || row.eligibleAt() != tick + rules.cooldownTicks()
        || row.pauseTicks() != rules.pauseTicks() + Math.floorMod(row.actor().hashCode(), 4) * 20L
        || row.phase()
            != ((row.kind() == ResidentRoutine.Kind.STROLL || row.kind() == ResidentRoutine.Kind.SIT)
                ? ResidentRoutine.Phase.WALKING
                : ResidentRoutine.Phase.PAUSING))
      throw new IllegalArgumentException("routine start changed its nominal policy or identity");
    var changes = FrontierWorldStateUpdate.begin().routines(s.routines().with(row));
    if (row.seat().isPresent()) changes.humanPopulation(s.humanPopulation().withSeats(s.humanPopulation().seats().reserve(
        SeatUse.reserve(e, SeatContacts.place(s, row.home(), row.seat().orElseThrow()), tick))));
    return ActorExecutionComposition.LIFECYCLE.prepareBegin(s, e, tick).commit(s, changes);
  }

  public static FrontierWorldState arrived(
      FrontierWorldState s, RoutineEvent.Arrived event, long tick) {
    var row = require(s, event.execution());
    s.actorExecutions().requireCurrent(event.execution());
    if (row.sequence() != event.sequence()
        || s.actorMovements().containsKey(row.actor())
        || !s.actorLocations().get(row.actor()).supportingSurface().equals(row.goal()))
      throw new IllegalArgumentException("routine arrival lacks actual common journey completion");
    return s.withChanges(
        FrontierWorldStateUpdate.begin().routines(s.routines().with(row.arrived(tick))));
  }

  public static FrontierWorldState seated(FrontierWorldState s, RoutineEvent.Seated event, long tick) {
    var row = require(s, event.execution());
    s.actorExecutions().requireCurrent(event.execution());
    if (row.sequence() != event.sequence() || row.phase() != ResidentRoutine.Phase.SEATING
        || use(s, row).phase() != SeatUse.Phase.SEATED || use(s, row).pendingBody().isPresent()
        || !s.actorLocations().get(row.actor()).supportingSurface().equals(use(s, row).place().contact()))
      throw new IllegalArgumentException("rest interval needs its exact settled seated body receipt");
    return s.withChanges(FrontierWorldStateUpdate.begin().routines(s.routines().with(row.seated(tick))));
  }

  public static FrontierWorldState ended(
      FrontierWorldState s, RoutineEvent.Ended event, long tick) {
    var row = require(s, event.execution());
    s.actorExecutions().requireCurrent(event.execution());
    if (row.sequence() != event.sequence()
        || s.actorMovements().containsKey(row.actor())
        || ActorInventoryInteractionFences.pending(s, row.actor())
        || !finishing(s, row, tick)
        || row.seat().isPresent() && (use(s, row).pendingBody().isPresent() || use(s, row).phase() != SeatUse.Phase.RESERVED))
      throw new IllegalArgumentException(
          "routine cannot end before its movement/effect checkpoint and duration");
    return s.withChanges(
        FrontierWorldStateUpdate.begin()
            .humanPopulation(row.seat().isPresent() ? s.humanPopulation().withSeats(s.humanPopulation().seats().without(row.actor())) : s.humanPopulation())
            .routines(s.routines().with(row.closed()))
            .actorExecutions(s.actorExecutions().finish(event.execution())));
  }

  private ResidentRoutines() {}
}
