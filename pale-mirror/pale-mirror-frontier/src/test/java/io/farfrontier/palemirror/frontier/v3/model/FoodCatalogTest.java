package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FoodCatalogTest {
    @Test void portionDependsOnNutritionTargetAndAvailableStockNotItemIdentity() {
        var food = new FoodCatalog.Food("minecraft:carrot", 100, 64);
        assertEquals(9, food.portionFor(900, 64));
        assertEquals(5, food.portionFor(900, 5));
        assertEquals(0, food.portionFor(900, 0));
        assertEquals(0, food.portionFor(0, 64));
        assertEquals(10, food.portionFor(901, 64));
        assertEquals(64, food.portionFor(1_000_000, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> food.portionFor(-1, 64));
        assertThrows(IllegalArgumentException.class, () -> new FoodCatalog.Food("minecraft:carrot", 0, 64));
    }

    @Test void catalogIsClosedAndItsStableContentIncludesNutritionalPolicy() {
        var a = new FoodCatalog.Food(FoodCatalog.BREAD, 1000, 64);
        var b = new FoodCatalog.Food("minecraft:carrot", 100, 16);
        var first = new FoodCatalog(Map.of(a.itemKind(), a, b.itemKind(), b));
        var second = new FoodCatalog(Map.of(b.itemKind(), b, a.itemKind(), a));
        assertEquals(first.canonicalText(), second.canonicalText());
        assertNotEquals(first.canonicalText(), FoodCatalog.bread(999).canonicalText());
        assertThrows(IllegalArgumentException.class, () -> first.require("minecraft:stone"));
        assertThrows(IllegalArgumentException.class, () -> new FoodCatalog(Map.of("minecraft:stone", a)));
        assertEquals(Map.of(FoodCatalog.BREAD, a), FrontierRulesets.production().residentLife().foods().foods());
    }
}
