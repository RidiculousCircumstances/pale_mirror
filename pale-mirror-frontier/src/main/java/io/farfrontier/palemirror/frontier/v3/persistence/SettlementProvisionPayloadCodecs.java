package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** WAL codecs for the bounded exact food-provision lifecycle. */
final class SettlementProvisionPayloadCodecs {
    private SettlementProvisionPayloadCodecs() { }

    static PayloadCodec started() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_provision_started_v2"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> write(output, ((SettlementProvisionStarted) payload).provision())); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new SettlementProvisionStarted(read(input))); }
    }; }

    static PayloadCodec legacyStarted() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_provision_started"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            LegacySettlementProvisionStarted started = (LegacySettlementProvisionStarted) payload;
            FrontierWorldPayloadCodecs.writeSubject(output, started.settlementId()); FrontierWorldStateCodec.writeCount(output, started.cycleOrdinal());
            output.writeLong(started.startedAtTick()); FrontierWorldStateCodec.writeCount(output, started.requiredRations());
            FrontierWorldStateCodec.writeCount(output, started.fulfilledRations()); FrontierWorldStateCodec.writeCount(output, started.allocations().size());
            for (LegacySettlementProvisionStarted.LegacyAllocation allocation : started.allocations()) {
                FrontierWorldPayloadCodecs.writeSubject(output, allocation.itemId()); FrontierWorldStateCodec.writeCount(output, allocation.count());
            }
            FrontierWorldStateCodec.writeCount(output, started.nextAllocation()); output.writeByte(started.status().wireTag()); output.writeBoolean(false);
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            SubjectId settlement = FrontierWorldPayloadCodecs.readSubject(input).value(); int cycle = FrontierWorldStateCodec.readCount(input); long startedAt = input.readLong();
            int required = FrontierWorldStateCodec.readCount(input); int fulfilled = FrontierWorldStateCodec.readCount(input);
            List<LegacySettlementProvisionStarted.LegacyAllocation> allocations = new ArrayList<>();
            for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
                allocations.add(new LegacySettlementProvisionStarted.LegacyAllocation(FrontierWorldPayloadCodecs.readSubject(input).value(), FrontierWorldStateCodec.readCount(input)));
            }
            int next = FrontierWorldStateCodec.readCount(input); int status = input.readUnsignedByte(); boolean active = input.readBoolean();
            if (status >= SettlementProvisionStatus.values().length || active) throw new IllegalArgumentException("invalid legacy settlement provision start");
            return new LegacySettlementProvisionStarted(settlement, cycle, startedAt, required, fulfilled, allocations, next, FrontierWireTags.require(SettlementProvisionStatus.class, status));
        }); }
    }; }

    static PayloadCodec consumed() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_provision_consumed"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            SettlementProvisionConsumed consumed = (SettlementProvisionConsumed) payload;
            FrontierWorldPayloadCodecs.writeSubject(output, consumed.settlementId()); FrontierWorldPayloadCodecs.writeSubject(output, consumed.itemId());
            FrontierWorldStateCodec.writeCount(output, consumed.count());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new SettlementProvisionConsumed(
                FrontierWorldPayloadCodecs.readSubject(input).value(), FrontierWorldPayloadCodecs.readSubject(input).value(), FrontierWorldStateCodec.readCount(input))); }
    }; }

    static PayloadCodec resolved() { return new PayloadCodec() {
        @Override public String type() { return "frontier.settlement_provision_resolved"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            SettlementProvisionResolved resolved = (SettlementProvisionResolved) payload;
            FrontierWorldPayloadCodecs.writeSubject(output, resolved.settlementId()); output.writeByte(resolved.status().wireTag());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            SubjectId settlement = FrontierWorldPayloadCodecs.readSubject(input).value(); int status = input.readUnsignedByte();
            if (status >= SettlementProvisionStatus.values().length) throw new IllegalArgumentException("unknown settlement provision resolution status");
            return new SettlementProvisionResolved(settlement, FrontierWireTags.require(SettlementProvisionStatus.class, status));
        }); }
    }; }

    private static void write(DataOutputStream output, SettlementProvision provision) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(output, provision.settlementId()); FrontierWorldStateCodec.writeCount(output, provision.cycleOrdinal());
        output.writeLong(provision.startedAtTick()); FrontierWorldStateCodec.writeCount(output, provision.requiredRations());
        FrontierWorldStateCodec.writeCount(output, provision.fulfilledRations()); FrontierWorldStateCodec.writeCount(output, provision.recipientIds().size());
        for (SubjectId recipient : provision.recipientIds()) FrontierWorldPayloadCodecs.writeSubject(output, recipient);
        FrontierWorldStateCodec.writeCount(output, provision.allocations().size());
        for (SettlementRationAllocation allocation : provision.allocations()) {
            FrontierWorldPayloadCodecs.writeSubject(output, allocation.itemId()); FrontierWorldStateCodec.writeCount(output, allocation.recipientIds().size());
            for (SubjectId recipient : allocation.recipientIds()) FrontierWorldPayloadCodecs.writeSubject(output, recipient);
        }
        FrontierWorldStateCodec.writeCount(output, provision.nextAllocation()); output.writeByte(provision.status().wireTag()); output.writeBoolean(provision.activeIntentId().isPresent());
        if (provision.activeIntentId().isPresent()) FrontierWorldStateCodec.writeString(output, provision.activeIntentId().orElseThrow().value());
    }

    private static SettlementProvision read(DataInputStream input) throws IOException {
        SubjectId settlement = FrontierWorldPayloadCodecs.readSubject(input).value(); int cycle = FrontierWorldStateCodec.readCount(input); long startedAt = input.readLong();
        int required = FrontierWorldStateCodec.readCount(input); int fulfilled = FrontierWorldStateCodec.readCount(input); List<SubjectId> recipients = new ArrayList<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) recipients.add(FrontierWorldPayloadCodecs.readSubject(input).value());
        List<SettlementRationAllocation> allocations = new ArrayList<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId item = FrontierWorldPayloadCodecs.readSubject(input).value(); List<SubjectId> allocationRecipients = new ArrayList<>();
            for (int recipient = 0, recipientCount = FrontierWorldStateCodec.readCount(input); recipient < recipientCount; recipient++) {
                allocationRecipients.add(FrontierWorldPayloadCodecs.readSubject(input).value());
            }
            allocations.add(new SettlementRationAllocation(item, allocationRecipients));
        }
        int next = FrontierWorldStateCodec.readCount(input); int status = input.readUnsignedByte(); boolean active = input.readBoolean();
        if (status >= SettlementProvisionStatus.values().length) throw new IllegalArgumentException("unknown settlement provision status");
        Optional<PhysicalIntentId> intent = active ? Optional.of(new PhysicalIntentId(FrontierWorldStateCodec.readString(input))) : Optional.empty();
        return new SettlementProvision(settlement, cycle, startedAt, required, fulfilled, List.copyOf(recipients), List.copyOf(allocations), next,
                FrontierWireTags.require(SettlementProvisionStatus.class, status), intent);
    }
}
