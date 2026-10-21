package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.nbt.*;
import java.util.*;

/** One original native stack and its complete, detached Vanilla drop plan.
 * A prepared plan is not evidence that any fragment reached the physical world. */
record FrontierV3ContainerDropTransaction(SubjectId container, int slot, FrontierV3OperationReceipt operation,
        List<FungibleResourceHandoffObserved> resources, List<ExactItemCustodyChanged> exactItems) {
    FrontierV3ContainerDropTransaction {
        Objects.requireNonNull(container); Objects.requireNonNull(operation);
        resources = List.copyOf(resources); exactItems = List.copyOf(exactItems);
        var declaration = operation.declaration();
        if (slot < 0 || slot >= 54 || resources.size() + exactItems.size() > 64
                || declaration.protocol() != FrontierV3OperationReceipt.Protocol.CONTAINER_DROP
                || !declaration.owner().equals(FrontierExecutionSubjects.PHYSICAL_EXECUTOR))
            throw new IllegalArgumentException("invalid native container drop transaction");
        var entities = new HashSet<UUID>();
        for (var transfer : resources) {
            if (!(transfer.destinationAccount().custody() instanceof ResourceCustody.Ground ground)
                    || !entities.add(ground.entityId())) throw new IllegalArgumentException("drop lacks a unique ground destination");
        }
        for (var transfer : exactItems) {
            if (!transfer.from().equals(new InventoryCustody.ContainerSlot(container, slot))
                    || !(transfer.to() instanceof InventoryCustody.WorldCarrier carrier) || !entities.add(carrier.carrierId()))
                throw new IllegalArgumentException("exact drop changed its source or destination");
        }
        new PhysicalInventoryTransfersObserved(resources, exactItems);
        var births = declaration.participants().stream().filter(p -> p.medium() == FrontierV3OperationReceipt.Medium.ENTITIES)
                .map(p -> UUID.fromString(p.identity())).collect(java.util.stream.Collectors.toSet());
        if (!births.containsAll(entities) || declaration.participants().size() != entities.size() + 1)
            throw new IllegalArgumentException("drop plan lacks exact native birth participants and one source");
    }
    UUID id() { return operation.ticket(); }
    boolean blocks(UUID entity) {
        return resources.stream().anyMatch(r -> ((ResourceCustody.Ground) r.destinationAccount().custody()).entityId().equals(entity))
                || exactItems.stream().anyMatch(i -> ((InventoryCustody.WorldCarrier) i.to()).carrierId().equals(entity));
    }
    PhysicalInventoryTransfersObserved outcome() {
        return new PhysicalInventoryTransfersObserved(resources, exactItems);
    }
    FrontierV3ContainerDropTransaction bind(FrontierV3OperationReceipt receipt) {
        if (!receipt.ticket().equals(id()) || !receipt.declaration().equals(operation.declaration())
                || receipt.sequence() != operation.sequence() || !receipt.applied().containsAll(operation.applied())
                || !receipt.saved().containsAll(operation.saved()) || operation.ownerClosed() && !receipt.ownerClosed())
            throw new IllegalArgumentException("drop changed its native operation");
        return new FrontierV3ContainerDropTransaction(container, slot, receipt, resources, exactItems);
    }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putString("container", container.value()); tag.putInt("slot", slot);
        tag.put("operation", FrontierV3OperationReceiptCodec.write(operation));
        tag.putByteArray("outcome", FrontierWorldRuntimeDefinition.payloadCodecs().encode(outcome()));
        return tag;
    }
    static FrontierV3ContainerDropTransaction read(CompoundTag tag) {
        if (!tag.contains("outcome", Tag.TAG_BYTE_ARRAY)) throw new IllegalArgumentException("native drop lacks its complete plan");
        var draft = (PhysicalInventoryTransfersObserved) FrontierWorldRuntimeDefinition.payloadCodecs()
                .decode("frontier.physical_inventory_transfers_observed", tag.getByteArray("outcome"));
        var result = new FrontierV3ContainerDropTransaction(new SubjectId(tag.getString("container")), tag.getInt("slot"),
                FrontierV3OperationReceiptCodec.read(tag.getCompound("operation")), draft.resources(), draft.exactItems());
        if (!result.save().equals(tag)) throw new IllegalArgumentException("noncanonical container drop witness");
        return result;
    }
}
