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
        if (assault == null) {
            // A visible operation may wake exact cocoon occupants, but it may not pull them
            // through an intact block or pretend that a chunk visit created an attacker.
            // Mobilisation retains the active task until the same roster reaches its Ganglion
            // staging surfaces, where the dedicated departure transition owns admission.
            return HiveMobilizationProcess.forSettlementAssault(state, task, sighting.orElseThrow(), action.dueAt().ticks())
                    .<List<ProposedEvent>>map(mobilization -> List.of(transition(task, StrategicTaskStatus.ACTIVE),
                            new ProposedEvent(mobilization.hiveId(), new HiveMobilizationStarted(mobilization))))
                    .orElseGet(() -> List.of(transition(task, StrategicTaskStatus.BLOCKED)));
        }
        List<ProposedEvent> events = new ArrayList<>(admit(state, assault, action.dueAt().ticks()));
        events.addFirst(transition(task, StrategicTaskStatus.ACTIVE));
        return List.copyOf(events);
    }

    /**
     * Builds the one post-assembly transaction.  The caller supplies the speculative final
     * retained assembly so this method never looks for nearby replacement bioforms or geometry.
     */
    static List<ProposedEvent> planAssemblyDeparture(FrontierWorldState state, HiveMobilization mobilization,
                                                      HiveTaskAssembly completedAssembly, long now) {
        if (mobilization.status() != HiveMobilizationStatus.ASSEMBLING || !completedAssembly.complete()
                || !mobilization.assembly().orElseThrow().members().keySet().equals(completedAssembly.members().keySet())) {
            return List.of();
        }
        StrategicTask task = state.strategicPlans().tasks().get(mobilization.taskId());
        if (task == null || task.kind() != StrategicTaskKind.ASSAULT_SETTLEMENT || task.status() != StrategicTaskStatus.ACTIVE
                || !task.ownerId().equals(mobilization.hiveId()) || !targetGeometryExists(state, mobilization.sighting())
                || !currentSightingSupportsDeparture(state, mobilization, now) || !hasFreshLocalTerritory(state, mobilization.sighting(), now)) {
            return List.of(new ProposedEvent(mobilization.hiveId(), new HiveMobilizationConflicted(mobilization.id(),
                    HiveMobilizationConflictReason.DEPARTURE_UNAVAILABLE, Optional.empty())));
        }
        SettlementAssault assault = assaultFromAssembly(state, task, mobilization, completedAssembly);
        if (assault == null) {
            return List.of(new ProposedEvent(mobilization.hiveId(), new HiveMobilizationConflicted(mobilization.id(),
                    HiveMobilizationConflictReason.DEPARTURE_UNAVAILABLE, Optional.empty())));
        }
        List<ProposedEvent> events = new ArrayList<>();
        // One payload owns both ends of the custody transfer. A separate start event would
        // expose a departed roster without a strategic owner between WAL reductions.
        events.add(new ProposedEvent(mobilization.hiveId(), new HiveMobilizationDeparted(mobilization.id(), assault)));
        events.addAll(postAdmission(state, assault, now));
        return List.copyOf(events);
    }

    public static List<ProposedEvent> planProgress(FrontierWorldState state, ScheduledAction action) {
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(action.subject());
        if (assault == null || (assault.status() != SettlementAssaultStatus.APPROACHING
                && assault.status() != SettlementAssaultStatus.WAITING_FOR_BATTLE)
                || !action.id().equals(progress(assault, action.dueAt().ticks()).id())) return List.of();
        if (assault.status() == SettlementAssaultStatus.WAITING_FOR_BATTLE) {
            List<SubjectId> attackers = livingCombatantAttackers(state, assault), defenders = livingDefenders(state, assault);
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
        List<SubjectId> attackers = livingCombatantAttackers(state, assault), defenders = livingDefenders(state, assault);
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
                || !isExactOverseer(state, assault.overseerId()) || state.strategicPlans().settlementAssaults().containsKey(assault.id())) {
            throw new IllegalArgumentException("settlement assault start has a foreign owner, stale sighting or inactive task");
        }
        HiveMobilization mobilization = state.hiveColony().mobilizations().values().stream()
                .filter(value -> value.taskId().equals(assault.taskId())).findFirst().orElse(null);
        if (mobilization != null) {
            throw new IllegalArgumentException("a cocoon mobilisation may start its assault only through its atomic departure payload");
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
        if (hiveTurn != assault.combatantAttackerIds().contains(strike.attackerId()) || hiveTurn == assault.combatantAttackerIds().contains(strike.targetId())
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
        Bioform overseer = eligible.stream().filter(Bioform::isOverseer).findFirst().orElse(null);
        if (overseer == null) return null;
        List<Bioform> selected = new ArrayList<>();
        eligible.stream().filter(Bioform::isExplosiveAssaulter).limit(1).forEach(selected::add);
        eligible.stream().filter(Bioform::isDefender).filter(value -> !value.id().equals(overseer.id())).limit(2).forEach(selected::add);
        if (selected.stream().noneMatch(Bioform::isExplosiveAssaulter) || selected.stream().filter(Bioform::isDefender).count() != 2) return null;
        selected.add(overseer);
        if (!HiveCommandCapacity.admits(state.bootstrap().ruleset(), overseer.id(), selected.stream().map(Bioform::id).toList(), allBioforms(state))) return null;
        return assault(state, task, sighting, overseer.id(), selected.stream().map(Bioform::id).toList(),
                selected.stream().collect(java.util.stream.Collectors.toMap(Bioform::id,
                        value -> state.actorLocations().get(value.id()).supportingSurface())));
    }

    /**
     * Compiles the only admissible first state of an assault handed off by a completed
     * mobilisation.  Package-visible for the departure reducer: a durable payload is evidence
     * of the planner's decision, never authority to alter its exact routes or defender unit.
     */
    static SettlementAssault assaultFromAssembly(FrontierWorldState state, StrategicTask task, HiveMobilization mobilization,
                                                 HiveTaskAssembly completedAssembly) {
        if (mobilization.memberIds().stream().anyMatch(id -> !alive(state, id))
                || !isExactOverseer(state, mobilization.overseerId())
                || !HiveCommandCapacity.admits(state.bootstrap().ruleset(), mobilization.overseerId(), mobilization.memberIds(), allBioforms(state))) return null;
        Map<SubjectId, SurfaceAnchor> starts = completedAssembly.members().entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> entry.getValue().destinationSurface()));
        return assault(state, task, mobilization.sighting(), mobilization.overseerId(), mobilization.memberIds(), starts);
    }

    private static SettlementAssault assault(FrontierWorldState state, StrategicTask task, HiveSettlementKnowledge.Sighting sighting,
                                             SubjectId overseerId, List<SubjectId> attackers, Map<SubjectId, SurfaceAnchor> starts) {
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
        List<BlockPosition> floors = FrontierSettlementAssaultBattlefield.attackerFloors(state, sighting, defenders, attackers.size()).orElse(null);
        if (floors == null) return null;
        return new SettlementAssault(new SubjectId("assault:" + task.id().value().substring("task:".length())), task.id(), task.ownerId(), sighting,
                overseerId, java.util.stream.IntStream.range(0, attackers.size()).mapToObj(index -> new SettlementAssaultAttacker(attackers.get(index),
                        approach(state, starts.get(attackers.get(index)).support(), floors.get(index)), 0)).toList(),
                defenders, SettlementAssaultStatus.APPROACHING, 0, Optional.empty());
    }

    private static List<ProposedEvent> admit(FrontierWorldState state, SettlementAssault assault, long now) {
        List<ProposedEvent> events = new ArrayList<>();
        events.add(new ProposedEvent(assault.hiveId(), new SettlementAssaultStarted(assault)));
        events.addAll(postAdmission(state, assault, now));
        return List.copyOf(events);
    }

    /** Effects and schedules that follow an already atomically admitted assault. */
    private static List<ProposedEvent> postAdmission(FrontierWorldState state, SettlementAssault assault, long now) {
        List<ProposedEvent> events = new ArrayList<>();
        events.addAll(ProductionProcess.planSettlementDefenceInterruptions(state, assault));
        events.add(schedule(progress(assault, now + state.bootstrap().ruleset().cadence().hiveSettlementAssaultStepInterval())));
        events.add(schedule(DefenderEquipmentProcess.review(assault, now + 1L)));
        events.add(schedule(DefenderEquipmentReturnProcess.review(assault, now + 1L)));
        return List.copyOf(events);
    }

    private static boolean targetGeometryExists(FrontierWorldState state, HiveSettlementKnowledge.Sighting sighting) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), sighting.settlementId());
        return settlement.anchor().equals(sighting.settlementAnchor()) && settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.HALL).anyMatch(value -> state.structureConditions().get(value.id()) != StructureCondition.DESTROYED);
    }

    private static boolean currentSightingSupportsDeparture(FrontierWorldState state, HiveMobilization mobilization, long now) {
        HiveSettlementKnowledge.Sighting current = state.strategicPlans().hiveSettlementKnowledge().entries().get(mobilization.settlementId());
        return current != null && current.settlementAnchor().equals(mobilization.sighting().settlementAnchor())
                && current.observedAt() >= mobilization.sighting().observedAt()
                && current.observedAt() >= Math.subtractExact(now, state.bootstrap().ruleset().cadence().hiveSettlementKnowledgeMaxAge());
    }

    private static boolean isExactOverseer(FrontierWorldState state, SubjectId actorId) {
        return allBioforms(state).get(actorId) != null && allBioforms(state).get(actorId).isOverseer() && alive(state, actorId);
    }

    private static Map<SubjectId, Bioform> allBioforms(FrontierWorldState state) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Bioform::id, value -> value));
    }

    private static boolean availableBioform(FrontierWorldState state, SubjectId id, SettlementAssault currentAssault) {
        AmbientActorLease ambient = state.ambientLeases().get(id);
        // Strategic COLD movement has no authority to pull an exact identity through an intact
        // cocoon.  Mobilisation is its own lifecycle boundary; until then only deployed forms
        // may be selected for an assault.
        return HivePhysiologySupport.availableForIndependentOperation(state, id)
                && (ambient == null || ambient.status() == AmbientLeaseStatus.CLOSED)
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

    private static List<SubjectId> livingCombatantAttackers(FrontierWorldState state, SettlementAssault assault) { return assault.combatantAttackerIds().stream().filter(id -> alive(state, id)).sorted().toList(); }
    private static List<SubjectId> livingDefenders(FrontierWorldState state, SettlementAssault assault) { return assault.defenderIds().stream().filter(id -> alive(state, id)).sorted().toList(); }
    private static boolean alive(FrontierWorldState state, SubjectId actor) { return state.actorLocations().get(actor) != null && state.actorLocations().get(actor).condition().status() == ActorLifeStatus.ALIVE; }
    private static SubjectId choose(List<SubjectId> values, int epoch) { return values.get(Math.floorMod(epoch, values.size())); }
    private static SettlementAssaultOutcome outcome(FrontierWorldState state, SettlementAssault assault) {
        boolean attackers = !livingCombatantAttackers(state, assault).isEmpty(), defenders = !livingDefenders(state, assault).isEmpty();
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
