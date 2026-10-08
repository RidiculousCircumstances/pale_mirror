package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.group.*;
import java.util.*;

/** Mission policy supplies a real personal portion. Nutrition still consumes only the resident's own inventory. */
public final class ExpeditionReplenishmentAuthority {
    private ExpeditionReplenishmentAuthority() { }
    public enum TravelDecision { CONTINUE_ITINERARY, REST_FOR_LOCAL_SUPPLIES, COMPLETE_RETAINED_TRANSFER }
    /** No available local supply means continue the mission itinerary toward a supplier/home;
     * it must not become an endless individual home-food wait. A current transfer owns its fence. */
    public static TravelDecision travelDecision(FrontierWorldState state, TransportMission mission) {
        if (mission.replenishment().isPresent()) return TravelDecision.COMPLETE_RETAINED_TRANSFER;
        if (mission.supplies().isEmpty() || mission.stage() == TransportMission.Stage.LOADING || mission.stage() == TransportMission.Stage.COMPLETE)
            return TravelDecision.CONTINUE_ITINERARY;
        return requiresPersonalStock(state, mission) && hasLocalStock(state, mission)
                ? TravelDecision.REST_FOR_LOCAL_SUPPLIES : TravelDecision.CONTINUE_ITINERARY;
    }
    /** A depleted personal stock requests a group-owned rest, never an individual trip home. */
    public static boolean requiresLocalStock(FrontierWorldState state, TransportMission mission) {
        return travelDecision(state, mission) == TravelDecision.REST_FOR_LOCAL_SUPPLIES;
    }
    private static boolean requiresPersonalStock(FrontierWorldState state, TransportMission mission) {
        var life = state.bootstrap().ruleset().residentLife(); var resources = state.inventory().fungibleResources();
        return state.unitGroups().groups().get(mission.groupId()).members().stream().anyMatch(member -> {
            var resident = state.humanPopulation().resident(member.actorId());
            return resident != null && state.actorLocations().get(member.actorId()).condition().status() == ActorLifeStatus.ALIVE
                    && !state.humanPopulation().meals().containsKey(member.actorId())
                    && UnitInventoryPresentation.freeSlot(state, member.actorId()).isPresent()
                    && state.humanPopulation().nutrition(member.actorId()).wantsFood(life)
                    && life.foods().foods().keySet().stream().noneMatch(kind -> UnitInventory.available(resources,
                        member.actorId(), mission.sender().settlementId(), kind) > 0);
        });
    }
    private static boolean hasLocalStock(FrontierWorldState state, TransportMission mission) {
        var group = state.unitGroups().groups().get(mission.groupId());
        var resources = state.inventory().fungibleResources();
        for (var source : sources(state, mission, group)) {
            if (ReferenceContainerCustody.blocksCanonicalUse(state, source.container())) continue;
            var account = FungibleResourceCustodySupport.accountAtContainer(state, source.container()).orElse(null);
            if (account == null) continue;
            for (var kind : state.bootstrap().ruleset().residentLife().foods().foods().keySet()) {
                int quantity = source.mobile() ? resources.unclaimedQuantity(account.id(), source.owner(), kind)
                        : GoodsSpotPurchaseAuthority.availableQuantity(state, source.container(), source.owner(), mission.sender().settlementId(),
                            mission.financialBudgetId().orElseThrow(), kind);
                if (quantity > 0) return true;
            }
        }
        return false;
    }
    public static Optional<UnitResourceTransfer> select(FrontierWorldState state, TransportMission mission, long now) {
        if (mission.replenishment().isPresent()
                || mission.stage() == TransportMission.Stage.LOADING || mission.stage() == TransportMission.Stage.COMPLETE)
            return Optional.empty();
        var group = state.unitGroups().groups().get(mission.groupId());
        if (group == null || group.members().stream().anyMatch(m -> state.actorMovements().containsKey(m.actorId()))) return Optional.empty();
        var sources = sources(state, mission, group);
        if (sources.isEmpty()) return Optional.empty();
        var resources = state.inventory().fungibleResources(); var life = state.bootstrap().ruleset().residentLife();
        for (var member : group.members()) {
            var profile = state.humanPopulation().resident(member.actorId()); if (profile == null) continue;
            var body = state.actorLocations().get(member.actorId());
            if (body.condition().status() != ActorLifeStatus.ALIVE || state.humanPopulation().meals().containsKey(member.actorId())) continue;
            var nutrition = state.humanPopulation().nutrition(member.actorId()).accrueThrough(now, life,
                    profile.characteristics().effectiveMetabolismPermille(now));
            if (nutrition.satietyUnits() >= life.eatBelowUnits()) continue;
            var execution = UnitGroupMissionPorts.require(group).execution(state, group, member);
            var slot = UnitInventoryPresentation.freeSlot(state, member.actorId());
            if (execution.isEmpty() || slot.isEmpty()
                    || life.foods().foods().keySet().stream().anyMatch(kind -> UnitInventory.available(resources, member.actorId(), profile.settlementId(), kind) > 0)) continue;
            for (var location : sources) for (var food : new TreeMap<>(life.foods().foods()).values()) {
                if (ReferenceContainerCustody.blocksCanonicalUse(state, location.container())) continue;
                var source = FungibleResourceCustodySupport.accountAtContainer(state, location.container()).orElse(null);
                if (source == null) continue;
                int available = resources.unclaimedQuantity(source.id(), location.owner(), food.itemKind());
                if (!location.mobile()) available = Math.min(available, GoodsSpotPurchaseAuthority.availableQuantity(state, location.container(),
                        location.owner(), mission.sender().settlementId(), mission.financialBudgetId().orElseThrow(), food.itemKind()));
                int quantity = food.portionFor(nutrition.nutritionWanted(life), available);
                if (quantity == 0) continue;
                var station = location.mobile() ? MobileContainerAccess.station(state, member.actorId(), location.container(),
                        TransportGroupMissionPort.knowledgeForEndpoints(state, mission.sender(), mission.receiver()))
                        : Optional.of(mission.receiver().station());
                if (station.isEmpty()) continue;
                var selected = FungibleResourceCustodySupport.selectAtAccount(resources, source.id(), location.owner(), food.itemKind(), quantity).orElseThrow();
                var identity = UUID.nameUUIDFromBytes((mission.id().value() + "\n" + member.actorId().value() + "\n" + now)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var claim = new SubjectId("claim:expedition-refill/" + identity);
                var account = new SubjectId("custody:expedition-refill/" + identity);
                return Optional.of(new UnitResourceTransfer(claim, member.actorId(), location.container(), source.id(), account,
                        location.owner(), food.itemKind(), selected.lotQuantities(), slot.orElseThrow(), station.orElseThrow(), execution.orElseThrow(), Optional.empty()));
            }
        }
        return Optional.empty();
    }
    private record Source(SubjectId container, SubjectId owner, boolean mobile) { }
    private static List<Source> sources(FrontierWorldState state, TransportMission mission, UnitGroup group) {
        var sources = new ArrayList<Source>();
        mission.transportAssetId().ifPresent(id -> {
            var asset = state.transportFleet().require(id);
            if (state.actorLocations().get(id).condition().status() == ActorLifeStatus.ALIVE)
                sources.add(new Source(asset.containerId(), mission.sender().settlementId(), true));
        });
        if (group.phase() == UnitGroup.Phase.AT_GOAL && group.goalOrdinal() == 1 && mission.stage() == TransportMission.Stage.UNLOADING
                && mission.shipmentIds().stream().allMatch(id -> state.shipments().shipments().get(id).terminal()) && mission.financialBudgetId().isPresent())
            sources.add(new Source(mission.receiver().containerId(), mission.receiver().settlementId(), false));
        return List.copyOf(sources);
    }
    public static FrontierWorldState start(FrontierWorldState state, SubjectId missionId, UnitResourceTransfer transfer, long now) {
        var mission = require(state, missionId);
        if (!select(state, mission, now).equals(Optional.of(transfer))) throw new IllegalArgumentException("refill differs from current actual free supplies and recipient");
        var resources = state.inventory().fungibleResources();
        var claim = new ClaimAllocation(transfer.claimId(), mission.id(), transfer.sourceEconomicOwnerId(), transfer.itemKind(), transfer.quantity(), transfer.lots(), ClaimPurpose.EXPEDITION_SUPPLY);
        var bindings = resources.bindings().values().stream().filter(b -> b.accountId().equals(transfer.sourceAccountId())).toList();
        resources = bindings.isEmpty() ? resources.reserve(claim, transfer.sourceAccountId()) : resources.reserveBound(claim, transfer.sourceAccountId(), bindings.getFirst().authorityEpoch());
        var purchase = transfer.sourceEconomicOwnerId().equals(mission.sender().settlementId()) ? Optional.<GoodsSpotPurchase>empty()
                : Optional.of(GoodsSpotPurchaseAuthority.offer(state, transfer, mission.sender().settlementId(), mission.financialBudgetId().orElseThrow(), mission.id()).orElseThrow(
                        () -> new IllegalArgumentException("foreign food lacks seller consent and finite paid terms")));
        var inventory = state.inventory().withFungibleResources(resources);
        if (purchase.isPresent()) inventory = GoodsSpotPurchaseAuthority.reserve(inventory, purchase.orElseThrow());
        var changes = FrontierWorldStateUpdate.begin().inventory(inventory)
                .shipments(state.shipments().replaceReplenishment(mission, Optional.of(transfer), purchase));
        var current = state.actorExecutions().actors().get(transfer.actorId());
        return current != null && current.current().equals(Optional.of(transfer.execution())) ? state.withChanges(changes)
                : ActorExecutionComposition.LIFECYCLE.prepareVacant(state, transfer.execution()).commit(state, changes);
    }
    public static FrontierWorldState cold(FrontierWorldState state, SubjectId missionId, SubjectId claimId) {
        var mission = require(state, missionId); var transfer = retained(mission, claimId);
        requireExecution(state, mission, transfer);
        if (transfer.pending().isPresent() || !ActorExecutionCoordinator.coldAvailable(state, transfer.actorId()))
            throw new IllegalArgumentException("refill cannot overwrite current physical effects");
        return complete(state, mission, transfer, ActorItemCustody.transferCold(state, transfer.order(mission.id(), mission.revision())),
                OptionalLong.empty());
    }
    public static FrontierWorldState prepare(FrontierWorldState state, SubjectId missionId, SubjectId claimId, ActorItemTransferStep step) {
        var mission = require(state, missionId); var transfer = retained(mission, claimId); requireExecution(state, mission, transfer);
        var lease = state.ambientLeases().get(transfer.actorId());
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT || !ReferenceContainerCustody.hasOperationalCustody(state, transfer.containerId())
                || step.destinationSlot() != -1 || step.destinationBefore() != 0
                || step.destinationEpoch() != step.observation().actuation().body().physicalEpoch()
                || !step.source().equals(MaterialSourcePreparation.review(state, transfer.order(mission.id(), mission.revision())).requireReady()))
            throw new IllegalArgumentException("refill lacks exact body/source physical preimage");
        step.observation().require(state, transfer.execution(), lease.revision(), transfer.station().standingBody());
        return state.withChanges(FrontierWorldStateUpdate.begin().shipments(state.shipments().replaceReplenishment(mission, Optional.of(transfer.prepare(step)))));
    }
    public static FrontierWorldState observed(FrontierWorldState state, SubjectId missionId, SubjectId claimId, ActorItemTransferStep step,
            List<FungiblePhysicalObservation.Stack> remainder, List<FungiblePhysicalObservation.Stack> destination) {
        var mission = require(state, missionId); var transfer = retained(mission, claimId); requireExecution(state, mission, transfer);
        if (!transfer.pending().equals(Optional.of(step))) throw new IllegalArgumentException("refill receipt lost its retained pre-effect");
        var lease = state.ambientLeases().get(transfer.actorId());
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT) throw new IllegalArgumentException("refill lost physical recipient authority");
        step.observation().require(state, transfer.execution(), lease.revision(), transfer.station().standingBody());
        PhysicalStackAddress address = switch (transfer.slot()) {
            case ActorItemSlot.Pocket pocket -> new PhysicalStackAddress.ActorPocket(transfer.actorId(), ActorBodyId.entityId(state.bootstrap().worldId(), transfer.actorId()), pocket.index());
            case ActorItemSlot.Hand hand -> new PhysicalStackAddress.ActorHand(transfer.actorId(), ActorBodyId.entityId(state.bootstrap().worldId(), transfer.actorId()), hand.hand());
            case ActorItemSlot.AttachedStorage storage -> throw new IllegalArgumentException("refill has no personal slot");
        };
        if (!destination.equals(List.of(new FungiblePhysicalObservation.Stack(address, transfer.itemKind(), transfer.quantity()))))
            throw new IllegalArgumentException("refill did not enter its declared personal slot");
        return complete(state, mission, transfer, ActorItemCustody.transferObserved(state, transfer.order(mission.id(), mission.revision()),
                step.sourceEpoch(), step.destinationEpoch(), remainder, destination), OptionalLong.of(step.destinationEpoch()));
    }
    private static FrontierWorldState complete(FrontierWorldState state, TransportMission mission, UnitResourceTransfer transfer,
                                               ExactInventory inventory, OptionalLong observedDestinationEpoch) {
        inventory = mission.replenishmentPurchase().isPresent() ? GoodsSpotPurchaseAuthority.receive(inventory, transfer, mission.replenishmentPurchase().orElseThrow())
                : inventory.withFungibleResources(observedDestinationEpoch.isPresent()
                    ? inventory.fungibleResources().releaseBoundClaim(transfer.destinationAccountId(), transfer.claimId(), observedDestinationEpoch.orElseThrow())
                    : inventory.fungibleResources().releaseClaim(transfer.destinationAccountId(), transfer.claimId()));
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).shipments(state.shipments().replaceReplenishment(mission, Optional.empty())));
    }
    private static void requireExecution(FrontierWorldState state, TransportMission mission, UnitResourceTransfer transfer) {
        state.actorExecutions().requireCurrent(transfer.execution());
        var group = state.unitGroups().groups().get(mission.groupId()); group.member(transfer.actorId());
        if (group.members().stream().anyMatch(m -> state.actorMovements().containsKey(m.actorId()))
                || state.humanPopulation().meals().containsKey(transfer.actorId())) throw new IllegalArgumentException("refill requires a stationary declared expedition");
        var surface = state.inventory().surfaces().get(transfer.containerId());
        if (surface.location() instanceof ContainerLocation.Fixed && !accessAvailable(state, mission, transfer))
            throw new IllegalArgumentException("refill needs its ordinary exclusive service-point turn");
        if (surface.location() instanceof ContainerLocation.Mobile mobile && !MobileContainerAccess.reachable(
                state.actorLocations().get(transfer.actorId()).body(), state.actorLocations().get(mobile.actorId()).body()))
            throw new IllegalArgumentException("refill recipient has not reached its attached resource source");
    }
    public static TransportMission require(FrontierWorldState state, SubjectId missionId) {
        var mission = state.shipments().missions().get(missionId);
        if (mission == null || mission.stage() == TransportMission.Stage.LOADING || mission.stage() == TransportMission.Stage.COMPLETE)
            throw new IllegalArgumentException("refill lacks an active departed mission");
        return mission;
    }
    private static UnitResourceTransfer retained(TransportMission mission, SubjectId claimId) {
        return mission.replenishment().filter(t -> t.claimId().equals(claimId)).orElseThrow(() -> new IllegalArgumentException("refill has a stale claim"));
    }
    public static void validate(FrontierWorldState state, TransportMission mission) {
        mission.replenishment().ifPresent(t -> {
            var surface = state.inventory().surfaces().get(t.containerId());
            boolean ownedSource = t.sourceEconomicOwnerId().equals(mission.sender().settlementId());
            if (ownedSource ? mission.transportAssetId().isEmpty() || !state.transportFleet().require(mission.transportAssetId().orElseThrow()).containerId().equals(t.containerId())
                    : !mission.receiver().containerId().equals(t.containerId()) || !mission.receiver().settlementId().equals(t.sourceEconomicOwnerId()))
                throw new IllegalArgumentException("refill has no declared expedition or visited supplier source");
            var claim = state.inventory().fungibleResources().claims().get(t.claimId());
            var account = state.inventory().fungibleResources().accounts().get(t.sourceAccountId());
            if (surface == null || claim == null || claim.purpose() != ClaimPurpose.EXPEDITION_SUPPLY
                    || !claim.claimantId().equals(mission.id()) || !claim.lotQuantities().equals(t.lots()) || !claim.itemKind().equals(t.itemKind())
                    || !claim.economicOwnerId().equals(t.sourceEconomicOwnerId()) || ownedSource != mission.replenishmentPurchase().isEmpty()
                    || account == null || !account.custody().equals(new ResourceCustody.Container(t.containerId()))
                    || account.claimQuantities().getOrDefault(t.claimId(), 0) != t.quantity()
                    || state.inventory().fungibleResources().accounts().containsKey(t.destinationAccountId()))
                throw new IllegalArgumentException("mission refill lost exact resource ownership and custody");
            state.unitGroups().groups().get(mission.groupId()).member(t.actorId()); state.actorExecutions().requireCurrent(t.execution());
            mission.replenishmentPurchase().ifPresent(p -> {
                if (!p.payment().equals(state.inventory().economics().reservations().get(p.payment().id()))
                        || !p.payment().reasonId().equals(mission.id()) || !p.payment().budgetId().equals(mission.financialBudgetId())
                        || !p.title().claimId().equals(t.claimId()) || !p.title().accountId().equals(t.destinationAccountId())
                        || !p.title().sourceOwnerId().equals(t.sourceEconomicOwnerId()) || !p.title().destinationOwnerId().equals(mission.sender().settlementId())
                        || !p.title().portions().equals(t.lots())) throw new IllegalArgumentException("paid replenishment lost exact supplier, allocation or held terms");
            });
        });
    }
    public static boolean accessAvailable(FrontierWorldState state, TransportMission mission, UnitResourceTransfer transfer) {
        return state.inventory().surfaces().get(transfer.containerId()).location() instanceof ContainerLocation.Mobile
                || ServiceAccessCoordinator.available(state, ExpeditionSupplyServiceAccess.identity(mission, transfer));
    }
}
