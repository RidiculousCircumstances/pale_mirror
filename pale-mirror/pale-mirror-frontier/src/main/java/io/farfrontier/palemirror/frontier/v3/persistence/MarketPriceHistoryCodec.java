package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.market.MarketPriceHistory;
import java.io.*;
import java.util.*;

/** Snapshot grammar for bounded public completed-sale observations. */
final class MarketPriceHistoryCodec {
    private MarketPriceHistoryCodec() { }
    static void write(DataOutputStream out, MarketPriceHistory history) throws IOException {
        out.writeInt(history.commodities().size());
        for (var entry : history.commodities().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            out.writeUTF(entry.getKey()); out.writeInt(entry.getValue().size());
            for (var sale : entry.getValue()) {
                out.writeUTF(sale.receipt().value()); out.writeUTF(sale.seller().value()); out.writeUTF(sale.buyer().value());
                out.writeLong(sale.atTick()); out.writeInt(sale.quantity()); out.writeLong(sale.unitPrice().raw());
            }
        }
    }
    static MarketPriceHistory read(DataInputStream in) throws IOException {
        var result = new HashMap<String, List<MarketPriceHistory.Sale>>();
        for (int n = count(in, MarketPriceHistory.MAX_COMMODITIES); n > 0; n--) {
            var item = in.readUTF(); var sales = new ArrayList<MarketPriceHistory.Sale>();
            for (int s = count(in, MarketPriceHistory.MAX_SALES); s > 0; s--)
                sales.add(new MarketPriceHistory.Sale(new SubjectId(in.readUTF()), new SubjectId(in.readUTF()),
                        new SubjectId(in.readUTF()), in.readLong(), in.readInt(), new FixedScalar(in.readLong())));
            if (result.put(item, List.copyOf(sales)) != null) throw new IllegalArgumentException("duplicate market price commodity");
        }
        return new MarketPriceHistory(result);
    }
    private static int count(DataInputStream in, int maximum) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("invalid price history count");
        return count;
    }
}
