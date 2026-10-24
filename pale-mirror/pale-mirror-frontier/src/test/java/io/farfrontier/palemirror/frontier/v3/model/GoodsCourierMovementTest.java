package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.farfrontier.palemirror.frontier.v3.model.GoodsTradeTest.*;

/** Actual route boundaries and restart preserve cargo; only recipient acceptance settles the sale. */
class GoodsCourierMovementTest {
    @Test void courierActuallyWalksBothColdLegsWithTheCommonClockAndDoesNotSellOnArrival() {
        FrontierWorldState state = reserved(); Shipment shipment = shipment(state);
        var actor = shipment.execution().actorId(); var beforeBuyer = state.inventory().economics().require(BUYER).balance();
        state = shipmentFact(state, SELLER, new ShipmentDispatched(shipment));
        long now = 1;
        for (int leg = 0; leg < 2; leg++) {
            var action = io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.progress(shipment.id(), now);
            var start = io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.plan(state, action, now);
            assertTrue(start.stream().anyMatch(event -> event.payload()
                    instanceof io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementStarted),
                    "leg=" + leg + " start=" + state.actorLocations().get(actor).body() + " goal=" + state.shipments().shipments().get(shipment.id()).movementOrder());
            state = applyEvents(state, start, now, "shipments");
            var movement = state.actorMovements().get(actor);
            assertEquals(shipment.execution(), movement.executionId());
            now++;
            state = applyEvents(state, io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.plan(state,
                    io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.progress(movement, now), now), now, "actor-movement");
            movement = state.actorMovements().get(actor);
            var travel = movement.coldTravel().orElseThrow();
            assertTrue(travel.route().size() > 1);
            long middle = travel.departedAtTick() + (travel.arrivalTick() - travel.departedAtTick()) / 2;
            var expectedBody = io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.bodyAt(state, actor, middle);
            var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
            assertEquals(expectedBody, io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.bodyAt(state, actor, middle));
            now = travel.arrivalTick();
            state = applyEvents(state, io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.plan(state,
                    io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.progress(movement, now), now), now, "actor-movement");
            // Reaching the exterior boundary is not the cargo operation's arrival.
            for (int boundary = 0; state.actorMovements().containsKey(actor) && boundary < 8; boundary++) {
                movement = state.actorMovements().get(actor);
                now = movement.coldTravel().map(io.farfrontier.palemirror.frontier.v3.model.navigation.TimedKnownRoute::arrivalTick)
                        .orElse(now + 1);
                state = applyEvents(state, io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.plan(state,
                        io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.progress(movement, now), now), now, "actor-movement");
                assertEquals(beforeBuyer, state.inventory().economics().require(BUYER).balance(),
                        "neither approach nor admitted entrance settles the sale");
            }
            assertFalse(state.actorMovements().containsKey(actor));
            state.actorExecutions().requireCurrent(shipment.execution());
            assertEquals(leg == 0 ? shipment.sender().station() : shipment.receiver().station(), state.actorLocations().get(actor).supportingSurface());
            assertEquals(beforeBuyer, state.inventory().economics().require(BUYER).balance());
            now++;
            state = applyEvents(state, io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.plan(state,
                    io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.progress(shipment.id(), now), now), now, "shipments");
            assertEquals(leg == 0 ? Shipment.Status.CARRYING : Shipment.Status.DELIVERED, state.shipments().shipments().get(shipment.id()).status());
            now++;
        }
        assertEquals(beforeBuyer, state.inventory().economics().require(BUYER).balance());
        assertEquals(SELLER, state.inventory().fungibleResources().lots().get(LOT).economicOwnerId());
        long executedAt = Math.max(now + 59, state.companies().goodsTrade().orders().get(SELL_ORDER).expiresAtTick() + 1);
        var receiptReview = io.farfrontier.palemirror.frontier.v3.process.GoodsTradeReceiptProcess.review(CONTRACT,
                state.shipments().shipments().get(shipment.id()).reception().orElseThrow().id(), now);
        var receiptEvents = FrontierWorldRuntimeDefinition.planScheduled(state, receiptReview, new SimInstant(executedAt));
        assertTrue(receiptEvents.stream().map(ProposedEvent::payload)
                .filter(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class::isInstance)
                .map(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled.class::cast)
                .allMatch(rescheduled -> rescheduled.replacement().dueAt().ticks() == executedAt + 1));
        assertTrue(receiptEvents.stream().map(ProposedEvent::payload)
                .filter(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created.class::isInstance)
                .map(io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created.class::cast)
                .allMatch(created -> created.action().dueAt().ticks() == executedAt + 1));
        state = applyEvents(state, receiptEvents, executedAt, "goods-trade");
        assertEquals(beforeBuyer.minus(FixedScalar.whole(60)), state.inventory().economics().require(BUYER).balance());
        assertTrue(state.companies().goodsTrade().contracts().get(CONTRACT).fulfilled());
    }

}
