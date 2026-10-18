package io.farfrontier.palemirror.frontier.v3.model;

/** Pure appetite forecast shared by reserves, procurement and travel provisioning.
 * Eating at the hunger threshold is the conservative cadence: later meals waste
 * no more nutrition. Food above stomach capacity is not stored endurance.
 */
public final class FoodConsumptionForecast {
    private FoodConsumptionForecast() { }
    public record MealYield(int items, int usefulNutrition) {
        public MealYield {
            if (items < 1 || usefulNutrition < 1) throw new IllegalArgumentException("empty meal yield");
        }
        public long itemsFor(long nutrition) {
            if (nutrition < 0) throw new IllegalArgumentException("negative nutrition demand");
            return Math.ceilDiv(Math.multiplyExact(nutrition, (long) items), usefulNutrition);
        }
    }
    public static MealYield atThreshold(FoodCatalog.Food food, FrontierRuleset.ResidentLife life) {
        int start = life.eatBelowUnits() - 1;
        int items = Math.ceilDiv(life.mealTargetUnits() - start, food.nutritionPerItem());
        int useful = Math.toIntExact(Math.min(life.satietyCapacityUnits() - (long) start,
                Math.multiplyExact((long) items, food.nutritionPerItem())));
        return new MealYield(items, useful);
    }
}
