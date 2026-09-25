package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FungibleProductionObservationCodecTest {
    @Test
    void snapshotAndWalRetainTheSameNominalLotReceipt() throws Exception {
        var receipt = receipt(3L, List.of(stack("container:1-depot", 0)));
        var bytes = new ByteArrayOutputStream();
        PhysicalEffectObservationPayloadCodec.write(new DataOutputStream(bytes), receipt);
        assertEquals(23, Byte.toUnsignedInt(bytes.toByteArray()[0]));
        assertEquals(receipt, PhysicalEffectObservationPayloadCodec.read(input(bytes.toByteArray())));
        byte[] unknown = bytes.toByteArray(); unknown[0] = 127;
        assertThrows(IllegalArgumentException.class, () -> PhysicalEffectObservationPayloadCodec.read(input(unknown)));
        byte[] truncated = java.util.Arrays.copyOf(bytes.toByteArray(), bytes.size() - 1);
        assertThrows(IOException.class, () -> PhysicalEffectObservationPayloadCodec.read(input(truncated)));
        bytes.reset();
        PhysicalEffectObservationStateCodec.write(new DataOutputStream(bytes), Map.of(receipt.id(), receipt));
        assertEquals(Map.of(receipt.id(), receipt), PhysicalEffectObservationStateCodec.read(input(bytes.toByteArray())));
    }

    @Test
    void invalidEpochDuplicateForeignAndEmptyLayoutsAreNotReceipts() {
        var stack = stack("container:1-depot", 0);
        assertThrows(IllegalArgumentException.class, () -> receipt(0L, List.of(stack)));
        assertThrows(IllegalArgumentException.class, () -> receipt(3L, List.of(stack, stack)));
        assertThrows(IllegalArgumentException.class, () -> receipt(3L, List.of(stack("container:foreign", 0))));
        assertThrows(IllegalArgumentException.class, () -> receipt(3L, List.of()));
        assertThrows(IllegalArgumentException.class, () -> receipt(3L, List.of(stack("container:1-depot", 54))));
    }

    @Test
    void twoInputLotReceiptRetainsBothExactPortionsAcrossWalAndSnapshot() throws Exception {
        SubjectId first = new SubjectId("lot:field-one");
        SubjectId second = new SubjectId("lot:field-two");
        var receipt = new FungibleProductionObservation(new PhysicalObservationId("observation:two-input-production"),
                new PhysicalIntentId("intent:two-input-production"), new SubjectId("custody:production"),
                new SubjectId("container:1-depot"), first, Map.of(first, 32, second, 32),
                new SubjectId("claim:two-input-production"), new SubjectId("lot:bread"), 64, 3L,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 0)), "minecraft:bread", 64)));
        var bytes = new ByteArrayOutputStream();
        PhysicalEffectObservationPayloadCodec.write(new DataOutputStream(bytes), receipt);
        assertEquals(24, Byte.toUnsignedInt(bytes.toByteArray()[0]));
        assertEquals(receipt, PhysicalEffectObservationPayloadCodec.read(input(bytes.toByteArray())));
        bytes.reset();
        PhysicalEffectObservationStateCodec.write(new DataOutputStream(bytes), Map.of(receipt.id(), receipt));
        assertEquals(Map.of(receipt.id(), receipt), PhysicalEffectObservationStateCodec.read(input(bytes.toByteArray())));
        assertThrows(IllegalArgumentException.class, () -> new FungibleProductionObservation(receipt.id(), receipt.intentId(),
                receipt.accountId(), receipt.containerId(), first, Map.of(first, 32, second, 31), receipt.claimId(),
                receipt.outputLotId(), 64, 3L, receipt.observedStacks()));
    }

    private static FungibleProductionObservation receipt(long epoch, List<FungiblePhysicalObservation.Stack> stacks) {
        return new FungibleProductionObservation(new PhysicalObservationId("observation:production"), new PhysicalIntentId("intent:production"),
                new SubjectId("custody:production"), new SubjectId("container:1-depot"), new SubjectId("lot:wheat"),
                new SubjectId("claim:wheat"), new SubjectId("lot:bread"), 32, epoch, stacks);
    }

    private static FungiblePhysicalObservation.Stack stack(String container, int slot) {
        return new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(new SubjectId(container), slot)), "minecraft:bread", 32);
    }

    private static DataInputStream input(byte[] bytes) { return new DataInputStream(new ByteArrayInputStream(bytes)); }
}
