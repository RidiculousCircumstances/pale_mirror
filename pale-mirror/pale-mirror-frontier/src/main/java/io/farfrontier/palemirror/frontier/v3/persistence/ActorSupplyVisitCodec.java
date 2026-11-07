package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.CombatWire.*;

/** Source-neutral carried fungible supply, shared by activity adapters. */
final class ActorSupplyVisitCodec {
    static void write(DataOutputStream out,ActorSupplyVisit visit)throws IOException{
        ActorExecutionStateCodec.writeId(out,visit.execution());id(out,visit.economicOwner());var order=visit.order();
        id(out,order.ownerId());id(out,order.actorId());var p=(ActorContainerItemOrder.Portion.Fungible)order.portion();
        id(out,p.sourceAccountId());FungibleResourceStateCodec.writeResourceCustody(out,p.sourceCustody());
        id(out,p.destinationAccountId());FungibleResourceStateCodec.writeResourceCustody(out,p.destinationCustody());
        if(p.claimId().isPresent()||p.delegation().isPresent())throw new IllegalArgumentException("personal supply cannot borrow another owner's claim");
        out.writeUTF(p.itemKind());ShipmentStateCodec.lots(out,p.lotQuantities());
        switch(order){
            case ActorContainerItemOrder c->{out.writeByte(1);id(out,c.containerEndpoint().containerId());}
            case ActorGroundItemOrder g->{out.writeByte(2);uuid(out,g.entityId());out.writeLong(g.sourceEpoch());}
        }
        FrontierWorldStateCodec.writePosition(out,order.station().support());ActorItemSlotCodec.write(out,order.actorSlot());out.writeLong(order.goalOrdinal());out.writeLong(order.goalRevision());
        out.writeBoolean(visit.pending().isPresent());if(visit.pending().isPresent())ActorItemTransferStepCodec.write(out,visit.pending().orElseThrow());
    }
    static ActorSupplyVisit read(DataInputStream in)throws IOException{
        var execution=ActorExecutionStateCodec.readId(in);var home=id(in);var owner=id(in);var actor=id(in);var from=id(in);var fc=FungibleResourceStateCodec.readResourceCustody(in);
        var to=id(in);var tc=FungibleResourceStateCodec.readResourceCustody(in);var kind=in.readUTF();var lots=ShipmentStateCodec.lots(in);
        var portion=new ActorContainerItemOrder.Portion.Fungible(from,fc,to,tc,Optional.empty(),kind,lots);
        int tag=in.readUnsignedByte();var container=tag==1?id(in):null;var entity=tag==2?uuid(in):null;long epoch=tag==2?in.readLong():0;
        var station=new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in));var slot=ActorItemSlotCodec.read(in);long ordinal=in.readLong(),revision=in.readLong();
        ActorResourceItemOrder order=switch(tag){
            case 1->new ActorContainerItemOrder(owner,actor,ActorContainerItemOrder.Direction.TAKE,portion,new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(container),station,slot,ordinal,revision);
            case 2->new ActorGroundItemOrder(owner,actor,portion,entity,epoch,station,slot,ordinal,revision);
            default->throw new IllegalArgumentException("unknown personal supply endpoint");
        };
        return new ActorSupplyVisit(execution,home,order,in.readBoolean()?Optional.of(ActorItemTransferStepCodec.read(in)):Optional.empty());
    }
    private ActorSupplyVisitCodec(){}
}
