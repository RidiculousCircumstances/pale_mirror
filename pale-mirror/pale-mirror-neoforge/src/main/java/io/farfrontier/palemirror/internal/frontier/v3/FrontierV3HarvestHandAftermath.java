package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.*;

/** Settles already accounted carry before loot, then binds its observed hand after fatality.
 * No harvest action, dead-body recreation, slot restoration or new yield is authorized. */
final class FrontierV3HarvestHandAftermath {
    private FrontierV3HarvestHandAftermath() { }
    static Optional<FrontierV3OperationAftermath> prepare(ServerLevel level, FrontierWorldState state,
            ResourceSiteHarvestJob job, SceneLease lease, Mob body, ActorActuationId identity) {
        var ledger = FrontierV3ResourceSiteLedger.get(level); var witness = ledger.fieldHandProjection(job.siteId());
        if (witness != null && !witness.jobId().equals(job.id())) witness = null;
        var bindings = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(job.actorAccountId())).toList();
        if (witness == null) {
            if (!bindings.isEmpty() || job.hasPendingPhysicalWork()) return Optional.empty();
            var part = ResourceSiteHarvestCargo.part(state, job).orElse(null);
            if (part == null) return Optional.empty();
            // Newly admitted or released bodies already carry their canonical stock.
            // Without that provenance no new projection may be invented on death.
            if (!FrontierV3ActorCarryProjection.witnessed(state, job.workerId(), job.actorAccountId(), body))
                throw new IllegalStateException("unbound dying farmer has no retained carry projection");
            return bindRetained(level, state, job, body, identity, part.quantity());
        }
        var retainedPart = ResourceSiteHarvestCargo.part(state, job).orElseThrow();
        if (!witness.workerId().equals(identity.body().actorId()) || !witness.entityId().equals(body.getUUID())
                || !witness.actorAccountId().equals(job.actorAccountId()) || lease == null || !witness.leaseId().equals(lease.id())
                || job.hasPendingPhysicalWork() || witness.actorEpoch() > lease.revision()
                || witness.accountedPrefix() != job.progress().completedCropSlots()
                || !witness.lotId().equals(retainedPart.id()) || witness.quantity() != retainedPart.quantity())
            throw new IllegalStateException("field death projection differs from its exact retained cargo boundary");
        if (!bindings.isEmpty()) {
            if (bindings.size() != 1 || bindings.getFirst().authorityEpoch() != witness.actorEpoch()
                    || !bindings.getFirst().address().equals(new PhysicalStackAddress.ActorHand(job.workerId(), body.getUUID()))
                    || !FrontierV3HarvestHandOperation.after(state, witness, body))
                throw new IllegalStateException("accepted field projection differs from its actual death hand");
            ledger.retireFieldHandProjection(witness); ledger.persist(level); return Optional.empty();
        }
        // Complete only this retained projection while the body is still present,
        // before the parent's immutable pre-loot snapshot. The common operation
        // can consume a live unused permit or observe receipts, never replay a
        // recovered ambiguous attempt or replace a missing previously applied hand.
        var result = FrontierV3HarvestHandOperation.apply(level, state, witness, body);
        if (!result.complete()) throw new IllegalStateException("field projection is unresolved before fatality: " + result.status());
        return Optional.of(capture(level, state, witness, body, identity).orElseThrow(
                () -> new IllegalStateException("applied field projection lost its positive pre-loot receipt")));
    }
    private static Optional<FrontierV3OperationAftermath> bindRetained(ServerLevel level, FrontierWorldState state,
            ResourceSiteHarvestJob job, Mob body, ActorActuationId identity, int quantity) {
        // An already admitted carry may outlive its work scene. Observe that
        // exact body inventory; do not invent a scene or occupy a sibling's
        // pending field-writer slot for a non-mutating resource binding.
        var consequence = new HarvestRetiredHandObserved(identity, job.siteId(), job.actorAccountId(),
                identity.body().physicalEpoch(), new FungiblePhysicalObservation.Stack(
                    new PhysicalStackAddress.ActorHand(job.workerId(), body.getUUID()), "minecraft:wheat", quantity),
                job.progress().completedCropSlots());
        var ports = List.of(FrontierV3OperationEntityParticipant.current(body, identity.body().physicalEpoch()));
        var declaration = FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.FIELD_HAND_PROJECTION,
                job.id(), consequence, ports);
        java.util.function.BooleanSupplier exactCarry = () -> FrontierV3ActorCarryProjection.witnessed(
                state, job.workerId(), job.actorAccountId(), body);
        var result = FrontierV3MinecraftOperation.apply(level, declaration, ports, exactCarry, FrontierV3NativeEffect.observed(exactCarry), exactCarry);
        if (!result.complete()) throw new IllegalStateException("retained field carry lacks its exact native receipt: " + result.status());
        return Optional.of(FrontierV3OperationAftermath.capture(level, declaration, ports, consequence, true, exactCarry).orElseThrow());
    }
    static Optional<FrontierV3OperationAftermath> capture(ServerLevel level, FrontierWorldState state,
            FrontierV3ResourceSiteHandProjectionWitness witness, Mob body, ActorActuationId identity) {
        if (!identity.execution().activityOwnerId().equals(witness.jobId())
                || !identity.body().equals(ActorBodyAuthority.current(state, witness.workerId())))
            throw new IllegalArgumentException("retired projection capture has a foreign execution or incarnation");
        var binding = FrontierV3HarvestHandOperation.binding(state, witness, body);
        var projected = new HarvestRetiredHandObserved(identity, witness.siteId(), witness.actorAccountId(), witness.actorEpoch(),
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(witness.workerId(), witness.entityId()),
                    "minecraft:wheat", witness.quantity()), witness.accountedPrefix());
        return FrontierV3OperationAftermath.capture(level, binding.declaration(), binding.ports(),
                projected, true,
                () -> FrontierV3HarvestHandOperation.after(state, witness, body));
    }
    static void retireSettled(ServerLevel level, ActorBodyId body, List<FrontierV3OperationAftermath> effects) {
        var ledger = FrontierV3ResourceSiteLedger.get(level); var operations = FrontierV3OperationLedger.get(level);
        for (var effect : effects) {
            if (!(effect.consequence() instanceof HarvestRetiredHandObserved outcome) || !outcome.identity().body().equals(body)) continue;
            if (effect.outcome() != FrontierV3OperationAftermath.Outcome.APPLIED
                    || effect.child().declaration().protocol() != FrontierV3OperationReceipt.Protocol.FIELD_HAND_PROJECTION
                    || !effect.child().declaration().owner().equals(outcome.identity().execution().activityOwnerId()))
                throw new IllegalStateException("retired hand has a foreign native operation or outcome");
            var current = operations.find(effect.child().ticket()).orElse(null);
            if (current != null && !current.ownerClosed() || current == null && !operations.prunable(
                    effect.child().nativeReceipt(effect.child().declaration().participants().iterator().next()))) continue;
            var witness = ledger.fieldHandProjection(outcome.siteId());
            if (witness == null || !witness.jobId().equals(outcome.identity().execution().activityOwnerId())) continue;
            if (!effect.child().declaration().fingerprint().equals(FrontierV3OperationFingerprint.of(witness.write())))
                throw new IllegalStateException("retired hand substituted its original projection witness");
            ledger.retireFieldHandProjection(witness); ledger.persist(level);
        }
    }
}
