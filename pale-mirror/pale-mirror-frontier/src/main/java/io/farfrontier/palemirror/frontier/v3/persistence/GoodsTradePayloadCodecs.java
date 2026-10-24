package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

final class GoodsTradePayloadCodecs {
    private GoodsTradePayloadCodecs() { }
    static PayloadCodecs create() {
        return new PayloadCodecs(List.of(
                codec("frontier.settlement_economic_plan_published", (out, payload) ->
                        SettlementEconomicPlanCodec.write(out, ((SettlementEconomicPlanPublished) payload).plan()),
                        in -> new SettlementEconomicPlanPublished(SettlementEconomicPlanCodec.read(in))),
                codec("frontier.goods_trade_collected", (out, payload) -> {
                    var value = (GoodsTradeCollected) payload;
                    out.writeUTF(value.shipmentId().value()); out.writeLong(value.expectedShipmentRevision());
                    GoodsTradeStateCodec.acceptance(out, value.receipt());
                }, in -> new GoodsTradeCollected(new io.farfrontier.palemirror.frontier.v3.api.SubjectId(in.readUTF()),
                        in.readLong(), GoodsTradeStateCodec.acceptance(in))),
                codec("frontier.goods_order_withdrawn", (out, payload) -> {
                    var value = (GoodsTradeOrderWithdrawn) payload;
                    out.writeUTF(value.orderId().value()); out.writeInt(value.expectedCommittedQuantity());
                }, in -> new GoodsTradeOrderWithdrawn(new io.farfrontier.palemirror.frontier.v3.api.SubjectId(in.readUTF()), in.readInt())),
                codec("frontier.goods_participant_prices_reviewed", (out, payload) -> {
                    var value = (GoodsParticipantPricesReviewed) payload;
                    out.writeLong(value.expectedRevision()); out.writeLong(value.atTick()); ParticipantPriceCodec.write(out, value.prices());
                }, in -> new GoodsParticipantPricesReviewed(in.readLong(), in.readLong(), ParticipantPriceCodec.read(in))),
                codec("frontier.goods_participant_reviewed", (out, payload) -> {
                    var value = (GoodsParticipantReviewed) payload;
                    out.writeLong(value.expectedRevision()); out.writeUTF(value.decision()); out.writeLong(value.atTick());
                }, in -> new GoodsParticipantReviewed(in.readLong(), in.readUTF(), in.readLong())),
                codec("frontier.goods_order_placed", (out, payload) -> GoodsTradeStateCodec.writeOrder(out, ((GoodsTradeOrderPlaced) payload).order()),
                        in -> new GoodsTradeOrderPlaced(GoodsTradeStateCodec.readOrder(in))),
                codec("frontier.goods_trade_reserved", (out, payload) -> {
                    var reserved = (GoodsTradeReserved) payload; GoodsTradeStateCodec.writeContract(out, reserved.contract());
                    out.writeInt(reserved.allocations().size());
                    for (var allocation : reserved.allocations()) GoodsTradeStateCodec.allocation(out, allocation);
                }, in -> {
                    var contract = GoodsTradeStateCodec.readContract(in); int n = in.readInt();
                    if (n < 1 || n > 64) throw new IllegalArgumentException("invalid goods allocation payload count");
                    var allocations = new ArrayList<GoodsTradeStockAllocation>();
                    for (int i = 0; i < n; i++) allocations.add(GoodsTradeStateCodec.allocation(in));
                    return new GoodsTradeReserved(contract, allocations);
                }),
                codec("frontier.goods_trade_accepted", (out, payload) -> GoodsTradeStateCodec.acceptance(out, ((GoodsTradeAccepted) payload).receipt()),
                        in -> new GoodsTradeAccepted(GoodsTradeStateCodec.acceptance(in))),
                codec("frontier.goods_trade_cancelled", (out, payload) -> GoodsTradeStateCodec.disposition(out, ((GoodsTradeCancelled) payload).disposition()),
                        in -> new GoodsTradeCancelled(GoodsTradeStateCodec.disposition(in))),
                codec("frontier.goods_trade_claim_partitioned", (out, payload) -> {
                    var partitioned = (GoodsTradeClaimPartitioned) payload; out.writeUTF(partitioned.contractId().value());
                    GoodsTradeStateCodec.partition(out, partitioned.partition());
                }, in -> new GoodsTradeClaimPartitioned(new io.farfrontier.palemirror.frontier.v3.api.SubjectId(in.readUTF()),
                        GoodsTradeStateCodec.partition(in))),
                codec("frontier.goods_trade_retired", (out, payload) -> GoodsTradeStateCodec.retirement(out, (GoodsTradeRetired) payload),
                        GoodsTradeStateCodec::retirement)));
    }
    private interface Writer { void write(DataOutputStream out, FrontierPayload payload) throws IOException; }
    private interface Reader { FrontierPayload read(DataInputStream in) throws IOException; }
    private static PayloadCodec codec(String type, Writer writer, Reader reader) {
        return new PayloadCodec() {
            @Override public String type() { return type; }
            @Override public byte[] encode(FrontierPayload payload) {
                try {
                    var bytes = new ByteArrayOutputStream();
                    try (var out = new DataOutputStream(bytes)) { writer.write(out, payload); }
                    return bytes.toByteArray();
                } catch (IOException impossible) { throw new IllegalStateException("goods in-memory encode failed", impossible); }
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                    FrontierPayload payload = reader.read(in);
                    if (in.available() != 0) throw new IllegalArgumentException("trailing goods payload bytes");
                    return payload;
                } catch (IOException truncated) { throw new IllegalArgumentException("truncated goods payload", truncated); }
            }
        };
    }
}
