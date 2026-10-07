package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianPlanningReady;
import java.util.List;

final class PedestrianPlanningPayloadCodecs {
    private PedestrianPlanningPayloadCodecs() { }
    static PayloadCodecs create() {
        return new PayloadCodecs(List.of(new PayloadCodec() {
            @Override public String type() { return "frontier.pedestrian_planning_ready"; }
            @Override public byte[] encode(FrontierPayload payload) {
                return KernelCodec.encodeScheduledAction(((PedestrianPlanningReady) payload).expected());
            }
            @Override public FrontierPayload decode(byte[] bytes) {
                return new PedestrianPlanningReady(KernelCodec.decodeScheduledAction(bytes));
            }
        }));
    }
}
