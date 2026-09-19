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
import java.util.Comparator;

/** Family-agnostic closed composition for durable physical lifecycle capabilities. */
final class PhysicalIntentLifecycleCapabilities {
    private final Map<PhysicalIntentLifecycleOwner, PhysicalIntentLifecycleCapability> byOwner;
    private final String fingerprint;

    private PhysicalIntentLifecycleCapabilities(Map<PhysicalIntentLifecycleOwner, PhysicalIntentLifecycleCapability> byOwner) {
        this.byOwner = Map.copyOf(byOwner);
        this.fingerprint = fingerprint(this.byOwner);
    }

    static PhysicalIntentLifecycleCapabilities compose(Collection<? extends FrontierWorldProcessModule> modules) {
        Objects.requireNonNull(modules, "physical lifecycle modules");
        Map<PhysicalIntentLifecycleOwner, PhysicalIntentLifecycleCapability> capabilities = new EnumMap<>(PhysicalIntentLifecycleOwner.class);
        for (FrontierWorldProcessModule module : modules) {
            for (PhysicalIntentLifecycleCapability capability : module.physicalIntentLifecycleCapabilities()) {
                if (capability == null || capability.owner() == null || capability.compatibleKinds() == null
                        || capability.declaration() == null
                        || capability.retirementPolicy() == null || capability.retirementAccount() == null
                        || capability.retirementAccount().owner() != capability.owner()
                        || !capability.retirementAccount().checkedDimensions()
                        .containsAll(EnumSet.allOf(PhysicalIntentRetirementAccount.Dimension.class))) {
                    throw new IllegalArgumentException("physical lifecycle composition contains an undeclared capability");
                }
                if (capabilities.putIfAbsent(capability.owner(), capability) != null) {
                    throw new IllegalArgumentException("duplicate physical lifecycle capability: " + capability.owner().stableId());
                }
                PhysicalIntentLifecycleDeclaration declaration = capability.declaration();
                if (declaration.owner() != capability.owner()
                        || !declaration.kinds().equals(capability.compatibleKinds())) {
                    throw new IllegalArgumentException("physical lifecycle capability has mismatched declared role schemas: "
                            + capability.owner().stableId());
                }
            }
        }
        for (PhysicalIntentLifecycleOwner owner : PhysicalIntentLifecycleOwner.values()) {
            if (!capabilities.containsKey(owner)) {
                throw new IllegalArgumentException("missing physical lifecycle capability: " + owner.stableId());
            }
        }
        java.util.Set<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema> suppliedSchemas =
                java.util.EnumSet.noneOf(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema.class);
        for (PhysicalIntentLifecycleCapability capability : capabilities.values()) {
            for (io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema schema : capability.declaration().schemas()) {
                if (!suppliedSchemas.add(schema)) {
                    throw new IllegalArgumentException("physical lifecycle role schema is declared more than once: " + schema.wireTag());
                }
            }
        }
        if (!suppliedSchemas.equals(java.util.EnumSet.allOf(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema.class))) {
            throw new IllegalArgumentException("physical lifecycle composition is missing an exact role-schema declaration");
        }
        return new PhysicalIntentLifecycleCapabilities(capabilities);
    }

    CommandPlan planPrepared(FrontierWorldState state, FrontierCommand command, PhysicalIntentPrepared prepared) {
        PhysicalIntentLifecycleCapability capability = capability(prepared.intent());
        requirePreparationCapacity(state, capability);
        return capability.planPrepared(state, command, prepared);
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
        requirePreparationCapacity(state, capability);
        FrontierWorldState reduced = capability.reducePrepared(state, subject, intent);
        var existing = reduced.fencedRecovery().current().get(FencedRecoveryPhysicalIntentSupport.bindingId(intent));
        if (existing != null) {
            FencedRecoveryPhysicalIntentSupport.requirePreparedExecutionAuthority(reduced.fencedRecovery(), intent, capability.recoveryAsset(intent));
            return reduced;
        }
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
        if (capability == null || !capability.compatibleKinds().contains(intent.kind()) || !capability.declaration().admits(intent)) {
            throw new IllegalArgumentException("physical intent lifecycle capability is missing or mismatched: "
                    + intent.lifecycleOwner().stableId() + " / " + intent.kind());
        }
        return capability;
    }

    String fingerprint() { return fingerprint; }

    void requireRetainedState(FrontierWorldState state) {
        Objects.requireNonNull(state, "physical lifecycle retained state");
        for (PhysicalIntent intent : state.physicalIntents().values()) capability(intent);
    }

    PhysicalIntentLifecycleCompositionDiagnostic diagnostic(FrontierWorldState state) {
        requireRetainedState(state);
        java.util.List<PhysicalIntentLifecycleCompositionDiagnostic.Owner> owners = new java.util.ArrayList<>();
        for (PhysicalIntentLifecycleOwner owner : PhysicalIntentLifecycleOwner.values()) {
            PhysicalIntentLifecycleCapability capability = byOwner.get(owner);
            PhysicalIntentLifecycleDeclaration declaration = capability.declaration();
            int unresolved = 0, resolved = 0, recovery = 0;
            for (PhysicalIntent intent : state.physicalIntents().values()) {
                if (intent.lifecycleOwner() != owner) continue;
                if (declaration.unresolved(intent)) unresolved++; else resolved++;
                if (state.fencedRecovery().current().containsKey(
                        io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhysicalIntentSupport.bindingId(intent))) recovery++;
            }
            PhysicalIntentLifecycleCompositionDiagnostic.Pressure pressure = unresolved >= declaration.maxUnresolved()
                    ? PhysicalIntentLifecycleCompositionDiagnostic.Pressure.UNRESOLVED_SATURATED
                    : resolved >= declaration.maxResolvedRetention()
                    ? PhysicalIntentLifecycleCompositionDiagnostic.Pressure.COMPACTION_REQUIRED
                    : PhysicalIntentLifecycleCompositionDiagnostic.Pressure.OPEN;
            owners.add(new PhysicalIntentLifecycleCompositionDiagnostic.Owner(owner, declaration.version(),
                    declaration.schemas().stream().map(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema::wireTag).sorted().toList(), unresolved, resolved, recovery,
                    declaration.maxUnresolved(), declaration.maxResolvedRetention(), pressure));
        }
        return new PhysicalIntentLifecycleCompositionDiagnostic(fingerprint, owners);
    }

    private void requirePreparationCapacity(FrontierWorldState state, PhysicalIntentLifecycleCapability capability) {
        PhysicalIntentLifecycleDeclaration declaration = capability.declaration();
        int unresolved = 0, resolved = 0;
        for (PhysicalIntent existing : state.physicalIntents().values()) {
            if (existing.lifecycleOwner() != capability.owner()) continue;
            if (declaration.unresolved(existing)) unresolved++; else resolved++;
        }
        if (unresolved >= declaration.maxUnresolved()) {
            throw new IllegalArgumentException("physical lifecycle unresolved work is saturated: " + capability.owner().stableId());
        }
        if (resolved >= declaration.maxResolvedRetention()) {
            throw new IllegalArgumentException("physical lifecycle requires owner compaction before new work: " + capability.owner().stableId());
        }
    }

    private static String fingerprint(Map<PhysicalIntentLifecycleOwner, PhysicalIntentLifecycleCapability> capabilities) {
        String material = capabilities.values().stream().sorted(Comparator.comparing(capability -> capability.owner().stableId()))
                .map(capability -> capability.declaration().canonicalMaterial()).collect(java.util.stream.Collectors.joining("\n"));
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("JVM lacks required SHA-256 for physical lifecycle composition", unavailable);
        }
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
