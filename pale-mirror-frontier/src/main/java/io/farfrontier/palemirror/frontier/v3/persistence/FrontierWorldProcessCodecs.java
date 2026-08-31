package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.kernel.KernelPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;

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
        return PayloadCodecs.merge(BY_PROCESS.values().toArray(PayloadCodecs[]::new));
    }

    public static Map<String, Set<String>> typesByProcess() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        BY_PROCESS.forEach((owner, codecs) -> result.put(owner, codecs.types()));
        return Map.copyOf(result);
    }

    private static Map<String, PayloadCodecs> createByProcess() {
        Map<String, PayloadCodecs> result = new LinkedHashMap<>();
        result.put("kernel-schedule", KernelPayloadCodecs.scheduleEffects());
        result.put("physical-observation", FrontierWorldPayloadCodecs.physicalCodecs());
        result.put("ambient-actors", FrontierWorldPayloadCodecs.ambientCodecs());
        result.put("logistics-scenes", FrontierWorldPayloadCodecs.logisticsCodecs());
        result.put("population", FrontierWorldPayloadCodecs.populationCodecs());
        result.put("economy", FrontierWorldPayloadCodecs.economyCodecs());
        result.put("resource-sites", FrontierWorldPayloadCodecs.resourceSiteCodecs());
        result.put("hive", FrontierWorldPayloadCodecs.hiveCodecs());
        result.put("infrastructure", FrontierWorldPayloadCodecs.infrastructureCodecs());
        result.put("strategy", FrontierWorldPayloadCodecs.strategyCodecs());
        return Map.copyOf(result);
    }
}
