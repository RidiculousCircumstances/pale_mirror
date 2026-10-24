package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpeditionCrewAdmissionTest {
    @Test void personalFoodCannotFeedAnotherMemberAndRealAnimalStockCanCoverTheDeficit() throws Exception {
        var base = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:crew-food"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1")).initialState();
        var home = new SubjectId("settlement:1");
        var crew = base.humanPopulation().residents().values().stream().filter(r -> r.settlementId().equals(home))
                .map(ResidentProfile::id).sorted().limit(2).toList();
        var asset = base.transportFleet().assets().values().stream().filter(a -> a.homeSettlementId().equals(home)).findFirst().orElseThrow();
        var state = base.withInventory(base.inventory().withFungibleResources(FungibleResourceLedger.empty()));
        long tick = 1_000_000;
        var life = base.bootstrap().ruleset().residentLife(); var food = life.foods().require("minecraft:bread");
        int required = ExpeditionProvisioning.requiredFoodItems(0, 0,
                base.humanPopulation().resident(crew.getFirst()).characteristics().effectiveMetabolismPermille(tick), food, life);
        assertTrue(required > 0);
        try (var ignored = PedestrianRoutePlanning.bind((geometry, from, to) -> {
            throw new AssertionError("material prerequisite must not request a route");
        })) {
            assertFalse(ExpeditionCrewAdmission.canFeedBeforeTravel(state, home, crew, Optional.empty(), tick));
            var lot = new SubjectId("lot:personal-food");
            var resources = state.inventory().fungibleResources().issue(
                    new ResourceLot(lot, home, food.itemKind(), 64, "fixture:provisions", List.of()),
                    new CustodyAccount(new SubjectId("custody:personal-food"), new ResourceCustody.Actor(crew.getFirst()),
                            Map.of(lot, 64), Map.of(), Optional.of(UnitInventoryPresentation.slots().get(1))));
            state = state.withInventory(state.inventory().withFungibleResources(resources));
            assertTrue(ExpeditionCrewAdmission.canFeedBeforeTravel(state, home, List.of(crew.getFirst()), Optional.empty(), tick));
            assertFalse(ExpeditionCrewAdmission.canFeedBeforeTravel(state, home, crew, Optional.empty(), tick));
            assertEquals(List.of(crew.getFirst()), ExpeditionCrewAdmission.select(state, home,
                    List.of(crew.getLast(), crew.getFirst()), 1, Optional.empty(), 12000, tick, ids -> true),
                    "an unfed first candidate must not hide a provisioned alternative");
            assertTrue(ExpeditionCrewAdmission.select(state, home, crew, 2, Optional.empty(), 12000, tick, ids -> true).isEmpty(),
                    "one resident's personal stock cannot silently supply both crew members");
            assertTrue(ExpeditionCrewAdmission.select(state, home, crew, 1, Optional.empty(), 12000, tick,
                    ids -> !ids.contains(crew.getFirst())).isEmpty(), "provisions do not override the caller's work permission");
            var shared = new SubjectId("lot:shared-food");
            resources = resources.issue(new ResourceLot(shared, home, food.itemKind(), required, "fixture:provisions", List.of()),
                    new CustodyAccount(ReferenceContainerCustody.scopeId(asset.containerId()), new ResourceCustody.Container(asset.containerId()),
                            Map.of(shared, required), Map.of()));
            state = state.withInventory(state.inventory().withFungibleResources(resources));
            assertTrue(ExpeditionCrewAdmission.canFeedBeforeTravel(state, home, crew, Optional.of(asset), tick));
            assertFalse(ExpeditionCrewAdmission.canFeedBeforeTravel(state, home, crew, Optional.empty(), tick));
        }
    }
}
