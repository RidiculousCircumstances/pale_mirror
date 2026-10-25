package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.*;

/** Nominal owner/recipient/mode are persisted declarations, never recovered from IDs or price shapes. */
final class ConditionalProductionQuoteCodec {
    static void write(DataOutputStream out, List<ConditionalProductionQuote> quotes) throws IOException {
        out.writeInt(quotes.size());
        for (var quote : quotes.stream().sorted(Comparator.comparing((ConditionalProductionQuote q) -> q.recipient().id())
                .thenComparing(ConditionalProductionQuote::itemKind)).toList()) {
            party(out, quote.supplier()); party(out, quote.recipient()); out.writeUTF(quote.itemKind());
            out.writeInt(quote.quantity()); out.writeLong(quote.goodsUnitPrice());
            out.writeLong(quote.resourceFreight()); out.writeLong(quote.cashFreight());
            out.writeByte(quote.fulfillment().mode().wireTag()); party(out, quote.fulfillment().organizer());
            out.writeLong(quote.capacityWindowTicks()); out.writeLong(quote.deliveryTicks());
            out.writeLong(quote.sourceInputRevision()); out.writeLong(quote.sourcePlanRevision());
            out.writeLong(quote.assessedAtTick()); out.writeLong(quote.expiresAtTick());
        }
    }
    static List<ConditionalProductionQuote> read(DataInputStream in) throws IOException {
        var result = new ArrayList<ConditionalProductionQuote>();
        for (int n = EconomicPlanCodec.count(in, SettlementEconomicPlan.MAX_CONDITIONAL_QUOTES); n > 0; n--) {
            var supplier = party(in); var recipient = party(in); var item = in.readUTF();
            int quantity = in.readInt(); long goods = in.readLong(), resource = in.readLong(), cash = in.readLong();
            var fulfillment = new GoodsTradeFulfillment(GoodsTradeFulfillment.Mode.fromWireTag(in.readUnsignedByte()), party(in));
            result.add(new ConditionalProductionQuote(supplier, recipient, item, quantity, goods, resource, cash,
                    fulfillment, in.readLong(), in.readLong(), in.readLong(), in.readLong(), in.readLong(), in.readLong()));
        }
        return List.copyOf(result);
    }
    private static void party(DataOutputStream out, GoodsTradeParty value) throws IOException {
        out.writeUTF(value.id().value()); out.writeByte(value.kind().wireTag());
    }
    private static GoodsTradeParty party(DataInputStream in) throws IOException {
        return new GoodsTradeParty(new SubjectId(in.readUTF()), FrontierWireTags.require(EconomicOwnerKind.class, in.readUnsignedByte()));
    }
    private ConditionalProductionQuoteCodec() { }
}
