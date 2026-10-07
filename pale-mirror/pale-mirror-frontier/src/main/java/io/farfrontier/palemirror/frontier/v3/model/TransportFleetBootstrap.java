package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.expedition.TransportAsset;
import io.farfrontier.palemirror.frontier.v3.model.expedition.TransportFleet;
import java.util.LinkedHashMap;
import java.util.Optional;

/** Explicit finite bootstrap grant. Never called by recovery or loss handling. */
final class TransportFleetBootstrap {
    private TransportFleetBootstrap() { }
    static TransportFleet initial(FrontierBootstrap bootstrap) {
        if (bootstrap.ruleset().schemaVersion() < 17) return TransportFleet.empty();
        var assets = new LinkedHashMap<SubjectId, TransportAsset>();
        for (var settlement : bootstrap.settlements()) {
            // The common apron compiler owns this supported ground. The last of 48
            // declared slots is beyond the bootstrap resident census (at most 40).
            var apron = SettlementResidentIngressPlan.compile(bootstrap.bounds(), bootstrap.terrain(), settlement, 48);
            var actor = new SubjectId("actor:pack/" + settlement.id().value().replace(':', '-'));
            var container = new SubjectId("container:pack/" + settlement.id().value().replace(':', '-'));
            assets.put(actor, new TransportAsset(actor, settlement.id(), TransportAsset.Kind.CHEST_DONKEY,
                    container, 15, new SurfaceAnchor(apron.homeSlots().getLast()), Optional.empty()));
        }
        return new TransportFleet(assets);
    }
}
