package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Adapts actual roster, routes and stock to the pure policy; never creates food or a body. */
public final class ExpeditionSupplyPlanning {
    private ExpeditionSupplyPlanning() { }
    public static Optional<ExpeditionSupplyLoad> walkingLoad(FrontierWorldState state, TransportMission mission,
                                                            UnitGroup group, long now) {
        return walkingLoad(state, mission, group, now, 1, state.inventory().fungibleResources());
    }
    /** Generic actual transport declaration; walkingLoad remains the existing source-level entry point. */
    public static Optional<ExpeditionSupplyLoad> load(FrontierWorldState state, TransportMission mission, UnitGroup group, long now) {
        return walkingLoad(state, mission, group, now);
    }
    public static Optional<ExpeditionSupplyLoad> walkingLoad(FrontierWorldState state, TransportMission mission,
            UnitGroup group, long now, long revision, FungibleResourceLedger availableResources) {
        var rules = state.bootstrap().ruleset(); var life = rules.residentLife();
        var source = FungibleResourceCustodySupport.accountAtContainer(state, mission.organization().home().containerId()).orElse(null);
        if (source == null || ReferenceContainerCustody.blocksCanonicalUse(state, mission.organization().home().containerId())) return Optional.empty();
        var knowledge = TransportGroupMissionPort.knowledgeForEndpoints(state, mission.sender(), mission.receiver()).forActors(state, group.members().stream().map(UnitGroup.Member::actorId).toList());
        ExpeditionJourneyProof journey;
        try { journey = ExpeditionJourneyProof.select(state, mission, group); }
        catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) throw unavailable;
            return Optional.empty();
        }
        for (var food : life.foods().foods().values().stream().sorted(Comparator.comparing(FoodCatalog.Food::itemKind)).toList()) {
            var resources = availableResources; var initialResources = resources;
            var inputs = walkingInputs(state, mission, group, now, food, initialResources,
                    journey.outward().edges(), journey.returning().edges());
            if (inputs.isEmpty()) return Optional.empty();
            var input = inputs.orElseThrow(); var free = input.freeSlots();
            int cargoSlots = input.roster().stream().mapToInt(ExpeditionProvisioning.Member::cargoStackSlots).sum();
            int foodAvailable = resources.unclaimedQuantity(source.id(), mission.organization().sponsor().id(), food.itemKind());
            var plan = forecast(state, mission, group, input, food, foodAvailable);
            if (!plan.feasible()) continue;
            var allocations = new ArrayList<ExpeditionSupplyLoad.Allocation>(); var targets = new LinkedHashMap<SubjectId, Integer>();
            boolean fits = true;
            for (var member : group.members()) {
                var supply = plan.members().get(member.actorId()); if (supply == null) continue;
                targets.put(member.actorId(), supply.requiredFoodItems());
                int remaining = supply.loadFoodItems(), index = 0;
                while (remaining > 0) {
                    if (index >= free.get(member.actorId()).size()) { fits = false; break; }
                    int quantity = Math.min(64, remaining);
                    var selected = FungibleResourceCustodySupport.selectAtAccount(resources, source.id(),
                            mission.organization().sponsor().id(), food.itemKind(), quantity).orElseThrow();
                    var claim = supplyClaim(mission.id(), revision, member.actorId().value(), index);
                    var account = supplyAccount(claim);
                    var allocation = new ExpeditionSupplyLoad.Allocation(claim, member.actorId(), source.id(), account,
                            free.get(member.actorId()).get(index++), selected.lotQuantities(), ExpeditionSupplyLoad.Outcome.RESERVED, Optional.empty());
                    resources = reserve(resources, allocation, mission.id(), mission.organization().sponsor().id(), food.itemKind());
                    allocations.add(allocation); remaining -= quantity;
                }
                if (!fits) break;
            }
            if (fits && plan.sharedFoodItems() > 0) {
                var asset = state.transportFleet().require(mission.transportAssetId().orElseThrow());
                var destination = FungibleResourceCustodySupport.accountAtContainer(state, asset.containerId()).map(CustodyAccount::id)
                        .orElseGet(() -> ReferenceContainerCustody.scopeId(asset.containerId()));
                int stocked = resources.accounts().containsKey(destination) ? resources.unclaimedQuantity(destination, mission.organization().sponsor().id(), food.itemKind()) : 0;
                int remaining = Math.max(0, plan.sharedFoodItems() - stocked);
                int index = 0;
                while (remaining > 0) {
                    int quantity = Math.min(64, remaining);
                    var selected = FungibleResourceCustodySupport.selectAtAccount(resources, source.id(), mission.organization().sponsor().id(), food.itemKind(), quantity).orElseThrow();
                    var claim = supplyClaim(mission.id(), revision, "shared", index++);
                    var allocation = new ExpeditionSupplyLoad.Allocation(claim, asset.actorId(), source.id(), destination,
                            new ActorItemSlot.AttachedStorage(asset.containerId()), selected.lotQuantities(), ExpeditionSupplyLoad.Outcome.RESERVED, Optional.empty());
                    resources = reserve(resources, allocation, mission.id(), mission.organization().sponsor().id(), food.itemKind());
                    allocations.add(allocation); remaining -= quantity;
                }
            }
            if (fits) {
                try {
                    var living = group.members().stream().filter(member -> state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.ALIVE).toList();
                    var assembly = GroupFormation.stations(living, List.of(mission.homeRendezvous()), 0, knowledge, state.bootstrap().ruleset().expedition().formationSpacing());
                    return Optional.of(new ExpeditionSupplyLoad(food.itemKind(), plan.durationTicks(), now, targets, assembly, allocations, revision, journey));
                } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                    if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) throw unavailable;
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }
    /** Departure rechecks current need/capacity/route independently of depot stock availability. */
    public static Optional<ExpeditionProvisioning.Plan> walkingForecast(FrontierWorldState state, TransportMission mission,
                                                                       UnitGroup group, long now, String foodKind) {
        var life = state.bootstrap().ruleset().residentLife(); var food = life.foods().foods().get(foodKind);
        if (food == null) throw new IllegalArgumentException("forecast has no declared nutrition definition");
        return walkingInputs(state, mission, group, now, food, state.inventory().fungibleResources()).map(input ->
                forecast(state, mission, group, input, food, Integer.MAX_VALUE));
    }
    /** Pure departure validation: recompute needs from actual stock/time, never rediscover route geometry. */
    public static Optional<ExpeditionProvisioning.Plan> retainedForecast(FrontierWorldState state, TransportMission mission,
            UnitGroup group, long now, String foodKind, ExpeditionJourneyProof proof) {
        proof.require(state, mission, group);
        var food = state.bootstrap().ruleset().residentLife().foods().require(foodKind);
        return walkingInputs(state, mission, group, now, food, state.inventory().fungibleResources(),
                proof.outward().edges(), proof.returning().edges()).map(input ->
                forecast(state, mission, group, input, food, Integer.MAX_VALUE));
    }
    private static ExpeditionProvisioning.Plan forecast(FrontierWorldState state, TransportMission mission, UnitGroup group,
            WalkingInputs input, FoodCatalog.Food food, int foodAvailable) {
        var rules = state.bootstrap().ruleset();
        if (mission.transportAssetId().isPresent()) {
            var asset = state.transportFleet().require(mission.transportAssetId().orElseThrow());
            int cargoSlots = mission.shipmentIds().size();
            return ExpeditionProvisioning.planWithAsset(input.roster(), input.outward(), input.returning(), cargoSlots,
                    foodAvailable == Integer.MAX_VALUE ? foodAvailable : Math.addExact(foodAvailable, ExpeditionSupplyAuthority.sharedFood(state, mission, food.itemKind())),
                    asset.stackSlots(), food, rules.residentLife(), rules.expedition());
        }
        return ExpeditionProvisioning.plan(input.roster(), input.outward(), input.returning(), input.roster().stream()
                .mapToInt(ExpeditionProvisioning.Member::cargoStackSlots).sum(), foodAvailable, false, food, rules.residentLife(), rules.expedition());
    }
    /** Check one selected loading declaration without ranking food, lots or routes again. */
    public static void requireSelectedLoad(FrontierWorldState state, TransportMission mission, UnitGroup group,
            ExpeditionSupplyLoad load, FungibleResourceLedger resources) {
        load.journey().require(state, mission, group);
        var source = FungibleResourceCustodySupport.accountAtContainer(state, mission.organization().home().containerId()).orElseThrow();
        if (ReferenceContainerCustody.blocksCanonicalUse(state, mission.organization().home().containerId()))
            throw new IllegalArgumentException("selected supply source is physically unavailable");
        var food = state.bootstrap().ruleset().residentLife().foods().require(load.foodKind());
        var input = walkingInputs(state, mission, group, load.calculatedAtTick(), food, resources,
                load.journey().outward().edges(), load.journey().returning().edges()).orElseThrow();
        var plan = forecast(state, mission, group, input, food,
                resources.unclaimedQuantity(source.id(), mission.organization().sponsor().id(), food.itemKind()));
        var targets = new LinkedHashMap<SubjectId, Integer>();
        plan.members().forEach((id, member) -> targets.put(id, member.requiredFoodItems()));
        if (!plan.feasible() || plan.durationTicks() != load.forecastDurationTicks() || !targets.equals(load.foodTargets())
                || load.allocations().stream().anyMatch(a -> a.outcome() != ExpeditionSupplyLoad.Outcome.RESERVED || a.pending().isPresent()
                    || !a.sourceAccountId().equals(source.id()) || !(a.slot() instanceof ActorItemSlot.AttachedStorage)
                        && (!input.freeSlots().getOrDefault(a.actorId(), List.of()).contains(a.slot())
                            || resources.accounts().containsKey(a.destinationAccountId()))))
            throw new IllegalArgumentException("selected supplies have stale needs, slots, source or estimate");
        for (var member : plan.members().values()) {
            int actual = load.allocations().stream().filter(a -> a.actorId().equals(member.actorId())
                    && !(a.slot() instanceof ActorItemSlot.AttachedStorage)).mapToInt(ExpeditionSupplyLoad.Allocation::quantity).sum();
            if (actual != member.loadFoodItems()) throw new IllegalArgumentException("selected personal supply differs from actual need");
        }
        int shared = load.allocations().stream().filter(a -> a.slot() instanceof ActorItemSlot.AttachedStorage)
                .mapToInt(ExpeditionSupplyLoad.Allocation::quantity).sum();
        if (shared != Math.max(0, plan.sharedFoodItems() - ExpeditionSupplyAuthority.sharedFood(state, mission, food.itemKind())))
            throw new IllegalArgumentException("selected shared supply differs from actual transport stock");
        for (var a : load.allocations()) if (a.slot() instanceof ActorItemSlot.AttachedStorage storage) {
            var asset = state.transportFleet().require(mission.transportAssetId().orElseThrow());
            if (!a.actorId().equals(asset.actorId()) || !storage.containerId().equals(asset.containerId()))
                throw new IllegalArgumentException("selected shared supply has a foreign transport holder");
        }
        var living = group.members().stream().filter(m -> state.actorLocations().get(m.actorId()).condition().status() == ActorLifeStatus.ALIVE).toList();
        var knowledge = TransportGroupMissionPort.knowledgeForEndpoints(state, mission.sender(), mission.receiver()).forActors(state, group.members().stream().map(UnitGroup.Member::actorId).toList());
        if (!GroupFormation.stations(living, List.of(mission.homeRendezvous()), 0, knowledge,
                state.bootstrap().ruleset().expedition().formationSpacing()).equals(load.assemblyStations()))
            throw new IllegalArgumentException("selected supply assembly has foreign local geometry");
    }
    private record WalkingInputs(List<ExpeditionProvisioning.Member> roster, Map<SubjectId, List<ActorItemSlot>> freeSlots,
                                 long outward, long returning) { }
    private static Optional<WalkingInputs> walkingInputs(FrontierWorldState state, TransportMission mission, UnitGroup group,
                                                        long now, FoodCatalog.Food food, FungibleResourceLedger resources) {
        ExpeditionJourneyProof journey;
        try {
            journey = ExpeditionJourneyProof.select(state, mission, group);
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) throw unavailable;
            return Optional.empty();
        }
        return walkingInputs(state, mission, group, now, food, resources, journey.outward().edges(), journey.returning().edges());
    }
    private static Optional<WalkingInputs> walkingInputs(FrontierWorldState state, TransportMission mission, UnitGroup group,
            long now, FoodCatalog.Food food, FungibleResourceLedger resources, long outward, long returning) {
        var life = state.bootstrap().ruleset().residentLife();
        var roster = new ArrayList<ExpeditionProvisioning.Member>(); var free = new LinkedHashMap<SubjectId, List<ActorItemSlot>>();
        for (var member : group.members()) {
            var profile = state.humanPopulation().resident(member.actorId());
            var actor = state.actorLocations().get(member.actorId());
            if (actor == null) throw new IllegalArgumentException("provisioning roster lost its declared actor");
            if (actor.condition().status() != ActorLifeStatus.ALIVE) continue;
            if (actor.kind() == ActorKind.PACK_ANIMAL && mission.transportAssetId().equals(Optional.of(member.actorId()))) continue;
            if (profile == null) return Optional.empty();
            var presentations = UnitInventoryPresentation.inventory(state, member.actorId());
            var used = new HashSet<>(presentations.values().stream().map(ActorCarriedResources.Presentation::slot).toList());
            var main = new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.MAIN);
            if (!state.inventory().actorItems(member.actorId()).isEmpty()) used.add(main);
            var meal = state.humanPopulation().meals().get(member.actorId()); if (meal != null) used.add(meal.inventorySlot());
            int cargoSlots = member.role() == UnitGroup.Role.CARRIER ? 1 : 0;
            boolean cargoAlreadyCounted = cargoSlots != 0 && mission.shipmentIds().stream().map(state.shipments().shipments()::get)
                    .filter(Objects::nonNull).filter(shipment -> shipment.execution().actorId().equals(member.actorId()))
                    .anyMatch(shipment -> presentations.containsKey(shipment.carriedAccountId()));
            if (cargoSlots != 0 && used.contains(main) && !cargoAlreadyCounted) return Optional.empty();
            free.put(member.actorId(), UnitInventoryPresentation.slots().stream().filter(slot -> !used.contains(slot)
                    && (cargoSlots == 0 || !slot.equals(main))).toList());
            int rate = profile.characteristics().effectiveMetabolismPermille(now);
            var nutrition = state.humanPopulation().nutrition(member.actorId()).accrueThrough(now, life, rate);
            int personal = UnitInventory.accounts(resources, member.actorId()).stream().filter(a -> a.claimQuantities().isEmpty())
                    .mapToInt(a -> resources.unclaimedQuantity(a.id(), mission.organization().sponsor().id(), food.itemKind())).sum();
            roster.add(new ExpeditionProvisioning.Member(member.actorId(), nutrition.satietyUnits(), rate,
                    used.size() - (cargoAlreadyCounted ? 1 : 0), cargoSlots, personal, 0));
        }
        // Human losses cannot make a surviving, already identified autonomous
        // carrier wait for nonexistent eaters. No food need is invented for the
        // animal; its actual cargo capacity and living body remain mandatory.
        boolean livingAsset = mission.transportAssetId().filter(id -> group.members().stream().anyMatch(member -> member.actorId().equals(id)))
                .map(state.actorLocations()::get).filter(actor -> actor != null && actor.kind() == ActorKind.PACK_ANIMAL
                    && actor.condition().status() == ActorLifeStatus.ALIVE).isPresent();
        return roster.isEmpty() && !livingAsset ? Optional.empty()
                : Optional.of(new WalkingInputs(List.copyOf(roster), Map.copyOf(free), outward, returning));
    }
    public static FungibleResourceLedger reserve(FungibleResourceLedger resources, ExpeditionSupplyLoad.Allocation allocation,
                                                  SubjectId mission, SubjectId owner, String kind) {
        var claim = new ClaimAllocation(allocation.claimId(), mission, owner, kind, allocation.quantity(), allocation.lots(), ClaimPurpose.EXPEDITION_SUPPLY);
        var bindings = resources.bindings().values().stream().filter(b -> b.accountId().equals(allocation.sourceAccountId())).toList();
        return bindings.isEmpty() ? resources.reserve(claim, allocation.sourceAccountId())
                : resources.reserveBound(claim, allocation.sourceAccountId(), bindings.getFirst().authorityEpoch());
    }
    // Parent identifiers may already occupy their full legal length. Compose facts,
    // not nested identifier spellings; the retained allocation owns the relationship.
    static SubjectId supplyClaim(SubjectId mission, long revision, String recipient, int index) {
        return new SubjectId("claim:expedition/" + StableCorrelationIdentity.digest(
                mission.value() + "\n" + revision + "\n" + recipient + "\n" + index));
    }
    static SubjectId supplyAccount(SubjectId claim) {
        return new SubjectId("custody:expedition/" + StableCorrelationIdentity.digest(claim.value()));
    }
}
