package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.io.*;
import java.util.*;

final class GoodsSpotPurchaseCodec {
    private GoodsSpotPurchaseCodec() { }
    static void write(DataOutputStream out, GoodsSpotPurchase purchase) throws IOException {
        var p = purchase.payment(); UnitGroupStateCodec.id(out, p.id()); UnitGroupStateCodec.id(out, p.payerId()); UnitGroupStateCodec.id(out, p.payeeId());
        UnitGroupStateCodec.id(out, p.reasonId()); out.writeLong(p.amount().raw()); UnitGroupStateCodec.id(out, p.budgetId().orElseThrow());
        var t = purchase.title(); UnitGroupStateCodec.id(out, t.accountId()); UnitGroupStateCodec.id(out, t.claimId());
        UnitGroupStateCodec.id(out, t.sourceOwnerId()); UnitGroupStateCodec.id(out, t.destinationOwnerId());
        out.writeInt(t.portions().size()); for (var e : new TreeMap<>(t.portions()).entrySet()) { UnitGroupStateCodec.id(out, e.getKey()); out.writeInt(e.getValue()); }
        out.writeInt(t.splitLotIds().size()); for (var e : new TreeMap<>(t.splitLotIds()).entrySet()) { UnitGroupStateCodec.id(out, e.getKey()); UnitGroupStateCodec.id(out, e.getValue()); }
    }
    static GoodsSpotPurchase read(DataInputStream in) throws IOException {
        var p = new FinancialReservation(UnitGroupStateCodec.id(in), UnitGroupStateCodec.id(in), UnitGroupStateCodec.id(in), UnitGroupStateCodec.id(in),
                new FixedScalar(in.readLong()), Optional.of(UnitGroupStateCodec.id(in)));
        var account = UnitGroupStateCodec.id(in); var claim = UnitGroupStateCodec.id(in); var seller = UnitGroupStateCodec.id(in); var buyer = UnitGroupStateCodec.id(in);
        var lots = new LinkedHashMap<SubjectId, Integer>();
        for (int n = UnitGroupStateCodec.count(in, 64); n > 0; n--) if (lots.putIfAbsent(UnitGroupStateCodec.id(in), in.readInt()) != null) throw new IllegalArgumentException("duplicate spot purchase portion");
        var splits = new LinkedHashMap<SubjectId, SubjectId>();
        for (int n = UnitGroupStateCodec.count(in, 64); n > 0; n--) if (splits.putIfAbsent(UnitGroupStateCodec.id(in), UnitGroupStateCodec.id(in)) != null) throw new IllegalArgumentException("duplicate spot purchase split");
        return new GoodsSpotPurchase(p, new ResourceTitleTransfer(account, claim, seller, buyer, lots, splits));
    }
}
