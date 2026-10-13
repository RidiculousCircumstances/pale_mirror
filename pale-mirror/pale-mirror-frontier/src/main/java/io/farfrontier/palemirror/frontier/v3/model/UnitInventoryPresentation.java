package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Derives bounded slot presentation; no physical slot becomes resource identity or stock authority. */
public final class UnitInventoryPresentation {
    private static final List<ActorItemSlot> SLOTS = declaredSlots();
    private UnitInventoryPresentation() { }
    public static List<ActorItemSlot> slots() {
        return SLOTS;
    }
    private static List<ActorItemSlot> declaredSlots() {
        var slots = new ArrayList<ActorItemSlot>();
        for (int i = 0; i < 8; i++) slots.add(new ActorItemSlot.Pocket(i));
        slots.add(new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.OFF));
        slots.add(new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.MAIN));
        return List.copyOf(slots);
    }

    public static Map<SubjectId, ActorCarriedResources.Presentation> inventory(FrontierWorldState state, SubjectId actor) {
        return view(state, actor).inventory();
    }

    /** One current query supplies both the presentation and its capacity assessment. */
    public record View(Map<SubjectId, ActorCarriedResources.Presentation> inventory,
                       java.util.Set<ActorItemSlot> occupied) {
        public View { inventory = Map.copyOf(inventory); occupied = java.util.Set.copyOf(occupied); }
        public Optional<ActorItemSlot> freeSlot() {
            return slots().stream().filter(slot -> !occupied.contains(slot)).findFirst();
        }
    }

    public static View view(FrontierWorldState state, SubjectId actor) {
        var result = new LinkedHashMap<SubjectId, ActorCarriedResources.Presentation>();
        var used = new HashSet<>(UnitInventorySlotReservations.reserved(state, actor));
        if (!state.inventory().actorItems(actor).isEmpty())
            used.add(new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.MAIN));
        ActorCarryCapabilities.workCargo(state, actor).ifPresent(carry -> add(result, used, carry));
        var meal = state.humanPopulation().meals().get(actor);
        if (meal != null && !meal.portable()) {
            if (!used.add(meal.inventorySlot())) throw new IllegalArgumentException("meal conflicts with work presentation");
            if (meal.carriesFood()) result.put(meal.actorAccountId(),
                    new ActorCarriedResources.Presentation(actor, meal.actorAccountId(), meal.inventorySlot()));
        }
        var accounts = UnitInventory.accounts(state.inventory().fungibleResources(), actor);
        for (var account : accounts) if (account.actorPresentation().isPresent()) {
            var retained = account.actorPresentation().orElseThrow();
            var assigned = result.get(account.id());
            if (assigned == null) add(result, used, new ActorCarriedResources.Presentation(actor, account.id(), retained));
            else if (!assigned.slot().equals(retained)) throw new IllegalArgumentException("work presentation differs from retained inventory allocation");
        }
        // Retain already observed addresses before allocating any unbound account.
        for (var account : accounts) {
            var bindings = state.inventory().fungibleResources().bindings().values().stream()
                    .filter(binding -> binding.accountId().equals(account.id())).toList();
            if (bindings.isEmpty()) continue;
            if (bindings.size() != 1) throw new IllegalArgumentException("personal stack has ambiguous physical presentation");
            ActorItemSlot slot = switch (bindings.getFirst().address()) {
                case PhysicalStackAddress.ActorPocket pocket -> {
                    if (!pocket.actorId().equals(actor)) throw new IllegalArgumentException("foreign personal pocket");
                    yield new ActorItemSlot.Pocket(pocket.slot());
                }
                case PhysicalStackAddress.ActorHand hand -> {
                    if (!hand.actorId().equals(actor)) throw new IllegalArgumentException("foreign personal hand");
                    yield new ActorItemSlot.Hand(hand.hand());
                }
                default -> throw new IllegalArgumentException("personal account has a non-personal physical binding");
            };
            var assigned = result.get(account.id());
            if (assigned == null) add(result, used, new ActorCarriedResources.Presentation(actor, account.id(), slot));
            else if (!assigned.slot().equals(slot)) throw new IllegalArgumentException("physical inventory differs from declared presentation");
        }
        for (var account : accounts) {
            if (result.containsKey(account.id())) continue;
            var slot = slots().stream().filter(value -> !used.contains(value)).findFirst().orElseThrow(
                    () -> new IllegalArgumentException("personal inventory has no physical presentation capacity"));
            add(result, used, new ActorCarriedResources.Presentation(actor, account.id(), slot));
        }
        if (meal != null && meal.portable() && !result.get(meal.actorAccountId()).slot().equals(meal.inventorySlot()))
            throw new IllegalArgumentException("portable meal differs from its retained inventory presentation");
        if (meal != null) used.add(meal.inventorySlot());
        return new View(result, used);
    }

    public static Optional<ActorItemSlot> freeSlot(FrontierWorldState state, SubjectId actor) {
        var used = occupiedSlots(state, actor);
        return slots().stream().filter(slot -> !used.contains(slot)).findFirst();
    }

    public static boolean availableSlot(FrontierWorldState state, SubjectId actor, ActorItemSlot slot) {
        return slots().contains(slot) && !occupiedSlots(state, actor).contains(slot);
    }

    private static java.util.Set<ActorItemSlot> occupiedSlots(FrontierWorldState state, SubjectId actor) {
        return view(state, actor).occupied();
    }

    private static void add(Map<SubjectId, ActorCarriedResources.Presentation> result,
                            java.util.Set<ActorItemSlot> used, ActorCarriedResources.Presentation carry) {
        if (result.putIfAbsent(carry.accountId(), carry) != null || !used.add(carry.slot()))
            throw new IllegalArgumentException("competing personal account presentation");
    }
}
