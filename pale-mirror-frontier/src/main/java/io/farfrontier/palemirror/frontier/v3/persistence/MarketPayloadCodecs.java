package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** WAL codecs for the market's typed facts; snapshot retention remains in {@link CompanyRegistry}. */
final class MarketPayloadCodecs {
    private MarketPayloadCodecs() { }
    static PayloadCodec opened() { return new PayloadCodec() {
        @Override public String type() { return "frontier.market_demand_opened"; }
        @Override public byte[] encode(FrontierPayload payload) { return write(output -> writeDemand(output, ((MarketDemandOpened) payload).demand())); }
        @Override public FrontierPayload decode(byte[] bytes) { return new MarketDemandOpened(read(bytes, MarketPayloadCodecs::readDemand)); }
    }; }
    static PayloadCodec quote() { return new PayloadCodec() {
        @Override public String type() { return "frontier.market_quote_published"; }
        @Override public byte[] encode(FrontierPayload payload) { return write(output -> writeQuote(output, ((MarketQuotePublished) payload).quote())); }
        @Override public FrontierPayload decode(byte[] bytes) { return new MarketQuotePublished(read(bytes, MarketPayloadCodecs::readQuote)); }
    }; }
    static PayloadCodec accepted() { return new PayloadCodec() {
        @Override public String type() { return "frontier.market_work_order_accepted"; }
        @Override public byte[] encode(FrontierPayload payload) { return write(output -> writeOrder(output, ((MarketWorkOrderAccepted) payload).order())); }
        @Override public FrontierPayload decode(byte[] bytes) { return new MarketWorkOrderAccepted(read(bytes, MarketPayloadCodecs::readOrder)); }
    }; }
    static PayloadCodec workOrderCancelled() { return new PayloadCodec() {
        @Override public String type() { return "frontier.market_work_order_cancelled"; }
        @Override public byte[] encode(FrontierPayload payload) { return write(output -> {
            MarketWorkOrderCancelled cancelled = (MarketWorkOrderCancelled) payload;
            subject(output, cancelled.orderId()); subject(output, cancelled.jobId()); output.writeByte(cancelled.reason().wireTag());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return read(bytes, input -> {
            SubjectId order = subject(input), job = subject(input); int reason = input.readUnsignedByte();
            if (reason >= ProductionBlockReason.values().length) throw new IllegalArgumentException("invalid market work order cancellation reason");
            return new MarketWorkOrderCancelled(order, job, FrontierWireTags.require(ProductionBlockReason.class, reason));
        }); }
    }; }
    static PayloadCodec expired() { return new PayloadCodec() {
        @Override public String type() { return "frontier.market_demand_expired"; }
        @Override public byte[] encode(FrontierPayload payload) { return write(output -> subject(output, ((MarketDemandExpired) payload).demandId())); }
        @Override public FrontierPayload decode(byte[] bytes) { return new MarketDemandExpired(read(bytes, MarketPayloadCodecs::subject)); }
    }; }
    static PayloadCodec cancelled() { return new PayloadCodec() {
        @Override public String type() { return "frontier.market_demand_cancelled"; }
        @Override public byte[] encode(FrontierPayload payload) { return write(output -> {
            MarketDemandCancelled cancelled = (MarketDemandCancelled) payload;
            subject(output, cancelled.demandId()); output.writeByte(cancelled.reason().wireTag());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return read(bytes, input -> {
            SubjectId demand = subject(input); int reason = input.readUnsignedByte();
            if (reason >= MarketDemandCancellationReason.values().length) throw new IllegalArgumentException("invalid market demand cancellation reason");
            return new MarketDemandCancelled(demand, FrontierWireTags.require(MarketDemandCancellationReason.class, reason));
        }); }
    }; }

    private static void writeDemand(DataOutputStream output, MarketDemand demand) throws IOException {
        subject(output, demand.id()); subject(output, demand.buyerId()); subject(output, demand.reasonId()); output.writeUTF(demand.itemKind());
        output.writeInt(demand.itemCount()); output.writeLong(demand.maximumTotalPrice().raw()); output.writeLong(demand.openedAtTick());
        output.writeLong(demand.expiresAtTick()); output.writeByte(demand.status().wireTag());
    }
    private static MarketDemand readDemand(DataInputStream input) throws IOException {
        SubjectId id = subject(input), buyer = subject(input), reason = subject(input); String item = input.readUTF(); int count = input.readInt();
        FixedScalar maximum = new FixedScalar(input.readLong()); long opened = input.readLong(), expiry = input.readLong(); int status = input.readUnsignedByte();
        if (status >= MarketDemandStatus.values().length) throw new IllegalArgumentException("invalid market demand status");
        return new MarketDemand(id, buyer, reason, item, count, maximum, opened, expiry, FrontierWireTags.require(MarketDemandStatus.class, status));
    }
    private static void writeQuote(DataOutputStream output, CompanyQuote quote) throws IOException {
        subject(output, quote.id()); subject(output, quote.demandId()); subject(output, quote.sellerId()); output.writeInt(quote.itemCount());
        output.writeLong(quote.totalPrice().raw()); output.writeLong(quote.quotedAtTick()); output.writeLong(quote.expiresAtTick());
    }
    private static CompanyQuote readQuote(DataInputStream input) throws IOException {
        return new CompanyQuote(subject(input), subject(input), subject(input), input.readInt(), new FixedScalar(input.readLong()), input.readLong(), input.readLong());
    }
    private static void writeOrder(DataOutputStream output, MarketWorkOrder order) throws IOException {
        subject(output, order.id()); subject(output, order.demandId()); subject(output, order.quoteId()); subject(output, order.sellerId());
        subject(output, order.taskId()); subject(output, order.jobId()); subject(output, order.reservationId()); output.writeLong(order.acceptedTotalPrice().raw()); output.writeByte(order.status().wireTag());
    }
    private static MarketWorkOrder readOrder(DataInputStream input) throws IOException {
        SubjectId id = subject(input), demand = subject(input), quote = subject(input), seller = subject(input), task = subject(input), job = subject(input), reservation = subject(input);
        FixedScalar total = new FixedScalar(input.readLong()); int status = input.readUnsignedByte();
        if (status >= MarketWorkOrderStatus.values().length) throw new IllegalArgumentException("invalid market work order status");
        return new MarketWorkOrder(id, demand, quote, seller, task, job, reservation, total, FrontierWireTags.require(MarketWorkOrderStatus.class, status));
    }
    private static void subject(DataOutputStream output, SubjectId subject) throws IOException { output.writeUTF(subject.value()); }
    private static SubjectId subject(DataInputStream input) throws IOException { return new SubjectId(input.readUTF()); }
    private static byte[] write(Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); try (DataOutputStream output = new DataOutputStream(bytes)) { writer.write(output); }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException("in-memory market payload encoding failed", impossible); }
    }
    private static <T> T read(byte[] bytes, Reader<T> reader) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            T value = reader.read(input); if (input.available() != 0) throw new IllegalArgumentException("trailing market payload bytes"); return value;
        } catch (IOException error) { throw new IllegalArgumentException("truncated market payload", error); }
    }
    @FunctionalInterface private interface Writer { void write(DataOutputStream output) throws IOException; }
    @FunctionalInterface private interface Reader<T> { T read(DataInputStream input) throws IOException; }
}
