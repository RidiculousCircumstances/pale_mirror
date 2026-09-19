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
import io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentRetirementProof;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

/** Executable, transaction-local account supplied by the owner of a retiring physical intent. */
interface PhysicalIntentRetirementAccount {
    enum Dimension { REL_EDGES, ENGINE_CONTINUATION, LEASE_OR_CARRIER, RESOURCE_COMMITMENT, LATE_RECOVERY }

    PhysicalIntentLifecycleOwner owner();
    EnumSet<Dimension> checkedDimensions();
    void verifyOwnerState(FrontierWorldState before, FrontierWorldState after, PhysicalIntent intent,
                          PhysicalIntentTransition transition, Binding binding);
    void verifyDeclaredBinding(FrontierWorldState before, PhysicalIntent intent,
                               PhysicalIntentTransition transition, Binding binding);

    /**
     * The owner-supplied transaction-local facts for one terminal transition.  These are not a
     * second relationship store or scheduler: every exact value is read from the authoritative
     * pre-state and the engine command that is already crossing this transaction boundary.
     */
    record Binding(PhysicalIntentLifecycleOwner owner, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId intentId,
                   Obligation<List<FrontierDomainRelationships.Edge>> relations, Obligation<ScheduleId> continuation,
                   Obligation<SubjectId> leaseOrCarrier, Obligation<SubjectId> commitment,
                   LateDisposition lateDisposition) {
        public Binding {
            Objects.requireNonNull(owner, "retirement binding owner"); Objects.requireNonNull(intentId, "retirement binding intent");
            Objects.requireNonNull(relations, "retirement binding relations");
            Objects.requireNonNull(continuation, "retirement binding continuation");
            Objects.requireNonNull(leaseOrCarrier, "retirement binding lease/carrier");
            Objects.requireNonNull(commitment, "retirement binding commitment");
            Objects.requireNonNull(lateDisposition, "retirement binding late disposition");
        }
    }

    sealed interface Obligation<T> permits Exact, CheckedNone { }
    record Exact<T>(T value) implements Obligation<T> { public Exact { Objects.requireNonNull(value, "exact retirement obligation"); } }
    record CheckedNone<T>(PhysicalIntentRetirementProof.Absence absence) implements Obligation<T> {
        public CheckedNone { Objects.requireNonNull(absence, "checked-none retirement absence"); }
    }
    enum LateDisposition { REJECT_STALE_ONCE, RETAIN_AMBIGUOUS_RECOVERY, NO_PHYSICAL_INPUT }

    /** Builds the real account before plan publication, from authoritative facts only. */
    Binding bind(FrontierWorldState before, FrontierCommand command, PhysicalIntent intent,
                 PhysicalIntentTransition transition);

    private static LateDisposition lateDisposition(PhysicalIntentTransition transition) {
        return transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                ? LateDisposition.RETAIN_AMBIGUOUS_RECOVERY : LateDisposition.REJECT_STALE_ONCE;
    }

    default Binding verifyPlan(FrontierWorldState before, FrontierCommand command, PhysicalIntent intent, PhysicalIntentTransition transition, CommandPlan plan) {
        if (!(plan instanceof CommandPlan.Accepted accepted)) return null;
        Binding binding = bind(before, command, intent, transition);
        if (binding.owner() != owner() || !binding.intentId().equals(intent.id())) {
            throw new IllegalArgumentException("retirement account does not bind its exact owner intent");
        }
        for (FrontierDomainRelationships.Edge relation : exactRelations(binding.relations())) {
            if (relation == null) throw new IllegalArgumentException("retirement account has a null relation binding");
            if (!FrontierDomainRelationships.view(before).edges().contains(relation)) {
                throw new IllegalArgumentException("retirement account binds a relation absent from authoritative pre-state");
            }
        }
        validateObligation(binding.leaseOrCarrier(), exactRelations(binding.relations()), "lease/carrier");
        validateObligation(binding.commitment(), exactRelations(binding.relations()), "resource commitment");
        verifyDeclaredBinding(before, intent, transition, binding);
        verifyOwnerState(before, before, intent, transition, binding);
        if (binding.continuation() instanceof Exact<ScheduleId> expected) {
            long matching = accepted.events().stream().map(event -> event.payload()).filter(ScheduleEffect.class::isInstance)
                    .map(ScheduleEffect.class::cast).filter(effect -> affects(effect, expected.value())).count();
            // The engine owns its bound due-action and may consume it at the atomic command
            // boundary rather than represent it as a domain proposed event.  The account binds
            // that exact ID; a proposed schedule effect is permitted once, never duplicated.
            if (matching != 1L) throw new IllegalArgumentException("retirement account must emit exactly one disposition for its exact bound engine schedule effect");
        }
        if (binding.continuation() instanceof CheckedNone<ScheduleId>
                && command != null && command.scheduleBinding().isPresent()) {
            throw new IllegalArgumentException("retirement account falsely declares no engine continuation for a bound command");
        }
        if (command != null) command.scheduleBinding().ifPresent(scheduleBinding -> {
            ScheduleId id = scheduleBinding.action().id();
            long matching = accepted.events().stream().map(event -> event.payload()).filter(ScheduleEffect.class::isInstance)
                    .map(ScheduleEffect.class::cast).filter(effect -> affects(effect, id)).count();
            if (matching != 1L) throw new IllegalArgumentException("retirement account must emit exactly one disposition for its bound engine schedule effect");
        });
        return binding;
    }

    default void verifyReduced(FrontierWorldState before, FrontierWorldState after, PhysicalIntent intent,
                               PhysicalIntentTransition transition) {
        PhysicalIntentRetirementProof proof = transition.retirementProof().orElseThrow(() ->
                new IllegalArgumentException("terminal physical transition lacks its durable retirement proof"));
        Binding binding = fromProof(proof);
        if (binding.owner() != owner() || !binding.intentId().equals(intent.id()) || binding.lateDisposition() != lateDisposition(transition)) {
            throw new IllegalArgumentException("retirement account does not retain its terminal owner/intent/disposition binding");
        }
        for (FrontierDomainRelationships.Edge relation : exactRelations(binding.relations())) {
            if (!FrontierDomainRelationships.view(before).edges().contains(relation)) {
                throw new IllegalArgumentException("retirement account relation is not an authoritative pre-state edge");
            }
        }
        validateObligation(binding.leaseOrCarrier(), exactRelations(binding.relations()), "lease/carrier");
        validateObligation(binding.commitment(), exactRelations(binding.relations()), "resource commitment");
        verifyDeclaredBinding(before, intent, transition, binding);
        PhysicalIntent terminal = after.physicalIntents().get(intent.id());
        if (terminal == null || terminal.status() != transition.status()) throw new IllegalArgumentException("retirement account lost its exact terminal intent");
        var bindingId = FencedRecoveryPhysicalIntentSupport.bindingId(intent);
        var beforeBinding = before.fencedRecovery().current().get(bindingId);
        var recoveryBinding = after.fencedRecovery().current().get(bindingId);
        var tombstone = after.fencedRecovery().tombstones().get(bindingId);
        if (beforeBinding == null) throw new IllegalArgumentException("retirement account has no exact prepared recovery authority");
        if (transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            if (recoveryBinding == null || recoveryBinding.phase() != FencedRecoveryPhase.AMBIGUOUS || tombstone != null) {
                throw new IllegalArgumentException("retirement account did not retain inspectable late-input disposition");
            }
        } else {
            if (recoveryBinding != null || tombstone == null || tombstone.retiredEpoch() != beforeBinding.authorityEpoch()) {
                throw new IllegalArgumentException("retirement account did not consume exact recovery authority");
            }
            if (transition.status() == PhysicalIntentStatus.CONFIRMED && tombstone.disposition() != FencedRecoveryDisposition.REJECT_STALE) {
                throw new IllegalArgumentException("retirement account did not close confirmed late-input authority");
            }
        }
        verifyOwnerState(before, after, intent, transition, binding);
    }

    /** A closed checked-none account is still explicit: no physical intent may use it successfully. */
    static PhysicalIntentRetirementAccount noPhysical(PhysicalIntentLifecycleOwner owner) {
        return declared(owner, EnumSet.allOf(Dimension.class),
                (before, command, intent, transition) -> checkedNone(owner, command, intent, transition),
                (before, intent, transition, binding) -> {
                    throw new IllegalArgumentException("no-physical owner cannot declare a retirement account");
                },
                (before, after, intent, transition, binding) -> {
            throw new IllegalArgumentException("no-physical owner cannot retire an intent");
        });
    }

    static PhysicalIntentRetirementAccount declared(PhysicalIntentLifecycleOwner owner, EnumSet<Dimension> dimensions,
                                                     BindingFactory bindings, BindingCheck bindingCheck, OwnerStateCheck check) {
        Objects.requireNonNull(owner, "retirement account owner");
        EnumSet<Dimension> checked = EnumSet.copyOf(Objects.requireNonNull(dimensions, "retirement account dimensions"));
        if (!checked.containsAll(EnumSet.allOf(Dimension.class))) throw new IllegalArgumentException("retirement account omits a checked dimension");
        Objects.requireNonNull(check, "retirement account owner-state check");
        Objects.requireNonNull(bindings, "retirement account binding factory");
        Objects.requireNonNull(bindingCheck, "retirement account binding check");
        return new PhysicalIntentRetirementAccount() {
            @Override public PhysicalIntentLifecycleOwner owner() { return owner; }
            @Override public EnumSet<Dimension> checkedDimensions() { return EnumSet.copyOf(checked); }
            @Override public void verifyOwnerState(FrontierWorldState before, FrontierWorldState after, PhysicalIntent intent,
                                                   PhysicalIntentTransition transition, Binding binding) { check.verify(before, after, intent, transition, binding); }
            @Override public void verifyDeclaredBinding(FrontierWorldState before, PhysicalIntent intent,
                                                        PhysicalIntentTransition transition, Binding binding) {
                bindingCheck.verify(before, intent, transition, binding);
            }
            @Override public Binding bind(FrontierWorldState before, FrontierCommand command, PhysicalIntent intent,
                                          PhysicalIntentTransition transition) {
                Binding binding = bindings.bind(before, command, intent, transition);
                if (binding.owner() != owner || !binding.intentId().equals(intent.id())) {
                    throw new IllegalArgumentException("retirement account factory returned a mismatched owner or intent");
                }
                return binding;
            }
        };
    }

    static Binding checkedNone(PhysicalIntentLifecycleOwner owner, FrontierCommand command, PhysicalIntent intent,
                               PhysicalIntentTransition transition) {
        Optional<ScheduleId> schedule = command == null ? Optional.empty() : command.scheduleBinding().map(binding -> binding.action().id());
        return new Binding(owner, intent.id(), new CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_APPLICABLE_RELATION), schedule.<Obligation<ScheduleId>>map(Exact::new)
                .orElseGet(() -> new CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION)),
                new CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_LEASE_OR_CARRIER),
                new CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_RESOURCE_COMMITMENT), lateDisposition(transition));
    }


    static PhysicalIntentRetirementProof proof(Binding binding) {
        return new PhysicalIntentRetirementProof(binding.owner(), binding.intentId(),
                binding.relations() instanceof Exact<List<FrontierDomainRelationships.Edge>> exact
                        ? new PhysicalIntentRetirementProof.ExactRelations(exact.value())
                        : new PhysicalIntentRetirementProof.CheckedNoRelations(((CheckedNone<List<FrontierDomainRelationships.Edge>>) binding.relations()).absence()),
                binding.continuation() instanceof Exact<ScheduleId> exact
                        ? new PhysicalIntentRetirementProof.ExactSchedule(exact.value())
                        : new PhysicalIntentRetirementProof.CheckedNoSchedule(((CheckedNone<ScheduleId>) binding.continuation()).absence()),
                binding.leaseOrCarrier() instanceof Exact<SubjectId> exact
                        ? new PhysicalIntentRetirementProof.ExactSubject(exact.value())
                        : new PhysicalIntentRetirementProof.CheckedNoSubject(((CheckedNone<SubjectId>) binding.leaseOrCarrier()).absence()),
                binding.commitment() instanceof Exact<SubjectId> exact
                        ? new PhysicalIntentRetirementProof.ExactSubject(exact.value())
                        : new PhysicalIntentRetirementProof.CheckedNoSubject(((CheckedNone<SubjectId>) binding.commitment()).absence()),
                switch (binding.lateDisposition()) {
                    case REJECT_STALE_ONCE -> PhysicalIntentRetirementProof.LateDisposition.REJECT_STALE_ONCE;
                    case RETAIN_AMBIGUOUS_RECOVERY -> PhysicalIntentRetirementProof.LateDisposition.RETAIN_AMBIGUOUS_RECOVERY;
                    case NO_PHYSICAL_INPUT -> PhysicalIntentRetirementProof.LateDisposition.NO_PHYSICAL_INPUT;
                });
    }

    private static Binding fromProof(PhysicalIntentRetirementProof proof) {
        return new Binding(proof.owner(), proof.intentId(),
                proof.relations() instanceof PhysicalIntentRetirementProof.ExactRelations exact ? new Exact<>(exact.value())
                        : new CheckedNone<>(((PhysicalIntentRetirementProof.CheckedNoRelations) proof.relations()).absence()),
                proof.continuation() instanceof PhysicalIntentRetirementProof.ExactSchedule exact ? new Exact<>(exact.value())
                        : new CheckedNone<>(((PhysicalIntentRetirementProof.CheckedNoSchedule) proof.continuation()).absence()),
                proof.leaseOrCarrier() instanceof PhysicalIntentRetirementProof.ExactSubject exact ? new Exact<>(exact.value())
                        : new CheckedNone<>(((PhysicalIntentRetirementProof.CheckedNoSubject) proof.leaseOrCarrier()).absence()),
                proof.commitment() instanceof PhysicalIntentRetirementProof.ExactSubject exact ? new Exact<>(exact.value())
                        : new CheckedNone<>(((PhysicalIntentRetirementProof.CheckedNoSubject) proof.commitment()).absence()),
                switch (proof.lateDisposition()) {
                    case REJECT_STALE_ONCE -> LateDisposition.REJECT_STALE_ONCE;
                    case RETAIN_AMBIGUOUS_RECOVERY -> LateDisposition.RETAIN_AMBIGUOUS_RECOVERY;
                    case NO_PHYSICAL_INPUT -> LateDisposition.NO_PHYSICAL_INPUT;
                });
    }

    @FunctionalInterface interface BindingFactory {
        Binding bind(FrontierWorldState before, FrontierCommand command, PhysicalIntent intent, PhysicalIntentTransition transition);
    }

    /**
     * Re-executes the owner-declared account against pre-state during reduction and WAL replay.
     * It must compare the complete applicable relation/subject account, not discover an account
     * by searching for an ID in arbitrary relationship endpoints.  The schedule is deliberately
     * excluded here: its exact engine disposition is checked at the transaction boundary.
     */
    @FunctionalInterface interface BindingCheck {
        void verify(FrontierWorldState before, PhysicalIntent intent, PhysicalIntentTransition transition, Binding binding);
    }

    static void requireSameDeclaredAccount(Binding actual, Binding expected) {
        if (actual.owner() != expected.owner() || !actual.intentId().equals(expected.intentId())
                || !sameObligation(actual.relations(), expected.relations())
                || !sameObligation(actual.leaseOrCarrier(), expected.leaseOrCarrier())
                || !sameObligation(actual.commitment(), expected.commitment())
                || actual.lateDisposition() != expected.lateDisposition()) {
            throw new IllegalArgumentException("retirement proof does not equal the owner's complete declared account");
        }
    }

    private static boolean sameObligation(Obligation<?> left, Obligation<?> right) {
        if (left instanceof Exact<?> exactLeft && right instanceof Exact<?> exactRight) {
            Object a = exactLeft.value(); Object b = exactRight.value();
            if (a instanceof List<?> la && b instanceof List<?> lb) return java.util.Set.copyOf(la).equals(java.util.Set.copyOf(lb)) && la.size() == lb.size();
            return Objects.equals(a, b);
        }
        return left instanceof CheckedNone<?> l && right instanceof CheckedNone<?> r && l.absence() == r.absence();
    }

    private static void validateObligation(Obligation<SubjectId> obligation, List<FrontierDomainRelationships.Edge> relations, String dimension) {
        if (obligation instanceof Exact<SubjectId> exact && relations.stream().noneMatch(edge -> endpointIs(edge, exact.value()))) {
            throw new IllegalArgumentException("retirement account " + dimension + " is not bound by one of its exact relations");
        }
    }

    private static List<FrontierDomainRelationships.Edge> exactRelations(Obligation<List<FrontierDomainRelationships.Edge>> relations) {
        if (relations instanceof Exact<List<FrontierDomainRelationships.Edge>> exact) {
            List<FrontierDomainRelationships.Edge> value = List.copyOf(exact.value());
            if (value.isEmpty() || value.stream().distinct().count() != value.size()) {
                throw new IllegalArgumentException("exact retirement relation binding must be nonempty and duplicate-free");
            }
            return value;
        }
        return List.of();
    }

    private static boolean endpointIs(FrontierDomainRelationships.Edge edge, SubjectId id) {
        return endpointIs(edge.owner(), id) || endpointIs(edge.source(), id) || endpointIs(edge.target(), id);
    }

    private static boolean endpointIs(FrontierDomainRelationships.Endpoint endpoint, SubjectId id) {
        return endpoint instanceof FrontierDomainRelationships.SubjectEndpoint subject && subject.id().equals(id);
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
        void verify(FrontierWorldState before, FrontierWorldState after, PhysicalIntent intent, PhysicalIntentTransition transition, Binding binding);
    }
}
