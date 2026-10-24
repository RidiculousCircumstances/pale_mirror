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
        if (!couldSupplyShortestJourney(state, mission, group, now, availableResources)) return Optional.empty();
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
            int foodAvailable = availableFood(state, mission, resources, food.itemKind());
            var plan = forecast(state, mission, group, input, food, foodAvailable);
            if (!plan.feasible()) continue;
            var allocations = new ArrayList<ExpeditionSupplyLoad.Allocation>(); var targets = new LinkedHashMap<SubjectId, Integer>();
            var pickupOrigins = new HashMap<SubjectId, SurfaceAnchor>();
            boolean fits = true;
            for (var member : group.members()) {
                var supply = plan.members().get(member.actorId()); if (supply == null) continue;
                targets.put(member.actorId(), supply.requiredFoodItems());
                int remaining = supply.loadFoodItems(), index = 0;
                while (remaining > 0) {
                    if (index >= free.get(member.actorId()).size()) { fits = false; break; }
                    var source = source(state, mission, resources, member.actorId(), food.itemKind(), pickupOrigins);
                    if (source.isEmpty()) { fits = false; break; }
                    var selectedSource = source.orElseThrow();
                    int quantity = Math.min(Math.min(64, remaining), selectedSource.quantity());
                    var selected = FungibleResourceCustodySupport.selectAtAccount(resources, selectedSource.accountId(),
                            mission.organization().sponsor().id(), food.itemKind(), quantity).orElseThrow();
                    var claim = supplyClaim(mission.id(), revision, member.actorId().value(), index);
                    var account = supplyAccount(claim);
                    var allocation = new ExpeditionSupplyLoad.Allocation(claim, member.actorId(), selectedSource.endpoint(), selectedSource.accountId(), account,
                            free.get(member.actorId()).get(index++), selected.lotQuantities(), ExpeditionSupplyLoad.Outcome.RESERVED, Optional.empty());
                    resources = reserve(resources, allocation, mission.id(), mission.organization().sponsor().id(), food.itemKind());
                    allocations.add(allocation); remaining -= quantity;
                    pickupOrigins.put(member.actorId(), selectedSource.endpoint().station());
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
                    var source = source(state, mission, resources, asset.actorId(), food.itemKind(), pickupOrigins);
                    if (source.isEmpty()) { fits = false; break; }
                    var selectedSource = source.orElseThrow();
                    int quantity = Math.min(Math.min(64, remaining), selectedSource.quantity());
                    var selected = FungibleResourceCustodySupport.selectAtAccount(resources, selectedSource.accountId(), mission.organization().sponsor().id(), food.itemKind(), quantity).orElseThrow();
                    var claim = supplyClaim(mission.id(), revision, "shared", index++);
                    var allocation = new ExpeditionSupplyLoad.Allocation(claim, asset.actorId(), selectedSource.endpoint(), selectedSource.accountId(), destination,
                            new ActorItemSlot.AttachedStorage(asset.containerId()), selected.lotQuantities(), ExpeditionSupplyLoad.Outcome.RESERVED, Optional.empty());
                    resources = reserve(resources, allocation, mission.id(), mission.organization().sponsor().id(), food.itemKind());
                    allocations.add(allocation); remaining -= quantity;
                    pickupOrigins.put(asset.actorId(), selectedSource.endpoint().station());
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
    /** Rejection only: every cardinal route is at least this long. Stock, personal
     * provisions and actual transport capacity use the same owner forecast as the
     * full journey. A positive answer grants neither a route nor departure. */
    private static boolean couldSupplyShortestJourney(FrontierWorldState state, TransportMission mission,
            UnitGroup group, long now, FungibleResourceLedger resources) {
        var home = mission.homeRendezvous(); var remote = mission.destinationRendezvous();
        long edges = Math.abs((long) home.x() - remote.x()) + Math.abs((long) home.z() - remote.z());
        for (var food : state.bootstrap().ruleset().residentLife().foods().foods().values()) {
            var input = walkingInputs(state, mission, group, now, food, resources, edges, edges);
            int stock = availableFood(state, mission, resources, food.itemKind());
            if (input.isPresent() && forecast(state, mission, group, input.orElseThrow(), food, stock).feasible()) return true;
        }
        return false;
    }
    /** Pure material feasibility over the retained journey; not new route or departure authority. */
    public static boolean supplyAvailableForRetainedJourney(FrontierWorldState state, TransportMission mission,
            UnitGroup group, long now, FungibleResourceLedger resources) {
        var journey = mission.supplies().orElseThrow().journey();
        // This is material feasibility for the already committed journey, not a
        // new route/departure proof. No search or formation selection in replay.
        for (var food : state.bootstrap().ruleset().residentLife().foods().foods().values()) {
            var input = walkingInputs(state, mission, group, now, food, resources, journey.outward().edges(), journey.returning().edges());
            int stock = availableFood(state, mission, resources, food.itemKind());
            if (input.isPresent() && forecast(state, mission, group, input.orElseThrow(), food, stock).feasible()) return true;
        }
        return false;
    }
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
        for (var allocation : load.allocations()) {
            var source = allocation.source();
            ShipmentEndpointComposition.validate(state, source);
            var account = resources.accounts().get(allocation.sourceAccountId());
            if (!source.settlementId().equals(mission.organization().home().settlementId())
                    || !ResourceStorageSelection.permits(state.inventory(), source.containerId(),
                        mission.organization().sponsor().id(), load.foodKind(), Set.of(ResourceStorageRole.WAREHOUSE))
                    || ReferenceContainerCustody.blocksCanonicalUse(state, source.containerId())
                    || account == null || !account.custody().equals(new ResourceCustody.Container(source.containerId())))
                throw new IllegalArgumentException("selected supply source is foreign or physically unavailable");
        }
        var food = state.bootstrap().ruleset().residentLife().foods().require(load.foodKind());
        var input = walkingInputs(state, mission, group, load.calculatedAtTick(), food, resources,
                load.journey().outward().edges(), load.journey().returning().edges()).orElseThrow();
        // Replay checks only the declared stock. Reserving the exact lots below
        // proves availability; it cannot discover a different source or route.
        var plan = forecast(state, mission, group, input, food,
                load.allocations().stream().mapToInt(ExpeditionSupplyLoad.Allocation::quantity).sum());
        var targets = new LinkedHashMap<SubjectId, Integer>();
        plan.members().forEach((id, member) -> targets.put(id, member.requiredFoodItems()));
        if (!plan.feasible() || plan.durationTicks() != load.forecastDurationTicks() || !targets.equals(load.foodTargets())
                || load.allocations().stream().anyMatch(a -> a.outcome() != ExpeditionSupplyLoad.Outcome.RESERVED || a.pending().isPresent()
                    || !(a.slot() instanceof ActorItemSlot.AttachedStorage)
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
    private static int availableFood(FrontierWorldState state, TransportMission mission, FungibleResourceLedger resources, String food) {
        return ResourceStorageSelection.sources(state, resources, mission.organization().sponsor().id(), food,
                mission.homeRendezvous(), Set.of(ResourceStorageRole.WAREHOUSE)).stream()
                .filter(source -> source.endpoint().settlementId().equals(mission.organization().home().settlementId()))
                .mapToInt(ResourceStorageSelection.Source::quantity).reduce(0, Math::addExact);
    }
    private static Optional<ResourceStorageSelection.Source> source(FrontierWorldState state, TransportMission mission,
            FungibleResourceLedger resources, SubjectId actor, String food, Map<SubjectId, SurfaceAnchor> pickupOrigins) {
        var origin = pickupOrigins.getOrDefault(actor, state.actorLocations().get(actor).supportingSurface());
        for (var source : ResourceStorageSelection.sources(state, resources, mission.organization().sponsor().id(), food,
                origin, Set.of(ResourceStorageRole.WAREHOUSE))) {
            if (!source.endpoint().settlementId().equals(mission.organization().home().settlementId())) continue;
            var knowledge = ShipmentEndpointComposition.knowledge(state, List.of(mission.sender(), mission.receiver(), source.endpoint()));
            var approach = ActorStorageApproach.select(state, source.endpoint(), actor, origin, knowledge);
            if (approach.isPresent()) return Optional.of(new ResourceStorageSelection.Source(
                    approach.orElseThrow(), source.accountId(), source.quantity()));
        }
        return Optional.empty();
    }
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
                    .anyMatch(shipment -> shipment.cargo() instanceof ShipmentCargo.Individual cargo
                            ? state.inventory().actorItems(member.actorId()).stream().anyMatch(item -> item.id().equals(cargo.itemId()))
                            : presentations.containsKey(shipment.carriedAccountId()));
            if (cargoSlots != 0 && used.contains(main) && !cargoAlreadyCounted) return Optional.empty();
            free.put(member.actorId(), UnitInventoryPresentation.slots().stream().filter(slot -> !used.contains(slot)
                    && (cargoSlots == 0 || !slot.equals(main))).toList());
            int rate = profile.characteristics().effectiveMetabolismPermille(now);
            var nutrition = state.humanPopulation().nutrition(member.actorId()).accrueThrough(now, life, rate);
            int personal = UnitInventory.personalAccounts(state, resources, member.actorId()).stream().filter(a -> a.claimQuantities().isEmpty())
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
