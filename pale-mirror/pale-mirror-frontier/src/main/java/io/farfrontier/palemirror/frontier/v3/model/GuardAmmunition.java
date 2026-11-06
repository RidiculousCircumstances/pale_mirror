package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;
/** Guard-owned acquisition decisions. Shared inventory validates and performs every transfer. */
public final class GuardAmmunition {
    public static final String ARROW="minecraft:arrow";
    private static final Set<ResourceStorageRole> ROLES=Set.of(ResourceStorageRole.WAREHOUSE,ResourceStorageRole.LOCAL_BUFFER);
    public static Optional<GuardAmmunitionEvent.Selected> select(FrontierWorldState state,GuardDuty duty) {
        if(duty.method()!=CombatMethod.CROSSBOW || duty.phase()!=GuardDuty.Phase.ON_DUTY || !GuardEquipment.held(state,duty)
                || state.combat().ranged().unsettled(duty.id()) || Ammunition.available(state,duty.execution().actorId(),duty.home(),ARROW)>0)return Optional.empty();
        var pocket=Ammunition.freePocket(state,duty.execution().actorId());if(pocket.isEmpty())return Optional.empty();
        var resources=state.inventory().fungibleResources();var origin=state.actorLocations().get(duty.execution().actorId()).supportingSurface();
        for(var source:ResourceStorageSelection.withdrawalSources(state,resources,duty.home(),ARROW,origin,ROLES)) {
            int count=Math.min(source.quantity(),state.bootstrap().ruleset().ranged().loadSize());
            var lots=FungibleResourceCustodySupport.selectAtAccount(resources,source.accountId(),duty.home(),ARROW,count);if(lots.isEmpty())continue;
            var target=new SubjectId("custody:guard-ammo/"+UUID.nameUUIDFromBytes((duty.id().value()+"|"+duty.revision()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            if(!ActorCarriedResources.canAddAccount(resources,duty.execution().actorId(),target))return Optional.empty();
            var endpoint=ResourceStorageAccess.at(state,source.endpoint().containerId()).stations().stream()
                    .sorted(Comparator.comparingLong(s->s.manhattanDistance(origin))).findFirst();if(endpoint.isEmpty())continue;
            var chosen=source.endpoint().withStation(endpoint.orElseThrow());
            var knowledge=ResourceStorageNavigation.forEndpoint(state,chosen).forActor(state,duty.execution().actorId());
            try{knowledge.plannedBetween(origin,chosen.station());}
            catch(io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable waiting){
                if(waiting.status()!=io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH)throw waiting;continue;}
            var order=new ActorContainerItemOrder(duty.id(),duty.execution().actorId(),ActorContainerItemOrder.Direction.TAKE,
                    new ActorContainerItemOrder.Portion.Fungible(source.accountId(),resources.accounts().get(source.accountId()).custody(),target,
                        new ResourceCustody.Actor(duty.execution().actorId()),Optional.empty(),ARROW,lots.orElseThrow().lotQuantities()),
                    new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(chosen.containerId()),chosen.station(),pocket.orElseThrow(),
                    GuardDuty.Phase.RESUPPLYING.wireTag(),duty.revision()+1);
            return Optional.of(new GuardAmmunitionEvent.Selected(duty.id(),duty.revision(),new RangedSupply(duty.execution(),duty.method(),duty.home(),order,Optional.empty())));
        }
        return Optional.empty();
    }
    public static RangedSupply require(FrontierWorldState state,GuardDuty duty) {
        var supply=state.combat().ranged().supplies().get(duty.id());
        if(duty.phase()!=GuardDuty.Phase.RESUPPLYING || duty.method()!=CombatMethod.CROSSBOW || supply==null || !supply.execution().equals(duty.execution())
                || supply.method()!=duty.method() || !supply.economicOwner().equals(duty.home()) || !supply.order().movementOrder().equals(duty.order())
                || !supply.order().containerEndpoint().containerId().equals(duty.source().containerId()))throw new IllegalArgumentException("ammunition lost exact guard/goal declaration");
        return supply;
    }
    public static boolean sourceAvailable(FrontierWorldState state,GuardDuty duty) {
        var supply=require(state,duty);var order=supply.order();var p=(ActorContainerItemOrder.Portion.Fungible)order.portion();
        return ResourceStorageSelection.permitsWithdrawal(state.inventory(),duty.source().containerId(),duty.home(),ROLES)
                && !ReferenceContainerCustody.blocksCanonicalUse(state,duty.source().containerId())
                && ResourceStorageAccess.at(state,duty.source().containerId()).stations().contains(duty.source().station())
                && FungibleResourceCustodySupport.canReserve(state,new FungibleResourceCustodySupport.LotSelection(p.sourceAccountId(),p.lotQuantities()));
    }
    public static FrontierWorldState apply(FrontierWorldState state,SubjectId subject,GuardAmmunitionEvent event,long tick) {
        var duty=Optional.ofNullable(state.strategicPlans().guards().get(event.duty())).orElseThrow(()->new IllegalArgumentException("guard ammunition duty is absent"));
        if(!subject.equals(duty.id()))throw new IllegalArgumentException("foreign ammunition subject");
        var ranged=state.combat().ranged();var update=FrontierWorldStateUpdate.begin();
        if(event instanceof GuardAmmunitionEvent.Selected selected) {
            state.actorExecutions().requireCurrent(duty.execution());
            if(duty.revision()!=selected.revision() || duty.phase()!=GuardDuty.Phase.ON_DUTY || state.actorMovements().containsKey(duty.execution().actorId())
                    || GuardAuthority.pending(state,duty) || selected.supply().method()!=duty.method() || !selected.supply().execution().equals(duty.execution())
                    || !selected.supply().economicOwner().equals(duty.home()) || selected.supply().pending().isPresent()
                    || selected.supply().order().goalOrdinal()!=GuardDuty.Phase.RESUPPLYING.wireTag() || selected.supply().order().goalRevision()!=duty.revision()+1
                    || selected.supply().order().portion().quantity()>state.bootstrap().ruleset().ranged().loadSize()
                    || Ammunition.available(state,duty.execution().actorId(),duty.home(),ARROW)>0
                    || !Ammunition.freePocket(state,duty.execution().actorId()).equals(Optional.of(selected.supply().order().actorSlot())))throw new IllegalArgumentException("stale ammunition selection");
            var endpoint=state.inventory().containers().get(selected.supply().order().containerEndpoint().containerId()).storage().orElseThrow().endpoint().withStation(selected.supply().order().station());
            var portion=(ActorContainerItemOrder.Portion.Fungible)selected.supply().order().portion();
            var ledger=state.inventory().fungibleResources();
            ActorCarriedResources.requireNewAccountCapacity(ledger,duty.execution().actorId(),portion.destinationAccountId());
            if(!portion.itemKind().equals(ARROW) || !portion.sourceCustody().equals(endpoint.custody())
                    || !ResourceStorageSelection.permitsWithdrawal(state.inventory(),endpoint.containerId(),duty.home(),ROLES)
                    || !ResourceStorageAccess.at(state,endpoint.containerId()).stations().contains(endpoint.station())
                    || !FungibleResourceCustodySupport.canReserve(state,new FungibleResourceCustodySupport.LotSelection(portion.sourceAccountId(),portion.lotQuantities()))
                    || portion.lotQuantities().keySet().stream().map(ledger.lots()::get).anyMatch(lot->!lot.economicOwnerId().equals(duty.home()) || !lot.itemKind().equals(ARROW)))
                throw new IllegalArgumentException("ammunition admission has no actual unclaimed owned stock");
            return state.withChanges(update.strategicPlans(state.strategicPlans().withGuard(duty.resupplying(endpoint)))
                    .combat(state.combat().releasePosition(duty.id()).withdrawn(duty.execution()).withRanged(ranged.supply(selected.supply()))));
        }
        var supply=require(state,duty);long revision=event.revision();
        if(duty.revision()!=revision)throw new IllegalArgumentException("stale ammunition revision");
        if(event instanceof GuardAmmunitionEvent.Aborted aborted) {
            if(!supply.pending().equals(Optional.of(aborted.step())))throw new IllegalArgumentException("ammunition abort lacks its exact unused preimage");
        }else if(event instanceof GuardAmmunitionEvent.Cancelled) {
            if(supply.pending().isPresent() || state.actorMovements().containsKey(duty.execution().actorId()))throw new IllegalArgumentException("cannot cancel an unsettled ammunition visit");
        } else {
            if(state.actorMovements().containsKey(duty.execution().actorId()) ||
                    !duty.order().arrivedAt(state.actorLocations().get(duty.execution().actorId()).supportingSurface()))throw new IllegalArgumentException("ammunition transfer lacks exact arrival");
            if(event instanceof GuardAmmunitionEvent.Prepared prepared) {
                state.actorExecutions().requireCurrent(duty.execution());var lease=Objects.requireNonNull(state.ambientLeases().get(duty.execution().actorId()),"ammunition HOT scope");
                prepared.step().observation().require(state,duty.execution(),lease.revision(),duty.source().station().standingBody());
                if(supply.pending().isPresent() || !sourceAvailable(state,duty) || !ServiceAccessCoordinator.available(state,GuardAuthority.service(duty))
                        || !MaterialSourceSelection.select(state.inventory().fungibleResources(),supply.order()).equals(prepared.step().source())
                        || prepared.step().destinationEpoch()!=prepared.step().observation().actuation().body().physicalEpoch())throw new IllegalArgumentException("invalid ammunition physical preimage");
                return state.withChanges(update.combat(state.combat().withRanged(ranged.supply(supply.prepared(prepared.step())))));
            }
            var loaded=(GuardAmmunitionEvent.Loaded)event;
            if(loaded.step().isEmpty()) {
                if(supply.pending().isPresent() || !ActorExecutionCoordinator.coldAvailable(state,duty.execution().actorId())
                        || ReferenceContainerCustody.hasLiveCustody(state,duty.source().containerId()) || !sourceAvailable(state,duty)
                        || !ServiceAccessCoordinator.available(state,GuardAuthority.service(duty)))throw new IllegalArgumentException("ammunition COLD take conflicts with native custody");
                update=ActorResourceTransferComposition.cold(state,supply.order());
            } else {
                if(!loaded.step().equals(supply.pending()))throw new IllegalArgumentException("foreign ammunition receipt");
                var step=loaded.step().orElseThrow();
                update=ActorBodyAuthority.retainsPhysicalCustody(state,duty.execution().actorId())
                        ?ActorResourceTransferComposition.observed(state,supply.order(),step.sourceEpoch(),step.destinationEpoch(),loaded.source(),loaded.destination())
                        :ActorResourceTransferComposition.retiredObserved(state,supply.order(),step.observation().actuation(),step.sourceEpoch(),step.destinationEpoch(),loaded.source(),loaded.destination());
            }
        }
        return state.withChanges(update.strategicPlans(state.strategicPlans().withGuard(duty.resupplied()))
                .combat(state.combat().withRanged(ranged.withoutSupply(duty.id()))));
    }
    private GuardAmmunition(){}
}
