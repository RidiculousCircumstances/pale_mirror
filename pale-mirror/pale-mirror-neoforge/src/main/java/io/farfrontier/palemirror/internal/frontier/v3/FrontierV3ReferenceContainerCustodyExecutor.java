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
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.CustodyAcquired;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.CustodyCheckpointed;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.CustodyReleased;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.ReplicaDeclared;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.ReplicaEmitted;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.ReplicaConflictObserved;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.ReplicaObserved;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaRecord;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaState;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody;
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

    private FrontierV3ReferenceContainerCustodyExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        // An unloaded serialized chest is evidence, never a live custodian.  Drain one exact
        // scope per tick so unrelated containers keep progressing.
        for (PhysicalCustodyLease lease : state.replicaCustody().custodyByScope().values().stream()
                .filter(lease -> lease.providerId().equals(ReferenceContainerCustody.PROVIDER_ID) && lease.live())
                .sorted(Comparator.comparing(PhysicalCustodyLease::scopeId)).toList()) {
            ContainerSurface surface = state.inventory().surfaces().get(lease.objectId());
            if (surface == null || !naturallyTicking(level, position(surface))) {
                drain(runtime, lease, "unloaded");
                return;
            }
        }
        List<ContainerSurface> loaded = state.inventory().surfaces().values().stream().filter(surface -> naturallyTicking(level, position(surface))).toList();
        List<ContainerSurface> eligible = eligibleReferenceSurfaces(state, loaded);
        if (!eligible.isEmpty()) reconcile(level, runtime, state, selectRoundRobin(eligible, level.getGameTime()));
    }

    private static void reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
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
            Observed beforeCatchup = observed(state, containerId, chest);
            boolean retainedEvidenceMatches = beforeCatchup.fingerprint().equals(replica.fingerprint())
                    && beforeCatchup.provenance().equals(replica.provenance());
            if (!retainedEvidenceMatches) {
                conflict(runtime, replica, beforeCatchup);
                return;
            }
            boolean newerCanonicalSlots = !ReferenceContainerCustody.canonicalFingerprint(state, containerId).equals(replica.fingerprint());
            if (newerCanonicalSlots && reemit(runtime, state, replica)) {
                // `ReplicaEmitted` is the durable before-write boundary.  The state passed to
                // this tick predates that accepted command, so using it here would put the old
                // slots back into the chest and turn an owned physical mutation into false
                // drift on the next observation.  Read the exact accepted state instead.
                FrontierWorldState emitted = runtime.decodedState()
                        .orElseThrow(() -> new IllegalStateException("reference replica emission did not publish state"));
                FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, emitted, containerId);
                return;
            }
            // An unchanged released scope may begin its next exact custody cycle.
            // Its retained observation is still current, so no fabricated emission is needed.
            acquire(runtime, state, replica, containerId);
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
                    && release(runtime, lease)) {
                FrontierWorldState released = runtime.decodedState()
                        .orElseThrow(() -> new IllegalStateException("reference custody release did not publish state"));
                PhysicalReplicaRecord releasedReplica = released.replicaCustody().replicas().get(containerId);
                if (releasedReplica != null && reemit(runtime, released, releasedReplica)) {
                    FrontierWorldState emitted = runtime.decodedState()
                            .orElseThrow(() -> new IllegalStateException("reference replica emission did not publish state"));
                    FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, emitted, containerId);
                }
                return;
            }
            drain(runtime, lease, "renew");
        }
    }

    private static void declare(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SubjectId containerId) {
        long revision = runtime.canonicalState().orElseThrow().revision().value();
        submit(runtime, "declare", containerId, 1L, new ReplicaDeclared(PhysicalReplicaRecord.expected(containerId,
                ReferenceContainerCustody.semanticKind(state, containerId), revision,
                ReferenceContainerCustody.canonicalFingerprint(state, containerId), ReferenceContainerCustody.provenance(containerId))));
    }

    private static void observe(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                PhysicalReplicaRecord replica, ChestBlockEntity chest) {
        Observed observed = observed(state, replica.objectId(), chest);
        submit(runtime, "observe", replica.objectId(), replica.replicaRevision(), new ReplicaObserved(replica.objectId(),
                replica.emittedCanonicalRevision(), replica.replicaRevision(), observed.fingerprint(), observed.provenance(),
                replica.emittedCanonicalRevision()));
    }

    private static void conflict(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalReplicaRecord replica, Observed observed) {
        submit(runtime, "conflict", replica.objectId(), replica.replicaRevision(), new ReplicaConflictObserved(replica.objectId(),
                replica.emittedCanonicalRevision(), replica.replicaRevision(), observed.fingerprint(), observed.provenance()));
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

    private static boolean reemit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                  PhysicalReplicaRecord replica) {
        long revision = Math.max(runtime.canonicalState().orElseThrow().revision().value(), replica.emittedCanonicalRevision() + 1L);
        return submit(runtime, "emit", replica.objectId(), replica.replicaRevision(), new ReplicaEmitted(replica.objectId(),
                replica.emittedCanonicalRevision(), replica.replicaRevision(), revision,
                ReferenceContainerCustody.canonicalFingerprint(state, replica.objectId()), ReferenceContainerCustody.provenance(replica.objectId())));
    }

    /** Checkpoint then release; the next re-observation retains malformed/missing actual evidence. */
    private static void drain(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalCustodyLease lease, String reason) {
        if (lease.status() == PhysicalCustodyLeaseStatus.ACQUIRED) {
            submit(runtime, "checkpoint-" + reason, lease.objectId(), lease.authorityEpoch(), new CustodyCheckpointed(lease.scopeId(),
                    lease.authorityEpoch(), lease.expectedCanonicalRevision(), lease.expectedReplicaRevision()));
        } else if (lease.status() == PhysicalCustodyLeaseStatus.CHECKPOINTED) {
            submit(runtime, "release-" + reason, lease.objectId(), lease.authorityEpoch(), new CustodyReleased(lease.scopeId(),
                    lease.authorityEpoch(), lease.expectedCanonicalRevision(), lease.expectedReplicaRevision()));
        } else if (lease.status() == PhysicalCustodyLeaseStatus.UNRESOLVED) {
            // Retained unresolved custody is deliberately local and cannot be fabricated closed.
        }
    }

    private static boolean release(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalCustodyLease lease) {
        return submit(runtime, "release-renew", lease.objectId(), lease.authorityEpoch(), new CustodyReleased(lease.scopeId(),
                lease.authorityEpoch(), lease.expectedCanonicalRevision(), lease.expectedReplicaRevision()));
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
                // A retained local conflict is intentionally terminal for that one object.  Do
                // not let its lexical position starve another independently usable depot/store.
                .filter(surface -> {
                    PhysicalReplicaRecord replica = state.replicaCustody().replicas().get(surface.containerId());
                    return replica == null || replica.state() != PhysicalReplicaState.CONFLICT;
                }).toList();
    }

    static Observed observed(FrontierWorldState state, SubjectId containerId, ChestBlockEntity chest) {
        if (chest == null) return new Observed("sha256:missing-" + containerId.value(), "missing:" + containerId.value());
        List<ReferenceContainerCustody.ObservedSlot> slots = new ArrayList<>(chest.getContainerSize());
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty()) slots.add(ReferenceContainerCustody.ObservedSlot.empty(slot));
            else {
                CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
                String itemId = custom == null ? "foreign:" + BuiltInRegistries.ITEM.getKey(stack.getItem())
                        : custom.copyTag().getString(FrontierV3CargoHandoffExecutor.ITEM_ID_KEY);
                if (itemId.isBlank()) itemId = "foreign:" + BuiltInRegistries.ITEM.getKey(stack.getItem());
                slots.add(new ReferenceContainerCustody.ObservedSlot(slot, itemId,
                        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount()));
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
