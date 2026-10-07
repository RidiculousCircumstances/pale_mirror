package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FoodCatalog;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRuleset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure provisioning forecast. Reservations, transfers, spending and movement are separate owners. */
public final class ExpeditionProvisioning {
    private ExpeditionProvisioning() { }
    public record Member(SubjectId actorId, int satiety, int metabolismPermille, int occupiedStackSlots, int cargoStackSlots,
                         int existingFoodItems, int existingFoodStackRoom) {
        public Member(SubjectId actorId, int satiety, int metabolismPermille, int occupiedStackSlots, int cargoStackSlots) {
            this(actorId, satiety, metabolismPermille, occupiedStackSlots, cargoStackSlots, 0, 0);
        }
        public Member {
            Objects.requireNonNull(actorId);
            if (satiety < 0 || metabolismPermille < 1 || occupiedStackSlots < 0 || cargoStackSlots < 0
                    || existingFoodItems < 0 || existingFoodStackRoom < 0
                    || existingFoodItems + (long) existingFoodStackRoom > occupiedStackSlots * 64L
                    || existingFoodItems == 0 && existingFoodStackRoom != 0)
                throw new IllegalArgumentException("invalid provisioning member inputs");
        }
    }
    /** Requested allocation, not a receipt that food has been loaded. */
    public record PersonalSupply(SubjectId actorId, int requiredFoodItems, int carriedFoodItems, int existingFoodItems) {
        public PersonalSupply(SubjectId actorId, int requiredFoodItems, int carriedFoodItems) {
            this(actorId, requiredFoodItems, carriedFoodItems, 0);
        }
        public PersonalSupply {
            Objects.requireNonNull(actorId);
            if (requiredFoodItems < 0 || carriedFoodItems < 0 || carriedFoodItems > requiredFoodItems
                    || existingFoodItems < 0 || existingFoodItems > carriedFoodItems)
                throw new IllegalArgumentException("invalid personal supply allocation");
        }
        public int loadFoodItems() { return carriedFoodItems - existingFoodItems; }
    }
    public enum Transport { WALKING, PACK_ANIMAL }
    public enum Refusal { NONE, FOOD_STOCK, NO_TRANSPORT, CAPACITY }
    public record Plan(String foodKind, long durationTicks, Map<SubjectId, PersonalSupply> members,
                       int sharedFoodItems, int cargoStackSlots, Transport transport, Refusal refusal) {
        public Plan {
            Objects.requireNonNull(foodKind); Objects.requireNonNull(transport); Objects.requireNonNull(refusal);
            members = Map.copyOf(members);
            if (members.isEmpty() && transport != Transport.PACK_ANIMAL || durationTicks < 0 || sharedFoodItems < 0 || cargoStackSlots < 0
                    || members.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().actorId())))
                throw new IllegalArgumentException("invalid expedition provisioning plan");
        }
        public int foodItems() { return members.values().stream().mapToInt(PersonalSupply::requiredFoodItems)
                .reduce(0, Math::addExact); }
        public boolean feasible() { return refusal == Refusal.NONE; }
        public int foodToLoad() { return Math.addExact(sharedFoodItems, members.values().stream()
                .mapToInt(PersonalSupply::loadFoodItems).reduce(0, Math::addExact)); }
    }

    public static Plan plan(List<Member> roster, long outboundEdges, long returnEdges,
                            int cargoStackSlots, int availableFood, boolean animalAvailable,
                            FoodCatalog.Food food, FrontierRuleset.ResidentLife life, ExpeditionRules policy) {
        return plan(roster, outboundEdges, returnEdges, cargoStackSlots, availableFood, animalAvailable,
                food, life, policy, false, policy.packAnimalStackSlots());
    }
    /** A chosen real transport asset owns cargo capacity, never a fictitious resident's pockets. */
    public static Plan planWithAsset(List<Member> roster, long outboundEdges, long returnEdges,
            int cargoStackSlots, int availableFood, int actualAssetSlots,
            FoodCatalog.Food food, FrontierRuleset.ResidentLife life, ExpeditionRules policy) {
        if (actualAssetSlots < 1) throw new IllegalArgumentException("provisioning lacks an actual transport capacity");
        return plan(roster, outboundEdges, returnEdges, cargoStackSlots, availableFood, true,
                food, life, policy, true, actualAssetSlots);
    }
    private static Plan plan(List<Member> roster, long outboundEdges, long returnEdges,
            int cargoStackSlots, int availableFood, boolean animalAvailable,
            FoodCatalog.Food food, FrontierRuleset.ResidentLife life, ExpeditionRules policy,
            boolean carriedByAnimal, int actualAssetSlots) {
        if (roster.isEmpty() && !carriedByAnimal || roster.size() > 32 || cargoStackSlots < 0 || availableFood < 0
                || roster.stream().map(Member::actorId).distinct().count() != roster.size())
            throw new IllegalArgumentException("invalid expedition load inputs");
        if (!carriedByAnimal && roster.stream().mapToInt(Member::cargoStackSlots).reduce(0, Math::addExact) != cargoStackSlots
                || carriedByAnimal && roster.stream().anyMatch(member -> member.cargoStackSlots() != 0))
            throw new IllegalArgumentException("cargo capacity must belong to the declared actual carriers");
        long duration = policy.plannedDuration(outboundEdges, returnEdges);
        var supplies = new LinkedHashMap<SubjectId, PersonalSupply>();
        boolean walkingFoodFits = true;
        for (var member : roster) {
            if (member.satiety() > life.satietyCapacityUnits() || member.metabolismPermille() > life.metabolismMaxPermille()
                    || member.metabolismPermille() < life.metabolismMinPermille())
                throw new IllegalArgumentException("provisioning member differs from life policy");
            long loss = Math.ceilDiv(Math.multiplyExact(duration, (long) member.metabolismPermille()),
                    Math.multiplyExact(life.satietyUnitTicks(), 1_000L));
            // Bound per-item usefulness at a normal eating threshold. Surplus nutrition that
            // clips at satiety capacity must not create fictitious additional travel endurance.
            int usefulNutrition = Math.min(food.nutritionPerItem(), life.mealTargetUnits() - life.eatBelowUnits() + 1);
            int required = Math.toIntExact(Math.ceilDiv(Math.max(0L,
                    Math.addExact(loss, (long) life.mealTargetUnits() - member.satiety())), usefulNutrition));
            int freeSlots = Math.max(0, policy.personalStackSlots() - member.occupiedStackSlots());
            int existing = Math.min(required, member.existingFoodItems());
            int foodSlots = Math.ceilDiv(Math.max(0, required - existing - member.existingFoodStackRoom()), 64);
            walkingFoodFits &= Math.addExact(foodSlots, member.cargoStackSlots()) <= freeSlots;
            supplies.put(member.actorId(), new PersonalSupply(member.actorId(), required, required, existing));
        }
        Transport transport = walkingFoodFits && !carriedByAnimal ? Transport.WALKING : Transport.PACK_ANIMAL;
        int shared = 0;
        if (transport == Transport.PACK_ANIMAL) {
            for (var member : roster) {
                var supply = supplies.get(member.actorId());
                int personal = Math.max(supply.existingFoodItems(), Math.min(supply.requiredFoodItems(),
                        Math.min(policy.personalFoodItems(), Math.addExact(member.existingFoodItems(),
                                Math.addExact(member.existingFoodStackRoom(),
                                        Math.max(0, policy.personalStackSlots() - member.occupiedStackSlots()) * 64)))));
                supplies.put(member.actorId(), new PersonalSupply(member.actorId(), supply.requiredFoodItems(), personal, supply.existingFoodItems()));
                shared = Math.addExact(shared, supply.requiredFoodItems() - personal);
            }
        }
        int animalSlots = Math.addExact(cargoStackSlots, Math.ceilDiv(shared, 64));
        Refusal refusal = Math.addExact(shared, supplies.values().stream().mapToInt(PersonalSupply::loadFoodItems)
                .reduce(0, Math::addExact)) > availableFood
                ? Refusal.FOOD_STOCK : transport == Transport.PACK_ANIMAL && !animalAvailable
                ? Refusal.NO_TRANSPORT : transport == Transport.PACK_ANIMAL && animalSlots > Math.min(policy.packAnimalStackSlots(), actualAssetSlots)
                ? Refusal.CAPACITY : Refusal.NONE;
        return new Plan(food.itemKind(), duration, supplies, shared, cargoStackSlots, transport, refusal);
    }
}
