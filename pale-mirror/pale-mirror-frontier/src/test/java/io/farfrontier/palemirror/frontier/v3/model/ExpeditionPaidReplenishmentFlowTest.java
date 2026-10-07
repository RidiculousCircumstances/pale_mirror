package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.group.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Post-arrival unit fixture. Only the subsequent purchase, clearance and meal are registered outcomes;
 * the initial placement is not native evidence or a claim that the outward trip was exercised here. */
class ExpeditionPaidReplenishmentFlowTest {
    @Test void retainedPaidPurchaseSurvivesRecoveryAndClearsTheSupplierBeforePortableEating() {
        var base = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:paid-visited-supplies"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1"));
        var engine = FrontierEngines.createCanonicalStateAccess(base);
        TransportMission mission = null;
        for (int turn = 0; turn < 2048; turn++) {
            var state = engine.canonicalState().state();
            mission = state.shipments().missions().values().stream().filter(m -> m.stage() == TransportMission.Stage.OUTBOUND).findFirst().orElse(null);
            if (mission != null) break;
            advance(engine, 1);
        }
        assertNotNull(mission);
        var state = engine.canonicalState().state(); var group = state.unitGroups().groups().get(mission.groupId());
        long tick = engine.checkpoint().instant().ticks(); var recipient = group.members().getFirst().actorId();
        var positions = new LinkedHashMap<>(state.actorLocations());
        for (var member : group.members()) positions.put(member.actorId(), positions.get(member.actorId()).withBody(
                member.actorId().equals(recipient) ? mission.receiver().station().standingBody() : mission.destinationRendezvous().standingBody()));
        var groups = state.unitGroups().replace(group, new UnitGroup(group.id(), group.mission(), group.members(), group.formation(),
                UnitGroup.Phase.AT_GOAL, group.revision() + 1, 1, Optional.empty()));
        var movements = new LinkedHashMap<>(state.actorMovements()); group.members().forEach(m -> movements.remove(m.actorId()));
        var resources = state.inventory().fungibleResources(); var accounts = new LinkedHashMap<>(resources.accounts());
        for (var account : resources.accounts().values()) if (account.custody().equals(new ResourceCustody.Actor(recipient))
                && account.claimQuantities().isEmpty()) accounts.put(account.id(), new CustodyAccount(account.id(),
                        new ResourceCustody.WorldCarrier(UUID.nameUUIDFromBytes(account.id().value().getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                        account.lotQuantities(), Map.of()));
        resources = new FungibleResourceLedger(resources.lots(), resources.claims(), accounts, resources.bindings());
        var stock = new ResourceLot(new SubjectId("lot:visited-supplier-stock"), mission.receiver().settlementId(), FoodCatalog.BREAD,
                256, "fixture:finite-foreign-supplier-stock", List.of());
        var staging = new SubjectId("custody:visited-supplier-stock");
        resources = resources.issue(stock, new CustodyAccount(staging, new ResourceCustody.Container(mission.receiver().containerId()),
                Map.of(stock.id(), stock.quantity()), Map.of()));
        resources = resources.transfer(staging, ReferenceContainerCustody.scopeId(mission.receiver().containerId()), Map.of(stock.id(), stock.quantity()), Map.of());
        var population = state.humanPopulation(); var nutrition = new LinkedHashMap<>(population.nutrition());
        nutrition.put(recipient, new ResidentNutrition(ResidentNutritionStatus.HUNGRY, state.bootstrap().ruleset().residentLife().eatBelowUnits() - 1, tick, 0));
        var precondition = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(positions).actorMovements(movements).unitGroups(groups)
                .inventory(state.inventory().withFungibleResources(resources)).humanPopulation(new HumanPopulation(population.households(), population.residents(), population.birthJobs(),
                    population.health(), population.quarantines(), population.migrations(), population.provisions(), nutrition,
                    population.medicalOperations(), population.schedules(), population.meals(), population.mealResourceObligations())));
        // The ordinary goods workflow has first access. A refill cannot deadlock its
        // own courier's unloading, and terminal courier work must release UAE before reuse.
        var arrivingConfiguration = new FrontierEngineConfiguration<>(base.worldId(), precondition, new SimInstant(tick), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                base.stateCodec(), base.projectionMapper(), base.limits(), engine.checkpoint().schedules(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
        engine = FrontierEngines.createCanonicalStateAccess(arrivingConfiguration);
        var shipmentIds = mission.shipmentIds(); boolean unloaded = false;
        for (int turn = 0; turn < 2048; turn++) {
            var current = engine.canonicalState().state();
            if (shipmentIds.stream().allMatch(id -> current.shipments().shipments().get(id).terminal())) { unloaded = true; break; }
            advance(engine, 1);
        }
        assertTrue(unloaded); precondition = engine.canonicalState().state(); tick = engine.checkpoint().instant().ticks();
        var retainedMission = precondition.shipments().missions().get(mission.id());
        var transfer = io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.select(precondition, retainedMission, tick).orElseThrow();
        assertEquals(mission.receiver().containerId(), transfer.containerId());
        assertFalse(transfer.sourceEconomicOwnerId().equals(mission.sender().settlementId()));
        var held = io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.start(precondition, mission.id(), transfer, tick);
        FrontierReferenceClosure.validateTransition(precondition, held, List.of());
        var heldMission = held.shipments().missions().get(mission.id()); var purchase = heldMission.replenishmentPurchase().orElseThrow();
        assertEquals(held, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(held)));
        var schedules = new ArrayList<>(engine.checkpoint().schedules());
        schedules.removeIf(action -> group.members().stream().anyMatch(m -> m.actorId().equals(action.subject())) && action.kind().equals(ActorMovementProcess.PROGRESS));
        var review = TransportMissionProcess.progress(mission.id(), tick + 1);
        schedules.removeIf(action -> action.id().equals(review.id())); schedules.add(review);
        for (var resident : held.humanPopulation().residents().values()) {
            var need = ResidentNeedProcess.review(resident.id(), held.humanPopulation().nutrition(resident.id()).nextThresholdTick(held.bootstrap().ruleset().residentLife(),
                    resident.characteristics().effectiveMetabolismPermille(tick)));
            schedules.removeIf(action -> action.id().equals(need.id())); schedules.add(need);
        }
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), held, new SimInstant(tick), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                base.stateCodec(), base.projectionMapper(), base.limits(), schedules, base.transactionCommitter(), base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
        engine = FrontierEngines.createCanonicalStateAccess(configuration);
        var checkpoint = engine.checkpoint();
        engine = FrontierEngines.recoverCanonicalStateAccess(configuration,
                new RecoveryImage(configuration.worldId(), Optional.of(new SnapshotRecord(checkpoint, 0)), List.of()));
        boolean received = false, cleared = false, ate = false;
        for (int turn = 0; turn < 2048; turn++) {
            var current = engine.canonicalState().state();
            if (!current.inventory().economics().reservations().containsKey(purchase.payment().id())) {
                received = true;
                assertFalse(current.inventory().fungibleResources().claims().containsKey(transfer.claimId()));
            }
            var movement = current.actorMovements().get(recipient);
            cleared |= movement != null && movement.context() instanceof ActorMovementContext.ResourceAccessExit;
            if (current.humanPopulation().nutrition(recipient).satietyUnits() >= current.bootstrap().ruleset().residentLife().eatBelowUnits()) { ate = true; break; }
            advance(engine, 1);
        }
        assertTrue(received); assertTrue(cleared, "the paid pickup must free the supplier without replacing GROUP/COURIER responsibility");
        assertTrue(ate, "foreign food must become ordinary portable nutrition");
    }
    private static void advance(FrontierCanonicalStateAccess<FrontierWorldState, FrontierWorldProjection> engine, int budget) {
        var state = engine.canonicalState().state();
        var next = engine.checkpoint().schedules().stream().filter(action -> !FrontierWorldRuntimeDefinition.scheduledHeld(state, action)).sorted().findFirst().orElseThrow();
        var result = engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), next.dueAt().ticks())), new WorkBudget(budget, 1024));
        assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
    }
}
