package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.market.MarketPricePolicy;
import java.io.*;
import java.util.*;

final class ParticipantPriceCodec {
    private ParticipantPriceCodec() { }
    static void write(DataOutputStream out, Map<String, MarketPricePolicy.Quote> prices) throws IOException {
        out.writeInt(prices.size());
        for (var entry : prices.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            out.writeUTF(entry.getKey()); var quote = entry.getValue();
            out.writeLong(quote.ask().raw()); out.writeLong(quote.bid().raw()); out.writeLong(quote.reference().raw());
            out.writeLong(quote.reviewedAt()); out.writeLong(quote.surplus()); out.writeBoolean(quote.lastSale().isPresent());
            if (quote.lastSale().isPresent()) out.writeUTF(quote.lastSale().orElseThrow().value());
            out.writeLong(quote.unsoldOfferedTicks());
        }
    }
    static Map<String, MarketPricePolicy.Quote> read(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > 16) throw new IllegalArgumentException("unbounded saved participant prices");
        var prices = new HashMap<String, MarketPricePolicy.Quote>();
        for (int n = count; n > 0; n--) {
            String item = in.readUTF();
            var quote = new MarketPricePolicy.Quote(new FixedScalar(in.readLong()), new FixedScalar(in.readLong()),
                    new FixedScalar(in.readLong()), in.readLong(), in.readLong(),
                    in.readBoolean() ? Optional.of(new SubjectId(in.readUTF())) : Optional.empty(), in.readLong());
            if (prices.put(item, quote) != null) throw new IllegalArgumentException("duplicate participant price");
        }
        return Map.copyOf(prices);
    }
}
