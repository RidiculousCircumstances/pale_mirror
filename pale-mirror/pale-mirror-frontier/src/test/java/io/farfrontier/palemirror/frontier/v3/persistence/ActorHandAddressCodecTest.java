package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceLedger;
import io.farfrontier.palemirror.frontier.v3.model.FungibleStackLayoutObserved;
import io.farfrontier.palemirror.frontier.v3.model.FungibleStockDepartureObserved;
import io.farfrontier.palemirror.frontier.v3.model.FungibleStockContributionObserved;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceLot;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActorHandAddressCodecTest {
    private static final SubjectId ACTOR = new SubjectId("resident:field-worker");
    private static final SubjectId ACCOUNT = new SubjectId("custody:field-worker-harvest");
    private static final SubjectId LOT = new SubjectId("lot:field-worker-part");
    private static final UUID BODY = UUID.fromString("00000000-0000-0000-0000-000000000141");

    @Test void actorHandRetainsNominalActorAndExactBodyAcrossSnapshotAndEventCodecs() throws Exception {
        ResourceLot wheat = new ResourceLot(LOT, new SubjectId("settlement:one"), "minecraft:wheat", 2,
                "field:one:epoch-1:part-0", List.of());
        CustodyAccount account = new CustodyAccount(ACCOUNT, new ResourceCustody.Actor(ACTOR), Map.of(LOT, 2), Map.of());
        PhysicalStackAddress.ActorHand hand = new PhysicalStackAddress.ActorHand(ACTOR, BODY);
        PhysicalStackBinding binding = new PhysicalStackBinding(new SubjectId("binding:field-worker-hand"), ACCOUNT,
                hand, 1L, "minecraft:wheat", Map.of(LOT, 2), Map.of());
        FungibleResourceLedger ledger = new FungibleResourceLedger(Map.of(LOT, wheat), Map.of(),
                Map.of(ACCOUNT, account), Map.of(binding.id(), binding));

        var bytes = new ByteArrayOutputStream();
        FungibleResourceStateCodec.write(new DataOutputStream(bytes), ledger);
        assertEquals(ledger, FungibleResourceStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));

        var observed = new FungibleStackLayoutObserved(ACCOUNT, 1L,
                List.of(new FungiblePhysicalObservation.Stack(hand, "minecraft:wheat", 2)));
        var codecs = FungibleResourcePayloadCodecs.observationCodecs();
        assertEquals(observed, codecs.decode(observed.type(), codecs.encode(observed)));

        var departure = new FungibleStockDepartureObserved(ACCOUNT, new SubjectId("container:depot"),
                new SubjectId("settlement:one"), 4L, BODY,
                UUID.fromString("00000000-0000-0000-0000-000000000142"), Map.of(LOT, 1), List.of());
        assertEquals(departure, codecs.decode(departure.type(), codecs.encode(departure)));

        var gift = new FungibleStockContributionObserved(ACCOUNT, new SubjectId("container:depot"), 4L,
                BODY, UUID.fromString("00000000-0000-0000-0000-000000000143"),
                new ResourceLot(new SubjectId("lot:player-gift"), new SubjectId("settlement:one"),
                        "minecraft:bread", 4, "player-gift", List.of()),
                List.of(new FungiblePhysicalObservation.Stack(
                        new PhysicalStackAddress.ContainerSlot(new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot(
                                new SubjectId("container:depot"), 0)), "minecraft:bread", 4)));
        assertEquals(gift, codecs.decode(gift.type(), codecs.encode(gift)));

        var forged = new PhysicalStackBinding(new SubjectId("binding:foreign-hand"), ACCOUNT,
                new PhysicalStackAddress.ActorHand(new SubjectId("resident:other-worker"), BODY), 1L,
                "minecraft:wheat", Map.of(LOT, 2), Map.of());
        assertThrows(IllegalArgumentException.class, () -> new FungibleResourceLedger(Map.of(LOT, wheat), Map.of(),
                Map.of(ACCOUNT, account), Map.of(forged.id(), forged)));
    }
}
