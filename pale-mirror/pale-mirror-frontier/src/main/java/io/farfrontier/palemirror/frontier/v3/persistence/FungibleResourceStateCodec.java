package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.*;

/** Fresh-schema persistence for F0.3 lots, claims, custody accounts and transient HOT bindings. */
final class FungibleResourceStateCodec {
    private FungibleResourceStateCodec() { }

    static void write(DataOutputStream output, FungibleResourceLedger ledger) throws IOException {
        writeCount(output, ledger.lots().size());
        for (ResourceLot lot : ledger.lots().values().stream().sorted(Comparator.comparing(ResourceLot::id)).toList()) {
            writeString(output, lot.id().value()); writeString(output, lot.economicOwnerId().value()); writeString(output, lot.itemKind());
            writeCount(output, lot.quantity()); writeString(output, lot.provenance()); writeCount(output, lot.lineage().size());
            for (SubjectId parent : lot.lineage()) writeString(output, parent.value());
        }
        writeCount(output, ledger.claims().size());
        for (ClaimAllocation claim : ledger.claims().values().stream().sorted(Comparator.comparing(ClaimAllocation::id)).toList()) {
            writeString(output, claim.id().value()); writeString(output, claim.claimantId().value()); writeString(output, claim.economicOwnerId().value());
            writeString(output, claim.itemKind()); writeCount(output, claim.quantity()); writeQuantities(output, claim.lotQuantities());
            output.writeByte(FrontierWireTags.tag(claim.purpose()));
        }
        writeCount(output, ledger.accounts().size());
        for (CustodyAccount account : ledger.accounts().values().stream().sorted(Comparator.comparing(CustodyAccount::id)).toList()) {
            writeString(output, account.id().value()); writeResourceCustody(output, account.custody()); writeQuantities(output, account.lotQuantities());
            writeQuantities(output, account.claimQuantities());
            output.writeBoolean(account.actorPresentation().isPresent());
            if (account.actorPresentation().isPresent()) ActorItemSlotCodec.write(output, account.actorPresentation().orElseThrow());
        }
        writeCount(output, ledger.bindings().size());
        for (PhysicalStackBinding binding : ledger.bindings().values().stream().sorted(Comparator.comparing(PhysicalStackBinding::id)).toList()) {
            writeString(output, binding.id().value()); writeString(output, binding.accountId().value()); writePhysicalAddress(output, binding.address());
            output.writeLong(binding.authorityEpoch()); writeString(output, binding.itemKind()); writeQuantities(output, binding.lotQuantities());
            writeQuantities(output, binding.claimQuantities()); writeString(output, binding.playerSaveFence());
        }
    }

    static FungibleResourceLedger read(DataInputStream input) throws IOException {
        Map<SubjectId, ResourceLot> lots = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); SubjectId owner = new SubjectId(readString(input)); String kind = readString(input);
            int quantity = readCount(input); String provenance = readString(input); var lineage = new ArrayList<SubjectId>();
            for (int parent = 0, parents = readCount(input); parent < parents; parent++) lineage.add(new SubjectId(readString(input)));
            if (lots.put(id, new ResourceLot(id, owner, kind, quantity, provenance, lineage)) != null) throw new IllegalArgumentException("duplicate resource lot id");
        }
        Map<SubjectId, ClaimAllocation> claims = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); ClaimAllocation claim = new ClaimAllocation(id, new SubjectId(readString(input)),
                    new SubjectId(readString(input)), readString(input), readCount(input), readQuantities(input),
                    FrontierWireTags.require(ClaimPurpose.class, input.readUnsignedByte()));
            if (claims.put(id, claim) != null) throw new IllegalArgumentException("duplicate claim allocation id");
        }
        Map<SubjectId, CustodyAccount> accounts = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); CustodyAccount account = new CustodyAccount(id, readResourceCustody(input), readQuantities(input), readQuantities(input),
                    input.readBoolean() ? java.util.Optional.of(ActorItemSlotCodec.read(input)) : java.util.Optional.empty());
            if (accounts.put(id, account) != null) throw new IllegalArgumentException("duplicate custody account id");
        }
        Map<SubjectId, PhysicalStackBinding> bindings = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input)); PhysicalStackBinding binding = new PhysicalStackBinding(id,
                    new SubjectId(readString(input)), readPhysicalAddress(input), input.readLong(), readString(input), readQuantities(input), readQuantities(input), readString(input));
            if (bindings.put(id, binding) != null) throw new IllegalArgumentException("duplicate physical stack binding id");
        }
        return new FungibleResourceLedger(lots, claims, accounts, bindings);
    }

    private static void writeQuantities(DataOutputStream output, Map<SubjectId, Integer> quantities) throws IOException {
        writeCount(output, quantities.size());
        for (Map.Entry<SubjectId, Integer> entry : quantities.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) { writeString(output, entry.getKey().value()); writeCount(output, entry.getValue()); }
    }

    private static Map<SubjectId, Integer> readQuantities(DataInputStream input) throws IOException {
        Map<SubjectId, Integer> quantities = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) { SubjectId id = new SubjectId(readString(input)); if (quantities.put(id, readCount(input)) != null) throw new IllegalArgumentException("duplicate custody quantity"); }
        return quantities;
    }

    static void writePhysicalAddress(DataOutputStream output, PhysicalStackAddress address) throws IOException {
        switch (address) {
            case PhysicalStackAddress.ContainerSlot slot -> { output.writeByte(0); writeCustody(output, slot.slot()); }
            case PhysicalStackAddress.PlayerSlot slot -> { output.writeByte(1); writeString(output, slot.playerId().toString()); output.writeByte(slot.slot()); }
            case PhysicalStackAddress.HopperSlot slot -> { output.writeByte(2); writePosition(output, slot.position()); output.writeByte(slot.slot()); }
            case PhysicalStackAddress.WorldEntity entity -> { output.writeByte(3); writeString(output, entity.entityId().toString()); }
            case PhysicalStackAddress.ActorHand hand -> { output.writeByte(hand.hand() == ActorContainerItemOrder.Hand.OFF ? 4 : 6); writeString(output, hand.actorId().value()); writeString(output, hand.entityId().toString()); }
            case PhysicalStackAddress.ActorPocket pocket -> { output.writeByte(5); writeString(output, pocket.actorId().value()); writeString(output, pocket.entityId().toString()); output.writeByte(pocket.slot()); }
        }
    }

    static PhysicalStackAddress readPhysicalAddress(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> {
                InventoryCustody custody = readCustody(input);
                if (!(custody instanceof InventoryCustody.ContainerSlot slot)) throw new IllegalArgumentException("physical container address requires a container slot");
                yield new PhysicalStackAddress.ContainerSlot(slot);
            }
            case 1 -> new PhysicalStackAddress.PlayerSlot(UUID.fromString(readString(input)), input.readUnsignedByte());
            case 2 -> new PhysicalStackAddress.HopperSlot(readPosition(input), input.readUnsignedByte());
            case 3 -> new PhysicalStackAddress.WorldEntity(UUID.fromString(readString(input)));
            case 4 -> new PhysicalStackAddress.ActorHand(new SubjectId(readString(input)), UUID.fromString(readString(input)));
            case 5 -> new PhysicalStackAddress.ActorPocket(new SubjectId(readString(input)), UUID.fromString(readString(input)), input.readUnsignedByte());
            case 6 -> new PhysicalStackAddress.ActorHand(new SubjectId(readString(input)), UUID.fromString(readString(input)), ActorContainerItemOrder.Hand.MAIN);
            default -> throw new IllegalArgumentException("unknown physical stack address");
        };
    }

    private static void writeResourceCustody(DataOutputStream output, ResourceCustody custody) throws IOException {
        switch (custody) {
            case ResourceCustody.Container container -> { output.writeByte(0); writeString(output, container.containerId().value()); }
            case ResourceCustody.Player player -> { output.writeByte(1); writeString(output, player.playerId().toString()); }
            case ResourceCustody.Cargo cargo -> { output.writeByte(2); writeString(output, cargo.cargoId().value()); }
            case ResourceCustody.WorldCarrier carrier -> { output.writeByte(3); writeString(output, carrier.carrierId().toString()); }
            case ResourceCustody.Actor actor -> { output.writeByte(4); writeString(output, actor.actorId().value()); }
        }
    }

    private static ResourceCustody readResourceCustody(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> new ResourceCustody.Container(new SubjectId(readString(input)));
            case 1 -> new ResourceCustody.Player(UUID.fromString(readString(input)));
            case 2 -> new ResourceCustody.Cargo(new SubjectId(readString(input)));
            case 3 -> new ResourceCustody.WorldCarrier(UUID.fromString(readString(input)));
            case 4 -> new ResourceCustody.Actor(new SubjectId(readString(input)));
            default -> throw new IllegalArgumentException("unknown resource custody");
        };
    }
}
