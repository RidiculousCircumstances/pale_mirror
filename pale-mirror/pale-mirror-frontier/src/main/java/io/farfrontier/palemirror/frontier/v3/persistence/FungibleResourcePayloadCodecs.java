package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs.*;

/** F0.3 persistence registry for fungible lots, claims, HOT layouts and observed handoffs. */
final class FungibleResourcePayloadCodecs {
    private FungibleResourcePayloadCodecs() { }

    static PayloadCodecs observationCodecs() {
        return new PayloadCodecs(List.of(new FungibleStackLayoutObservedCodec(), new FungibleResourceHandoffObservedCodec(),
                new FungibleStockDepartureObservedCodec(), new FungibleStockContributionObservedCodec(),
                new FungibleStackBindingsReleasedCodec()));
    }

    static PayloadCodec productionCompleted() { return new FungibleProductionCompletedCodec(); }

    private static final class FungibleProductionCompletedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.fungible_production_completed"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            FungibleProductionCompleted completed = (FungibleProductionCompleted) payload; ResourceLot lot = completed.output();
            writeSubject(output, completed.jobId()); writeSubject(output, lot.id()); writeSubject(output, lot.economicOwnerId()); writeString(output, lot.itemKind());
            output.writeShort(lot.quantity()); writeString(output, lot.provenance()); output.writeByte(lot.lineage().size());
            for (SubjectId lineage : lot.lineage()) writeSubject(output, lineage);
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectId jobId = readSubject(input).value(); SubjectId id = readSubject(input).value(); SubjectId owner = readSubject(input).value();
            String kind = readString(input); int quantity = input.readUnsignedShort(); String provenance = readString(input); int count = input.readUnsignedByte();
            List<SubjectId> lineage = new ArrayList<>(); for (int index = 0; index < count; index++) lineage.add(readSubject(input).value());
            return new FungibleProductionCompleted(jobId, new ResourceLot(id, owner, kind, quantity, provenance, lineage));
        }); }
    }

    private static final class FungibleStackLayoutObservedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.fungible_stack_layout_observed"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            FungibleStackLayoutObserved observed = (FungibleStackLayoutObserved) payload;
            writeSubject(output, observed.accountId()); output.writeLong(observed.authorityEpoch()); output.writeShort(observed.stacks().size());
            for (FungiblePhysicalObservation.Stack stack : observed.stacks()) {
                writePhysicalStackAddress(output, stack.address()); writeString(output, stack.itemKind()); output.writeByte(stack.quantity());
            }
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectId account = readSubject(input).value(); long epoch = input.readLong(); int count = input.readUnsignedShort();
            if (count == 0 || count > 16_384) throw new IllegalArgumentException("invalid fungible stack observation count");
            List<FungiblePhysicalObservation.Stack> stacks = new ArrayList<>();
            for (int index = 0; index < count; index++) stacks.add(new FungiblePhysicalObservation.Stack(readPhysicalStackAddress(input), readString(input), input.readUnsignedByte()));
            return new FungibleStackLayoutObserved(account, epoch, stacks);
        }); }
    }

    private static final class FungibleResourceHandoffObservedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.fungible_resource_handoff_observed"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            FungibleResourceHandoffObserved observed = (FungibleResourceHandoffObserved) payload;
            writeSubject(output, observed.sourceAccountId()); writeFungibleAccount(output, observed.destinationAccount());
            output.writeLong(observed.sourceEpoch()); output.writeLong(observed.destinationEpoch()); writeFungibleQuantities(output, observed.lotQuantities());
            writeFungibleQuantities(output, observed.claimQuantities()); writeFungibleBindings(output, observed.remainingSource()); writeFungibleBindings(output, observed.destinationBindings());
            writeSubjects(output, observed.forfeitedClaimIds()); writeString(output, observed.playerSaveFence());
            output.writeBoolean(observed.retirementDiagnostic().isPresent());
            if (observed.retirementDiagnostic().isPresent()) writeDiagnosticTuple(output, observed.retirementDiagnostic().orElseThrow());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new FungibleResourceHandoffObserved(readSubject(input).value(),
                readFungibleAccount(input), input.readLong(), input.readLong(), readFungibleQuantities(input), readFungibleQuantities(input),
                readFungibleBindings(input), readFungibleBindings(input), readSubjects(input), readString(input),
                input.readBoolean() ? java.util.Optional.of(readDiagnosticTuple(input)) : java.util.Optional.empty())); }
    }

    private static final class FungibleStockDepartureObservedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.fungible_stock_departure_observed"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            FungibleStockDepartureObserved observed = (FungibleStockDepartureObserved) payload;
            writeSubject(output, observed.sourceAccountId()); writeSubject(output, observed.containerId());
            writeSubject(output, observed.economicOwnerId()); output.writeLong(observed.authorityEpoch());
            writeString(output, observed.playerId().toString()); writeString(output, observed.interactionId().toString());
            writeFungibleQuantities(output, observed.departedLots()); output.writeShort(observed.remaining().size());
            for (FungiblePhysicalObservation.Stack stack : observed.remaining()) {
                writePhysicalStackAddress(output, stack.address()); writeString(output, stack.itemKind()); output.writeByte(stack.quantity());
            }
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectId account = readSubject(input).value(); SubjectId container = readSubject(input).value();
            SubjectId owner = readSubject(input).value(); long epoch = input.readLong();
            java.util.UUID player = java.util.UUID.fromString(readString(input));
            java.util.UUID interaction = java.util.UUID.fromString(readString(input));
            Map<SubjectId, Integer> lots = readFungibleQuantities(input);
            int count = input.readUnsignedShort();
            if (count > FungibleResourceLedger.MAX_BINDINGS) throw new IllegalArgumentException("invalid stock departure stack count");
            List<FungiblePhysicalObservation.Stack> remaining = new ArrayList<>();
            for (int index = 0; index < count; index++) remaining.add(new FungiblePhysicalObservation.Stack(
                    readPhysicalStackAddress(input), readString(input), input.readUnsignedByte()));
            return new FungibleStockDepartureObserved(account, container, owner, epoch, player, interaction, lots, remaining);
        }); }
    }

    private static final class FungibleStockContributionObservedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.fungible_stock_contribution_observed"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            FungibleStockContributionObserved observed = (FungibleStockContributionObserved) payload;
            ResourceLot lot = observed.contribution();
            writeSubject(output, observed.accountId()); writeSubject(output, observed.containerId());
            output.writeLong(observed.authorityEpoch());
            writeString(output, observed.playerId().toString()); writeString(output, observed.interactionId().toString());
            writeSubject(output, lot.id()); writeSubject(output, lot.economicOwnerId());
            writeString(output, lot.itemKind()); output.writeShort(lot.quantity());
            writeString(output, lot.provenance()); output.writeByte(lot.lineage().size());
            for (SubjectId ancestor : lot.lineage()) writeSubject(output, ancestor);
            output.writeShort(observed.observed().size());
            for (FungiblePhysicalObservation.Stack stack : observed.observed()) {
                writePhysicalStackAddress(output, stack.address()); writeString(output, stack.itemKind());
                output.writeByte(stack.quantity());
            }
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> {
            SubjectId account = readSubject(input).value(); SubjectId container = readSubject(input).value();
            long epoch = input.readLong(); java.util.UUID player = java.util.UUID.fromString(readString(input));
            java.util.UUID interaction = java.util.UUID.fromString(readString(input));
            SubjectId id = readSubject(input).value(); SubjectId owner = readSubject(input).value();
            String kind = readString(input); int quantity = input.readUnsignedShort(); String provenance = readString(input);
            int ancestorCount = input.readUnsignedByte();
            List<SubjectId> ancestors = new ArrayList<>();
            for (int index = 0; index < ancestorCount; index++) ancestors.add(readSubject(input).value());
            ResourceLot lot = new ResourceLot(id, owner, kind, quantity, provenance, ancestors);
            int count = input.readUnsignedShort();
            if (count == 0 || count > FungibleResourceLedger.MAX_BINDINGS) throw new IllegalArgumentException("invalid stock contribution stack count");
            List<FungiblePhysicalObservation.Stack> stacks = new ArrayList<>();
            for (int index = 0; index < count; index++) stacks.add(new FungiblePhysicalObservation.Stack(
                    readPhysicalStackAddress(input), readString(input), input.readUnsignedByte()));
            return new FungibleStockContributionObserved(account, container, epoch, player, interaction, lot, stacks);
        }); }
    }

    private static final class FungibleStackBindingsReleasedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.fungible_stack_bindings_released"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeProduction(output -> {
            FungibleStackBindingsReleased released = (FungibleStackBindingsReleased) payload;
            writeSubject(output, released.accountId()); output.writeLong(released.authorityEpoch());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeProduction(bytes, input -> new FungibleStackBindingsReleased(readSubject(input).value(), input.readLong())); }
    }

    private static void writePhysicalStackAddress(DataOutputStream output, PhysicalStackAddress address) throws IOException {
        switch (address) {
            case PhysicalStackAddress.ContainerSlot slot -> { output.writeByte(0); writeSubject(output, slot.slot().containerId()); output.writeByte(slot.slot().slot()); }
            case PhysicalStackAddress.PlayerSlot slot -> { output.writeByte(1); writeString(output, slot.playerId().toString()); output.writeByte(slot.slot()); }
            case PhysicalStackAddress.HopperSlot slot -> { output.writeByte(2); output.writeInt(slot.position().x()); output.writeInt(slot.position().y()); output.writeInt(slot.position().z()); output.writeByte(slot.slot()); }
            case PhysicalStackAddress.WorldEntity entity -> { output.writeByte(3); writeString(output, entity.entityId().toString()); }
            case PhysicalStackAddress.ActorHand hand -> { output.writeByte(4); writeSubject(output, hand.actorId()); writeString(output, hand.entityId().toString()); }
        }
    }

    private static PhysicalStackAddress readPhysicalStackAddress(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(readSubject(input).value(), input.readUnsignedByte()));
            case 1 -> new PhysicalStackAddress.PlayerSlot(java.util.UUID.fromString(readString(input)), input.readUnsignedByte());
            case 2 -> new PhysicalStackAddress.HopperSlot(new BlockPosition(input.readInt(), input.readInt(), input.readInt()), input.readUnsignedByte());
            case 3 -> new PhysicalStackAddress.WorldEntity(java.util.UUID.fromString(readString(input)));
            case 4 -> new PhysicalStackAddress.ActorHand(readSubject(input).value(), java.util.UUID.fromString(readString(input)));
            default -> throw new IllegalArgumentException("unknown physical stack address");
        };
    }

    private static void writeFungibleAccount(DataOutputStream output, CustodyAccount account) throws IOException {
        writeSubject(output, account.id()); writeResourceCustody(output, account.custody()); writeFungibleQuantities(output, account.lotQuantities());
        writeFungibleQuantities(output, account.claimQuantities());
    }

    private static CustodyAccount readFungibleAccount(DataInputStream input) throws IOException {
        return new CustodyAccount(readSubject(input).value(), readResourceCustody(input), readFungibleQuantities(input), readFungibleQuantities(input));
    }

    private static void writeResourceCustody(DataOutputStream output, ResourceCustody custody) throws IOException {
        switch (custody) {
            case ResourceCustody.Container container -> { output.writeByte(0); writeSubject(output, container.containerId()); }
            case ResourceCustody.Player player -> { output.writeByte(1); writeString(output, player.playerId().toString()); }
            case ResourceCustody.Cargo cargo -> { output.writeByte(2); writeSubject(output, cargo.cargoId()); }
            case ResourceCustody.WorldCarrier carrier -> { output.writeByte(3); writeString(output, carrier.carrierId().toString()); }
            case ResourceCustody.Actor actor -> { output.writeByte(4); writeSubject(output, actor.actorId()); }
        }
    }

    private static ResourceCustody readResourceCustody(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> new ResourceCustody.Container(readSubject(input).value());
            case 1 -> new ResourceCustody.Player(java.util.UUID.fromString(readString(input)));
            case 2 -> new ResourceCustody.Cargo(readSubject(input).value());
            case 3 -> new ResourceCustody.WorldCarrier(java.util.UUID.fromString(readString(input)));
            case 4 -> new ResourceCustody.Actor(readSubject(input).value());
            default -> throw new IllegalArgumentException("unknown resource custody");
        };
    }

    private static void writeFungibleQuantities(DataOutputStream output, Map<SubjectId, Integer> quantities) throws IOException {
        output.writeShort(quantities.size());
        for (Map.Entry<SubjectId, Integer> entry : quantities.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeSubject(output, entry.getKey()); output.writeShort(entry.getValue());
        }
    }

    private static Map<SubjectId, Integer> readFungibleQuantities(DataInputStream input) throws IOException {
        Map<SubjectId, Integer> quantities = new LinkedHashMap<>();
        for (int index = 0, count = input.readUnsignedShort(); index < count; index++) {
            SubjectId id = readSubject(input).value(); if (quantities.put(id, input.readUnsignedShort()) != null) throw new IllegalArgumentException("duplicate fungible quantity identity");
        }
        return quantities;
    }

    private static void writeFungibleBindings(DataOutputStream output, List<PhysicalStackBinding> bindings) throws IOException {
        output.writeShort(bindings.size());
        for (PhysicalStackBinding binding : bindings) {
            writeSubject(output, binding.id()); writeSubject(output, binding.accountId()); writePhysicalStackAddress(output, binding.address()); output.writeLong(binding.authorityEpoch());
            writeString(output, binding.itemKind()); writeFungibleQuantities(output, binding.lotQuantities()); writeFungibleQuantities(output, binding.claimQuantities()); writeString(output, binding.playerSaveFence());
        }
    }

    private static List<PhysicalStackBinding> readFungibleBindings(DataInputStream input) throws IOException {
        List<PhysicalStackBinding> bindings = new ArrayList<>();
        for (int index = 0, count = input.readUnsignedShort(); index < count; index++) bindings.add(new PhysicalStackBinding(readSubject(input).value(),
                readSubject(input).value(), readPhysicalStackAddress(input), input.readLong(), readString(input), readFungibleQuantities(input), readFungibleQuantities(input), readString(input)));
        return bindings;
    }

    private static void writeSubjects(DataOutputStream output, Set<SubjectId> values) throws IOException {
        output.writeShort(values.size()); for (SubjectId value : values.stream().sorted().toList()) writeSubject(output, value);
    }

    private static Set<SubjectId> readSubjects(DataInputStream input) throws IOException {
        Set<SubjectId> values = new LinkedHashSet<>();
        for (int index = 0, count = input.readUnsignedShort(); index < count; index++) {
            if (!values.add(readSubject(input).value())) throw new IllegalArgumentException("duplicate forfeited claim identity");
        }
        return values;
    }
}
