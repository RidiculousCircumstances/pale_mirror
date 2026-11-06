package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Canonical preconditions are unit fixtures, not native evidence. Tests exercise real owned transitions. */
class RangedGuardTest {
    private record Ready(FrontierWorldState state,GuardDuty duty){}
    private static Ready guard(){
        var s=FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:ranged-test"),139,FrontierRulesets.installed("frontier-v3-ranged-guards-r33")));
        var home=s.bootstrap().settlements().getFirst().id();var staffing=SettlementStaffingComposition.change(s,home,1);
        if(staffing.isPresent())s=SettlementStaffingComposition.reduce(s,home,staffing.orElseThrow());
        var actor=SettlementWorkPolicy.permissions(s,home).workers(ResidentWorkKind.GUARD).stream().sorted().findFirst().orElseThrow();
        var endpoint=ResourceStorageAccess.at(s,FrontierWorldState.depotId(home)).endpoint();
        var id=new SubjectId("guard-duty:ranged-test");var execution=s.actorExecutions().next(actor,ActorActivityKind.GUARD,id);
        var sourceSlot=new InventoryCustody.ContainerSlot(endpoint.containerId(),s.inventory().firstFreeSlot(endpoint.containerId()).orElseThrow());
        var weapon=new ExactItemStack(new SubjectId("item:test-crossbow"),home,"minecraft:crossbow",1,new InventoryCustody.Actor(actor),new ItemCondition(465,0));
        var duty=new GuardDuty(id,home,execution,weapon.id(),endpoint,GuardDuty.Phase.ON_DUTY,1,CombatMethod.CROSSBOW);
        s=ActorExecutionComposition.LIFECYCLE.prepareVacant(s,execution).commit(s,FrontierWorldStateUpdate.begin()
                .inventory(s.inventory().store(weapon.withCustody(sourceSlot)).moveObservedItem(weapon.id(),sourceSlot,weapon.custody())).strategicPlans(s.strategicPlans().withGuard(duty)));
        s=s.withActorBody(actor,endpoint.station().standingBody());
        return new Ready(s,duty);
    }
    private static Ready selected(int count){var r=guard();var s=AutomaticStationProductionTest.supply(r.state(),r.state().inventory().containers().get(r.duty().source().containerId()),GuardAmmunition.ARROW,count,"ranged-stock");
        var selected=GuardAmmunition.select(s,r.duty()).orElseThrow();s=GuardAmmunition.apply(s,r.duty().id(),selected,100);
        var duty=s.strategicPlans().guards().get(r.duty().id());s=s.withActorBody(duty.execution().actorId(),duty.source().station().standingBody());return new Ready(s,duty);}
    @Test void staffingHasSeparateRealWeaponsAndNoGenesisAmmunition(){
        var r=guard();var s=r.state();int total=SettlementWorkPolicy.permissions(s,r.duty().home()).workers(ResidentWorkKind.GUARD).size();
        var demands=new GuardEquipmentDemand().read(s,r.duty().home());assertEquals(total,demands.stream().mapToInt(d->d.concurrentUnits()).sum());
        assertEquals(2,demands.size());assertEquals(0,Ammunition.available(s,r.duty().execution().actorId(),r.duty().home(),GuardAmmunition.ARROW));
        assertTrue(GuardAmmunition.select(s,r.duty()).isEmpty());assertEquals("Crossbow guard",ResidentPresentation.from(s,r.duty().execution().actorId()).role());
        var codec=new FrontierWorldStateCodec();assertEquals(s,codec.decode(codec.encode(s)));
    }
    @Test void coldSupplyMovesOneRealLoadAndSurvivesRecoveryWithoutDuplicatingIt(){
        var r=selected(40);var s=r.state();var codec=new FrontierWorldStateCodec();s=codec.decode(codec.encode(s));
        var supply=GuardAmmunition.require(s,r.duty());assertEquals(32,supply.order().portion().quantity());
        var loaded=new GuardAmmunitionEvent.Loaded(r.duty().id(),r.duty().revision(),Optional.empty(),List.of(),List.of());
        s=GuardAmmunition.apply(s,r.duty().id(),loaded,101);
        assertEquals(32,Ammunition.available(s,r.duty().execution().actorId(),r.duty().home(),GuardAmmunition.ARROW));
        assertEquals(40,s.inventory().fungibleResources().totalQuantity(r.duty().home(),GuardAmmunition.ARROW));
        assertTrue(s.combat().ranged().supplies().isEmpty());assertEquals(GuardDuty.Phase.ON_DUTY,s.strategicPlans().guards().get(r.duty().id()).phase());
        var after=s;assertThrows(IllegalArgumentException.class,()->GuardAmmunition.apply(after,r.duty().id(),loaded,102));
        assertEquals(s,codec.decode(codec.encode(s)));
    }
    @Test void ammunitionWaitsForProjectionAndExactBindingThenCanBeAdmittedAfterRecovery(){
        var r=guard();var container=r.state().inventory().containers().get(r.duty().source().containerId());
        var s=AutomaticStationProductionTest.supply(r.state(),container,GuardAmmunition.ARROW,40,"loading-ammo");
        var selected=GuardAmmunition.select(s,r.duty()).orElseThrow();
        var expected=PhysicalReplicaRecord.expected(container.id(),ReferenceContainerCustody.semanticKind(s,container.id()),1,
                ReferenceContainerCustody.canonicalFingerprint(s,container.id()),ReferenceContainerCustody.provenance(container.id()));
        var scope=ReferenceContainerCustody.scopeId(container.id());
        var custody=s.replicaCustody().declare(expected).prepareProjection(new PhysicalCustodyLease(scope,container.id(),
                ReferenceContainerCustody.PROVIDER_ID,1,1,1,PhysicalCustodyLeaseStatus.PREPARING,null));
        s=s.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
        assertFalse(ResourceStorageSelection.withdrawalSources(s,s.inventory().fungibleResources(),r.duty().home(),GuardAmmunition.ARROW,
                r.duty().source().station(),Set.of(ResourceStorageRole.WAREHOUSE,ResourceStorageRole.LOCAL_BUFFER)).isEmpty(),
                "read-only discovery includes loading stores but cannot grant physical stock authority");
        assertTrue(GuardAmmunition.select(s,r.duty()).isEmpty());
        var preparing=s;assertThrows(IllegalArgumentException.class,()->GuardAmmunition.apply(preparing,r.duty().id(),selected,100));
        s=ReferenceContainerCustodyFixtures.observedAndHeld(r.state().withInventory(s.inventory()),container.id());
        assertTrue(GuardAmmunition.select(s,r.duty()).isEmpty(),"acquired custody still awaits exact material bindings");
        var account=FungibleResourceCustodySupport.accountAtContainer(s,container.id()).orElseThrow();
        var ledger=s.inventory().fungibleResources();
        var occupied=new HashSet<Integer>();
        for(var item:s.inventory().items().values())if(item.custody() instanceof InventoryCustody.ContainerSlot address
                && address.containerId().equals(container.id()))occupied.add(address.slot());
        var stacks=new ArrayList<FungiblePhysicalObservation.Stack>();int slot=0;
        for(var entry:account.lotQuantities().entrySet()) {
            var kind=ledger.lots().get(entry.getKey()).itemKind();int remaining=entry.getValue();
            while(remaining>0){while(occupied.contains(slot))slot++;int count=Math.min(64,remaining);stacks.add(new FungiblePhysicalObservation.Stack(
                    new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(container.id(),slot++)),kind,count));remaining-=count;}
        }
        ledger=ledger.rebind(account.id(),1,FungiblePhysicalObservation.bind(ledger,account.id(),1,stacks));
        s=s.withInventory(s.inventory().withFungibleResources(ledger));
        var codec=new FrontierWorldStateCodec();s=codec.decode(codec.encode(s));
        var admitted=GuardAmmunition.select(s,r.duty()).orElseThrow();s=GuardAmmunition.apply(s,r.duty().id(),admitted,101);
        assertEquals(GuardDuty.Phase.RESUPPLYING,s.strategicPlans().guards().get(r.duty().id()).phase());
        assertEquals(32,GuardAmmunition.require(s,s.strategicPlans().guards().get(r.duty().id())).order().portion().quantity());
        assertEquals(40,s.inventory().fungibleResources().totalQuantity(r.duty().home(),GuardAmmunition.ARROW));
    }
    @Test void playerTakingStockInvalidatesTheVisitWithoutInventingArrows(){
        var r=selected(8);var s=r.state();var supply=GuardAmmunition.require(s,r.duty());var p=(ActorContainerItemOrder.Portion.Fungible)supply.order().portion();
        var ledger=s.inventory().fungibleResources().destroy(p.sourceAccountId(),p.lotQuantities(),Map.of());s=s.withInventory(s.inventory().withFungibleResources(ledger));
        assertFalse(GuardAmmunition.sourceAvailable(s,r.duty()));var before=s;
        assertThrows(IllegalArgumentException.class,()->GuardAmmunition.apply(before,r.duty().id(),new GuardAmmunitionEvent.Loaded(r.duty().id(),r.duty().revision(),Optional.empty(),List.of(),List.of()),101));
        s=GuardAmmunition.apply(s,r.duty().id(),new GuardAmmunitionEvent.Cancelled(r.duty().id(),r.duty().revision()),101);
        assertEquals(0,Ammunition.available(s,r.duty().execution().actorId(),r.duty().home(),GuardAmmunition.ARROW));assertTrue(s.combat().ranged().supplies().isEmpty());
    }
    @Test void selectedSupplyCannotChangeWeaponRoleOrEconomicOwner(){
        var r=selected(8);var supply=GuardAmmunition.require(r.state(),r.duty());
        assertThrows(IllegalArgumentException.class,()->new RangedSupply(supply.execution(),CombatMethod.MELEE,supply.economicOwner(),supply.order(),Optional.empty()));
        assertThrows(NullPointerException.class,()->new GuardDuty(r.duty().id(),r.duty().home(),r.duty().execution(),r.duty().weapon(),r.duty().source(),GuardDuty.Phase.ON_DUTY,1,null));
        assertThrows(IllegalArgumentException.class,()->CombatMethod.require(0));
        assertThrows(IllegalArgumentException.class,()->CombatPositioning.register(List.of()));
    }
    @Test void bowstringAndArrowAssemblyParticipateInTheSameMaterialDependencyGraph(){
        var r=guard();var s=r.state();var targets=SettlementReservePlanning.read(s,r.duty().home(),100);
        assertTrue(targets.get(GuardAmmunition.ARROW).quantity()>0,"unfilled quivers create a finite ammunition obligation");
        var flows=SettlementReservePlanning.retainedBasis(s,r.duty().home()).flows();
        var assembly=flows.stream().filter(f->f.product().equals(GuardAmmunition.ARROW)).findFirst().orElseThrow();
        assertEquals(Set.of("pale_mirror:iron_arrowhead","minecraft:stick","pale_mirror:cloth"),assembly.inputsPerOutput().keySet());
        var heads=flows.stream().filter(f->f.product().equals("pale_mirror:iron_arrowhead")).findFirst().orElseThrow();
        assertTrue(heads.inputsPerOutput().containsKey("minecraft:iron_ingot"));
        assertTrue(heads.inputsPerOutput().containsKey("minecraft:coal"),"attended forging includes real combustion");
        var catalog=s.bootstrap().ruleset().production();var arrow=catalog.require(new ProductionRecipe.Id("pale_mirror:cloth_arrows"));
        assertEquals(Map.of("pale_mirror:iron_arrowhead",4,"minecraft:stick",4,"pale_mirror:cloth",1),ProductionRecipeInputs.select(arrow,k->0L));
        assertEquals(4,arrow.product().quantity());assertFalse(arrow.requiresCombustion());
    }
    @Test void permanentPreparationChangesReloadAndAccuracyWithoutChangingCosts(){
        var rules=RangedRules.initial();
        var novice=new CombatProfile(new CombatAttributes(50,10,50,10,50),Map.of(CombatSkillId.CROSSBOW,10,CombatSkillId.EVASION,0,CombatSkillId.WEAPON_DEFENCE,0));
        var veteran=new CombatProfile(new CombatAttributes(50,90,50,90,50),Map.of(CombatSkillId.CROSSBOW,90,CombatSkillId.EVASION,0,CombatSkillId.WEAPON_DEFENCE,0));
        assertTrue(RangedMechanics.reloadTicks(veteran,rules)<RangedMechanics.reloadTicks(novice,rules));
        assertTrue(RangedMechanics.aimTicks(veteran,rules)<RangedMechanics.aimTicks(novice,rules));
        assertTrue(RangedMechanics.inaccuracy(veteran,rules)<RangedMechanics.inaccuracy(novice,rules));
    }
    private record Aimed(FrontierWorldState state,GuardDuty duty,RangedShot shot){}
    private static Aimed aimed(){
        var r=guard();var s=r.state();var duty=r.duty();var actor=duty.execution().actorId();
        s=ActorBodyAuthority.demand(s,actor);var body=ActorBodyAuthority.current(s,actor);s=ActorBodyAuthority.running(s,body);
        var actuation=new ActorActuationId(body,duty.execution());
        var target=new CombatTarget(CombatTarget.Kind.PLAYER,new UUID(42,19),"pale_mirror:frontier_graybox");
        var origin=s.actorLocations().get(actor).supportingSurface();var point=origin.offset(10,0,0);
        s=SecurityTransitions.apply(s,duty.home(),new SecurityEvent.Incident(duty.home(),body,target,origin.support(),Optional.empty(),1),10000);
        s=SecurityTransitions.apply(s,duty.home(),new SecurityEvent.Seen(actuation,new SecurityState.Contact(duty.home(),target,point.support(),10001,1),origin.support()),10001);
        s=new SecurityResponsesAllocated(duty.home(),SecurityDispatch.plan(s,duty.home(),10001)).apply(s,duty.home(),10001);
        var lot=new SubjectId("lot:ranged-pocket");var account=new SubjectId("custody:ranged-pocket");var pocket=new ActorItemSlot.Pocket(3);
        var ledger=s.inventory().fungibleResources().issue(new ResourceLot(lot,duty.home(),GuardAmmunition.ARROW,2,"fixture:arrows",List.of()),
                new CustodyAccount(account,new ResourceCustody.Actor(actor),Map.of(lot,2),Map.of(),Optional.of(pocket)));
        var stack=new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorPocket(actor,ActorBodyId.entityId(s.bootstrap().worldId(),actor),3),GuardAmmunition.ARROW,2);
        ledger=ledger.rebind(account,body.physicalEpoch(),FungiblePhysicalObservation.bind(ledger,account,body.physicalEpoch(),List.of(stack)));
        s=s.withInventory(s.inventory().withFungibleResources(ledger));
        var order=CombatComposition.CAPABILITIES.current(s,duty.execution(),10001).orElseThrow();
        s=RangedTransitions.apply(s,duty.id(),new RangedEvent.Observed(new RangedStance(actuation,target,order.targetPosition(),true,true,10001)),10001,CombatComposition.CAPABILITIES);
        var binding=Ammunition.spendable(s,actor,duty.home(),GuardAmmunition.ARROW).orElseThrow();
        var shot=new RangedShot(actuation,CombatMethod.CROSSBOW,duty.revision(),duty.weapon(),s.inventory().items().get(duty.weapon()).condition(),target,7,account,lot,GuardAmmunition.ARROW,binding,10002,10001,order.profile());
        return new Aimed(s,duty,shot);
    }
    @Test void rangedEmissionDebitsOneArrowAndWeaponWearOnceAcrossRecovery(){
        var r=aimed();var s=RangedTransitions.apply(r.state(),r.duty().id(),new RangedEvent.Prepared(r.shot()),10001,CombatComposition.CAPABILITIES);
        assertTrue(GuardAuthority.pendingEffect(s,r.duty()));var pending=s;
        assertThrows(IllegalArgumentException.class,()->ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.GUARD).validateAmbientRelease(pending,r.duty().execution()));
        var codec=new FrontierWorldStateCodec();s=codec.decode(codec.encode(s));assertEquals(r.shot(),s.combat().ranged().pending().get(r.duty().id()));
        var remaining=List.of(new FungiblePhysicalObservation.Stack(r.shot().binding().address(),GuardAmmunition.ARROW,1));
        var receipt=new RangedEvent.Completed(r.duty().id(),r.shot().attemptOrdinal(),true,remaining);
        s=RangedTransitions.apply(s,r.duty().id(),receipt,10040,CombatComposition.CAPABILITIES);
        assertEquals(1,Ammunition.available(s,r.duty().execution().actorId(),r.duty().home(),GuardAmmunition.ARROW));
        assertEquals(1,s.inventory().items().get(r.duty().weapon()).condition().damage());assertFalse(GuardAuthority.pendingEffect(s,r.duty()));
        var after=s;assertThrows(IllegalArgumentException.class,()->RangedTransitions.apply(after,r.duty().id(),receipt,10041,CombatComposition.CAPABILITIES));
        assertEquals(s,codec.decode(codec.encode(s)));
    }
    @Test void cancelledShotCannotConsumeOrDamageAndForeignReceiptsFailBeforeMutation(){
        var r=aimed();var s=RangedTransitions.apply(r.state(),r.duty().id(),new RangedEvent.Prepared(r.shot()),10001,CombatComposition.CAPABILITIES);var before=s;
        assertThrows(IllegalArgumentException.class,()->RangedTransitions.apply(before,r.duty().id(),new RangedEvent.Completed(r.duty().id(),r.shot().attemptOrdinal()+1,true,List.of()),10040,CombatComposition.CAPABILITIES));
        assertThrows(IllegalArgumentException.class,()->RangedTransitions.apply(before,r.duty().id(),new RangedEvent.Completed(r.duty().id(),r.shot().attemptOrdinal(),true,List.of()),10040,CombatComposition.CAPABILITIES));
        s=RangedTransitions.apply(s,r.duty().id(),new RangedEvent.Completed(r.duty().id(),r.shot().attemptOrdinal(),false,List.of()),10040,CombatComposition.CAPABILITIES);
        assertEquals(2,Ammunition.available(s,r.duty().execution().actorId(),r.duty().home(),GuardAmmunition.ARROW));assertEquals(0,s.inventory().items().get(r.duty().weapon()).condition().damage());
    }

}
