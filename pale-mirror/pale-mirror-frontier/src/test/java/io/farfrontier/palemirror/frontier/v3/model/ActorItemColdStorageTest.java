package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorItemColdStorageTest {
    @Test void attachedDestinationMustYieldItsOwnLeaseEvenWhenSourceAndActorAreCold() {
        var initial=FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:attached-cold-wait"),139,
                FrontierRulesets.installed("frontier-v3-clinical-care-r35")));
        var pack=initial.transportFleet().assetsAt(new SubjectId("settlement:1")).getFirst();
        var source=CommunalDiningPlan.initialFoodStorage(initial.bootstrap(),pack.homeSettlementId());
        var account=FungibleResourceCustodySupport.accountAtContainer(initial,source).orElseThrow();
        var lot=account.lotQuantities().keySet().stream().sorted().findFirst().orElseThrow();
        var portion=new ActorContainerItemOrder.Portion.Fungible(account.id(),account.custody(),
                ReferenceContainerCustody.scopeId(pack.containerId()),new ResourceCustody.Container(pack.containerId()),
                Optional.empty(),initial.inventory().fungibleResources().lots().get(lot).itemKind(),Map.of(lot,1));
        var order=new ActorContainerItemOrder(new SubjectId("mission:storage-readiness"),pack.actorId(),ActorContainerItemOrder.Direction.TAKE,
                portion,new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(source),pack.homeStation(),
                new ActorItemSlot.AttachedStorage(pack.containerId()),0,1);
        assertTrue(ActorExecutionCoordinator.coldAvailable(initial,pack.actorId()));
        assertTrue(ActorItemCustody.coldStorageAvailable(initial,order));
        for(var container:List.of(source,pack.containerId())) {
            var record=PhysicalReplicaRecord.expected(container,ReferenceContainerCustody.semanticKind(initial,container),1,
                    ReferenceContainerCustody.canonicalFingerprint(initial,container),ReferenceContainerCustody.provenance(container));
            var scope=ReferenceContainerCustody.scopeId(container);
            var custody=initial.replicaCustody().declare(record).observe(container,1,1,record.fingerprint(),record.provenance(),1)
                    .acquire(new PhysicalCustodyLease(scope,container,ReferenceContainerCustody.PROVIDER_ID,1,1,2,
                            PhysicalCustodyLeaseStatus.ACQUIRED,null));
            for(var status:List.of(PhysicalCustodyLeaseStatus.ACQUIRED,PhysicalCustodyLeaseStatus.CHECKPOINTED)) {
                var lease=custody.custodyByScope().get(scope);
                var leases=new LinkedHashMap<>(custody.custodyByScope());
                leases.put(scope,status==PhysicalCustodyLeaseStatus.ACQUIRED?lease:lease.checkpoint(1,2));
                var state=initial.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(
                        new PhysicalReplicaCustodyState(custody.replicas(),leases,custody.diagnostics())));
                var codec=new FrontierWorldStateCodec();var recovered=codec.decode(codec.encode(state));
                assertTrue(ActorExecutionCoordinator.coldAvailable(recovered,pack.actorId()));
                assertFalse(ActorItemCustody.coldStorageAvailable(recovered,order),container+" "+status);
                assertEquals(initial.inventory(),recovered.inventory());
            }
            var lease=custody.custodyByScope().get(scope);var leases=new LinkedHashMap<>(custody.custodyByScope());
            leases.put(scope,lease.release(1,2));
            var released=initial.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(
                    new PhysicalReplicaCustodyState(custody.replicas(),leases,custody.diagnostics())));
            assertTrue(ActorItemCustody.coldStorageAvailable(released,order));
        }
    }
}
