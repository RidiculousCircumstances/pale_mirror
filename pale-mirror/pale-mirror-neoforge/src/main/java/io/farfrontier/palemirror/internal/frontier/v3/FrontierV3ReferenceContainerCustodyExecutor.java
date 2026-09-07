package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLease;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.CustodyAcquired;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.CustodyCheckpointed;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.CustodyReleased;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.ReplicaDeclared;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.ReplicaEmitted;
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
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
            if (surface == null || !level.hasChunkAt(position(surface))) {
                drain(runtime, lease, "unloaded");
                return;
            }
        }
        state.inventory().surfaces().values().stream()
                .filter(surface -> ReferenceContainerCustody.isReferenceContainer(state, surface.containerId()))
                .sorted(Comparator.comparing(ContainerSurface::containerId))
                .filter(surface -> level.hasChunkAt(position(surface)))
                // A retained local conflict is intentionally terminal for that one object.  Do
                // not let its lexical position starve another independently usable depot/store.
                .filter(surface -> {
                    PhysicalReplicaRecord replica = state.replicaCustody().replicas().get(surface.containerId());
                    return replica == null || replica.state() != PhysicalReplicaState.CONFLICT;
                })
                .findFirst().ifPresent(surface -> reconcile(level, runtime, state, surface));
    }

    private static void reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                  FrontierWorldState state, ContainerSurface surface) {
        SubjectId containerId = surface.containerId();
        PhysicalReplicaRecord replica = state.replicaCustody().replicas().get(containerId);
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.activeChest(level, position(surface), containerId);
        if (replica == null) {
            // Initial projection remains owned by the generic surface materializer.  This adapter
            // starts only after a naturally loaded exact chest is present to observe.
            if (chest != null) declare(runtime, state, containerId);
            return;
        }
        if (replica.state() == PhysicalReplicaState.CONFLICT) return;
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
            // whatever happens to be in the chest now.  Retain an actual mismatch as a normal
            // kernel conflict; only the old retained fingerprint/provenance can authorize the
            // one catch-up write for a newer canonical snapshot.
            Observed beforeCatchup = observed(state, containerId, chest);
            boolean retainedEvidenceMatches = beforeCatchup.fingerprint().equals(replica.fingerprint())
                    && beforeCatchup.provenance().equals(replica.provenance());
            boolean newerCanonicalSlots = !ReferenceContainerCustody.canonicalFingerprint(state, containerId).equals(replica.fingerprint());
            if (reemit(runtime, state, replica) && retainedEvidenceMatches && newerCanonicalSlots) {
                FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, state, containerId);
            }
            return;
        }
        String canonical = ReferenceContainerCustody.canonicalFingerprint(state, containerId);
        Observed observed = observed(state, containerId, chest);
        if (!canonical.equals(replica.fingerprint()) || !observed.fingerprint().equals(replica.fingerprint())
                || !observed.provenance().equals(replica.provenance())) {
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

    private static Observed observed(FrontierWorldState state, SubjectId containerId, ChestBlockEntity chest) {
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
        String provenance = chest.getPersistentData().getString(REPLICA_PROVENANCE_KEY);
        if (provenance.isBlank()) provenance = containerId.value().equals(chest.getPersistentData().getString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY))
                ? ReferenceContainerCustody.provenance(containerId) : "foreign:" + containerId.value();
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
    private record Observed(String fingerprint, String provenance) { }
}
