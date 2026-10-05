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
