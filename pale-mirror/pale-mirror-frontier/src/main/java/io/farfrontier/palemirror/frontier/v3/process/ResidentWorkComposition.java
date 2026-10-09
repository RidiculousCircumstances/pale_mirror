package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;

/** Composition root only. Families supply readiness; the selector owns permission/priority decisions. */
public final class ResidentWorkComposition {
    public static final ResidentWorkSelection SELECTION = create(List.of(
            new ResidentWorkAvailabilityPort() {
                public ResidentWorkKind kind() { return ResidentWorkKind.AGRICULTURE; }
                public HumanCapability capability() { return HumanCapability.AGRICULTURE; }
                public boolean available(FrontierWorldState state, ResidentProfile resident, long tick) {
                    return ResourceSiteHarvestPlanning.availableWork(state, resident, tick);
                }
            }, new ResidentWorkAvailabilityPort() {
                public ResidentWorkKind kind() { return ResidentWorkKind.BAKING; }
                public HumanCapability capability() { return HumanCapability.INDUSTRY; }
                public boolean available(FrontierWorldState state, ResidentProfile resident, long tick) {
                    return BakeryJobAdmission.availableWork(state, resident, tick);
                }
            }, new ResidentWorkAvailabilityPort() {
                public ResidentWorkKind kind() { return ResidentWorkKind.LOGISTICS; }
                public HumanCapability capability() { return HumanCapability.LOGISTICS; }
                public boolean available(FrontierWorldState state, ResidentProfile resident, long tick) {
                    return GoodsShipmentPlanning.availableWork(state, resident, tick) || ExtractionHaulingPolicy.available(state, resident, tick);
                }
            }, new ResidentWorkAvailabilityPort() {
                public ResidentWorkKind kind() { return ResidentWorkKind.EXTRACTION; }
                public HumanCapability capability() { return HumanCapability.EXTRACTION; }
                public boolean available(FrontierWorldState state, ResidentProfile resident, long tick) {
                    return ExtractionWorkPlanning.availableWork(state, resident, tick);
                }
            }));
    private ResidentWorkComposition() { }
    private static ResidentWorkSelection create(List<ResidentWorkAvailabilityPort> ports) {
        var registered = ports.stream().map(ResidentWorkAvailabilityPort::kind)
                .collect(java.util.stream.Collectors.toSet());
        if (!registered.equals(java.util.EnumSet.allOf(ResidentWorkKind.class)))
            throw new IllegalArgumentException("every enabled work kind must have one family-owned opportunity port");
        return new ResidentWorkSelection(ports);
    }
}
