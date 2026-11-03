package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3LocalGoalCorrectionTest {
    @Test void correctionCannotOutliveOrBorrowItsParentOrderOrBodyAndDoesNotAwardArrival(){
        var execution=new ActorExecutionId(new SubjectId("resident:guidance"),ActorActivityKind.COURIER,new SubjectId("shipment:guidance"),1);
        var body=new ActorActuationId(new ActorBodyId(execution.actorId(),1),execution);
        var station=SurfaceAnchor.at(0,63,0);var correction=station.offset(1,0,1);
        var order=new MovementOrder(execution.activityOwnerId(),execution.actorId(),1,1,List.of(station),TraversalCapability.PEDESTRIAN,MovementOrder.ArrivalPolicy.EXACT_STATION);
        var envelope=LocalNavigationEnvelope.around(station.standingBody(),correction.standingBody());
        var value=new FrontierV3LocalGoalCorrection.Correction(body,order,1,correction,envelope,120);
        assertTrue(value.current(body,order,120));assertFalse(value.current(body,order,121));
        assertFalse(order.arrivedAt(correction));
        assertFalse(value.current(new ActorActuationId(new ActorBodyId(execution.actorId(),2),execution),order,100));
        var successor=new MovementOrder(order.ownerId(),order.actorId(),1,2,order.legalStations(),order.capability(),order.arrivalPolicy());
        assertFalse(value.current(body,successor,100));
        assertThrows(IllegalArgumentException.class,()->new FrontierV3LocalGoalCorrection.Correction(body,order,1,station.offset(20,0,0),envelope,120));
    }
}
