package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhase;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * One construction/placement/admission owner for every exact actor body.
 * Resource and presentation owners initialize only a not-yet-admitted body;
 * they cannot select the admission protocol or insert it into Minecraft.
 * Existing-body handoff/removal is still being migrated, not implemented here
 * by delegating to the old scene/ambient owner transfer.
 */
final class FrontierV3ActorBodyController {
    enum Result { APPLIED, DEFERRED, CONFLICT }
    enum Admission { FIRST, RECONSTRUCTION, CONFLICT }

    @FunctionalInterface
    interface NewBodyProjection {
        boolean initialize(Mob body);
    }

    record BirthRequest(FrontierV3ActorCarrierComposition.InventoryEntry requester,
                        FrontierV3ActorOwnerBinding binding, List<SurfaceAnchor> surfaces,
                        FrontierV3NavigationScope scope,
                        BiFunction<ServerLevel, BlockPos, BlockPos> standing,
                        NewBodyProjection projection) {
        BirthRequest {
            FrontierV3ActorCarrierComposition.requireRole(requester, FrontierV3ActorCarrierComposition.Role.ADOPTER);
            Objects.requireNonNull(binding); Objects.requireNonNull(scope);
            Objects.requireNonNull(standing); Objects.requireNonNull(projection);
            surfaces = List.copyOf(surfaces);
            if (surfaces.isEmpty()) throw new IllegalArgumentException("body birth requires an explicit placement zone");
            if (binding.declaration().representation() != FrontierV3ActorCarrierComposition.Representation.LIVE_BODY)
                throw new IllegalArgumentException("body birth requires a declared living representation");
        }
    }

    private FrontierV3ActorBodyController() { }

    static Result materialize(ServerLevel level, FrontierWorldState state, BirthRequest request) {
        var declaration = request.binding().declaration();
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        Admission admission = admission(state, request.binding(), ledger, level.getEntity(declaration.entityId()) != null);
        if (admission == Admission.CONFLICT) return Result.CONFLICT;
        // Only the controller has the producer capability. Families supply exact
        // demand and owned projections, never their own creation permission.
        Mob body = FrontierV3ActorCarrierFactory.create(
                FrontierV3ActorCarrierComposition.InventoryEntry.ACTOR_BODY, level, declaration,
                state.actorLocations().get(declaration.actorId()).condition());
        if (FrontierV3BodyPlacement.select(level, body, request.surfaces(), request.scope(), request.standing()).isEmpty())
            return Result.DEFERRED;
        body.setPersistenceRequired();
        body.setNoAi(true);
        request.binding().stamp(body);
        if (!request.projection().initialize(body)) return Result.CONFLICT;
        // A resource projection is not allowed to change the complete physical
        // declaration, and must not have admitted or removed its private body.
        if (body.isRemoved() || level.getEntity(declaration.entityId()) != null
                || !FrontierV3ActorOwnerBinding.from(body).filter(request.binding()::equals).isPresent())
            return Result.CONFLICT;
        if (!FrontierV3BodyPlacement.available(level, body, body.getBoundingBox())) return Result.DEFERRED;
        boolean added = admit(ledger, request.binding(), admission,
                () -> ledger.persist(level, state.bootstrap().worldId()), () -> level.addFreshEntity(body));
        return added ? Result.APPLIED : Result.CONFLICT;
    }

    /** Exact physical incarnation plus durable history, never UUID absence alone. */
    static Admission admission(FrontierWorldState state, FrontierV3ActorOwnerBinding binding,
                               FrontierV3AmbientCarrierLedger ledger, boolean bodyPresent) {
        var declaration = binding.declaration();
        var actor = state.actorLocations().get(declaration.actorId());
        if (actor == null || actor.kind() != declaration.kind()
                || declaration.representation() != FrontierV3ActorCarrierComposition.Representation.LIVE_BODY
                || !SceneLease.deterministicEntityId(state.bootstrap().worldId(), declaration.actorId()).equals(declaration.entityId()))
            throw new IllegalArgumentException("birth declaration does not identify its canonical actor body");
        var bodyId = ActorBodyAuthority.current(state, declaration.actorId());
        if (bodyId.physicalEpoch() != declaration.epoch())
            throw new IllegalArgumentException("birth declaration has a stale physical incarnation");
        if (actor.condition().status() != io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.ALIVE
                || ActorBodyAuthority.require(state, bodyId).phase() != FencedRecoveryPhase.PREPARED
                || bodyPresent || ledger.pendingAdoption(declaration.actorId()).isPresent()
                || ledger.pendingHandoff(declaration.actorId()).isPresent()) return Admission.CONFLICT;
        var reconciliation = ledger.reconciliation(declaration, bodyPresent);
        if (reconciliation == FrontierV3AmbientCarrierLedger.Reconciliation.READY) return Admission.RECONSTRUCTION;
        return reconciliation == FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER
                && hasUnusedFirstAdmission(ledger, declaration) ? Admission.FIRST : Admission.CONFLICT;
    }

    static boolean hasUnusedFirstAdmission(FrontierV3AmbientCarrierLedger ledger,
                                           FrontierV3ActorCarrierComposition.Declaration declaration) {
        return ledger.firstAdmission(declaration.actorId()).filter(value ->
                value.identity().matches(declaration) && (value.phase() == FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED
                    || value.phase() == FrontierV3ActorFirstAdmission.Phase.PROVEN_ABSENT
                        && value.attempt().orElseThrow().declaration().equals(declaration))).isPresent();
    }

    private static boolean admit(FrontierV3AmbientCarrierLedger ledger, FrontierV3ActorOwnerBinding binding,
                                  Admission admission, Runnable persist, java.util.function.BooleanSupplier insert) {
        return switch (admission) {
            case FIRST -> FrontierV3ActorFirstAdmissionBoundary.admit(ledger, binding, persist, insert);
            case RECONSTRUCTION -> FrontierV3ActorAdoptionAdmission.admit(ledger, binding, persist, insert);
            case CONFLICT -> false;
        };
    }
}
