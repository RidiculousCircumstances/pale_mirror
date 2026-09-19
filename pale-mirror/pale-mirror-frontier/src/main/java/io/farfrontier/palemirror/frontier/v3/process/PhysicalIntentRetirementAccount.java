package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryDisposition;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhase;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhysicalIntentSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;

import java.util.EnumSet;
import java.util.Objects;

/** Executable, transaction-local account supplied by the owner of a retiring physical intent. */
interface PhysicalIntentRetirementAccount {
    enum Dimension { REL_EDGES, ENGINE_CONTINUATION, LEASE_OR_CARRIER, RESOURCE_COMMITMENT, LATE_RECOVERY }

    PhysicalIntentLifecycleOwner owner();
    EnumSet<Dimension> checkedDimensions();
    void verifyOwnerState(FrontierWorldState before, FrontierWorldState after, PhysicalIntent intent,
                          PhysicalIntentTransition transition);

    default void verifyPlan(FrontierCommand command, PhysicalIntent intent, PhysicalIntentTransition transition, CommandPlan plan) {
        if (!(plan instanceof CommandPlan.Accepted accepted)) return;
        command.scheduleBinding().ifPresent(binding -> {
            ScheduleId id = binding.action().id();
            long matching = accepted.events().stream().map(event -> event.payload()).filter(ScheduleEffect.class::isInstance)
                    .map(ScheduleEffect.class::cast).filter(effect -> affects(effect, id)).count();
            if (matching > 1L) throw new IllegalArgumentException("retirement account duplicates bound engine schedule effect");
        });
    }

    default void verifyReduced(FrontierWorldState before, FrontierWorldState after, PhysicalIntent intent,
                               PhysicalIntentTransition transition) {
        PhysicalIntent terminal = after.physicalIntents().get(intent.id());
        if (terminal == null || terminal.status() != transition.status()) throw new IllegalArgumentException("retirement account lost its exact terminal intent");
        var bindingId = FencedRecoveryPhysicalIntentSupport.bindingId(intent);
        var beforeBinding = before.fencedRecovery().current().get(bindingId);
        var binding = after.fencedRecovery().current().get(bindingId);
        var tombstone = after.fencedRecovery().tombstones().get(bindingId);
        if (beforeBinding == null) throw new IllegalArgumentException("retirement account has no exact prepared recovery authority");
        if (transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            if (binding == null || binding.phase() != FencedRecoveryPhase.AMBIGUOUS || tombstone != null) {
                throw new IllegalArgumentException("retirement account did not retain inspectable late-input disposition");
            }
        } else {
            if (binding != null || tombstone == null || tombstone.retiredEpoch() != beforeBinding.authorityEpoch()) {
                throw new IllegalArgumentException("retirement account did not consume exact recovery authority");
            }
            if (transition.status() == PhysicalIntentStatus.CONFIRMED && tombstone.disposition() != FencedRecoveryDisposition.REJECT_STALE) {
                throw new IllegalArgumentException("retirement account did not close confirmed late-input authority");
            }
        }
        verifyOwnerState(before, after, intent, transition);
    }

    static PhysicalIntentRetirementAccount declared(PhysicalIntentLifecycleOwner owner, EnumSet<Dimension> dimensions,
                                                     OwnerStateCheck check) {
        Objects.requireNonNull(owner, "retirement account owner");
        EnumSet<Dimension> checked = EnumSet.copyOf(Objects.requireNonNull(dimensions, "retirement account dimensions"));
        if (!checked.containsAll(EnumSet.allOf(Dimension.class))) throw new IllegalArgumentException("retirement account omits a checked dimension");
        Objects.requireNonNull(check, "retirement account owner-state check");
        return new PhysicalIntentRetirementAccount() {
            @Override public PhysicalIntentLifecycleOwner owner() { return owner; }
            @Override public EnumSet<Dimension> checkedDimensions() { return EnumSet.copyOf(checked); }
            @Override public void verifyOwnerState(FrontierWorldState before, FrontierWorldState after, PhysicalIntent intent,
                                                   PhysicalIntentTransition transition) { check.verify(before, after, intent, transition); }
        };
    }

    private static boolean affects(ScheduleEffect effect, ScheduleId id) {
        return switch (effect) {
            case ScheduleEffect.Cancelled cancelled -> cancelled.scheduleId().equals(id);
            case ScheduleEffect.Consumed consumed -> consumed.scheduleId().equals(id);
            case ScheduleEffect.Rescheduled rescheduled -> rescheduled.scheduleId().equals(id);
            case ScheduleEffect.Created ignored -> false;
        };
    }

    @FunctionalInterface interface OwnerStateCheck {
        void verify(FrontierWorldState before, FrontierWorldState after, PhysicalIntent intent, PhysicalIntentTransition transition);
    }
}
