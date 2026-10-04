package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierMedicalTreatmentSceneSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.level.ServerLevel;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/** Closed participant authority strategies, separate from exact stack/effect mechanics. */
final class FrontierV3ExactConsumptionAuthority {
    @FunctionalInterface interface Permission { boolean current(); }
    @FunctionalInterface interface Capture {
        Optional<Permission> capture(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent);
    }
    record Policy(PhysicalIntentLifecycleOwner owner, BiPredicate<FrontierWorldState, PhysicalIntent> eligible, Capture capture) { }
    private static final Map<PhysicalIntentLifecycleOwner, Policy> POLICIES = List.of(
            // Hive growth is a retained machine/cocoon effect, not resident body actuation.
            new Policy(PhysicalIntentLifecycleOwner.HIVE_GROWTH, (state, intent) -> true,
                    (level, runtime, intent) -> Optional.of(() -> true)),
            new Policy(PhysicalIntentLifecycleOwner.MEDICAL_TREATMENT, (state, intent) -> intent.status() == PhysicalIntentStatus.PREPARED
                    ? FrontierMedicalTreatmentSceneSupport.permitsCurrentConsumptionIntent(state, intent)
                    : FrontierMedicalTreatmentSceneSupport.permitsConsumptionReceipt(state, intent),
                    FrontierV3MedicalTreatmentSceneExecutor::consumptionAuthority),
            // Retired ration-cycle wire ownership grants neither selection nor mutation.
            new Policy(PhysicalIntentLifecycleOwner.SETTLEMENT_PROVISION, (state, intent) -> false,
                    (level, runtime, intent) -> Optional.empty()))
            .stream().collect(Collectors.toUnmodifiableMap(Policy::owner, policy -> policy));

    private FrontierV3ExactConsumptionAuthority() { }
    static boolean eligible(FrontierWorldState state, PhysicalIntent intent) {
        return policy(intent).eligible().test(state, intent);
    }
    static Optional<Permission> capture(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent) {
        return policy(intent).capture().capture(level, runtime, intent);
    }
    private static Policy policy(PhysicalIntent intent) {
        var policy = POLICIES.get(intent.lifecycleOwner());
        if (policy == null) throw new IllegalArgumentException("exact consumption has no declared participant authority policy");
        return policy;
    }
}
