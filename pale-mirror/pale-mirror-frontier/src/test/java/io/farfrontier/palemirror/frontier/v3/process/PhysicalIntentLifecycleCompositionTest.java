package io.farfrontier.palemirror.frontier.v3.process;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentRetirementProof;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
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
        FrontierWorldProcessModule missingAccount = module(Arrays.stream(PhysicalIntentLifecycleOwner.values())
                .map(owner -> capability(owner, supported(owner), PhysicalIntentLifecycleRetirementPolicy.noPhysical(owner),
                        owner == first ? null : account(owner))).toList());
        FrontierWorldProcessModule mismatchedAccount = module(Arrays.stream(PhysicalIntentLifecycleOwner.values())
                .map(owner -> capability(owner, supported(owner), PhysicalIntentLifecycleRetirementPolicy.noPhysical(owner),
                        owner == first ? account(PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION) : account(owner))).toList());
        PhysicalIntentRetirementAccount incomplete = new PhysicalIntentRetirementAccount() {
            @Override public PhysicalIntentLifecycleOwner owner() { return first; }
            @Override public java.util.EnumSet<Dimension> checkedDimensions() { return java.util.EnumSet.noneOf(Dimension.class); }
            @Override public Binding bind(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState before,
                                          io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command,
                                          PhysicalIntent intent, PhysicalIntentTransition transition) {
                return PhysicalIntentRetirementAccount.checkedNone(first, command, intent, transition);
            }
            @Override public void verifyDeclaredBinding(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState before,
                                                        PhysicalIntent intent, PhysicalIntentTransition transition, Binding binding) { }
            @Override public void verifyOwnerState(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState before,
                                                   io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState after,
                                                   PhysicalIntent intent, PhysicalIntentTransition transition, Binding binding) { }
        };
        FrontierWorldProcessModule incompleteAccount = module(Arrays.stream(PhysicalIntentLifecycleOwner.values())
                .map(owner -> capability(owner, supported(owner), PhysicalIntentLifecycleRetirementPolicy.noPhysical(owner),
                        owner == first ? incomplete : account(owner))).toList());
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(missing)));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(duplicate)));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(mismatch)));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(undeclared)));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(missingRetirement)));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(missingAccount)));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(mismatchedAccount)));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(incompleteAccount)));
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
    void terminalPlanCarriesItsTypedAccountInsteadOfAProcessGlobalBinding() {
        PhysicalIntentLifecycleOwner owner = PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION;
        List<PhysicalIntentLifecycleCapability> capabilities = Arrays.stream(PhysicalIntentLifecycleOwner.values())
                .map(candidate -> capability(candidate, supported(candidate), candidate == owner
                        ? PhysicalIntentLifecycleRetirementPolicy.of((state, command, intent, transition) ->
                        new CommandPlan.Accepted(List.of(new io.farfrontier.palemirror.frontier.v3.api.ProposedEvent(intent.causeSubjectId(), transition))),
                        (state, subject, intent, transition) -> state)
                        : PhysicalIntentLifecycleRetirementPolicy.noPhysical(candidate))).toList();
        PhysicalIntentLifecycleCapabilities composition = PhysicalIntentLifecycleCapabilities.compose(List.of(module(capabilities)));
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:durable-retirement-account"),
                PhysicalIntentKind.RESOURCE_SITE_PREPARATION, PhysicalIntentStatus.RUNNING,
                new SubjectId("site:durable-retirement-account"), List.of(new SubjectId("site:durable-retirement-account"), new SubjectId("job:durable-retirement-account")),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.whole(64), FixedScalar.ZERO), 0,
                PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED, owner);
        PhysicalIntentTransition terminal = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty());

        CommandPlan.Accepted plan = assertInstanceOf(CommandPlan.Accepted.class, composition.planTransition(null, null, intent, terminal));
        PhysicalIntentTransition committed = assertInstanceOf(PhysicalIntentTransition.class, plan.events().getFirst().payload());
        assertTrue(committed.retirementProof().isPresent());
        assertEquals(owner, committed.retirementProof().orElseThrow().owner());
        assertEquals(intent.id(), committed.retirementProof().orElseThrow().intentId());
    }

    @Test
    void explicitNoPhysicalPolicyRejectsRetirementRatherThanFallingBackToAnotherOwner() {
        NoPhysicalIntentLifecyclePolicy policy = new NoPhysicalIntentLifecyclePolicy(PhysicalIntentLifecycleOwner.ROUTE_PATROL);
        assertInstanceOf(CommandPlan.Rejected.class, policy.retirementPolicy().plan(null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> policy.retirementPolicy().reduce(null, null, null, null));
    }

    @Test
    void checkedNoneScheduleCannotHideTheEngineBoundActionAtPlanPublication() {
        PhysicalIntentLifecycleOwner owner = PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION;
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:none-schedule"),
                PhysicalIntentKind.RESOURCE_SITE_PREPARATION, PhysicalIntentStatus.RUNNING,
                new SubjectId("site:none-schedule"), List.of(new SubjectId("site:none-schedule"), new SubjectId("job:none-schedule")),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.whole(64), FixedScalar.ZERO), 0,
                PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED, owner);
        PhysicalIntentTransition terminal = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty());
        CommandId id = new CommandId("command:none-schedule");
        FrontierCommand command = new FrontierCommand(FrontierCommand.SCHEMA_VERSION, id, new WorldId("world:none-schedule"), Revision.ZERO,
                SimInstant.ZERO, intent.causeSubjectId(), CauseChain.root(id), terminal, java.util.Optional.of(new EngineScheduleBinding(Revision.ZERO,
                new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:none-schedule"), SimInstant.ZERO, 0,
                        intent.causeSubjectId(), "frontier.test.none-schedule", 1))));
        PhysicalIntentRetirementAccount dishonest = PhysicalIntentRetirementAccount.declared(owner,
                java.util.EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class),
                (before, ignored, candidate, transition) -> PhysicalIntentRetirementAccount.checkedNone(owner, null, candidate, transition),
                (before, candidate, transition, binding) -> PhysicalIntentRetirementAccount.requireSameDeclaredAccount(binding,
                        PhysicalIntentRetirementAccount.checkedNoneWithContinuation(owner, binding.continuation(), candidate, transition)),
                (before, after, candidate, transition, binding) -> { });
        assertThrows(IllegalArgumentException.class, () -> dishonest.verifyPlan(null, command, intent, terminal,
                new CommandPlan.Accepted(List.of(new io.farfrontier.palemirror.frontier.v3.api.ProposedEvent(intent.causeSubjectId(), terminal)))));
    }

    @Test
    void replayAccountEqualityRejectsAChangedEngineScheduleDisposition() {
        PhysicalIntentLifecycleOwner owner = PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION;
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:replay-schedule"),
                PhysicalIntentKind.RESOURCE_SITE_PREPARATION, PhysicalIntentStatus.RUNNING,
                new SubjectId("site:replay-schedule"), List.of(new SubjectId("site:replay-schedule"), new SubjectId("job:replay-schedule")),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.whole(64), FixedScalar.ZERO), 0,
                PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED, owner);
        PhysicalIntentTransition terminal = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty());
        var scheduled = PhysicalIntentRetirementAccount.checkedNoneWithContinuation(owner,
                new PhysicalIntentRetirementAccount.Exact<>(new ScheduleId("schedule:replay-schedule")), intent, terminal);
        var absent = PhysicalIntentRetirementAccount.checkedNone(owner, null, intent, terminal);
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalIntentRetirementAccount.requireSameDeclaredAccount(scheduled, absent),
                "reduction/replay must not substitute checked-none for the durable schedule account");
    }

    @Test
    void exactScheduleAccountRejectsMissingWrongAndDuplicateDispositions() {
        PhysicalIntentLifecycleOwner owner = PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION;
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:exact-schedule"),
                PhysicalIntentKind.RESOURCE_SITE_PREPARATION, PhysicalIntentStatus.RUNNING,
                new SubjectId("site:exact-schedule"), List.of(new SubjectId("site:exact-schedule"), new SubjectId("job:exact-schedule")),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.whole(64), FixedScalar.ZERO), 0,
                PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED, owner);
        PhysicalIntentTransition terminal = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty());
        ScheduleId schedule = new ScheduleId("schedule:exact-schedule");
        FrontierCommand command = boundTerminalCommand(intent, terminal, schedule);
        PhysicalIntentRetirementAccount account = PhysicalIntentRetirementAccount.declared(owner,
                java.util.EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class),
                (before, bound, candidate, transition) -> PhysicalIntentRetirementAccount.checkedNoneWithContinuation(owner,
                        new PhysicalIntentRetirementAccount.Exact<>(bound.scheduleBinding().orElseThrow().action().id()), candidate, transition),
                (before, candidate, transition, binding) -> PhysicalIntentRetirementAccount.requireSameDeclaredAccount(binding,
                        PhysicalIntentRetirementAccount.checkedNoneWithContinuation(owner, binding.continuation(), candidate, transition)),
                (before, after, candidate, transition, binding) -> { });
        var terminalEvent = new io.farfrontier.palemirror.frontier.v3.api.ProposedEvent(intent.causeSubjectId(), terminal);
        assertThrows(IllegalArgumentException.class, () -> account.verifyPlan(null, command, intent, terminal,
                new CommandPlan.Accepted(List.of(terminalEvent))));
        assertThrows(IllegalArgumentException.class, () -> account.verifyPlan(null, command, intent, terminal,
                new CommandPlan.Accepted(List.of(terminalEvent, new io.farfrontier.palemirror.frontier.v3.api.ProposedEvent(intent.causeSubjectId(),
                        new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Consumed(new ScheduleId("schedule:wrong")))))));
        var disposition = new io.farfrontier.palemirror.frontier.v3.api.ProposedEvent(intent.causeSubjectId(),
                new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Consumed(schedule));
        assertThrows(IllegalArgumentException.class, () -> account.verifyPlan(null, command, intent, terminal,
                new CommandPlan.Accepted(List.of(terminalEvent, disposition, disposition))));
    }

    @Test
    void terminalProofCodecPreservesTheExactScheduleForReplayValidation() {
        PhysicalIntentLifecycleOwner owner = PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION;
        PhysicalIntentId intentId = new PhysicalIntentId("intent:proof-codec");
        PhysicalIntentRetirementProof proof = new PhysicalIntentRetirementProof(owner, intentId,
                new PhysicalIntentRetirementProof.CheckedNoRelations(PhysicalIntentRetirementProof.Absence.NO_APPLICABLE_RELATION),
                new PhysicalIntentRetirementProof.ExactSchedule(new ScheduleId("schedule:proof-codec")),
                new PhysicalIntentRetirementProof.CheckedNoSubject(PhysicalIntentRetirementProof.Absence.NO_LEASE_OR_CARRIER),
                new PhysicalIntentRetirementProof.CheckedNoSubject(PhysicalIntentRetirementProof.Absence.NO_RESOURCE_COMMITMENT),
                PhysicalIntentRetirementProof.LateDisposition.RETAIN_AMBIGUOUS_RECOVERY);
        PhysicalIntentTransition payload = new PhysicalIntentTransition(intentId, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART,
                java.util.Optional.empty(), java.util.Optional.of(proof));
        assertEquals(payload, FrontierWorldRuntimeDefinition.payloadCodecs().decode(payload.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(payload)));
    }

    private static FrontierWorldProcessModule module(List<PhysicalIntentLifecycleCapability> capabilities) {
        return new FrontierWorldProcessModule() {
            @Override public List<PhysicalIntentLifecycleCapability> physicalIntentLifecycleCapabilities() { return capabilities; }
        };
    }

    private static FrontierCommand boundTerminalCommand(PhysicalIntent intent, PhysicalIntentTransition terminal, ScheduleId schedule) {
        CommandId command = new CommandId("command:exact-schedule");
        return new FrontierCommand(FrontierCommand.SCHEMA_VERSION, command, new WorldId("world:exact-schedule"), Revision.ZERO,
                SimInstant.ZERO, intent.causeSubjectId(), CauseChain.root(command), terminal, java.util.Optional.of(new EngineScheduleBinding(Revision.ZERO,
                new ScheduledAction(schedule, SimInstant.ZERO, 0, intent.causeSubjectId(), "frontier.test.exact-schedule", 1))));
    }

    private static PhysicalIntentLifecycleCapability capability(PhysicalIntentLifecycleOwner owner, Set<PhysicalIntentKind> kinds) {
        return capability(owner, kinds, PhysicalIntentLifecycleRetirementPolicy.noPhysical(owner));
    }

    private static PhysicalIntentLifecycleCapability capability(PhysicalIntentLifecycleOwner owner, Set<PhysicalIntentKind> kinds,
                                                                 PhysicalIntentLifecycleRetirementPolicy retirementPolicy) {
        return capability(owner, kinds, retirementPolicy, account(owner));
    }

    private static PhysicalIntentLifecycleCapability capability(PhysicalIntentLifecycleOwner owner, Set<PhysicalIntentKind> kinds,
                                                                 PhysicalIntentLifecycleRetirementPolicy retirementPolicy,
                                                                 PhysicalIntentRetirementAccount account) {
        return new AbstractPhysicalIntentLifecycleCapability(owner, kinds, retirementPolicy, account) { };
    }

    private static PhysicalIntentRetirementAccount account(PhysicalIntentLifecycleOwner owner) {
        return PhysicalIntentRetirementAccount.declared(owner,
                java.util.EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class),
                (before, command, intent, transition) -> PhysicalIntentRetirementAccount.checkedNone(owner, command, intent, transition),
                (before, intent, transition, binding) -> PhysicalIntentRetirementAccount.requireSameDeclaredAccount(binding,
                        PhysicalIntentRetirementAccount.checkedNoneWithContinuation(owner, binding.continuation(), intent, transition)),
                (before, after, intent, transition, binding) -> {
                    if (intent.lifecycleOwner() != owner) throw new IllegalArgumentException("test owner mismatch");
                });
    }

    private static Set<PhysicalIntentKind> supported(PhysicalIntentLifecycleOwner owner) {
        return Arrays.stream(PhysicalIntentKind.values()).filter(owner::supports).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
