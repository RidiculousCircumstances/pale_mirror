package io.farfrontier.palemirror.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ResourceTransferTest {
    private static final WorldObjectId COMMUNITY = new WorldObjectId("pale_mirror:test_community");

    @Test
    void receiptedDepositAndWithdrawalChangeOnlyCanonicalStock() {
        WorldState state = stateWithIron(100, 40, 12);
        DomainServices services = new DomainServices();

        services.commands().execute(state, new DomainCommand.DepositResource(COMMUNITY, ResourceKind.IRON, 20, "transfer:deposit"));
        assertEquals(60, state.economy(COMMUNITY).orElseThrow().require(ResourceKind.IRON).stock());
        assertTrue(state.history().stream().anyMatch(value -> value.type() == DomainEventType.RESOURCE_DEPOSITED));

        services.commands().execute(state, new DomainCommand.WithdrawResource(COMMUNITY, ResourceKind.IRON,
                16, 24, "transfer:withdraw"));
        assertEquals(44, state.economy(COMMUNITY).orElseThrow().require(ResourceKind.IRON).stock());
    }

    @Test
    void withdrawalFailsClosedAtEmergencyReserve() {
        WorldState state = stateWithIron(100, 39, 12);
        assertTrue(new DomainServices().commands().execute(state, new DomainCommand.WithdrawResource(COMMUNITY,
                ResourceKind.IRON, 16, 24, "transfer:withdraw")).isEmpty());
        assertEquals(39, state.economy(COMMUNITY).orElseThrow().require(ResourceKind.IRON).stock());
    }

    @Test
    void oversizedDepositDoesNotPartiallyApply() {
        WorldState state = stateWithIron(50, 45, 12);
        assertThrows(IllegalStateException.class, () -> new DomainServices().commands().execute(state,
                new DomainCommand.DepositResource(COMMUNITY, ResourceKind.IRON, 10, "transfer:overflow")));
        assertEquals(45, state.economy(COMMUNITY).orElseThrow().require(ResourceKind.IRON).stock(),
                "command validation must happen before callers prepare a physical transfer");
    }

    private static WorldState stateWithIron(int capacity, int stock, int consumption) {
        WorldState state = new WorldState();
        state.putEconomy(new SettlementEconomy(COMMUNITY, Map.of(ResourceKind.IRON,
                new ResourceAccount(capacity, stock, 0, consumption, consumption))));
        return state;
    }
}
