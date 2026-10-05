package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.Optional;

/** Strict representation shared by canonical state and physical retirement seals. */
public final class ResourceSiteHarvestAcceptanceCodec implements PayloadCodec {
    private static final int MAX_RECEIPT_BYTES = 2048;
    @Override public String type() { return "frontier.resource_site_harvest_work_acknowledged"; }
    @Override public byte[] encode(FrontierPayload payload) {
        return encodeAcceptance(((ResourceSiteHarvestWorkAcknowledged) payload).acceptance());
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return new ResourceSiteHarvestWorkAcknowledged(decodeAcceptance(bytes));
    }
    public static byte[] encodeAcceptance(ResourceSiteHarvestWorkAcceptance acceptance) {
        return FrontierWorldPayloadCodecs.encodeProduction(output -> write(output, acceptance));
    }
    public static ResourceSiteHarvestWorkAcceptance decodeAcceptance(byte[] bytes) {
        return ((ResourceSiteHarvestWorkAcknowledged) FrontierWorldPayloadCodecs.decodeProduction(bytes,
                input -> new ResourceSiteHarvestWorkAcknowledged(read(input)))).acceptance();
    }
    static void writeOptional(DataOutputStream output, Optional<ResourceSiteHarvestWorkAcceptance> value) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) write(output, value.orElseThrow());
    }
    static Optional<ResourceSiteHarvestWorkAcceptance> readOptional(DataInputStream input) throws IOException {
        return input.readBoolean() ? Optional.of(read(input)) : Optional.empty();
    }
    private static void write(DataOutputStream output, ResourceSiteHarvestWorkAcceptance value) throws IOException {
        byte[] receipt = ResourceSitePayloadCodecs.harvestProgressed().encode(value.receipt());
        output.writeInt(receipt.length); output.write(receipt);
        output.writeByte(value.transition().isHarvestAndReplant() ? 2 : 1);
        writeCondition(output, value.transition().before()); writeCondition(output, value.transition().after());
    }
    private static ResourceSiteHarvestWorkAcceptance read(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size < 1 || size > MAX_RECEIPT_BYTES) throw new IllegalArgumentException("field acceptance receipt exceeds its bound");
        byte[] bytes = input.readNBytes(size);
        if (bytes.length != size) throw new IllegalArgumentException("truncated field acceptance receipt");
        var receipt = (ResourceSiteHarvestProgressed) ResourceSitePayloadCodecs.harvestProgressed().decode(bytes);
        int mode = input.readUnsignedByte();
        var before = readCondition(input); var after = readCondition(input);
        var transition = switch (mode) {
            case 1 -> ResourceFieldCellTransition.between(receipt.siteId(), receipt.epoch(), receipt.layoutRevision(),
                    receipt.cellId(), before, after);
            case 2 -> ResourceFieldCellTransition.harvestAndReplant(receipt.siteId(), receipt.epoch(), receipt.layoutRevision(),
                    receipt.cellId(), before);
            default -> throw new IllegalArgumentException("unknown accepted field work mode " + mode);
        };
        if (!transition.after().equals(after)) throw new IllegalArgumentException("accepted field work has a foreign successor");
        return new ResourceSiteHarvestWorkAcceptance(receipt, transition);
    }
    private static void writeCondition(DataOutputStream output, ResourceFieldPhysicalSurface.Condition value) throws IOException {
        output.writeByte(ResourceFieldCycleStateCodec.soilTag(value.soil()));
        output.writeByte(ResourceFieldCycleStateCodec.cropTag(value.crop())); output.writeByte(value.growthStage());
    }
    private static ResourceFieldPhysicalSurface.Condition readCondition(DataInputStream input) throws IOException {
        return new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycleStateCodec.soil(input.readUnsignedByte()),
                ResourceFieldCycleStateCodec.crop(input.readUnsignedByte()), input.readUnsignedByte());
    }
}
