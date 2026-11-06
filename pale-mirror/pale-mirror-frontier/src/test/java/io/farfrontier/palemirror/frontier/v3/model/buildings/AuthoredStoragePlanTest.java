package io.farfrontier.palemirror.frontier.v3.model.buildings;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthoredStoragePlanTest {
    @Test void inventoryFillChangesPresentationButNotStructureOrComposition() {
        var original = new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState("minecolonies:blockminecoloniesrack",java.util.Map.of("variant","blockrackemptysingle","facing","west"));
        assertTrue(StructuralMaterialIdentity.matches(original,new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState(original.kind(),java.util.Map.of("variant","blockrackfullsingle","facing","west"))));
        assertFalse(StructuralMaterialIdentity.matches(original,new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState(original.kind(),java.util.Map.of("variant","blockrackfull","facing","west"))));
    }
    @Test void coldGenesisUsesActualInventoryBlocksAndKeepsFoodInKitchens() {
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:authored-storage"),139, FrontierRulesets.installed("frontier-v3-textiles-terrain-r32"));
        var state = FrontierWorldState.initial(bootstrap);
        var entries = AuthoredStoragePlan.compile(bootstrap);
        assertFalse(entries.isEmpty());
        assertFalse(state.inventory().containers().containsKey(FrontierRouteNetwork.MAINTENANCE_CONTAINER));
        assertTrue(entries.stream().anyMatch(e -> e.container().storageBlock().orElseThrow() == StorageBlockKind.RACK));
        for(var entry:entries) {
            var endpoint = (ShipmentEndpoint.Storage)entry.container().storage().orElseThrow().endpoint();
            var building = bootstrap.settlements().stream().flatMap(s->s.structures().stream())
                    .filter(b->b.id().equals(endpoint.facilityId().orElseThrow())).findFirst().orElseThrow();
            var block = building.definition().blocks().get(building.placement().local(entry.surface().position()));
            assertNotNull(block);
            assertTrue(java.util.Set.of("minecraft:chest","minecraft:barrel","minecolonies:blockminecoloniesrack").contains(block.state().kind()));
            assertEquals(building.kind()==StructureKind.KITCHEN,entry.container().storage().orElseThrow().filter().accepts("minecraft:bread"));
            assertEquals(entry.container(),state.inventory().containers().get(entry.container().id()));
            assertEquals(ServicePointId.Kind.RESOURCE_STORAGE,ServiceBoundaryComposition.declaration(state,entry.container().id()).identity().kind());
        }
        for(var home:bootstrap.settlements()) {
            var depot = state.inventory().containers().get(FrontierWorldState.depotId(home.id()));
            assertEquals(ContainerPurpose.RESOURCE_STORAGE,depot.purpose());
            assertTrue(entries.stream().anyMatch(e->e.container().id().equals(CommunalDiningPlan.initialFoodStorage(bootstrap,home.id()))));
        }
        var codec=new FrontierWorldStateCodec();
        assertArrayEquals(codec.encode(state),codec.encode(codec.decode(codec.encode(state))));
        var obsolete=codec.encode(state);java.nio.ByteBuffer.wrap(obsolete).putInt(4,342);
        assertThrows(IllegalArgumentException.class,()->codec.decode(obsolete));
    }
}
