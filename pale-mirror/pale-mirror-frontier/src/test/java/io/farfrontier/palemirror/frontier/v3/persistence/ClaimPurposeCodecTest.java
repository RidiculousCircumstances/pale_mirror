package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ClaimAllocation;
import io.farfrontier.palemirror.frontier.v3.model.ClaimPurpose;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceLedger;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceLot;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClaimPurposeCodecTest {
    @Test
    void claimOwnerPurposeIsDurableAndUnknownTagFailsClosed() throws Exception {
        byte[] ration = encode(ClaimPurpose.SETTLEMENT_RATION);
        byte[] supply = encode(ClaimPurpose.SUPPLY_CONTRACT);
        assertEquals(ration.length, supply.length);
        List<Integer> changed = IntStream.range(0, ration.length).filter(index -> ration[index] != supply[index]).boxed().toList();
        assertEquals(1, changed.size(), "only the declared purpose tag may change between otherwise identical claims");
        int purposeOffset = changed.getFirst();
        assertEquals(2, Byte.toUnsignedInt(ration[purposeOffset]));
        assertEquals(3, Byte.toUnsignedInt(supply[purposeOffset]));
        assertEquals(ClaimPurpose.SETTLEMENT_RATION, decode(ration).claims().values().iterator().next().purpose());
        assertEquals(ClaimPurpose.SUPPLY_CONTRACT, decode(supply).claims().values().iterator().next().purpose());
        ration[purposeOffset] = 127;
        assertThrows(IllegalArgumentException.class, () -> decode(ration));
    }

    private static byte[] encode(ClaimPurpose purpose) throws Exception {
        SubjectId owner = new SubjectId("settlement:1"), lot = new SubjectId("lot:bread"), account = new SubjectId("custody:bread");
        FungibleResourceLedger ledger = FungibleResourceLedger.empty().issue(
                new ResourceLot(lot, owner, "minecraft:bread", 8, "test", List.of()),
                new CustodyAccount(account, new ResourceCustody.Container(new SubjectId("container:1-depot")), Map.of(lot, 8), Map.of()));
        ledger = ledger.reserve(new ClaimAllocation(new SubjectId("claim:bread"), owner, owner, "minecraft:bread", 8,
                Map.of(lot, 8), purpose), account);
        var bytes = new ByteArrayOutputStream();
        FungibleResourceStateCodec.write(new DataOutputStream(bytes), ledger);
        return bytes.toByteArray();
    }

    private static FungibleResourceLedger decode(byte[] bytes) throws Exception {
        return FungibleResourceStateCodec.read(new DataInputStream(new ByteArrayInputStream(bytes)));
    }
}
