package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;
import java.util.Set;

/** Field-owned hand presentation. Retained carry proof permits binding, never re-issuance.
 * The common native operation owns the one mutation attempt and durable outcome. */
final class FrontierV3HarvestHandOperation {
    record Binding(FrontierV3OperationReceipt.Declaration declaration, List<FrontierV3MinecraftOperation.Participant> ports) { }
    private FrontierV3HarvestHandOperation() { }

    static FrontierV3MinecraftOperation.Result apply(ServerLevel level, FrontierWorldState state,
            FrontierV3ResourceSiteHandProjectionWitness witness, Mob body) {
        var binding = binding(state, witness, body);
        return FrontierV3MinecraftOperation.apply(level, binding.declaration(), binding.ports(),
                () -> before(state, witness, body), FrontierV3NativeEffect.observed(() -> switch (witness.mode()) {
                    case BIND_RETAINED -> retained(state, witness, body);
                    case MATERIALIZE_COLD -> FrontierV3VillagerHandMutation.setOffhand(body, stack(state, witness));
                }), () -> after(state, witness, body));
    }

    /** Finish the same positively saved operation before scene/body departure barriers.
     * No native mutation port or new resource issue exists on this path. */
    static boolean recoverDeparture(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, SceneLease lease) {
        var cause = FrontierSceneBehaviors.resourceSiteHarvest(lease);
        var fields = FrontierV3ResourceSiteLedger.get(level);
        var witness = fields.fieldHandProjection(cause.siteId());
        if (witness == null || !witness.leaseId().equals(lease.id())) return false;
        var body = FrontierV3DepartedBodyEvidence.retainedBody(state, witness.workerId()).orElse(null);
        if (body == null || !FrontierV3DepartedBodyEvidence.absent(level, runtime, state, body)) return false;
        var carriers = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var departure = carriers.bodyDeparture(witness.workerId()).orElse(null);
        if (departure == null || !departure.current(state) || !carriers.savedBodyDeparture(departure)) return false;
        if (settledProjection(state, lease, witness, departure)) {
            retireProjection(level, fields, witness);
            return true; // Crash after the exact canonical acknowledgement, before owner cleanup.
        }
        var operations = FrontierV3OperationLedger.get(level);
        var fingerprint = FrontierV3OperationFingerprint.of(witness.write());
        var operation = operations.find(FrontierV3OperationReceipt.Protocol.FIELD_HAND_PROJECTION,
                witness.jobId(), fingerprint).orElse(null);
        var projected = savedProjection(state, lease, witness, departure, operation).orElse(null);
        if (projected == null) return false;
        FrontierV3OperationConfirmation.submit(level, runtime, operation.ticket(), projected,
                () -> retireProjection(level, fields, witness));
        return true;
    }

    /** All declared cargo/scene identities and saved native participants are checked together. */
    static java.util.Optional<ResourceSiteHarvestHandProjected> savedProjection(FrontierWorldState state, SceneLease lease,
            FrontierV3ResourceSiteHandProjectionWitness witness, FrontierV3ActorBodyDeparture departure,
            FrontierV3OperationReceipt operation) {
        var projected = departedProjection(state, lease, witness, departure);
        if (projected.isEmpty()) return projected;
        var fingerprint = FrontierV3OperationFingerprint.of(witness.write());
        if (operation == null || operation.cancelled()
                || operation.declaration().protocol() != FrontierV3OperationReceipt.Protocol.FIELD_HAND_PROJECTION
                || !operation.declaration().owner().equals(witness.jobId())
                || !operation.declaration().operation().equals(fingerprint)
                || !operation.declaration().fingerprint().equals(fingerprint)
                || operation.declaration().participants().size() != 1
                || !operation.saved().equals(operation.declaration().participants())) return java.util.Optional.empty();
        var participant = operation.declaration().participants().iterator().next();
        return participant.medium() == FrontierV3OperationReceipt.Medium.ENTITIES
                && participant.identity().equals(witness.entityId().toString()) ? projected : java.util.Optional.empty();
    }

    static boolean settledProjection(FrontierWorldState state, SceneLease lease,
            FrontierV3ResourceSiteHandProjectionWitness witness, FrontierV3ActorBodyDeparture departure) {
        var projected = departedProjection(state, lease, witness, departure).orElse(null);
        if (projected == null) return false;
        var resources = state.inventory().fungibleResources();
        var account = resources.accounts().get(witness.actorAccountId());
        var bindings = resources.bindings().values().stream()
                .filter(value -> value.accountId().equals(witness.actorAccountId())).toList();
        return account != null && bindings.size() == 1 && bindings.getFirst().authorityEpoch() == witness.actorEpoch()
                && bindings.getFirst().address().equals(projected.hand().address())
                && bindings.getFirst().itemKind().equals(projected.hand().itemKind())
                && bindings.getFirst().quantity() == projected.hand().quantity()
                && bindings.getFirst().lotQuantities().equals(account.lotQuantities())
                && bindings.getFirst().claimQuantities().equals(account.claimQuantities());
    }

    private static java.util.Optional<ResourceSiteHarvestHandProjected> departedProjection(FrontierWorldState state,
            SceneLease lease, FrontierV3ResourceSiteHandProjectionWitness witness, FrontierV3ActorBodyDeparture departure) {
        var cause = FrontierSceneBehaviors.resourceSiteHarvest(lease);
        var lifecycle = state.resourceSites().sites().get(cause.siteId());
        var job = lifecycle == null ? null : lifecycle.harvestJob(cause.jobId()).orElse(null);
        if (job == null || job.hasPendingPhysicalWork() || state.resourceSites().harvestMutationPending(job)
                || !witness.siteId().equals(cause.siteId()) || !witness.jobId().equals(job.id())
                || !witness.workerId().equals(job.workerId()) || !witness.actorAccountId().equals(job.actorAccountId())
                || !witness.leaseId().equals(lease.id()) || witness.actorEpoch() != lease.revision()
                || witness.fieldEpoch() != state.resourceSites().cycle(job.siteId()).epoch()
                || witness.accountedPrefix() != job.progress().completedCropSlots()
                || lease.members().size() != 1 || !lease.members().getFirst().actorId().equals(witness.workerId())
                || !lease.members().getFirst().entityId().equals(witness.entityId()) || !departure.current(state)
                || !departure.identity().actorId().equals(witness.workerId())
                || !departure.identity().entityId().equals(witness.entityId())) return java.util.Optional.empty();
        var part = ResourceSiteHarvestCargo.part(state, job).orElse(null);
        var hand = departure.offhand().orElse(null);
        if (part == null || !part.id().equals(witness.lotId()) || part.quantity() != witness.quantity()
                || hand == null || !hand.itemKind().equals(part.itemKind()) || hand.quantity() != witness.quantity()
                || hand.exactItemId().isPresent() || !hand.components().isEmpty()) return java.util.Optional.empty();
        return java.util.Optional.of(new ResourceSiteHarvestHandProjected(witness.siteId(), witness.jobId(),
                witness.actorAccountId(), witness.leaseId(), witness.actorEpoch(), new FungiblePhysicalObservation.Stack(
                    new PhysicalStackAddress.ActorHand(witness.workerId(), witness.entityId()), part.itemKind(), witness.quantity())));
    }

    static void retireProjection(ServerLevel level, FrontierV3ResourceSiteLedger ledger,
            FrontierV3ResourceSiteHandProjectionWitness witness) {
        if (!witness.equals(ledger.fieldHandProjection(witness.siteId()))) return;
        ledger.retireFieldHandProjection(witness); ledger.persist(level);
    }

    static Binding binding(FrontierWorldState state, FrontierV3ResourceSiteHandProjectionWitness witness, Mob body) {
        if (!body.getUUID().equals(witness.entityId()))
            throw new IllegalArgumentException("field hand operation has a foreign body");
        var participant = FrontierV3OperationEntityParticipant.current(body,
                ActorBodyAuthority.current(state, witness.workerId()).physicalEpoch());
        var declaration = new FrontierV3OperationReceipt.Declaration(
                FrontierV3OperationReceipt.Protocol.FIELD_HAND_PROJECTION, witness.jobId(),
                FrontierV3OperationFingerprint.of(witness.write()), FrontierV3OperationFingerprint.of(witness.write()),
                Set.of(participant.identity()));
        return new Binding(declaration, List.of(participant));
    }

    static boolean after(FrontierWorldState state, FrontierV3ResourceSiteHandProjectionWitness witness, Mob body) {
        return ItemStack.matches(body.getOffhandItem(), stack(state, witness))
                && (witness.mode() != FrontierV3ResourceSiteHandProjectionWitness.Mode.BIND_RETAINED || retained(state, witness, body));
    }

    private static boolean before(FrontierWorldState state, FrontierV3ResourceSiteHandProjectionWitness witness, Mob body) {
        return switch (witness.mode()) {
            case MATERIALIZE_COLD -> body.getOffhandItem().isEmpty();
            case BIND_RETAINED -> retained(state, witness, body);
        };
    }

    private static boolean retained(FrontierWorldState state, FrontierV3ResourceSiteHandProjectionWitness witness, Mob body) {
        return FrontierV3ActorCarryProjection.witnessed(state, witness.workerId(), witness.actorAccountId(), body);
    }

    private static ItemStack stack(FrontierWorldState state, FrontierV3ResourceSiteHandProjectionWitness witness) {
        return FrontierV3FungibleItemStacks.stack(state.resourceSite(witness.siteId()).kind().resourceKind(), witness.quantity());
    }
}
