package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ColdResourceConsumption;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceCustodySupport;
import java.io.*;
import java.util.*;

/** One current-format grammar for both snapshot and WAL; tags are explicit and never inferred. */
final class ColdResourceConsumptionCodec {
    private ColdResourceConsumptionCodec() { }
    static void write(DataOutputStream out, ColdResourceConsumption selected) throws IOException {
        out.writeByte(switch (selected) { case ColdResourceConsumption.Exact ignored -> 1; case ColdResourceConsumption.Fungible ignored -> 2; });
        FrontierWorldStateCodec.writeString(out, selected.ownerId().value());
        FrontierWorldStateCodec.writeString(out, selected.containerId().value());
        FrontierWorldStateCodec.writeString(out, selected.itemKind());
        switch (selected) {
            case ColdResourceConsumption.Exact exact -> {
                FrontierWorldStateCodec.writeString(out, exact.itemId().value()); out.writeByte(exact.quantity());
            }
            case ColdResourceConsumption.Fungible fungible -> {
                FrontierWorldStateCodec.writeString(out, fungible.lots().accountId().value());
                FrontierWorldStateCodec.writeCount(out, fungible.lots().lotQuantities().size());
                for (var entry : fungible.lots().lotQuantities().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                    FrontierWorldStateCodec.writeString(out, entry.getKey().value()); out.writeByte(entry.getValue());
                }
            }
        }
    }
    static ColdResourceConsumption read(DataInputStream in) throws IOException {
        int tag = in.readUnsignedByte();
        if (tag != 1 && tag != 2) throw new IllegalArgumentException("unknown COLD resource consumption tag");
        var owner = new SubjectId(FrontierWorldStateCodec.readString(in));
        var container = new SubjectId(FrontierWorldStateCodec.readString(in));
        var itemKind = FrontierWorldStateCodec.readString(in);
        if (tag == 1) return new ColdResourceConsumption.Exact(owner, container, itemKind,
                new SubjectId(FrontierWorldStateCodec.readString(in)), in.readUnsignedByte());
        var account = new SubjectId(FrontierWorldStateCodec.readString(in));
        int count = FrontierWorldStateCodec.readCount(in);
        if (count < 1 || count > 64) throw new IllegalArgumentException("invalid COLD resource portion count");
        Map<SubjectId, Integer> portions = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            if (portions.put(new SubjectId(FrontierWorldStateCodec.readString(in)), in.readUnsignedByte()) != null)
                throw new IllegalArgumentException("duplicate COLD resource portion");
        }
        return new ColdResourceConsumption.Fungible(owner, container, itemKind,
                new FungibleResourceCustodySupport.LotSelection(account, portions));
    }
}
