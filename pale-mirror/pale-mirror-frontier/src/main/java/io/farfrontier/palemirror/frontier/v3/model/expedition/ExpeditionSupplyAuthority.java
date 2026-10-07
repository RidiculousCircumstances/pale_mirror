package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.group.*;
import java.util.*;

/** Supply owner retains promises and accepts resource receipts; it does not own stock or actor motion. */
public final class ExpeditionSupplyAuthority {
    private ExpeditionSupplyAuthority() { }
    public static Optional<ActorExecutionId> execution(FrontierWorldState state, TransportMission mission,
                                                       ExpeditionSupplyLoad.Allocation allocation) {
        var group = state.unitGroups().groups().get(mission.groupId());
        if (group == null || !group.mission().id().equals(mission.id())) throw new IllegalArgumentException("supply load lost its group");
        return UnitGroupMissionPorts.require(group).execution(state, group, group.member(allocation.actorId()));
    }
    public static boolean pendingForContainer(FrontierWorldState state, SubjectId container) {
        return state.shipments().missions().values().stream().filter(m -> m.sender().containerId().equals(container))
                .flatMap(m -> m.supplies().stream()).flatMap(load -> load.allocations().stream()).anyMatch(a -> a.pending().isPresent());
    }
    public static boolean pendingForActor(FrontierWorldState state, SubjectId actor) {
        return pendingOwnerForActor(state, actor).isPresent();
    }
    public static Optional<SubjectId> pendingOwnerForActor(FrontierWorldState state, SubjectId actor) {
        var owners = state.shipments().missions().values().stream().filter(mission -> mission.supplies().stream()
                .flatMap(load -> load.allocations().stream()).anyMatch(a -> a.actorId().equals(actor) && a.pending().isPresent()))
                .map(TransportMission::id).toList();
        if (owners.size() > 1) throw new IllegalArgumentException("actor has competing expedition load effects");
        return owners.stream().findFirst();
    }
    public static Set<ActorItemSlot> reservedSlots(FrontierWorldState state, SubjectId actor) {
        return state.shipments().missions().values().stream().flatMap(m -> m.supplies().stream())
                .flatMap(load -> load.allocations().stream()).filter(a -> !a.loaded() && !a.withdrawn() && a.actorId().equals(actor))
                .map(ExpeditionSupplyLoad.Allocation::slot).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public static FungibleResourceLedger reserveDeclaration(FrontierWorldState state, TransportMission mission, UnitGroup group) {
        if (mission.supplies().isEmpty()) {
            if (state.bootstrap().ruleset().schemaVersion() >= 17)
                throw new IllegalArgumentException("current expedition omits its computed supply declaration");
            return state.inventory().fungibleResources();
        }
        var load = mission.supplies().orElseThrow();
        var expected = ExpeditionSupplyPlanning.walkingLoad(state, mission, group, load.calculatedAtTick());
        if (!expected.equals(Optional.of(load))) throw new IllegalArgumentException("supply declaration differs from actual roster, routes, stock or capacity");
        var resources = state.inventory().fungibleResources();
        for (var allocation : load.allocations()) resources = ExpeditionSupplyPlanning.reserve(resources, allocation,
                mission.id(), mission.sender().settlementId(), load.foodKind());
        return resources;
    }
    /** Read-only replacement calculation. Release old promises in a local ledger value, never publish it alone. */
    public static Optional<ExpeditionSupplyLoad> reconsider(FrontierWorldState state, TransportMission mission, long now) {
        var load = mission.supplies().orElseThrow();
        if (mission.stage() != TransportMission.Stage.LOADING || load.allocations().stream().anyMatch(a -> a.pending().isPresent())
                || load.foodTargets().keySet().stream().anyMatch(actor -> state.actorMovements().containsKey(actor)
                    || state.humanPopulation().meals().containsKey(actor))) return Optional.empty();
        var group = state.unitGroups().groups().get(mission.groupId());
        if (group.members().stream().anyMatch(member -> UnitGroupMissionPorts.require(group).execution(state, group, member).isEmpty()))
            return Optional.empty();
        return ExpeditionSupplyPlanning.walkingLoad(state, mission, group, now, Math.addExact(load.revision(), 1), released(state, load));
    }
    public static FrontierWorldState replanned(FrontierWorldState state, ExpeditionSupplyReplanned value, long now) {
        var mission = state.shipments().missions().get(value.missionId());
        if (mission == null || mission.supplies().isEmpty() || mission.supplies().orElseThrow().revision() != value.expectedRevision()
                || value.replacement().calculatedAtTick() != now || !reconsider(state, mission, now).equals(Optional.of(value.replacement())))
            throw new IllegalArgumentException("supply replanning has stale, unavailable or forged material assumptions");
        var resources = released(state, mission.supplies().orElseThrow());
        for (var allocation : value.replacement().allocations()) resources = ExpeditionSupplyPlanning.reserve(resources, allocation,
                mission.id(), mission.sender().settlementId(), value.replacement().foodKind());
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(resources))
                .shipments(state.shipments().replaceSupplies(mission, value.replacement())));
    }
    private static FungibleResourceLedger released(FrontierWorldState state, ExpeditionSupplyLoad load) {
        var claims = load.allocations().stream().filter(a -> !a.loaded() && !a.withdrawn())
                .map(ExpeditionSupplyLoad.Allocation::claimId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return claims.isEmpty() ? state.inventory().fungibleResources() : state.inventory().fungibleResources().releaseClaims(claims);
    }
    public static FungibleResourceLedger releaseForAbort(FrontierWorldState state, TransportMission mission) {
        if (mission.stage() != TransportMission.Stage.LOADING || mission.supplies().stream()
                .flatMap(load -> load.allocations().stream()).anyMatch(a -> a.pending().isPresent()))
            throw new IllegalArgumentException("mission abort cannot discard an unresolved supply effect");
        return mission.supplies().map(load -> released(state, load)).orElse(state.inventory().fungibleResources());
    }
    public static void validate(FrontierWorldState state, TransportMission mission, UnitGroup group) {
        if (mission.supplies().isEmpty()) return;
        var load = mission.supplies().orElseThrow(); var resources = state.inventory().fungibleResources();
        if (!load.foodTargets().keySet().equals(group.members().stream().map(UnitGroup.Member::actorId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet())) || !state.bootstrap().ruleset().residentLife().foods().foods().containsKey(load.foodKind())
                || mission.stage() != TransportMission.Stage.LOADING && !load.complete())
            throw new IllegalArgumentException("expedition lost its supply roster, food policy or departure gate");
        for (var a : load.allocations()) {
            var claim = resources.claims().get(a.claimId());
            if (a.loaded() || a.withdrawn()) {
                if (claim != null) throw new IllegalArgumentException("loaded personal stock retains its provisioning claim");
                if (a.withdrawn() && resources.accounts().containsKey(a.destinationAccountId()))
                    throw new IllegalArgumentException("withdrawn source promise invents loaded personal stock");
                continue; // Account may have been eaten or exactly disposed; this is a historical receipt.
            }
            var account = resources.accounts().get(a.sourceAccountId());
            if (claim == null || claim.purpose() != ClaimPurpose.EXPEDITION_SUPPLY || !claim.claimantId().equals(mission.id())
                    || !claim.economicOwnerId().equals(mission.sender().settlementId()) || !claim.itemKind().equals(load.foodKind())
                    || claim.quantity() != a.quantity() || !claim.lotQuantities().equals(a.lots()) || account == null
                    || !account.custody().equals(new ResourceCustody.Container(mission.sender().containerId()))
                    || account.claimQuantities().getOrDefault(a.claimId(), 0) != a.quantity()
                    || resources.accounts().containsKey(a.destinationAccountId()))
                throw new IllegalArgumentException("unloaded expedition stock lost its exact source reservation");
            a.pending().ifPresent(step -> {
                state.actorExecutions().requireRetained(step.observation().actuation().execution());
                if (!step.observation().actuation().body().actorId().equals(a.actorId())
                        || !step.source().equals(MaterialSourceSelection.select(resources, load.order(mission.id(), mission.sender(), a))))
                    throw new IllegalArgumentException("prepared supply interaction lost its exact actor/resource preimage");
            });
        }
    }
    public static boolean loadedForDeparture(FrontierWorldState state, TransportMission mission, long now) {
        if (mission.supplies().isEmpty()) return state.bootstrap().ruleset().schemaVersion() < 17;
        var load = mission.supplies().orElseThrow();
        if (!load.complete() || now < load.calculatedAtTick()) return false;
        var group = state.unitGroups().groups().get(mission.groupId());
        var fresh = ExpeditionSupplyPlanning.walkingForecast(state, mission, group, now, load.foodKind());
        return fresh.filter(plan -> plan.feasible() && plan.transport() == ExpeditionProvisioning.Transport.WALKING
                && plan.members().values().stream().allMatch(member -> member.existingFoodItems() >= member.requiredFoodItems())).isPresent();
    }
    public static io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder assemblyOrder(TransportMission mission, SubjectId actor) {
        var station = mission.supplies().orElseThrow().assemblyStations().get(actor);
        if (station == null || mission.stage() != TransportMission.Stage.LOADING) throw new IllegalArgumentException("assembly order lacks its loading participant");
        return new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(mission.id(), actor, 0, mission.supplies().orElseThrow().revision(), List.of(station),
                TraversalCapability.PEDESTRIAN, io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
    public static FrontierWorldState coldLoaded(FrontierWorldState state, SubjectId missionId, SubjectId claimId) {
        var mission = requireLoading(state, missionId, claimId); var load = mission.supplies().orElseThrow();
        var allocation = load.next().orElseThrow(); var order = load.order(mission.id(), mission.sender(), allocation);
        if (allocation.pending().isPresent() || execution(state, mission, allocation).isEmpty()
                || state.actorMovements().containsKey(allocation.actorId()) || !ServiceAccessCoordinator.available(state,
                    ExpeditionSupplyServiceAccess.identity(mission, allocation)))
            throw new IllegalArgumentException("COLD supply handoff lacks its exact available task and service turn");
        var inventory = ActorItemCustody.transferCold(state, order);
        inventory = inventory.withFungibleResources(inventory.fungibleResources().releaseClaim(allocation.destinationAccountId(), allocation.claimId()));
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).shipments(state.shipments().replaceSupplies(mission, load.replace(allocation, allocation.confirmed()))));
    }
    public static FrontierWorldState prepare(FrontierWorldState state, SubjectId missionId, SubjectId claimId, ActorItemTransferStep step) {
        var mission = requireLoading(state, missionId, claimId); var load = mission.supplies().orElseThrow(); var a = load.next().orElseThrow();
        var order = load.order(mission.id(), mission.sender(), a);
        var lease = state.ambientLeases().get(a.actorId()); var execution = execution(state, mission, a).orElseThrow();
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT || state.actorMovements().containsKey(a.actorId())
                || !ReferenceContainerCustody.hasOperationalCustody(state, mission.sender().containerId())
                || !ServiceAccessCoordinator.available(state, ExpeditionSupplyServiceAccess.identity(mission, a))
                || !MaterialSourceSelection.select(state.inventory().fungibleResources(), order).equals(step.source())
                || step.destinationEpoch() != step.observation().actuation().body().physicalEpoch())
            throw new IllegalArgumentException("supply preparation lacks its exact physical source, body or service turn");
        step.observation().require(state, execution, lease.revision(), order.station().standingBody());
        return state.withChanges(FrontierWorldStateUpdate.begin().shipments(state.shipments().replaceSupplies(mission, load.replace(a, a.prepare(step)))));
    }
    public static FrontierWorldState observed(FrontierWorldState state, SubjectId missionId, SubjectId claimId, ActorItemTransferStep step,
            List<FungiblePhysicalObservation.Stack> remainder, List<FungiblePhysicalObservation.Stack> destination) {
        var mission = requireLoading(state, missionId, claimId); var load = mission.supplies().orElseThrow(); var a = load.next().orElseThrow();
        var order = load.order(mission.id(), mission.sender(), a);
        if (!a.pending().equals(Optional.of(step))) throw new IllegalArgumentException("supply receipt has no exact retained pre-effect declaration");
        var execution = execution(state, mission, a).orElseThrow(); var lease = state.ambientLeases().get(a.actorId());
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT || state.actorMovements().containsKey(a.actorId()))
            throw new IllegalArgumentException("supply receipt lost current physical custody");
        step.observation().require(state, execution, lease.revision(), order.station().standingBody());
        var address = switch (a.slot()) {
            case ActorItemSlot.Pocket pocket -> (PhysicalStackAddress) new PhysicalStackAddress.ActorPocket(a.actorId(),
                    io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(state.bootstrap().worldId(), a.actorId()), pocket.index());
            case ActorItemSlot.Hand hand -> new PhysicalStackAddress.ActorHand(a.actorId(),
                    io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(state.bootstrap().worldId(), a.actorId()), hand.hand());
        };
        if (!destination.equals(List.of(new FungiblePhysicalObservation.Stack(address, load.foodKind(), a.quantity()))))
            throw new IllegalArgumentException("supply receipt did not enter its exact personal slot");
        var inventory = ActorItemCustody.transferObserved(state, order, step.sourceEpoch(), step.destinationEpoch(), remainder, destination);
        inventory = inventory.withFungibleResources(
                inventory.fungibleResources().releaseClaims(Set.of(a.claimId())));
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).shipments(state.shipments().replaceSupplies(mission, load.replace(a, a.confirmed()))));
    }
    public static TransportMission requireLoading(FrontierWorldState state, SubjectId missionId, SubjectId claimId) {
        var mission = state.shipments().missions().get(missionId);
        if (mission == null || mission.stage() != TransportMission.Stage.LOADING || mission.supplies().isEmpty()
                || !mission.supplies().orElseThrow().next().map(ExpeditionSupplyLoad.Allocation::claimId).equals(Optional.of(claimId)))
            throw new IllegalArgumentException("supply interaction has a stale or foreign loading obligation");
        return mission;
    }
}
