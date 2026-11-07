package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import java.io.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.CombatWire.id;
/** One exact physical binding grammar, shared by prepared consumable operations. */
final class PhysicalStackBindingCodec {
    static void write(DataOutputStream out,PhysicalStackBinding b)throws IOException{
        id(out,b.id());id(out,b.accountId());FungibleResourceStateCodec.writePhysicalAddress(out,b.address());out.writeLong(b.authorityEpoch());out.writeUTF(b.itemKind());
        ShipmentStateCodec.lots(out,b.lotQuantities());ShipmentStateCodec.lots(out,b.claimQuantities());out.writeUTF(b.playerSaveFence());
    }
    static PhysicalStackBinding read(DataInputStream in)throws IOException{return new PhysicalStackBinding(id(in),id(in),FungibleResourceStateCodec.readPhysicalAddress(in),in.readLong(),in.readUTF(),
            ShipmentStateCodec.lots(in),ShipmentStateCodec.cargoPortions(in),in.readUTF());}
    private PhysicalStackBindingCodec(){}
}
