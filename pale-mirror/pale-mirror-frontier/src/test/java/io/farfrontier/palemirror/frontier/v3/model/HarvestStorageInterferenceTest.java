package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HarvestStorageInterferenceTest {
    @Test void playerCanOccupyPlannedHarvestSlotAndFarmerReplansWithinTheSameChest() throws Exception {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0); var state = fixture.state(); var job = fixture.job();
        var depot = job.outputSlot().containerId(); var account = job.depotAccountId();
        var owner = state.resourceSite(job.siteId()).settlementId();
        var stacks = new java.util.ArrayList<FungiblePhysicalObservation.Stack>();
        ReferenceContainerCustody.expectedFungibleSlots(state, depot).forEach((slot, item) -> stacks.add(
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(depot, slot)), item.itemKind(), item.quantity())));
        var ledger = state.inventory().fungibleResources();
        ledger = ledger.rebind(account, 1L, FungiblePhysicalObservation.bind(ledger, account, 1L, stacks));
        state = ReferenceContainerCustodyFixtures.observedAndHeld(state.withInventory(
                state.inventory().withFungibleResources(ledger).withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED)
                        .withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)), depot);
        stacks.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(job.outputSlot()), "minecraft:bread", 1));
        var gift = new ResourceLot(new SubjectId("lot:player-filled-harvest-slot"), owner, "minecraft:bread", 1, "player", List.of());
        var observed = new FungibleStockContributionObserved(account, depot, 1L, new java.util.UUID(0, 1),
                new java.util.UUID(0, 2), gift, stacks);
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap());
        var config = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(base.worldId(), state,
                new SimInstant(23_000), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(),
                base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter(), base.stateValidator());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.createCanonicalStateAccess(config);
        var cp = engine.checkpoint(); var id = new CommandId("test:occupy-harvest-destination");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(
                new FrontierCommand(FrontierCommand.SCHEMA_VERSION, id, base.worldId(), cp.revision(), cp.instant(),
                        FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), observed, Optional.empty())));
        state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(gift, state.inventory().fungibleResources().lots().get(gift.id()));
        assertFalse(ResourceSiteHarvestCargo.deliveryCapacityAvailable(state, job));
        assertFalse(state.reservedContainerSlots(depot).contains(job.outputSlot().slot()));
        assertTrue(FrontierResourceSiteHarvestSceneSupport.candidate(state, job).isEmpty(),
                "HOT admission must not reclaim the worker before storage replanning");
        // Selection is advice, not a physical write or custody release.
        var beforeResources = state.inventory().fungibleResources();
        var replacement = HarvestStorageReplacement.select(state, job).orElseThrow();
        assertEquals(depot, replacement.destination().containerId());
        assertNotEquals(job.outputSlot(), replacement.destination());
        state = HarvestStorageReplacement.apply(state, job.siteId(), replacement);
        assertEquals(beforeResources, state.inventory().fungibleResources());
        var next = state.resourceSites().site(job.siteId()).harvestJob(job.id()).orElseThrow();
        assertTrue(ResourceSiteHarvestCargo.deliveryCapacityAvailable(state, next));
        assertTrue(FrontierResourceSiteHarvestSceneSupport.candidate(state, next).isPresent());
        assertEquals(job.undeliveredYieldQuantity(), next.undeliveredYieldQuantity());
    }

}
