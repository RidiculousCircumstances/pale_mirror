package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.kernel.KernelPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticProducerContract;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Closed persistence-side wiring from a deterministic process owner to its stable codecs.
 *
 * <p>Process code deliberately does not import persistence. The runtime joins this catalog to
 * the process descriptors and rejects any disagreement before an engine starts. Keeping the
 * mapping here preserves that dependency direction while making a codec's owner explicit.</p>
 */
public final class FrontierWorldProcessCodecs {
    private static final Map<String, PayloadCodecs> BY_PROCESS = createByProcess();

    private FrontierWorldProcessCodecs() { }

    public static PayloadCodecs create() {
        PayloadCodecs codecs = PayloadCodecs.merge(BY_PROCESS.values().toArray(PayloadCodecs[]::new));
        DiagnosticProducerContract.requireCompleteInventory(codecs);
        return codecs;
    }

    public static Map<String, Set<String>> typesByProcess() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        BY_PROCESS.forEach((owner, codecs) -> result.put(owner, codecs.types()));
        return Map.copyOf(result);
    }

    private static Map<String, PayloadCodecs> createByProcess() {
        Map<String, PayloadCodecs> result = new LinkedHashMap<>();
        result.put("kernel-schedule", PayloadCodecs.merge(
                KernelPayloadCodecs.scheduleEffects(), FrontierWorldPayloadCodecs.kernelDiagnosticCodecs()));
        result.put("physical-observation", FrontierWorldPayloadCodecs.physicalCodecs());
        result.put("replica-custody", FrontierWorldPayloadCodecs.replicaCustodyCodecs());
        result.put("ambient-actors", FrontierWorldPayloadCodecs.ambientCodecs());
        result.put("scene-lifecycle", FrontierWorldPayloadCodecs.sceneLifecycleCodecs());
        result.put("population", FrontierWorldPayloadCodecs.populationCodecs());
        result.put("actor-movement", ActorMovementPayloadCodecs.create());
        result.put("actor-execution", ActorExecutionPayloadCodecs.create());
        result.put("actor-body", ActorBodyPayloadCodecs.create());
        result.put("economy", FrontierWorldPayloadCodecs.economyCodecs());
        result.put("goods-trade", GoodsTradePayloadCodecs.create());
        result.put("shipments", ShipmentPayloadCodecs.create());
        result.put("unit-groups", UnitGroupPayloadCodecs.groups());
        result.put("pedestrian-planning", PedestrianPlanningPayloadCodecs.create());
        result.put("transport-missions", UnitGroupPayloadCodecs.transport());
        result.put("unit-inventory", UnitInventoryPayloadCodecs.create());
        result.put("expedition-supplies", ExpeditionSupplyPayloadCodecs.create());
        result.put("resource-sites", FrontierWorldPayloadCodecs.resourceSiteCodecs());
        result.put("hive", FrontierWorldPayloadCodecs.hiveCodecs());
        result.put("infrastructure", FrontierWorldPayloadCodecs.infrastructureCodecs());
        result.put("settlement-service-work", FrontierWorldPayloadCodecs.settlementServiceWorkCodecs());
        result.put("strategy", FrontierWorldPayloadCodecs.strategyCodecs());
        return Map.copyOf(result);
    }
}
