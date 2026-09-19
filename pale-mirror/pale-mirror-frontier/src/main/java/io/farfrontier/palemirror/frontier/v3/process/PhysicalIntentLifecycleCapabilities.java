package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.CommandRejection;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.RejectionCode;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhysicalIntentSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/** Family-agnostic closed composition for durable physical lifecycle capabilities. */
final class PhysicalIntentLifecycleCapabilities {
    private final Map<PhysicalIntentLifecycleOwner, PhysicalIntentLifecycleCapability> byOwner;

    private PhysicalIntentLifecycleCapabilities(Map<PhysicalIntentLifecycleOwner, PhysicalIntentLifecycleCapability> byOwner) {
        this.byOwner = Map.copyOf(byOwner);
    }

    static PhysicalIntentLifecycleCapabilities compose(Collection<? extends FrontierWorldProcessModule> modules) {
        Objects.requireNonNull(modules, "physical lifecycle modules");
        Map<PhysicalIntentLifecycleOwner, PhysicalIntentLifecycleCapability> capabilities = new EnumMap<>(PhysicalIntentLifecycleOwner.class);
        for (FrontierWorldProcessModule module : modules) {
            for (PhysicalIntentLifecycleCapability capability : module.physicalIntentLifecycleCapabilities()) {
                if (capability == null || capability.owner() == null || capability.compatibleKinds() == null
                        || capability.retirementPolicy() == null || capability.retirementAccount() == null
                        || capability.retirementAccount().owner() != capability.owner()
                        || !capability.retirementAccount().checkedDimensions()
                        .containsAll(EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class))) {
                    throw new IllegalArgumentException("physical lifecycle composition contains an undeclared capability");
                }
                if (capabilities.putIfAbsent(capability.owner(), capability) != null) {
                    throw new IllegalArgumentException("duplicate physical lifecycle capability: " + capability.owner().stableId());
                }
                if (!capability.compatibleKinds().stream().allMatch(capability.owner()::supports)
                        || EnumSet.allOf(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.class).stream()
                        .filter(capability.owner()::supports).anyMatch(kind -> !capability.compatibleKinds().contains(kind))) {
                    throw new IllegalArgumentException("physical lifecycle capability has mismatched declared kinds: " + capability.owner().stableId());
                }
            }
        }
        for (PhysicalIntentLifecycleOwner owner : PhysicalIntentLifecycleOwner.values()) {
            if (!capabilities.containsKey(owner)) {
                throw new IllegalArgumentException("missing physical lifecycle capability: " + owner.stableId());
            }
        }
        return new PhysicalIntentLifecycleCapabilities(capabilities);
    }

    CommandPlan planPrepared(FrontierWorldState state, FrontierCommand command, PhysicalIntentPrepared prepared) {
        return capability(prepared.intent()).planPrepared(state, command, prepared);
    }

    CommandPlan planTransition(FrontierWorldState state, FrontierCommand command, PhysicalIntent intent,
                               PhysicalIntentTransition transition) {
        PhysicalIntentLifecycleCapability capability = capability(intent);
        try {
            CommandPlan plan = retires(transition) ? capability.retirementPolicy().plan(state, command, intent, transition)
                    : capability.planTransition(state, command, intent, transition);
            if (!retires(transition)) return plan;
            PhysicalIntentRetirementAccount.Binding binding = capability.retirementAccount()
                    .verifyPlan(state, command, intent, transition, plan);
            if (!(plan instanceof CommandPlan.Accepted accepted) || binding == null) return plan;
            int terminalEvents = 0;
            java.util.List<ProposedEvent> accounted = new java.util.ArrayList<>(accepted.events().size());
            for (ProposedEvent event : accepted.events()) {
                if (event.payload() instanceof PhysicalIntentTransition candidate
                        && candidate.intentId().equals(intent.id()) && candidate.status() == transition.status()) {
                    terminalEvents++;
                    accounted.add(new ProposedEvent(event.subject(), candidate.withRetirementProof(PhysicalIntentRetirementAccount.proof(binding))));
                } else {
                    accounted.add(event);
                }
            }
            if (terminalEvents != 1) throw new IllegalArgumentException("retirement account must attach one proof to its exact terminal event");
            return new CommandPlan.Accepted(accounted);
        } catch (IllegalArgumentException invalid) {
            return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
        }
    }

    FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        PhysicalIntentLifecycleCapability capability = capability(intent);
        FrontierWorldState reduced = capability.reducePrepared(state, subject, intent);
        return reduced.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(
                FencedRecoveryPhysicalIntentSupport.prepared(reduced.fencedRecovery(), intent, capability.recoveryAsset(intent))));
    }

    FrontierWorldState reduceTransition(FrontierWorldState state, SubjectId subject, PhysicalIntent intent,
                                        PhysicalIntentTransition transition) {
        PhysicalIntentLifecycleCapability capability = capability(intent);
        FrontierWorldState reduced = retires(transition)
                ? capability.retirementPolicy().reduce(state, subject, intent, transition)
                : capability.reduceTransition(state, subject, intent, transition);
        FrontierWorldState fenced = reduced.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(
                FencedRecoveryPhysicalIntentSupport.transition(state.fencedRecovery(), intent, transition.status(), capability.recoveryAsset(intent))));
        if (retires(transition)) capability.retirementAccount().verifyReduced(state, fenced, intent, transition);
        return fenced;
    }


    private PhysicalIntentLifecycleCapability capability(PhysicalIntent intent) {
        Objects.requireNonNull(intent, "physical lifecycle intent");
        PhysicalIntentLifecycleCapability capability = byOwner.get(intent.lifecycleOwner());
        if (capability == null || !capability.compatibleKinds().contains(intent.kind())) {
            throw new IllegalArgumentException("physical intent lifecycle capability is missing or mismatched: "
                    + intent.lifecycleOwner().stableId() + " / " + intent.kind());
        }
        return capability;
    }

    /** Terminal protocol state, not family classification; owner-local policy supplies its behavior. */
    private static boolean retires(PhysicalIntentTransition transition) {
        return switch (transition.status()) {
            case CONFIRMED, UNKNOWN_AFTER_RESTART, CONFLICTED -> true;
            case RUNNING -> false;
            case PREPARED -> throw new IllegalArgumentException("physical intent cannot retire to prepared");
        };
    }
}
