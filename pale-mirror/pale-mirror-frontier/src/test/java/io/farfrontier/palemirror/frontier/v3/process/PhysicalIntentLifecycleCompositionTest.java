package io.farfrontier.palemirror.frontier.v3.process;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PhysicalIntentLifecycleCompositionTest {
    @Test
    void installedCompositionSuppliesEveryDurableOwner() {
        assertDoesNotThrow(FrontierWorldProcessCatalog::physicalLifecycles);
        assertDoesNotThrow(() -> PhysicalIntentLifecycleCapabilities.compose(List.of(module(Arrays.stream(
                PhysicalIntentLifecycleOwner.values()).map(owner -> capability(owner, supported(owner))).toList()))));
    }

    @Test
    void compositionFailsClosedForMissingDuplicateAndMismatchedCapabilities() {
        PhysicalIntentLifecycleOwner first = PhysicalIntentLifecycleOwner.RESOURCE_SITE_HARVEST;
        FrontierWorldProcessModule missing = module(List.of(capability(first, supported(first))));
        FrontierWorldProcessModule duplicate = module(List.of(capability(first, supported(first)), capability(first, supported(first))));
        FrontierWorldProcessModule mismatch = module(List.of(capability(PhysicalIntentLifecycleOwner.RESOURCE_SITE_HARVEST,
                Set.of(PhysicalIntentKind.SCENE_STRIKE))));
        FrontierWorldProcessModule undeclared = new FrontierWorldProcessModule() {
            @Override public List<PhysicalIntentLifecycleCapability> physicalIntentLifecycleCapabilities() {
                return java.util.Collections.singletonList(null);
            }
        };
        FrontierWorldProcessModule missingRetirement = module(Arrays.stream(PhysicalIntentLifecycleOwner.values())
                .map(owner -> capability(owner, supported(owner), owner == first ? null
                        : PhysicalIntentLifecycleRetirementPolicy.noPhysical(owner))).toList());
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(missing)));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(duplicate)));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(mismatch)));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(undeclared)));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(missingRetirement)));
    }

    @Test
    void terminalInputInvokesTheExplicitOwnerRetirementPolicyRatherThanTheOrdinaryTransitionCallback() {
        PhysicalIntentLifecycleOwner owner = PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION;
        AtomicBoolean retired = new AtomicBoolean();
        List<PhysicalIntentLifecycleCapability> capabilities = Arrays.stream(PhysicalIntentLifecycleOwner.values())
                .map(candidate -> capability(candidate, supported(candidate), candidate == owner
                        ? PhysicalIntentLifecycleRetirementPolicy.of(
                        (state, command, intent, transition) -> {
                            retired.set(true);
                            return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                                    io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, "retired"));
                        }, (state, subject, intent, transition) -> state)
                        : PhysicalIntentLifecycleRetirementPolicy.noPhysical(candidate))).toList();
        PhysicalIntentLifecycleCapabilities composition = PhysicalIntentLifecycleCapabilities.compose(List.of(module(capabilities)));
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:retirement-owner"),
                PhysicalIntentKind.RESOURCE_SITE_PREPARATION, PhysicalIntentStatus.RUNNING,
                new SubjectId("site:retirement-owner"), List.of(new SubjectId("site:retirement-owner"), new SubjectId("job:retirement-owner")),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.whole(64), FixedScalar.ZERO), 0,
                PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED, owner);

        composition.planTransition(null, null, intent,
                new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty()));
        assertTrue(retired.get(), "terminal input must execute the owner-supplied retirement boundary");
    }

    @Test
    void explicitNoPhysicalPolicyRejectsRetirementRatherThanFallingBackToAnotherOwner() {
        NoPhysicalIntentLifecyclePolicy policy = new NoPhysicalIntentLifecyclePolicy(PhysicalIntentLifecycleOwner.ROUTE_PATROL);
        assertInstanceOf(CommandPlan.Rejected.class, policy.retirementPolicy().plan(null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> policy.retirementPolicy().reduce(null, null, null, null));
    }

    private static FrontierWorldProcessModule module(List<PhysicalIntentLifecycleCapability> capabilities) {
        return new FrontierWorldProcessModule() {
            @Override public List<PhysicalIntentLifecycleCapability> physicalIntentLifecycleCapabilities() { return capabilities; }
        };
    }

    private static PhysicalIntentLifecycleCapability capability(PhysicalIntentLifecycleOwner owner, Set<PhysicalIntentKind> kinds) {
        return capability(owner, kinds, PhysicalIntentLifecycleRetirementPolicy.noPhysical(owner));
    }

    private static PhysicalIntentLifecycleCapability capability(PhysicalIntentLifecycleOwner owner, Set<PhysicalIntentKind> kinds,
                                                                 PhysicalIntentLifecycleRetirementPolicy retirementPolicy) {
        return new AbstractPhysicalIntentLifecycleCapability(owner, kinds, retirementPolicy) { };
    }

    private static Set<PhysicalIntentKind> supported(PhysicalIntentLifecycleOwner owner) {
        return Arrays.stream(PhysicalIntentKind.values()).filter(owner::supports).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
