package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Durable admission and COLD progression of a Scout-rooted settlement assault. */
public final class HiveSettlementAssaultProcess {
    private HiveSettlementAssaultProcess() { }

    public static ScheduledAction start(StrategicTask task, HiveSettlementKnowledge.Sighting sighting, long dueAt) {
        String suffix = task.id().value().substring("task:".length()) + "-" + sighting.scoutId().value().replace(':', '-')
                + "-" + sighting.observedAt();
        return new ScheduledAction(new ScheduleId("schedule:settlement-assault-start-" + suffix), new SimInstant(dueAt), 0, task.id(),
                "frontier.settlement_assault.start", 1);
    }

    public static boolean hasPendingOrActiveAssault(FrontierWorldState state, SubjectId settlementId) {
        return state.strategicPlans().settlementAssaults().values().stream().anyMatch(assault -> assault.settlementId().equals(settlementId)
                && assault.status() != SettlementAssaultStatus.RESOLVED);
    }

    public static boolean hasFreshLocalTerritory(FrontierWorldState state, HiveSettlementKnowledge.Sighting sighting, long now) {
        return state.strategicPlans().hiveTerritoryKnowledge().freshInfection(state.bootstrap().ruleset(), now).keySet().stream().anyMatch(cell -> nearby(
                cell.originAtY(sighting.settlementAnchor().y()), sighting.settlementAnchor(),
                state.bootstrap().ruleset().spatial().hiveSettlementAssaultTerritoryRadius()));
    }

    public static List<ProposedEvent> planStart(FrontierWorldState state, ScheduledAction action) {
        StrategicTask task = state.strategicPlans().tasks().get(action.subject());
        if (task == null || task.kind() != StrategicTaskKind.ASSAULT_SETTLEMENT || task.status() != StrategicTaskStatus.PENDING) return List.of();
        Optional<HiveSettlementKnowledge.Sighting> sighting = state.strategicPlans().hiveSettlementKnowledge().entries().values().stream()
                .filter(value -> start(task, value, action.dueAt().ticks()).id().equals(action.id()))
                .filter(value -> value.observedAt() >= Math.subtractExact(action.dueAt().ticks(),
                        state.bootstrap().ruleset().cadence().hiveSettlementKnowledgeMaxAge())).findFirst();
        if (state.strategicPlans().hiveDoctrine().doctrine() != HiveDoctrine.INTERDICT || sighting.isEmpty()
                || !hasFreshLocalTerritory(state, sighting.orElseThrow(), action.dueAt().ticks())
                || hasPendingOrActiveAssault(state, sighting.orElseThrow().settlementId()) || !targetGeometryExists(state, sighting.orElseThrow())) {
            return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        }
        SettlementAssault assault = assault(state, task, sighting.orElseThrow());
        if (assault == null) return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        List<ProposedEvent> events = new ArrayList<>();
        events.add(transition(task, StrategicTaskStatus.ACTIVE));
        events.addAll(ProductionProcess.planSettlementDefenceInterruptions(state, assault));
        events.add(new ProposedEvent(assault.hiveId(), new SettlementAssaultStarted(assault)));
        events.add(schedule(progress(assault, action.dueAt().ticks() + state.bootstrap().ruleset().cadence().hiveSettlementAssaultStepInterval())));
        events.add(schedule(DefenderEquipmentProcess.review(assault, action.dueAt().ticks() + 1L)));
        events.add(schedule(DefenderEquipmentReturnProcess.review(assault, action.dueAt().ticks() + 1L)));
        return List.copyOf(events);
    }

    public static List<ProposedEvent> planProgress(FrontierWorldState state, ScheduledAction action) {
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(action.subject());
        if (assault == null || (assault.status() != SettlementAssaultStatus.APPROACHING
                && assault.status() != SettlementAssaultStatus.WAITING_FOR_BATTLE)
                || !action.id().equals(progress(assault, action.dueAt().ticks()).id())) return List.of();
        if (assault.status() == SettlementAssaultStatus.WAITING_FOR_BATTLE) {
            List<SubjectId> attackers = livingAttackers(state, assault), defenders = livingDefenders(state, assault);
            if (attackers.isEmpty() || defenders.isEmpty()) return terminal(assault, attackers, defenders);
            if (FrontierSettlementAssaultBattlefield.candidate(state, assault).isEmpty()) {
                return List.of(new ProposedEvent(assault.hiveId(), new SettlementAssaultTransition(assault.id(), SettlementAssaultStatus.CONFLICT)));
            }
            if (!coldAvailable(state, assault)) return List.of(schedule(progress(assault, action.dueAt().ticks()
                    + state.bootstrap().ruleset().cadence().hiveSettlementAssaultStepInterval())));
            return List.of(new ProposedEvent(assault.hiveId(), new SettlementAssaultTransition(assault.id(), SettlementAssaultStatus.COLD_COMBAT)),
                    schedule(combat(assault, action.dueAt().ticks() + state.bootstrap().ruleset().cadence().hiveSettlementAssaultCombatInterval())));
        }
        if (!coldAvailable(state, assault)) return List.of(schedule(progress(assault, action.dueAt().ticks()
                + state.bootstrap().ruleset().cadence().hiveSettlementAssaultStepInterval())));
        SettlementAssaultAttacker next = assault.attackers().stream().filter(value -> !value.atDestination()).findFirst().orElse(null);
        if (next == null) return List.of(new ProposedEvent(assault.hiveId(), new SettlementAssaultTransition(assault.id(), SettlementAssaultStatus.WAITING_FOR_BATTLE)),
                schedule(progress(assault, action.dueAt().ticks() + state.bootstrap().ruleset().cadence().hiveSettlementAssaultStepInterval())));
        return List.of(new ProposedEvent(assault.hiveId(), new SettlementAssaultAttackerAdvanced(assault.id(), next.actorId(), next.routeIndex() + 1)),
                schedule(progress(assault, action.dueAt().ticks() + state.bootstrap().ruleset().cadence().hiveSettlementAssaultStepInterval())));
    }

    public static List<ProposedEvent> planCombat(FrontierWorldState state, ScheduledAction action) {
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(action.subject());
        if (assault == null || assault.status() != SettlementAssaultStatus.COLD_COMBAT
                || !action.id().equals(combat(assault, action.dueAt().ticks()).id())) return List.of();
        List<SubjectId> attackers = livingAttackers(state, assault), defenders = livingDefenders(state, assault);
        if (attackers.isEmpty() || defenders.isEmpty()) return terminal(assault, attackers, defenders);
        if (FrontierSettlementAssaultBattlefield.candidate(state, assault).isEmpty()) {
            return List.of(new ProposedEvent(assault.hiveId(), new SettlementAssaultTransition(assault.id(), SettlementAssaultStatus.CONFLICT)));
        }
        if (!coldAvailable(state, assault)) return List.of(schedule(combat(assault, action.dueAt().ticks()
                + state.bootstrap().ruleset().cadence().hiveSettlementAssaultCombatInterval())));
        boolean hiveTurn = (assault.nextStrikeEpoch() & 1) == 0;
        SubjectId attacker = choose(hiveTurn ? attackers : defenders, assault.nextStrikeEpoch());
        SubjectId target = choose(hiveTurn ? defenders : attackers, assault.nextStrikeEpoch());
        SettlementAssaultStrike strike = new SettlementAssaultStrike(assault.id(), attacker, target, assault.nextStrikeEpoch(), damage(state, attacker));
        FixedScalar after = state.actorLocations().get(target).condition().health().minus(strike.damage());
        List<ProposedEvent> events = new ArrayList<>(List.of(new ProposedEvent(assault.hiveId(), strike)));
        if (after.compareTo(FixedScalar.ZERO) <= 0 && ((hiveTurn && defenders.size() == 1) || (!hiveTurn && attackers.size() == 1))) {
            events.add(new ProposedEvent(assault.hiveId(), new SettlementAssaultResolved(assault.id(),
                    hiveTurn ? SettlementAssaultOutcome.HIVE_VICTORY : SettlementAssaultOutcome.SETTLEMENT_VICTORY)));
        } else events.add(schedule(combat(assault, action.dueAt().ticks()
                + state.bootstrap().ruleset().cadence().hiveSettlementAssaultCombatInterval())));
        return List.copyOf(events);
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, SettlementAssaultStarted started) {
        SettlementAssault assault = started.assault();
        StrategicTask task = state.strategicPlans().tasks().get(assault.taskId());
        if (!subject.equals(assault.hiveId()) || task == null || task.kind() != StrategicTaskKind.ASSAULT_SETTLEMENT
                || task.status() != StrategicTaskStatus.ACTIVE || !task.ownerId().equals(subject)
                || !state.strategicPlans().hiveSettlementKnowledge().entries().getOrDefault(assault.settlementId(), assault.sighting()).equals(assault.sighting())) {
            throw new IllegalArgumentException("settlement assault start has a foreign owner, stale sighting or inactive task");
        }
        return state.withStrategicPlans(state.strategicPlans().startSettlementAssault(assault));
    }

    public static FrontierWorldState reduceAdvanced(FrontierWorldState state, SubjectId subject, SettlementAssaultAttackerAdvanced advanced) {
        SettlementAssault assault = assault(state, advanced.assaultId());
        if (!subject.equals(assault.hiveId()) || !coldAvailable(state, assault)) {
            throw new IllegalArgumentException("COLD assault advance has a foreign owner or leased actor");
        }
        SettlementAssault next = assault.advanceAttacker(advanced.attackerId(), advanced.routeIndex());
        SettlementAssaultAttacker attacker = next.attackers().stream().filter(value -> value.actorId().equals(advanced.attackerId())).findFirst().orElseThrow();
        return state.withActorBody(attacker.actorId(), BodyPosition.above(new SurfaceAnchor(attacker.position())), state.strategicPlans().replaceSettlementAssault(next));
    }

    public static FrontierWorldState reduceTransition(FrontierWorldState state, SubjectId subject, SettlementAssaultTransition transition) {
        SettlementAssault assault = assault(state, transition.assaultId());
        boolean waiting = transition.status() == SettlementAssaultStatus.WAITING_FOR_BATTLE
                && (!assault.allAttackersAtBattlefield() || !coldAvailable(state, assault));
        boolean cold = transition.status() == SettlementAssaultStatus.COLD_COMBAT
                && (!assault.allAttackersAtBattlefield() || !coldAvailable(state, assault)
                || FrontierSettlementAssaultBattlefield.candidate(state, assault).isEmpty());
        if (!subject.equals(assault.hiveId()) || waiting || cold) {
            throw new IllegalArgumentException("settlement assault transition has invalid COLD authority");
        }
        return state.withStrategicPlans(state.strategicPlans().transitionSettlementAssault(assault.id(), transition.status()));
    }

    public static FrontierWorldState reduceStrike(FrontierWorldState state, SubjectId subject, SettlementAssaultStrike strike) {
        SettlementAssault assault = assault(state, strike.assaultId());
        if (!subject.equals(assault.hiveId()) || assault.status() != SettlementAssaultStatus.COLD_COMBAT || !coldAvailable(state, assault)
                || strike.epoch() != assault.nextStrikeEpoch()) throw new IllegalArgumentException("invalid COLD settlement assault strike");
        boolean hiveTurn = (strike.epoch() & 1) == 0;
        if (hiveTurn != assault.attackerIds().contains(strike.attackerId()) || hiveTurn == assault.attackerIds().contains(strike.targetId())
                || !alive(state, strike.attackerId()) || !alive(state, strike.targetId()) || !damage(state, strike.attackerId()).equals(strike.damage())) {
            throw new IllegalArgumentException("settlement assault strike does not match exact combatants");
        }
        ActorLocation target = state.actorLocations().get(strike.targetId());
        FixedScalar remaining = target.condition().health().minus(strike.damage());
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(strike.targetId(), remaining.compareTo(FixedScalar.ZERO) <= 0 ? target.deadAt(target.body())
                : new ActorLocation(target.body(), target.condition().withHealth(remaining)));
        return copy(state, actors, state.strategicPlans().replaceSettlementAssault(assault.afterStrike(strike.epoch())));
    }

    public static FrontierWorldState reduceResolved(FrontierWorldState state, SubjectId subject, SettlementAssaultResolved resolved) {
        SettlementAssault assault = assault(state, resolved.assaultId());
        if (!subject.equals(assault.hiveId()) || assault.status() == SettlementAssaultStatus.RESOLVED || assault.status() == SettlementAssaultStatus.HOT
                || resolved.outcome() != SettlementAssaultOutcome.ABORTED && outcome(state, assault) != resolved.outcome()) {
            throw new IllegalArgumentException("settlement assault resolution disagrees with exact combatants");
        }
        StrategicPlanState plans = state.strategicPlans().resolveSettlementAssault(assault.id(), resolved.outcome());
        StrategicTask task = plans.tasks().get(assault.taskId());
        plans = plans.transitionTask(task.id(), resolved.outcome() == SettlementAssaultOutcome.HIVE_VICTORY
                ? StrategicTaskStatus.COMPLETED : StrategicTaskStatus.BLOCKED);
        return state.withStrategicPlans(plans);
    }

    private static SettlementAssault assault(FrontierWorldState state, SubjectId id) {
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(id);
        if (assault == null) throw new IllegalArgumentException("unknown settlement assault");
        return assault;
    }

    private static SettlementAssault assault(FrontierWorldState state, StrategicTask task, HiveSettlementKnowledge.Sighting sighting) {
        List<Bioform> eligible = java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(value -> alive(state, value.id())).filter(value -> availableBioform(state, value.id(), null))
                .sorted(Comparator.comparingLong((Bioform value) -> distanceSquared(state.actorLocations().get(value.id()).supportingSurface().support(), sighting.settlementAnchor()))
                        .thenComparing(Bioform::id)).toList();
        List<Bioform> selected = new ArrayList<>();
        eligible.stream().filter(value -> value.role() == BioformRole.BOMBER).limit(1).forEach(selected::add);
        eligible.stream().filter(value -> value.role() == BioformRole.GUARD).limit(2).forEach(selected::add);
        if (selected.stream().noneMatch(value -> value.role() == BioformRole.BOMBER) || selected.stream().noneMatch(value -> value.role() == BioformRole.GUARD)) return null;
        HumanAssignmentProjection assignments = HumanAssignmentProjection.compile(state);
        List<SubjectId> defenders = state.humanPopulation().residents().values().stream().filter(value -> value.settlementId().equals(sighting.settlementId()))
                .filter(value -> assignments.idle(value.id()) || ProductionProcess.interruptibleForSettlementDefence(state, value.id()).isPresent())
                .filter(value -> FrontierWorldStateSupport.workCapable(state, value))
                .sorted(Comparator.comparing((ResidentProfile value) -> value.profession() != ResidentProfession.SECURITY_WORKER)
                        .thenComparing(Comparator.comparing((ResidentProfile value) -> value.capability(HumanCapability.SECURITY)).reversed())
                        .thenComparing(Comparator.comparing((ResidentProfile value) -> value.capability(HumanCapability.CIVIC)).reversed())
                        .thenComparing(ResidentProfile::id))
                .limit(SettlementAssault.MAX_DEFENDERS).map(ResidentProfile::id).toList();
        if (defenders.isEmpty()) return null;
        List<BlockPosition> floors = FrontierSettlementAssaultBattlefield.attackerFloors(state, sighting, defenders, selected.size()).orElse(null);
        if (floors == null) return null;
        return new SettlementAssault(new SubjectId("assault:" + task.id().value().substring("task:".length())), task.id(), task.ownerId(), sighting,
                java.util.stream.IntStream.range(0, selected.size()).mapToObj(index -> new SettlementAssaultAttacker(selected.get(index).id(),
                        approach(state, state.actorLocations().get(selected.get(index).id()).supportingSurface().support(), floors.get(index)), 0)).toList(),
                defenders, SettlementAssaultStatus.APPROACHING, 0, Optional.empty());
    }

    private static boolean targetGeometryExists(FrontierWorldState state, HiveSettlementKnowledge.Sighting sighting) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), sighting.settlementId());
        return settlement.anchor().equals(sighting.settlementAnchor()) && settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.HALL).anyMatch(value -> state.structureConditions().get(value.id()) != StructureCondition.DESTROYED);
    }

    private static boolean availableBioform(FrontierWorldState state, SubjectId id, SettlementAssault currentAssault) {
        AmbientActorLease ambient = state.ambientLeases().get(id);
        return (ambient == null || ambient.status() == AmbientLeaseStatus.CLOSED)
                && state.strategicPlans().routeEngagements().values().stream().noneMatch(value -> value.status() != RouteEngagementStatus.RESOLVED && value.attackerIds().contains(id))
                && state.strategicPlans().settlementAssaults().values().stream().noneMatch(value -> !value.equals(currentAssault)
                && value.status() != SettlementAssaultStatus.RESOLVED && value.attackerIds().contains(id));
    }

    private static boolean coldAvailable(FrontierWorldState state, SettlementAssault assault) {
        List<SubjectId> combatants = new ArrayList<>(assault.attackerIds());
        combatants.addAll(assault.defenderIds());
        HumanAssignmentProjection assignments = HumanAssignmentProjection.compile(state);
        return FrontierSceneAdmission.available(state, combatants)
                && assault.attackerIds().stream().allMatch(id -> availableBioform(state, id, assault))
                && assault.defenderIds().stream().allMatch(id -> assignments.assignment(id).kind() == HumanAssignmentKind.SETTLEMENT_DEFENCE
                && assignments.assignment(id).ownerId().filter(assault.id()::equals).isPresent()
                && !FrontierSceneAdmission.reservedByOtherThanSettlementAssault(state, id, assault.id()));
    }

    private static List<SubjectId> livingAttackers(FrontierWorldState state, SettlementAssault assault) { return assault.attackerIds().stream().filter(id -> alive(state, id)).sorted().toList(); }
    private static List<SubjectId> livingDefenders(FrontierWorldState state, SettlementAssault assault) { return assault.defenderIds().stream().filter(id -> alive(state, id)).sorted().toList(); }
    private static boolean alive(FrontierWorldState state, SubjectId actor) { return state.actorLocations().get(actor) != null && state.actorLocations().get(actor).condition().status() == ActorLifeStatus.ALIVE; }
    private static SubjectId choose(List<SubjectId> values, int epoch) { return values.get(Math.floorMod(epoch, values.size())); }
    private static SettlementAssaultOutcome outcome(FrontierWorldState state, SettlementAssault assault) {
        boolean attackers = !livingAttackers(state, assault).isEmpty(), defenders = !livingDefenders(state, assault).isEmpty();
        if (attackers && !defenders) return SettlementAssaultOutcome.HIVE_VICTORY;
        if (!attackers && defenders) return SettlementAssaultOutcome.SETTLEMENT_VICTORY;
        if (!attackers) return SettlementAssaultOutcome.ABORTED;
        throw new IllegalArgumentException("settlement assault still has living combatants");
    }
    private static List<ProposedEvent> terminal(SettlementAssault assault, List<SubjectId> attackers, List<SubjectId> defenders) {
        SettlementAssaultOutcome outcome = attackers.isEmpty() && defenders.isEmpty() ? SettlementAssaultOutcome.ABORTED
                : attackers.isEmpty() ? SettlementAssaultOutcome.SETTLEMENT_VICTORY : SettlementAssaultOutcome.HIVE_VICTORY;
        return List.of(new ProposedEvent(assault.hiveId(), new SettlementAssaultResolved(assault.id(), outcome)));
    }
    private static FixedScalar damage(FrontierWorldState state, SubjectId actor) { return RouteEngagementCombatRules.damage(state, actor); }
    private static List<BlockPosition> approach(FrontierWorldState state, BlockPosition start, BlockPosition end) {
        if (start.equals(end)) return List.of(start);
        long dx = (long) end.x() - start.x(), dy = (long) end.y() - start.y(), dz = (long) end.z() - start.z();
        long distance = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        int step = state.bootstrap().ruleset().spatial().hiveSettlementAssaultColdStepBlocks();
        int steps = Math.max(1, Math.toIntExact((distance + step - 1) / step));
        List<BlockPosition> route = new ArrayList<>(steps + 1);
        for (int index = 0; index <= steps; index++) route.add(new BlockPosition(interpolate(start.x(), dx, index, steps), interpolate(start.y(), dy, index, steps), interpolate(start.z(), dz, index, steps)));
        return List.copyOf(route);
    }
    private static int interpolate(int start, long delta, int index, int steps) { return Math.toIntExact(Math.addExact(start, Math.floorDiv(Math.multiplyExact(delta, index), steps))); }
    private static long distanceSquared(BlockPosition left, BlockPosition right) { long x = (long) left.x() - right.x(), y = (long) left.y() - right.y(), z = (long) left.z() - right.z(); return x * x + y * y + z * z; }
    private static ScheduledAction progress(SettlementAssault assault, long dueAt) { return action("progress", assault.id(), dueAt); }
    public static ScheduledAction combat(SettlementAssault assault, long dueAt) { return action("combat", assault.id(), dueAt); }
    private static ScheduledAction action(String phase, SubjectId assaultId, long dueAt) {
        // A due action is consumed only after its replacement is admitted. Retaining the
        // due instant in the immutable identity makes COLD combat rescheduling atomic instead
        // of attempting to insert a second copy of the current schedule ID.
        return new ScheduledAction(new ScheduleId("schedule:settlement-assault-" + phase + "-" + assaultId.value().substring("assault:".length()) + "-at-" + dueAt), new SimInstant(dueAt), 0,
                assaultId, "frontier.settlement_assault." + phase, 1);
    }
    private static ProposedEvent transition(StrategicTask task, StrategicTaskStatus status) { return new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), status)); }
    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }
    private static FrontierWorldState copy(FrontierWorldState state, Map<SubjectId, ActorLocation> actors, StrategicPlanState plans) {
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).strategicPlans(plans));
    }

    private static boolean nearby(BlockPosition left, BlockPosition right, int radius) {
        long x = (long) left.x() - right.x(), z = (long) left.z() - right.z();
        return x * x + z * z <= (long) radius * radius;
    }
}
