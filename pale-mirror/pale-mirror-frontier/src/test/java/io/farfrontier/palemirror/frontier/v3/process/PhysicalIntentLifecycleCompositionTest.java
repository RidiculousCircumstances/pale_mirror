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
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema;
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
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentRecoveryDiagnosticProducer;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticCategory;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticDisposition;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticOwner;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticOwnerKind;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticReason;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticSubject;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticSubjectKind;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticTuple;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PhysicalIntentLifecycleCompositionTest {
    @Test
    void emptyQueuesDoNotReportSaturationForExplicitlyNonPhysicalOwners() {
        FrontierWorldState state = FrontierWorldRuntimeDefinition.configuration(
                new WorldId("frontier:empty-lifecycle-pressure"), 91L).initialState();
        var diagnostic = FrontierWorldProcessCatalog.physicalLifecycles().diagnostic(state);
        assertTrue(diagnostic.owners().stream().anyMatch(owner -> owner.schemaTags().isEmpty()));
        for (var owner : diagnostic.owners()) {
            assertEquals(0, owner.unresolved());
            assertEquals(0, owner.resolvedRetained());
            assertEquals(PhysicalIntentLifecycleCompositionDiagnostic.Pressure.OPEN, owner.pressure(), owner.owner().toString());
            if (owner.schemaTags().isEmpty()) assertEquals(0, owner.maxUnresolved(),
                    "no intent admission is granted by an empty pressure report");
        }
    }

    @Test
    void installedCompositionSuppliesEveryDurableOwner() {
        assertDoesNotThrow(FrontierWorldProcessCatalog::physicalLifecycles);
        assertDoesNotThrow(() -> PhysicalIntentLifecycleCapabilities.compose(List.of(module(Arrays.stream(
                PhysicalIntentLifecycleOwner.values()).map(owner -> capability(owner, supported(owner))).toList()))));
        long physicalOwnerCount = Arrays.stream(PhysicalIntentRoleSchema.values())
                .map(PhysicalIntentRoleSchema::owner).distinct().count();
        assertEquals(physicalOwnerCount * PhysicalIntentLifecycleDeclaration.MAX_PER_OWNER,
                FrontierWorldProcessCatalog.physicalLifecycles().unresolvedAdmissionCapacity(),
                "every declared physical owner receives the same bounded unresolved admission share");
        assertTrue(FrontierWorldProcessCatalog.physicalLifecycles().unresolvedAdmissionCapacity()
                        <= FrontierWorldState.MAX_PHYSICAL_INTENTS,
                "physical-owner quotas must never exceed the aggregate unresolved admission bound");
    }

    @Test
    void unresolvedAndConflictedIntentsCannotBecomeCompactableHistory() {
        PhysicalIntentLifecycleDeclaration declaration = PhysicalIntentLifecycleDeclaration.physical(
                PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION,
                Set.of(PhysicalIntentKind.RESOURCE_SITE_PREPARATION), Set.of(PhysicalIntentRoleSchema.RESOURCE_SITE_PREPARATION));
        PhysicalIntentResolvedRetentionPolicy policy = PhysicalIntentResolvedRetentionPolicy.confirmedReceiptWithoutRecovery();
        PhysicalIntent unknown = retentionIntent("unknown", PhysicalIntentStatus.UNKNOWN_AFTER_RESTART);
        PhysicalIntent conflicted = retentionIntent("conflicted", PhysicalIntentStatus.CONFLICTED);

        assertTrue(declaration.unresolved(unknown));
        assertTrue(declaration.unresolved(conflicted));
        assertTrue(!policy.mayCompact(null, unknown));
        assertTrue(!policy.mayCompact(null, conflicted));
    }

    @Test
    void compositionFailsClosedForMissingDuplicateAndMismatchedCapabilities() {
        PhysicalIntentLifecycleOwner first = PhysicalIntentLifecycleOwner.RESOURCE_SITE_HARVEST;
        FrontierWorldProcessModule missing = module(List.of(capability(first, supported(first))));
        FrontierWorldProcessModule duplicate = module(List.of(capability(first, supported(first)), capability(first, supported(first))));
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
        FrontierWorldProcessModule unfairShare = module(Arrays.stream(PhysicalIntentLifecycleOwner.values()).map(owner -> {
            if (owner != first) return capability(owner, supported(owner));
            return new AbstractPhysicalIntentLifecycleCapability(new PhysicalIntentLifecycleDeclaration(first,
                    PhysicalIntentLifecycleDeclaration.VERSION, supported(first), Set.of(PhysicalIntentRoleSchema.RESOURCE_SITE_HARVEST), 257, 256),
                    PhysicalIntentLifecycleRetirementPolicy.noPhysical(first), account(first),
                    PhysicalIntentResolvedRetentionPolicy.confirmedReceiptWithoutRecovery(), PhysicalIntentRecoveryDiagnosticProducer.RESOURCE_SITE_PREPARATION) { };
        }).toList());
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
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(undeclared)));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(missingRetirement)));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(missingAccount)));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(mismatchedAccount)));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(unfairShare)),
                "one owner cannot enlarge its declared share before aggregate admission is exhausted");
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleCapabilities.compose(List.of(incompleteAccount)));
        assertThrows(IllegalArgumentException.class, () -> new PhysicalIntentLifecycleDeclaration(first,
                PhysicalIntentLifecycleDeclaration.VERSION + 1, supported(first), Set.of(PhysicalIntentRoleSchema.RESOURCE_SITE_HARVEST), 256, 256),
                "a stale declaration version must fail before the capability can compose");
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleDeclaration.physical(first, supported(first), Set.of()),
                "owner-and-kind construction without an exact role schema must fail before composition");
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleDeclaration.physical(first, supported(first),
                        Set.of(PhysicalIntentRoleSchema.RESOURCE_SITE_PREPARATION)),
                "a foreign owner/kind/schema tuple must fail at declaration construction, not be repaired by discovery");
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleDeclaration.physical(first,
                        Set.of(PhysicalIntentKind.SCENE_STRIKE), Set.of(PhysicalIntentRoleSchema.RESOURCE_SITE_HARVEST)),
                "a mismatched supplied kind must fail at declaration construction, not select a compatible capability");
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleDeclaration.physical(first,
                        Set.of(PhysicalIntentKind.RESOURCE_SITE_HARVEST, PhysicalIntentKind.SCENE_STRIKE),
                        Set.of(PhysicalIntentRoleSchema.RESOURCE_SITE_HARVEST)),
                "an extra kind without an exact schema must fail rather than becoming a partial compatible declaration");
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleDeclaration.physical(
                        PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE,
                        supported(PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE), Set.of(PhysicalIntentRoleSchema.STRUCTURAL_REPAIR)),
                "a partial owner declaration must fail at its boundary before composition can discover missing schemas");
        assertThrows(NullPointerException.class, () -> new AbstractPhysicalIntentLifecycleCapability(
                declaration(first, supported(first)), PhysicalIntentLifecycleRetirementPolicy.noPhysical(first), account(first), null,
                PhysicalIntentRecoveryDiagnosticProducer.RESOURCE_SITE_PREPARATION) { },
                "a physical owner cannot omit its executable resolved-history retention policy");
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
                new SubjectId("site:retirement-owner"), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.sitePreparation(new SubjectId("site:retirement-owner"), new SubjectId("job:retirement-owner")),
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
                new SubjectId("site:durable-retirement-account"), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.sitePreparation(new SubjectId("site:durable-retirement-account"), new SubjectId("job:durable-retirement-account")),
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
                new SubjectId("site:none-schedule"), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.sitePreparation(new SubjectId("site:none-schedule"), new SubjectId("job:none-schedule")),
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
    void declaredOwnerCannotCloseByRoundTrippingAGenericAllNoneAccount() {
        PhysicalIntentLifecycleOwner owner = PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION;
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:generic-none"), PhysicalIntentKind.RESOURCE_SITE_PREPARATION,
                PhysicalIntentStatus.RUNNING, new SubjectId("site:generic-none"), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.sitePreparation(new SubjectId("site:generic-none"), new SubjectId("job:generic-none")),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.whole(64), FixedScalar.ZERO), 0,
                PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED, owner);
        PhysicalIntentTransition terminal = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty());
        PhysicalIntentRetirementAccount generic = PhysicalIntentRetirementAccount.declared(owner,
                java.util.EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class),
                (before, command, candidate, transition) -> PhysicalIntentRetirementAccount.checkedNone(owner, command, candidate, transition),
                (before, candidate, transition, binding) -> PhysicalIntentRetirementAccount.requireSameDeclaredAccount(binding,
                        PhysicalIntentRetirementAccount.checkedNoneWithContinuation(owner, binding.continuation(), candidate, transition)),
                (before, after, candidate, transition, binding) -> { });
        assertThrows(IllegalArgumentException.class, () -> generic.verifyPlan(null, null, intent, terminal,
                new CommandPlan.Accepted(List.of(new io.farfrontier.palemirror.frontier.v3.api.ProposedEvent(intent.causeSubjectId(), terminal)))));
    }

    @Test
    void replayAccountEqualityRejectsAChangedEngineScheduleDisposition() {
        PhysicalIntentLifecycleOwner owner = PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION;
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:replay-schedule"),
                PhysicalIntentKind.RESOURCE_SITE_PREPARATION, PhysicalIntentStatus.RUNNING,
                new SubjectId("site:replay-schedule"), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.sitePreparation(new SubjectId("site:replay-schedule"), new SubjectId("job:replay-schedule")),
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
                new SubjectId("site:exact-schedule"), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.sitePreparation(new SubjectId("site:exact-schedule"), new SubjectId("job:exact-schedule")),
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
                java.util.Optional.empty(), java.util.Optional.of(proof), java.util.Optional.of(recoveryDiagnostic(intentId)));
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
        return new AbstractPhysicalIntentLifecycleCapability(declaration(owner, kinds), retirementPolicy, account,
                PhysicalIntentResolvedRetentionPolicy.confirmedReceiptWithoutRecovery(),
                kinds.isEmpty() ? PhysicalIntentRecoveryDiagnosticProducer.NO_PHYSICAL_CAPABILITY : PhysicalIntentRecoveryDiagnosticProducer.valueOf(owner.name())) { };
    }

    private static DiagnosticTuple recoveryDiagnostic(PhysicalIntentId intentId) {
        SubjectId id = new SubjectId(intentId.value());
        return new DiagnosticTuple(DiagnosticReason.PHYSICAL_CUSTODY_UNRESOLVED, DiagnosticCategory.RECOVERY_UNKNOWN,
                new DiagnosticOwner(DiagnosticOwnerKind.PHYSICAL_INTENT, id),
                new DiagnosticSubject(DiagnosticSubjectKind.PHYSICAL_EFFECT, id), DiagnosticDisposition.INSPECT);
    }

    private static PhysicalIntentRetirementAccount account(PhysicalIntentLifecycleOwner owner) {
        return PhysicalIntentRetirementAccount.declared(owner,
                java.util.EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class),
                (before, command, intent, transition) -> new PhysicalIntentRetirementAccount.Binding(owner, intent.id(),
                        new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_APPLICABLE_RELATION),
                        command == null ? new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION)
                                : command.scheduleBinding().<PhysicalIntentRetirementAccount.Obligation<ScheduleId>>map(value -> new PhysicalIntentRetirementAccount.Exact<>(value.action().id()))
                                        .orElseGet(() -> new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_ENGINE_CONTINUATION)),
                        new PhysicalIntentRetirementAccount.Exact<>(intent.causeSubjectId()),
                        new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_RESOURCE_COMMITMENT),
                        transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART ? PhysicalIntentRetirementAccount.LateDisposition.RETAIN_AMBIGUOUS_RECOVERY
                                : PhysicalIntentRetirementAccount.LateDisposition.REJECT_STALE_ONCE),
                (before, intent, transition, binding) -> PhysicalIntentRetirementAccount.requireSameDeclaredAccount(binding,
                        new PhysicalIntentRetirementAccount.Binding(owner, intent.id(),
                                new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_APPLICABLE_RELATION), binding.continuation(),
                                new PhysicalIntentRetirementAccount.Exact<>(intent.causeSubjectId()),
                                new PhysicalIntentRetirementAccount.CheckedNone<>(PhysicalIntentRetirementProof.Absence.NO_RESOURCE_COMMITMENT),
                                transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART ? PhysicalIntentRetirementAccount.LateDisposition.RETAIN_AMBIGUOUS_RECOVERY
                                        : PhysicalIntentRetirementAccount.LateDisposition.REJECT_STALE_ONCE)),
                (before, after, intent, transition, binding) -> {
                    if (intent.lifecycleOwner() != owner) throw new IllegalArgumentException("test owner mismatch");
                });
    }

    private static Set<PhysicalIntentKind> supported(PhysicalIntentLifecycleOwner owner) {
        return Arrays.stream(PhysicalIntentRoleSchema.values()).filter(schema -> schema.owner() == owner)
                .map(PhysicalIntentRoleSchema::kind).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Test-only complete fixture; production must supply these sets at each family boundary. */
    private static PhysicalIntentLifecycleDeclaration declaration(PhysicalIntentLifecycleOwner owner, Set<PhysicalIntentKind> kinds) {
        Set<PhysicalIntentRoleSchema> schemas = Arrays.stream(PhysicalIntentRoleSchema.values())
                .filter(schema -> schema.owner() == owner && kinds.contains(schema.kind()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return kinds.isEmpty() ? new PhysicalIntentLifecycleDeclaration(owner, PhysicalIntentLifecycleDeclaration.VERSION, Set.of(), Set.of(), 0, 0)
                : PhysicalIntentLifecycleDeclaration.physical(owner, kinds, schemas);
    }

    private static PhysicalIntent retentionIntent(String suffix, PhysicalIntentStatus status) {
        SubjectId site = new SubjectId("site:retention-" + suffix);
        PhysicalIntent prepared = new PhysicalIntent(new PhysicalIntentId("intent:retention-" + suffix), PhysicalIntentKind.RESOURCE_SITE_PREPARATION,
                status == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART ? PhysicalIntentStatus.PREPARED : status, site, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.sitePreparation(site,
                new SubjectId("job:retention-" + suffix)), new FixedPosition(FixedScalar.ZERO, FixedScalar.whole(64), FixedScalar.ZERO),
                0, PhysicalPostcondition.RESOURCE_SITE_PREPARED_OBSERVED, PhysicalIntentLifecycleOwner.RESOURCE_SITE_PREPARATION);
        return status == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                ? prepared.withRecoveryUnknown(PhysicalIntentRecoveryDiagnosticProducer.RESOURCE_SITE_PREPARATION.stamp(prepared)) : prepared;
    }
}
