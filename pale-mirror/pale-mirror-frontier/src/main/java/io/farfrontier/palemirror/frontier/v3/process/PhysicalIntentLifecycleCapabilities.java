package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.CommandRejection;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.RejectionCode;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhysicalIntentSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;

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
    private final int unresolvedAdmissionCapacity;

    private PhysicalIntentLifecycleCapabilities(Map<PhysicalIntentLifecycleOwner, PhysicalIntentLifecycleCapability> byOwner) {
        this.byOwner = Map.copyOf(byOwner);
        this.fingerprint = fingerprint(this.byOwner);
        this.unresolvedAdmissionCapacity = this.byOwner.values().stream()
                .mapToInt(capability -> capability.declaration().maxUnresolved()).sum();
    }

    static PhysicalIntentLifecycleCapabilities compose(Collection<? extends FrontierWorldProcessModule> modules) {
        Objects.requireNonNull(modules, "physical lifecycle modules");
        Map<PhysicalIntentLifecycleOwner, PhysicalIntentLifecycleCapability> capabilities = new EnumMap<>(PhysicalIntentLifecycleOwner.class);
        for (FrontierWorldProcessModule module : modules) {
            for (PhysicalIntentLifecycleCapability capability : module.physicalIntentLifecycleCapabilities()) {
                if (capability == null || capability.owner() == null || capability.compatibleKinds() == null
                        || capability.declaration() == null
                        || capability.recoveryDiagnosticProducer() == null
                        || capability.resolvedRetentionPolicy() == null
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
                        || !declaration.kinds().equals(capability.compatibleKinds())
                        || (!declaration.kinds().isEmpty() && capability.recoveryDiagnosticProducer().owner() != capability.owner())) {
                    throw new IllegalArgumentException("physical lifecycle capability has mismatched declared role schemas: "
                            + capability.owner().stableId());
                }
                if (!declaration.kinds().isEmpty()
                        && (declaration.maxUnresolved() != PhysicalIntentLifecycleDeclaration.MAX_PER_OWNER
                        || declaration.maxResolvedRetention() != PhysicalIntentLifecycleDeclaration.MAX_PER_OWNER)) {
                    throw new IllegalArgumentException("physical lifecycle owner must declare the exact fair retention share: "
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
        PhysicalIntentLifecycleCapabilities composition = new PhysicalIntentLifecycleCapabilities(capabilities);
        if (composition.unresolvedAdmissionCapacity > FrontierWorldState.MAX_PHYSICAL_INTENTS) {
            throw new IllegalArgumentException("physical lifecycle declarations exceed the aggregate unresolved admission bound");
        }
        return composition;
    }

    CommandPlan planPrepared(FrontierWorldState state, FrontierCommand command, PhysicalIntentPrepared prepared) {
        PhysicalIntentLifecycleCapability capability = capability(prepared.intent());
        requirePreparationCapacity(state, capability);
        return capability.planPrepared(state, command, prepared);
    }

    CommandPlan planTransition(FrontierWorldState state, FrontierCommand command, PhysicalIntent intent,
                               PhysicalIntentTransition transition) {
        PhysicalIntentLifecycleCapability capability = capability(intent);
        PhysicalIntentTransition declared = transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                ? transition.withRecoveryDiagnostic(capability.recoveryUnknownDiagnostic(intent)) : transition;
        try {
            CommandPlan plan = retires(declared) ? capability.retirementPolicy().plan(state, command, intent, declared)
                    : capability.planTransition(state, command, intent, declared);
            if (!retires(declared)) return plan;
            PhysicalIntentRetirementAccount.Binding binding = capability.retirementAccount()
                    .verifyPlan(state, command, intent, declared, plan);
            if (!(plan instanceof CommandPlan.Accepted accepted) || binding == null) return plan;
            int terminalEvents = 0;
            java.util.List<ProposedEvent> accounted = new java.util.ArrayList<>(accepted.events().size());
            for (ProposedEvent event : accepted.events()) {
                if (event.payload() instanceof PhysicalIntentTransition candidate
                        && candidate.intentId().equals(intent.id()) && candidate.status() == declared.status()) {
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
        FrontierWorldState retained = compactResolvedForPreparation(state, capability);
        requirePreparationCapacity(retained, capability);
        FrontierWorldState reduced = capability.reducePrepared(retained, subject, intent);
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
        if (transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART && transition.diagnostic().isEmpty()) {
            throw new IllegalArgumentException("persisted recovery-unknown transition lacks a producer-stamped diagnostic tuple");
        }
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

    /** Sum of explicit owner quotas; diagnostics expose the exact fair aggregate partition. */
    int unresolvedAdmissionCapacity() { return unresolvedAdmissionCapacity; }

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
            // An explicitly non-physical owner has no intent queue to saturate.
            // Its zero quota forbids admission; it does not indicate retained pressure.
            PhysicalIntentLifecycleCompositionDiagnostic.Pressure pressure = declaration.kinds().isEmpty()
                    ? PhysicalIntentLifecycleCompositionDiagnostic.Pressure.OPEN
                    : unresolved >= declaration.maxUnresolved()
                    ? PhysicalIntentLifecycleCompositionDiagnostic.Pressure.UNRESOLVED_SATURATED
                    : resolved >= declaration.maxResolvedRetention() || unresolved + resolved >= declaration.maxUnresolved()
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
        java.util.Set<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId> compactable = compactableResolvedIds(state, capability);
        if (resolved >= declaration.maxResolvedRetention()
                && resolved - compactable.size() >= declaration.maxResolvedRetention()) {
            throw new IllegalArgumentException("physical lifecycle resolved history requires owner-declared compaction: "
                    + capability.owner().stableId());
        }
        if (unresolved + resolved - compactable.size() >= declaration.maxUnresolved()) {
            throw new IllegalArgumentException("physical lifecycle owner retention share is saturated: "
                    + capability.owner().stableId());
        }
        int retainedAfterOwnerCompaction = state.physicalIntents().size() - compactable.size();
        if (retainedAfterOwnerCompaction >= FrontierWorldState.MAX_PHYSICAL_INTENTS) {
            throw new IllegalArgumentException("physical lifecycle aggregate retention is saturated by unresolved owner obligations");
        }
    }

    private FrontierWorldState compactResolvedForPreparation(FrontierWorldState state, PhysicalIntentLifecycleCapability capability) {
        java.util.Set<PhysicalIntentId> resolvedIds = compactableResolvedIds(state, capability);
        if (resolvedIds.isEmpty()) return state;
        Map<PhysicalIntentId, PhysicalIntent> intents = new java.util.LinkedHashMap<>(state.physicalIntents());
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new java.util.LinkedHashMap<>(state.physicalObservations());
        for (PhysicalIntentId id : resolvedIds) {
            PhysicalIntent intent = intents.get(id);
            if (intent == null || intent.status() != PhysicalIntentStatus.CONFIRMED) {
                throw new IllegalArgumentException("owner-selected terminal intent is not confirmed: " + id.value());
            }
            PhysicalObservationId observationId = intent.postconditionObservationId()
                    .orElseThrow(() -> new IllegalArgumentException("confirmed physical intent lacks its exact receipt"));
            PhysicalEffectObservation observation = observations.get(observationId);
            if (observation == null || !observation.intentId().equals(id)) {
                throw new IllegalArgumentException("confirmed physical intent lacks its exact paired receipt");
            }
            intents.remove(id);
            observations.remove(observationId);
        }
        // The closed composition is the sole terminal-history mutation authority. Constructing
        // the replacement through the aggregate validator proves no retained obligation needs it.
        return state.withChanges(FrontierWorldStateUpdate.begin().physicalIntents(intents).physicalObservations(observations));
    }

    private java.util.Set<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId> compactableResolvedIds(
            FrontierWorldState state, PhysicalIntentLifecycleCapability capability) {
        int resolved = 0;
        for (PhysicalIntent intent : state.physicalIntents().values()) {
            if (intent.lifecycleOwner() == capability.owner() && !capability.declaration().unresolved(intent)) resolved++;
        }
        if (resolved < capability.declaration().maxResolvedRetention()
                && resolved + unresolvedCount(state, capability) < capability.declaration().maxUnresolved()) return java.util.Set.of();
        java.util.Set<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId> ids = new java.util.LinkedHashSet<>();
        for (PhysicalIntent intent : state.physicalIntents().values()) {
            if (intent.lifecycleOwner() == capability.owner() && capability.resolvedRetentionPolicy().mayCompact(state, intent)) {
                ids.add(intent.id());
            }
        }
        return java.util.Set.copyOf(ids);
    }

    private int unresolvedCount(FrontierWorldState state, PhysicalIntentLifecycleCapability capability) {
        int unresolved = 0;
        for (PhysicalIntent intent : state.physicalIntents().values()) {
            if (intent.lifecycleOwner() == capability.owner() && capability.declaration().unresolved(intent)) unresolved++;
        }
        return unresolved;
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
