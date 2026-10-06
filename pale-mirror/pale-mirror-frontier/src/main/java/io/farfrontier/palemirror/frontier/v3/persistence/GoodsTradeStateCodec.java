package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.*;

/** Current-schema goods grammar shared by snapshot and WAL. No old-world inference. */
final class GoodsTradeStateCodec {
    private GoodsTradeStateCodec() { }
    static void write(DataOutputStream out, GoodsTradeState state) throws IOException {
        out.writeInt(state.orders().size());
        for (var entry : state.orders().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) writeOrder(out, entry.getValue());
        out.writeInt(state.contracts().size());
        for (var entry : state.contracts().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) writeContract(out, entry.getValue());
        GoodsParticipantStateCodec.write(out, state.participants());
    }
    static GoodsTradeState read(DataInputStream in) throws IOException {
        Map<SubjectId, GoodsTradeOrder> orders = new HashMap<>();
        for (int n = count(in, GoodsTradeState.MAX_ORDERS); n > 0; n--) {
            var order = readOrder(in); put(orders, order.id(), order);
        }
        Map<SubjectId, GoodsTradeContract> contracts = new HashMap<>();
        for (int n = count(in, GoodsTradeState.MAX_CONTRACTS); n > 0; n--) {
            var contract = readContract(in); put(contracts, contract.id(), contract);
        }
        return new GoodsTradeState(orders, contracts, GoodsParticipantStateCodec.read(in));
    }
    static void writeOrder(DataOutputStream out, GoodsTradeOrder order) throws IOException {
        id(out, order.id()); party(out, order.party()); party(out, order.counterparty()); out.writeByte(order.side().wireTag());
        id(out, order.containerId()); out.writeUTF(order.itemKind()); out.writeInt(order.quantity());
        out.writeInt(order.committedQuantity()); out.writeLong(order.unitPriceLimit().raw()); out.writeLong(order.expiresAtTick());
    }
    static GoodsTradeOrder readOrder(DataInputStream in) throws IOException {
        return new GoodsTradeOrder(id(in), party(in), party(in), GoodsTradeOrder.Side.fromWireTag(in.readUnsignedByte()),
                id(in), in.readUTF(), in.readInt(), in.readInt(), new FixedScalar(in.readLong()), in.readLong());
    }
    static void writeContract(DataOutputStream out, GoodsTradeContract contract) throws IOException {
        id(out, contract.id()); id(out, contract.sellOrderId()); id(out, contract.buyOrderId());
        party(out, contract.seller()); party(out, contract.buyer()); id(out, contract.sourceContainerId()); id(out, contract.receiverContainerId());
        out.writeUTF(contract.itemKind()); out.writeInt(contract.quantity()); out.writeLong(contract.deliveredUnitPrice().raw());
        id(out, contract.financialReservationId()); out.writeLong(contract.revision()); quantities(out, contract.outstandingClaims());
        out.writeInt(contract.acceptedQuantity()); out.writeInt(contract.disposedQuantity());
        out.writeInt(contract.acceptances().size());
        for (var entry : contract.acceptances().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) acceptance(out, entry.getValue());
        out.writeInt(contract.dispositions().size());
        for (var entry : contract.dispositions().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) disposition(out, entry.getValue());
    }
    static GoodsTradeContract readContract(DataInputStream in) throws IOException {
        SubjectId id = id(in), sell = id(in), buy = id(in); GoodsTradeParty seller = party(in), buyer = party(in);
        SubjectId source = id(in), receiver = id(in); String kind = in.readUTF(); int quantity = in.readInt();
        FixedScalar price = new FixedScalar(in.readLong()); SubjectId reservation = id(in); long revision = in.readLong();
        Map<SubjectId, Integer> claims = quantities(in); int accepted = in.readInt(), disposed = in.readInt();
        Map<SubjectId, GoodsTradeAcceptance> receipts = new HashMap<>();
        for (int n = count(in, GoodsTradeContract.MAX_RECEIPTS); n > 0; n--) { var receipt = acceptance(in); put(receipts, receipt.id(), receipt); }
        Map<SubjectId, GoodsTradeDisposition> dispositions = new HashMap<>();
        for (int n = count(in, GoodsTradeContract.MAX_RECEIPTS); n > 0; n--) { var disposition = disposition(in); put(dispositions, disposition.id(), disposition); }
        return new GoodsTradeContract(id, sell, buy, seller, buyer, source, receiver, kind, quantity, price, reservation, revision, claims,
                receipts, dispositions, accepted, disposed);
    }
    static void disposition(DataOutputStream out, GoodsTradeDisposition disposition) throws IOException {
        id(out, disposition.id()); id(out, disposition.contractId()); out.writeLong(disposition.expectedContractRevision());
        id(out, disposition.claimId()); out.writeInt(disposition.quantity()); out.writeByte(disposition.reason().wireTag());
    }
    static GoodsTradeDisposition disposition(DataInputStream in) throws IOException {
        return new GoodsTradeDisposition(id(in), id(in), in.readLong(), id(in), in.readInt(),
                GoodsTradeDisposition.Reason.fromWireTag(in.readUnsignedByte()));
    }
    static void acceptance(DataOutputStream out, GoodsTradeAcceptance receipt) throws IOException {
        id(out, receipt.id()); id(out, receipt.contractId()); out.writeLong(receipt.expectedContractRevision());
        ResourceTitleTransfer title = receipt.title(); id(out, title.accountId()); id(out, title.claimId());
        id(out, title.sourceOwnerId()); id(out, title.destinationOwnerId()); quantities(out, title.portions());
        out.writeInt(title.splitLotIds().size());
        for (var entry : title.splitLotIds().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) { id(out, entry.getKey()); id(out, entry.getValue()); }
    }
    static GoodsTradeAcceptance acceptance(DataInputStream in) throws IOException {
        SubjectId receipt = id(in), contract = id(in); long revision = in.readLong();
        SubjectId account = id(in), claim = id(in), seller = id(in), buyer = id(in); var portions = quantities(in);
        Map<SubjectId, SubjectId> splits = new HashMap<>();
        for (int n = count(in, 64); n > 0; n--) put(splits, id(in), id(in));
        return new GoodsTradeAcceptance(receipt, contract, revision, new ResourceTitleTransfer(account, claim, seller, buyer, portions, splits));
    }
    static void allocation(DataOutputStream out, GoodsTradeStockAllocation allocation) throws IOException {
        id(out, allocation.accountId()); ClaimAllocation claim = allocation.claim();
        id(out, claim.id()); id(out, claim.claimantId()); id(out, claim.economicOwnerId()); out.writeUTF(claim.itemKind());
        out.writeInt(claim.quantity()); quantities(out, claim.lotQuantities());
        out.writeByte(allocation.authority().wireTag()); out.writeLong(allocation.epoch());
    }
    static GoodsTradeStockAllocation allocation(DataInputStream in) throws IOException {
        SubjectId account = id(in);
        ClaimAllocation claim = new ClaimAllocation(id(in), id(in), id(in), in.readUTF(), in.readInt(), quantities(in), ClaimPurpose.GOODS_TRADE);
        return new GoodsTradeStockAllocation(account, claim, GoodsTradeStockAllocation.Authority.fromWireTag(in.readUnsignedByte()), in.readLong());
    }
    private static void party(DataOutputStream out, GoodsTradeParty party) throws IOException {
        id(out, party.id()); out.writeByte(FrontierWireTags.tag(party.kind()));
    }
    private static GoodsTradeParty party(DataInputStream in) throws IOException {
        return new GoodsTradeParty(id(in), FrontierWireTags.require(EconomicOwnerKind.class, in.readUnsignedByte()));
    }
    static void partition(DataOutputStream out, ResourceClaimPartition partition) throws IOException {
        id(out, partition.accountId()); id(out, partition.claimId()); id(out, partition.childClaimId()); quantities(out, partition.lotQuantities());
    }
    static ResourceClaimPartition partition(DataInputStream in) throws IOException {
        return new ResourceClaimPartition(id(in), id(in), id(in), quantities(in));
    }
    static void retirement(DataOutputStream out, GoodsTradeRetired retired) throws IOException {
        out.writeInt(retired.orderIds().size());
        for (SubjectId id : retired.orderIds().stream().sorted().toList()) id(out, id);
        out.writeInt(retired.contractIds().size());
        for (SubjectId id : retired.contractIds().stream().sorted().toList()) id(out, id);
    }
    static GoodsTradeRetired retirement(DataInputStream in) throws IOException {
        java.util.Set<SubjectId> orders = new java.util.HashSet<>(), contracts = new java.util.HashSet<>();
        for (int n = count(in, GoodsTradeState.MAX_ORDERS); n > 0; n--) {
            if (!orders.add(id(in))) throw new IllegalArgumentException("duplicate retired goods order");
        }
        for (int n = count(in, GoodsTradeState.MAX_CONTRACTS); n > 0; n--) {
            if (!contracts.add(id(in))) throw new IllegalArgumentException("duplicate retired goods contract");
        }
        return new GoodsTradeRetired(orders, contracts);
    }
    private static void quantities(DataOutputStream out, Map<SubjectId, Integer> values) throws IOException {
        out.writeInt(values.size()); for (var entry : values.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            id(out, entry.getKey()); out.writeInt(entry.getValue());
        }
    }
    private static Map<SubjectId, Integer> quantities(DataInputStream in) throws IOException {
        Map<SubjectId, Integer> result = new HashMap<>();
        for (int n = count(in, 64); n > 0; n--) put(result, id(in), in.readInt());
        return Map.copyOf(result);
    }
    private static void id(DataOutputStream out, SubjectId id) throws IOException { out.writeUTF(id.value()); }
    private static SubjectId id(DataInputStream in) throws IOException { return new SubjectId(in.readUTF()); }
    private static int count(DataInputStream in, int maximum) throws IOException {
        int n = in.readInt(); if (n < 0 || n > maximum) throw new IllegalArgumentException("invalid bounded goods codec count"); return n;
    }
    private static <T> void put(Map<SubjectId, T> map, SubjectId id, T value) {
        if (map.putIfAbsent(id, value) != null) throw new IllegalArgumentException("duplicate goods codec identity");
    }
}
