package io.farfrontier.palemirror.frontier.v3.model.buildings;

import static org.junit.jupiter.api.Assertions.*;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import java.util.*;
import org.junit.jupiter.api.Test;

class FurnishedRestaurantTest {
    private static final String PREFIX="/io/farfrontier/palemirror/frontier/v3/buildings/medieval_spruce/kitchen_furnished_5";
    private static byte[] resource(String extension) throws Exception {
        try(var in=FurnishedRestaurantTest.class.getResourceAsStream(PREFIX+extension)) { return in.readAllBytes(); }
    }
    @Test void fullRestaurantRetainsRealStairsAndExplicitTablelessRestThroughRecovery() {
        var world=new WorldId("frontier:furnished-restaurant");
        var old=FrontierBootstrapper.create(world,139,FrontierRulesets.installed("frontier-v3-leisure-seats-r37"));
        var current=FrontierBootstrapper.create(world,139,FrontierRulesets.installed("frontier-v3-furnished-restaurant-r38"));
        assertEquals(old.settlements().subList(1,old.settlements().size()),current.settlements().subList(1,current.settlements().size()));
        var kitchen=current.settlements().getFirst().structures().stream().filter(b->b.kind()==StructureKind.KITCHEN).findFirst().orElseThrow();
        var definition=kitchen.definition();
        BuildingGeometryValidation.validate(definition,
                block->BuildingCollisionCatalog.standard(block).flatMap(io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObservation::cell),
                work->io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCell.SOLID);
        assertEquals(28,definition.seats().size());
        assertEquals(16,definition.seats().values().stream().filter(s->definition.zones().get(s.zoneId()).use()==BuildingDefinition.ZoneUse.DINING).count());
        assertEquals(10,definition.seats().values().stream().filter(s->s.table().isEmpty()).count());
        for(int x=12;x<=15;x++) for(int z=4;z<=5;z++)
            assertEquals("minecraft:spruce_stairs",definition.blocks().get(new BlockPosition(x,17-x,z)).state().kind());
        var state=FrontierWorldState.initial(current);var codec=new FrontierWorldStateCodec();
        assertEquals(state,codec.decode(codec.encode(state)));
    }
    @Test void tableOmissionIsExplicitAndDiningCannotBorrowRestSemantics() throws Exception {
        var props=new TreeMap<>(BuildingDefinitionFormat.properties(resource(".properties")));
        var blocks=BuildingDefinitionFormat.blocks(resource(".blocks"));
        assertDoesNotThrow(()->BuildingDefinitionFormat.decode(props,blocks,"a".repeat(64)));
        String key="seat.furnished_9.tableBlock";
        assertEquals("none",props.get(key));
        props.remove(key);
        assertThrows(IllegalArgumentException.class,()->BuildingDefinitionFormat.decode(props,blocks,"a".repeat(64)));
        props.put(key,"none");props.put("zone.lounge.use","DINING");
        assertThrows(IllegalArgumentException.class,()->BuildingDefinitionFormat.decode(props,blocks,"a".repeat(64)));
    }
}
