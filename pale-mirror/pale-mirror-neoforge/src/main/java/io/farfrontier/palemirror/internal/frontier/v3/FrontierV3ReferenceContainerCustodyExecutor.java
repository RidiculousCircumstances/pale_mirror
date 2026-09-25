package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLease;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyUnresolvedReason;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.CustodyAcquired;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.CustodyCheckpointed;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.CustodyReleased;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.ReplicaDeclared;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.ReplicaConflictObserved;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.ReplicaObserved;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.ReferenceProjectionPrepared;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.ProjectionCustodyConfirmed;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaRecord;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody;
import io.farfrontier.palemirror.frontier.v3.model.ReplicaCustodyDiagnosticProducer;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.FungibleStackBindingsReleased;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * The one physical adapter for the F0.2B depot and nest-store reference scopes.
 *
 * <p>It consumes the old surface only as a naturally loaded chest locator.  It never makes a
 * surface phase authoritative, force-loads a chunk, or writes a chest.  Every observed state is
 * first recorded through the replica kernel; only a matching current observation receives the
 * short-lived exact-container lease used by the two reference processes.</p>
 */
final class FrontierV3ReferenceContainerCustodyExecutor {
    static final String REPLICA_PROVENANCE_KEY = "pale_mirror:reference_container_provenance";
    private static final long ABSENT_CONFIRMATION_TICKS = 2L;
    /** Transient sampling debounce only; all admitted replica evidence remains durable. */
    private static final Map<ServerLevel, Map<SubjectId, Long>> ABSENT_SINCE_TICK = new WeakHashMap<>();
    /**
     * JVM-local observation only.  A reboot starts with no physical witness, so its persisted
     * HOT layout must survive until a naturally ticking source can reconcile it.  Once that
     * exact epoch has been witnessed in this server process, a later ordinary unload must take
     * the normal checkpoint/release path rather than retaining HOT custody indefinitely.
     */
    private static final Map<ServerLevel, Map<SubjectId, Long>> OBSERVED_CUSTODY_EPOCHS = new WeakHashMap<>();

    private FrontierV3ReferenceContainerCustodyExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        // An unloaded serialized chest is evidence, never a live custodian.  Drain one exact
        // scope per tick so unrelated containers keep progressing.
        for (PhysicalCustodyLease lease : state.replicaCustody().custodyByScope().values().stream()
                .filter(lease -> lease.providerId().equals(ReferenceContainerCustody.PROVIDER_ID) && lease.live())
                .sorted(Comparator.comparing(PhysicalCustodyLease::scopeId)).toList()) {
            if (FrontierV3ResourceSiteLedger.get(level).hasPendingFieldDelivery(lease.objectId())) continue;
            ContainerSurface surface = state.inventory().surfaces().get(lease.objectId());
            if (surface == null || !naturallyTicking(level, position(surface))) {
                // Pending or contradictory writes cannot be blindly released. They also
                // must not consume the only drain turn while making no transition.
                if (lease.status() == PhysicalCustodyLeaseStatus.PREPARING || lease.status() == PhysicalCustodyLeaseStatus.UNRESOLVED) continue;
                // A persisted HOT fungible layout is still the only authority for a possible
                // player/container handoff.  On restart the player is normally not connected
                // when this first loop runs, so checkpointing an unobserved source would turn
                // its saved physical split into an unexplainable COLD mismatch before its
                // naturally loaded recovery visit.  Retain only a current, same-epoch binding;
                // stale or unbound scopes keep the ordinary bounded drain path.
                if (retainsUnobservedRestartFungibleHot(observedCustodyEpochs(level),
                        state.inventory().fungibleResources(), lease)) continue;
                drain(runtime, lease, "unloaded");
                return;
            }
            rememberCurrentProcessObservation(level, lease);
        }
        List<ContainerSurface> loaded = state.inventory().surfaces().values().stream()
                .filter(surface -> naturallyTicking(level, position(surface))
                        && !FrontierV3ResourceSiteLedger.get(level).hasPendingFieldDelivery(surface.containerId())).toList();
        List<ContainerSurface> eligible = eligibleReferenceSurfaces(state, loaded);
        if (!eligible.isEmpty()) reconcile(level, runtime, state, selectRoundRobin(eligible, level.getGameTime()));
    }

    static void reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                  FrontierWorldState state, ContainerSurface surface) {
        SubjectId containerId = surface.containerId();
        PhysicalReplicaRecord replica = state.replicaCustody().replicas().get(containerId);
        ChestBlockEntity chest = chestAt(level, position(surface));
        if (replica == null) {
            // Initial projection remains owned by the generic surface materializer.  It can
            // install and tag the chest before the durable ACTIVE transition is visible to this
            // adapter.  Declaring in that pre-ACTIVE interval would let a later materializer
            // turn be mistaken for missing world evidence, so the first replica boundary is
            // admitted only against a durable active surface and its exact owned chest.
            if (initialDeclarationReady(surface.status(), FrontierV3ContainerSurfaceExecutor.activeChest(level, position(surface), containerId))) {
                declare(runtime, state, containerId);
            }
            return;
        }
        PhysicalCustodyLease projectionLease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(containerId));
        if (projectionLease != null && (projectionLease.status() == PhysicalCustodyLeaseStatus.PREPARING
                || (projectionLease.status() == PhysicalCustodyLeaseStatus.UNRESOLVED && replica.state() == PhysicalReplicaState.CONFLICT))) {
            if (surface.status() == ContainerSurfaceStatus.ACTIVE && stableLoadedObservation(level, containerId, chest)) {
                reconcilePreparedProjection(runtime, state, projectionLease, chest);
            }
            return;
        }
        if (replica.state() == PhysicalReplicaState.CONFLICT) return;
        // The generic socket owner can retain PREPARED while its own durable write/recovery
        // boundary is incomplete.  That is not replica evidence: no reference observation,
        // acquisition, or conflict is admitted until there is a completed physical surface to
        // sample.  This is a lifecycle fence only; ACTIVE never grants custody or overrides
        // the exact fingerprint/provenance comparison below.
        if (!readyForReplicaObservation(surface.status())) return;
        // A placed chest becomes a block before its block entity is available on the server.
        // That short normal-world lifecycle window is neither missing evidence nor foreign
        // evidence; wait until the exact block entity can be classified.  A non-chest block
        // with no entity is still actual missing evidence below.
        if (chest == null && level.getBlockState(position(surface)).is(Blocks.CHEST)) {
            forgetAbsent(level, containerId);
            return;
        }
        // An ordinary player replacement consists of a real break followed by a real placement
        // on adjacent server ticks.  Do not persist the one scheduling gap as missing when a
        // fresh block entity is still arriving; a continuously absent loaded socket is retained
        // as typed missing evidence after this fixed, two-sample bound.  No write is allowed in
        // either branch, and restart discards the debounce rather than fabricating evidence.
        if (!stableLoadedObservation(level, containerId, chest)) return;
        PhysicalCustodyLease lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(containerId));
        if (replica.state() == PhysicalReplicaState.EXPECTED) {
            observe(runtime, state, replica, chest);
            return;
        }
        if (lease == null) {
            acquire(runtime, state, replica, containerId);
            return;
        }
        if (!lease.live()) {
            // A released scope is a durable, exact old observation, not permission to replace
            // whatever happens to be in the chest now.  The old retained comparison is made
            // before a new emission, so a changed/foreign/missing chest is a durable local
            // conflict rather than a new expected projection that would hide its cause.
            // Compare the retained physical image in its own grammar. A COLD successor may
            // already have consumed the old fungible slot, so classifying its still-saved
            // plain stack from today's canonical layout would falsely turn it into foreign
            // exact stock before the old replica fingerprint can be checked.
            Observed beforeCatchup = observedRetained(state, containerId, chest);
            boolean retainedEvidenceMatches = beforeCatchup.fingerprint().equals(replica.fingerprint())
                    && beforeCatchup.provenance().equals(replica.provenance());
            if (!retainedEvidenceMatches) {
                conflict(runtime, replica, beforeCatchup);
                return;
            }
            if (prepareReleasedProjection(runtime, state, replica)) {
                // Fence COLD completion and transfer held exact inputs before writing.
                // Even unchanged inventory slots may hide an input held by a COLD job;
                // reacquiring directly would strand that input outside the physical chest.
                FrontierWorldState emitted = runtime.decodedState()
                        .orElseThrow(() -> new IllegalStateException("reference replica emission did not publish state"));
                FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, emitted, containerId);
            }
            return;
        }
        String canonical = ReferenceContainerCustody.canonicalFingerprint(state, containerId);
        Observed observed = observed(state, containerId, chest);
        if (!canonical.equals(replica.fingerprint()) || !observed.fingerprint().equals(replica.fingerprint())
                || !observed.provenance().equals(replica.provenance())) {
            // A physical executor can legitimately mutate this chest while its exact lease is
            // live.  Its durable canonical receipt and the loaded observed slots then agree on
            // the next fingerprint, whereas ordinary player/world drift does not.  Preserve
            // that fact through checkpoint/release and immediately establish the next emitted
            // boundary; otherwise the later released-scope comparison mistakes our own effect
            // for foreign drift.  No mismatching observation is adopted here.
            if (lease.status() == PhysicalCustodyLeaseStatus.CHECKPOINTED
                    && canonical.equals(observed.fingerprint()) && replica.provenance().equals(observed.provenance())
                    && closeConfirmedMutation(runtime, state, containerId)) {
                FrontierWorldState emitted = runtime.decodedState()
                        .orElseThrow(() -> new IllegalStateException("reference mutation boundary did not publish state"));
                if (emitted.replicaCustody().replicas().containsKey(containerId)) {
                    FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, emitted, containerId);
                }
                return;
            }
            drain(runtime, lease, "renew");
        } else if (lease.status() == PhysicalCustodyLeaseStatus.CHECKPOINTED) {
            // A loaded, unchanged chest does not cancel an already recorded drain.
            // Finish it even when an older transaction removed its resource bindings
            // before this scope was released. No physical write or inferred repair.
            release(runtime, lease);
        }
    }

    private static void declare(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SubjectId containerId) {
        long revision = runtime.canonicalState().orElseThrow().revision().value();
        submit(runtime, "declare", containerId, 1L, new ReplicaDeclared(PhysicalReplicaRecord.expected(containerId,
                ReferenceContainerCustody.semanticKind(state, containerId), revision,
                ReferenceContainerCustody.canonicalFingerprint(state, containerId), ReferenceContainerCustody.provenance(containerId))));
    }

    static boolean prepareInitialProjection(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                            FrontierWorldState state, SubjectId containerId) {
        long epoch = Math.addExact(state.replicaCustody().custodyByScope().values().stream()
                .filter(prior -> prior.objectId().equals(containerId)).mapToLong(PhysicalCustodyLease::authorityEpoch).max().orElse(0L), 1L);
        return submit(runtime, "prepare-projection", containerId, epoch, new ReferenceProjectionPrepared(containerId, epoch, 0L, "", ""));
    }

    /** Only actual loaded slots may confirm or conflict the already durable write fence. */
    static boolean reconcilePreparedProjection(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                               PhysicalCustodyLease lease, ChestBlockEntity chest) {
        PhysicalReplicaRecord replica = state.replicaCustody().replicas().get(lease.objectId());
        Observed actual = observed(state, lease.objectId(), chest);
        if (replica.fingerprint().equals(actual.fingerprint()) && replica.provenance().equals(actual.provenance())) {
            return submit(runtime, "confirm-projection", lease.objectId(), replica.replicaRevision(),
                    new ProjectionCustodyConfirmed(lease.scopeId(), lease.authorityEpoch(), lease.expectedCanonicalRevision(),
                            lease.expectedReplicaRevision(), actual.fingerprint(), actual.provenance()));
        }
        if (lease.status() != PhysicalCustodyLeaseStatus.PREPARING) return false;
        return submit(runtime, "projection-conflict", lease.objectId(), replica.replicaRevision(),
                ReplicaCustodyDiagnosticProducer.projectionConflict(lease.scopeId(), lease.authorityEpoch(), lease.expectedCanonicalRevision(),
                        lease.expectedReplicaRevision(), actual.fingerprint(), actual.provenance()));
    }

    private static void observe(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                PhysicalReplicaRecord replica, ChestBlockEntity chest) {
        Observed observed = observed(state, replica.objectId(), chest);
        submit(runtime, "observe", replica.objectId(), replica.replicaRevision(), new ReplicaObserved(replica.objectId(),
                replica.emittedCanonicalRevision(), replica.replicaRevision(), observed.fingerprint(), observed.provenance(),
                replica.emittedCanonicalRevision()));
    }

    private static void conflict(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalReplicaRecord replica, Observed observed) {
        submit(runtime, "conflict", replica.objectId(), replica.replicaRevision(), ReplicaCustodyDiagnosticProducer.conflict(replica.objectId(),
                replica.emittedCanonicalRevision(), replica.replicaRevision(), observed.fingerprint(), observed.provenance()));
    }

    /** A witnessed non-replayable transfer keeps the depot exclusive; it may still report real foreign contents. */
    static boolean reportForeignDuringFieldDelivery(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                    FrontierWorldState state, PhysicalReplicaRecord replica,
                                                    ChestBlockEntity chest) {
        Observed actual = observed(state, replica.objectId(), chest);
        if (actual.fingerprint().equals(replica.fingerprint()) && actual.provenance().equals(replica.provenance()))
            return false;
        conflict(runtime, replica, actual);
        return true;
    }

    private static void acquire(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                PhysicalReplicaRecord replica, SubjectId containerId) {
        long epoch = state.replicaCustody().custodyByScope().values().stream()
                .filter(prior -> prior.objectId().equals(containerId)).mapToLong(PhysicalCustodyLease::authorityEpoch).max().orElse(0L) + 1L;
        PhysicalCustodyLease requested = new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(containerId), containerId,
                ReferenceContainerCustody.PROVIDER_ID, epoch, replica.observedCanonicalRevision(), replica.replicaRevision(),
                PhysicalCustodyLeaseStatus.ACQUIRED, null);
        submit(runtime, "acquire", containerId, epoch, new CustodyAcquired(requested));
    }

    static boolean prepareReleasedProjection(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                  PhysicalReplicaRecord replica) {
        long epoch = Math.addExact(state.replicaCustody().custodyByScope().values().stream()
                .filter(prior -> prior.objectId().equals(replica.objectId()))
                .mapToLong(PhysicalCustodyLease::authorityEpoch).max().orElse(0L), 1L);
        return submit(runtime, "prepare-projection", replica.objectId(), epoch,
                new ReferenceProjectionPrepared(replica.objectId(), epoch, replica.replicaRevision(),
                        replica.fingerprint(), replica.provenance()));
    }

    /** Checkpoint then release; the next re-observation retains malformed/missing actual evidence. */
    private static void drain(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalCustodyLease lease, String reason) {
        if (lease.status() == PhysicalCustodyLeaseStatus.ACQUIRED) {
            submit(runtime, "checkpoint-" + reason, lease.objectId(), lease.authorityEpoch(), new CustodyCheckpointed(lease.scopeId(),
                    lease.authorityEpoch(), lease.expectedCanonicalRevision(), lease.expectedReplicaRevision()));
        } else if (lease.status() == PhysicalCustodyLeaseStatus.CHECKPOINTED) {
            release(runtime, lease);
        } else if (lease.status() == PhysicalCustodyLeaseStatus.UNRESOLVED) {
            // Retained unresolved custody is deliberately local and cannot be fabricated closed.
        }
    }

    private static boolean release(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalCustodyLease lease) {
        FrontierWorldState state = runtime.decodedState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        List<PhysicalStackBinding> bound = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> state.inventory().fungibleResources().accounts().get(binding.accountId()).custody() instanceof ResourceCustody.Container container
                        && container.containerId().equals(lease.objectId())).toList();
        if (!bound.isEmpty()) {
            if (bound.stream().anyMatch(binding -> binding.authorityEpoch() != lease.authorityEpoch())) {
                throw new IllegalStateException("fungible resource binding cannot outlive its reference custody epoch");
            }
            SubjectId accountId = bound.getFirst().accountId();
            if (bound.stream().anyMatch(binding -> !binding.accountId().equals(accountId))) {
                throw new IllegalStateException("one reference container has multiple live fungible accounts");
            }
            return submit(runtime, "fungible-release", lease.objectId(), lease.authorityEpoch(),
                    new FungibleStackBindingsReleased(accountId, lease.authorityEpoch()));
        }
        return submit(runtime, "release-renew", lease.objectId(), lease.authorityEpoch(), new CustodyReleased(lease.scopeId(),
                lease.authorityEpoch(), lease.expectedCanonicalRevision(), lease.expectedReplicaRevision()));
    }

    /**
     * An unloaded reference scope may release only after its fungible layout is no longer the
     * current HOT evidence.  This is intentionally narrower than a generic live-lease check:
     * an epoch mismatch remains fenced and follows the normal release/conflict route.
     */
    static boolean retainsUnobservedRestartFungibleHot(Map<SubjectId, Long> observedEpochs,
                                                       io.farfrontier.palemirror.frontier.v3.model.FungibleResourceLedger resources,
                                                       PhysicalCustodyLease lease) {
        return !Objects.equals(observedEpochs.get(lease.scopeId()), lease.authorityEpoch())
                && resources.bindings().values().stream().anyMatch(binding -> binding.authorityEpoch() == lease.authorityEpoch()
                && resources.accounts().get(binding.accountId()).custody() instanceof ResourceCustody.Container container
                && container.containerId().equals(lease.objectId()));
    }

    private static Map<SubjectId, Long> observedCustodyEpochs(ServerLevel level) {
        return OBSERVED_CUSTODY_EPOCHS.computeIfAbsent(level, ignored -> new HashMap<>());
    }

    private static void rememberCurrentProcessObservation(ServerLevel level, PhysicalCustodyLease lease) {
        observedCustodyEpochs(level).put(lease.scopeId(), lease.authorityEpoch());
    }

    /** Closes the checked physical effect through the one typed successor-boundary transaction. */
    static boolean checkpointConfirmedMutation(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                               SubjectId containerId, ChestBlockEntity chest) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        PhysicalReplicaRecord replica = state.replicaCustody().replicas().get(containerId);
        PhysicalCustodyLease lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(containerId));
        if (replica == null || lease == null || !ReferenceContainerCustody.hasOperationalCustody(state, containerId) || !lease.objectId().equals(containerId)
                || !lease.providerId().equals(ReferenceContainerCustody.PROVIDER_ID)) return false;
        Observed observed = observed(state, containerId, chest);
        if (!ReferenceContainerCustody.canonicalFingerprint(state, containerId).equals(observed.fingerprint())
                || !replica.provenance().equals(observed.provenance())) return retainUnresolved(runtime, lease, PhysicalCustodyUnresolvedReason.OBSERVATION_MISMATCH);
        return closeConfirmedMutation(runtime, state, containerId) || retainUnresolved(runtime, lease, PhysicalCustodyUnresolvedReason.PROVIDER_LOST);
    }

    private static boolean closeConfirmedMutation(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                   FrontierWorldState state, SubjectId containerId) {
        try {
            PhysicalCustodyLease lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(containerId));
            if (lease == null) return false;
            long revision = Math.max(runtime.canonicalState().orElseThrow().revision().value(), lease.expectedCanonicalRevision() + 1L);
            return submit(runtime, "close-confirmed-mutation", containerId, revision,
                    ReferenceContainerCustody.confirmedMutationTransition(state, containerId, revision));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean retainUnresolved(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                            PhysicalCustodyLease lease, PhysicalCustodyUnresolvedReason reason) {
        if (lease.status() == PhysicalCustodyLeaseStatus.UNRESOLVED) return true;
        if (!lease.live()) return false;
        return submit(runtime, "unresolved-confirmed-mutation", lease.objectId(), lease.authorityEpoch(),
                ReplicaCustodyDiagnosticProducer.unresolved(lease.scopeId(), lease.authorityEpoch(),
                        lease.expectedCanonicalRevision(), lease.expectedReplicaRevision(), reason));
    }

    static ContainerSurface selectRoundRobin(List<ContainerSurface> eligible, long tick) {
        if (eligible.isEmpty()) throw new IllegalArgumentException("reference custody has no eligible container");
        return eligible.get((int) Math.floorMod(tick, eligible.size()));
    }

    static boolean initialDeclarationReady(ContainerSurfaceStatus status, ChestBlockEntity chest) {
        return status == ContainerSurfaceStatus.ACTIVE && chest != null;
    }

    static boolean readyForReplicaObservation(ContainerSurfaceStatus status) {
        return status == ContainerSurfaceStatus.ACTIVE;
    }

    private static boolean stableLoadedObservation(ServerLevel level, SubjectId containerId, ChestBlockEntity chest) {
        if (chest != null) {
            forgetAbsent(level, containerId);
            return true;
        }
        Map<SubjectId, Long> absent = ABSENT_SINCE_TICK.computeIfAbsent(level, ignored -> new HashMap<>());
        long first = absent.computeIfAbsent(containerId, ignored -> level.getGameTime());
        if (level.getGameTime() - first < ABSENT_CONFIRMATION_TICKS) return false;
        absent.remove(containerId);
        if (absent.isEmpty()) ABSENT_SINCE_TICK.remove(level);
        return true;
    }

    private static void forgetAbsent(ServerLevel level, SubjectId containerId) {
        Map<SubjectId, Long> absent = ABSENT_SINCE_TICK.get(level);
        if (absent == null) return;
        absent.remove(containerId);
        if (absent.isEmpty()) ABSENT_SINCE_TICK.remove(level);
    }

    static List<ContainerSurface> eligibleReferenceSurfaces(FrontierWorldState state, List<ContainerSurface> loaded) {
        return loaded.stream().filter(surface -> ReferenceContainerCustody.isReferenceContainer(state, surface.containerId()))
                .sorted(Comparator.comparing(ContainerSurface::containerId))
                // Ordinary released conflicts remain isolated. A retained pending-write
                // conflict may receive matching later evidence under its same exact epoch;
                // round-robin selection prevents that inspection from monopolizing service.
                .filter(surface -> {
                    PhysicalReplicaRecord replica = state.replicaCustody().replicas().get(surface.containerId());
                    if (replica == null || replica.state() != PhysicalReplicaState.CONFLICT) return true;
                    PhysicalCustodyLease lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(surface.containerId()));
                    return ReferenceContainerCustody.hasLiveCustody(state, surface.containerId()) && lease.status() == PhysicalCustodyLeaseStatus.UNRESOLVED
                            && lease.unresolvedReason() == PhysicalCustodyUnresolvedReason.OBSERVATION_MISMATCH;
                }).toList();
    }

    static Observed observed(FrontierWorldState state, SubjectId containerId, ChestBlockEntity chest) {
        return observed(state, containerId, chest, false);
    }

    static Observed observedRetained(FrontierWorldState state, SubjectId containerId, ChestBlockEntity chest) {
        return observed(state, containerId, chest, true);
    }

    private static Observed observed(FrontierWorldState state, SubjectId containerId, ChestBlockEntity chest,
                                     boolean retainedImage) {
        if (chest == null) return new Observed("sha256:missing-" + containerId.value(), "missing:" + containerId.value());
        List<ReferenceContainerCustody.ObservedSlot> slots = new ArrayList<>(chest.getContainerSize());
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty()) slots.add(ReferenceContainerCustody.ObservedSlot.empty(slot));
            else {
                CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
                String kind = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                ReferenceContainerCustody.ProjectedFungibleSlot fungible = ReferenceContainerCustody.expectedFungibleSlot(state, containerId, slot).orElse(null);
                if (custom == null && (retainedImage || fungible != null && fungible.itemKind().equals(kind)
                        && fungible.quantity() == stack.getCount())
                        && ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem(), stack.getCount()))) {
                    slots.add(ReferenceContainerCustody.ObservedSlot.fungible(slot, kind, stack.getCount()));
                    continue;
                }
                String itemId = custom == null ? "foreign:" + BuiltInRegistries.ITEM.getKey(stack.getItem())
                        : custom.copyTag().getString(FrontierV3CargoHandoffExecutor.ITEM_ID_KEY);
                if (itemId.isBlank()) itemId = "foreign:" + BuiltInRegistries.ITEM.getKey(stack.getItem());
                slots.add(new ReferenceContainerCustody.ObservedSlot(slot, itemId, kind, stack.getCount()));
            }
        }
        String owner = chest.getPersistentData().getString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY);
        String taggedProvenance = chest.getPersistentData().getString(REPLICA_PROVENANCE_KEY);
        String provenance = !owner.equals(containerId.value())
                ? "foreign:container-owner=" + (owner.isBlank() ? "untagged" : owner) + ";replica-provenance=" + (taggedProvenance.isBlank() ? "missing" : taggedProvenance)
                : taggedProvenance.isBlank() ? "missing:replica-provenance:" + containerId.value() : taggedProvenance;
        return new Observed(ReferenceContainerCustody.observedFingerprint(state, containerId, slots), provenance);
    }

    private static boolean submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, String phase, SubjectId objectId,
                                  long fence, FrontierPayload payload) {
        var checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId command = new CommandId("executor:reference-container-" + phase + "-" + objectId.value().replace(':', '-') + "-" + fence);
        CommandResult result = runtime.submit(new FrontierCommand(1, command, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), payload)).orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        return result instanceof CommandResult.Accepted;
    }

    private static BlockPos position(ContainerSurface surface) { return new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()); }
    /** A retained full chunk cache is serialized evidence, not a normally ticking physical scope. */
    private static boolean naturallyTicking(ServerLevel level, BlockPos position) {
        return level.hasChunkAt(position) && level.shouldTickBlocksAt(position);
    }
    private static ChestBlockEntity chestAt(ServerLevel level, BlockPos position) {
        return level.getBlockEntity(position) instanceof ChestBlockEntity chest ? chest : null;
    }
    record Observed(String fingerprint, String provenance) { }
}
