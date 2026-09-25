package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestDeliveryObservation;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

/** Shared field-receipt body for WAL payload and checkpoint state; the outer codecs own tags/IDs. */
final class ResourceSiteHarvestDeliveryObservationCodec {
    private ResourceSiteHarvestDeliveryObservationCodec() { }

    static void write(DataOutputStream output, ResourceSiteHarvestDeliveryObservation value) throws IOException {
        string(output, value.siteId().value()); string(output, value.jobId().value()); string(output, value.workerId().value());
        string(output, value.actorAccountId().value()); string(output, value.depotAccountId().value());
        string(output, value.leaseId().value());
        output.writeLong(value.entityId().getMostSignificantBits()); output.writeLong(value.entityId().getLeastSignificantBits());
        output.writeLong(value.actorEpoch()); output.writeByte(value.harvestedQuantity()); output.writeLong(value.depotEpoch());
        output.writeByte(value.depotStacks().size());
        for (FungiblePhysicalObservation.Stack stack : value.depotStacks()) {
            if (!(stack.address() instanceof PhysicalStackAddress.ContainerSlot slot))
                throw new IllegalArgumentException("field delivery receipt requires container-slot stacks");
            string(output, slot.slot().containerId().value()); output.writeByte(slot.slot().slot());
            string(output, stack.itemKind()); output.writeByte(stack.quantity());
        }
        string(output, value.depotFingerprint()); output.writeLong(value.emittedCanonicalRevision());
        string(output, value.witnessId());
    }

    static ResourceSiteHarvestDeliveryObservation read(DataInputStream input, PhysicalObservationId id,
                                                       PhysicalIntentId intent) throws IOException {
        SubjectId site = subject(input), job = subject(input), worker = subject(input);
        SubjectId actorAccount = subject(input), depotAccount = subject(input);
        SceneLeaseId lease = new SceneLeaseId(FrontierWorldStateCodec.readString(input));
        UUID entity = new UUID(input.readLong(), input.readLong());
        long actorEpoch = input.readLong(); int quantity = input.readUnsignedByte(); long depotEpoch = input.readLong();
        int count = input.readUnsignedByte();
        if (count > 128) throw new IllegalArgumentException("field delivery chest layout exceeds its bounded receipt");
        ArrayList<FungiblePhysicalObservation.Stack> stacks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            InventoryCustody.ContainerSlot slot = new InventoryCustody.ContainerSlot(subject(input), input.readUnsignedByte());
            stacks.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(slot),
                    FrontierWorldStateCodec.readString(input), input.readUnsignedByte()));
        }
        return new ResourceSiteHarvestDeliveryObservation(id, intent, site, job, worker, actorAccount,
                depotAccount, lease, entity, actorEpoch, quantity, depotEpoch, stacks,
                FrontierWorldStateCodec.readString(input), input.readLong(), FrontierWorldStateCodec.readString(input));
    }

    private static void string(DataOutputStream output, String value) throws IOException {
        FrontierWorldStateCodec.writeString(output, value);
    }
    private static SubjectId subject(DataInputStream input) throws IOException {
        return new SubjectId(FrontierWorldStateCodec.readString(input));
    }
}
