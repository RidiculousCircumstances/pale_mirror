package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
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
    static final String RESIDENCE_KEY = "pm_v3_body_residence_generation";
    /** Source firewall and the Forge join event may observe the same object twice. */
    private static final java.util.Map<Mob, Long> JOINED_RESIDENCES = new java.util.WeakHashMap<>();
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

    /** Physical fatality is recognized by common provenance, never by presentation membership. */
    static boolean observeDeath(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                net.minecraft.world.entity.Entity entity, net.minecraft.world.entity.Entity source) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || !(entity instanceof Mob body) || body.getHealth() > 0.0F
                || body.level() != level || !recognizes(state, body)) return false;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(body).orElseThrow();
        if (level.getEntity(declaration.entityId()) != body) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!ledger.permitsRecordedOwner(FrontierV3ActorOwnerBinding.body(declaration))
                || !hasKnownResidence(ledger, body, declaration.actorId())
                || !ledger.currentBodyResidence(declaration.actorId(), body.getPersistentData().getLong(RESIDENCE_KEY))) return false;
        // A positively identified indexed fatality also resolves contradictory living unload
        // receipts. Those receipts still forbid movement; they must not hide the actual death.
        // Resource owners prepare their own settlement. That may commit a newer state;
        // the body event must capture the resulting baseline rather than the entry snapshot.
        var resources = FrontierV3ActorDeathResourceComposition.prepare(level, runtime, body,
                ActorBodyAuthority.current(state, declaration.actorId()));
        state = runtime.decodedState().orElse(null);
        if (state == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE
                || !recognizes(state, body)) return false;
        var actor = state.actorLocations().get(declaration.actorId());
        var id = ActorBodyAuthority.current(state, declaration.actorId());
        var observed = FrontierV3BodyObservation.capture(body).supportedBody();
        String cause = source == null ? "environment" : "entity:" + source.getUUID();
        var payload = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied(id, actor.body(),
                actor.condition().health(), observed, FrontierV3ActorBodyDeparture.execution(state, declaration.actorId()), cause);
        var result = FrontierV3CommandSubmission.submit(runtime, "actor-body-death", declaration.actorId().value(), payload);
        if (!(result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)) return false;
        ledger.retireDeadActor(runtime.decodedState().orElseThrow(), declaration.actorId());
        FrontierV3GoalNavigation.retireIncarnation(body, id);
        resources.settle();
        return true;
    }

    /** Called by bounded actor probes, including actors with no remaining projection scope. */
    static void progressDeparture(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                  FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var receipt = ledger.bodyDeparture(actor).orElse(null);
        if (receipt == null || !receipt.current(state) || !ledger.savedBodyDeparture(receipt)
                || !ledger.currentBodyResidence(actor, receipt.residenceGeneration())
                || level.getEntity(receipt.identity().entityId()) != null
                || FrontierV3AmbientPendingAdmissions.get(runtime, receipt.identity().entityId()) != null) return;
        var body = ActorBodyAuthority.current(state, actor);
        var phase = ActorBodyAuthority.require(state, body).phase();
        if (phase != FencedRecoveryPhase.RUNNING && phase != FencedRecoveryPhase.AMBIGUOUS) return;
        // A process must first settle its own hand/effect/observation obligations.
        // These are explicit projection-scope constraints, never a body owner lookup.
        var ambient = state.ambientLeases().get(actor);
        if (ambient != null && ambient.status() != io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.CLOSED
                || state.sceneLeases().values().stream().anyMatch(scene -> scene.retainsMemberCustody(actor))) return;
        var declaration = receipt.identity();
        if (!ledger.fence(declaration, declaration.epoch(), 0L)) return;
        ledger.persist(level, state.bootstrap().worldId());
        var location = state.actorLocations().get(actor);
        var payload = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyUnloaded(body,
                location.body(), location.condition().health(), receipt.observed().body(), receipt.observed().health(),
                FrontierV3ActorBodyDeparture.execution(state, actor));
        FrontierV3CommandSubmission.submit(runtime, "actor-body-unloaded", actor.value(), payload);
    }

    /** The source callback records physical facts, never discovers a job/scene owner. */
    static boolean observeLeave(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof Mob body) || body.getHealth() <= 0.0F
                || entity.getRemovalReason() != net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK) return false;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        var receipt = capture(level, state, body, true).orElse(null);
        return receipt != null && FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()).recordBodyDeparture(receipt);
    }

    static void observeJoin(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                            net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof Mob body) || !body.isAlive()) return;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(body).orElse(null);
        if (declaration == null || body.level() != level || !recognizesDeclaration(state, declaration)) return;
        var indexed = level.getEntity(declaration.entityId());
        if (indexed != null && indexed != body) return;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!ledger.permitsRecordedOwner(FrontierV3ActorOwnerBinding.body(declaration))
                || !hasKnownResidence(ledger, body, declaration.actorId())) return;
        long residence = body.getPersistentData().getLong(RESIDENCE_KEY);
        if (java.util.Objects.equals(JOINED_RESIDENCES.get(body), residence)) return;
        var receipt = ledger.bodyDeparture(declaration.actorId()).orElse(null);
        if (receipt != null) {
            var actual = capture(level, state, body, false).orElse(null);
            if (actual == null) return;
            if (!receipt.current(state) || receipt.residenceGeneration() != actual.residenceGeneration()
                    || !receipt.identity().equals(actual.identity()) || !receipt.observed().equals(actual.observed())
                    || !receipt.offhand().equals(actual.offhand()) || !receipt.mainhand().equals(actual.mainhand())
                    || !ledger.resumeBodyDeparture(receipt)) return;
        }
        // Private first/reconstruction insertion already reserved this residency.
        if (ledger.pendingAdoption(declaration.actorId()).isPresent()
                || ledger.firstAdmission(declaration.actorId()).filter(value ->
                    value.phase() == FrontierV3ActorFirstAdmission.Phase.PENDING).isPresent()) {
            if (!ledger.currentBodyResidence(declaration.actorId(), residence)) return;
            JOINED_RESIDENCES.put(body, residence); return;
        }
        if (!recognizesRecordedBody(level, state, body)) return;
        long next = ledger.beginBodyResidence(FrontierV3ActorOwnerBinding.from(body).orElseThrow().declaration());
        body.getPersistentData().putLong(RESIDENCE_KEY, next);
        ledger.persist(level, state.bootstrap().worldId());
        JOINED_RESIDENCES.put(body, next);
    }

    private static boolean hasKnownResidence(FrontierV3AmbientCarrierLedger ledger, net.minecraft.world.entity.Entity body,
                                             io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        return body.getPersistentData().contains(RESIDENCE_KEY, net.minecraft.nbt.Tag.TAG_LONG)
                && ledger.knownBodyResidence(actor, body.getPersistentData().getLong(RESIDENCE_KEY));
    }

    private static java.util.Optional<FrontierV3ActorBodyDeparture> capture(ServerLevel level, FrontierWorldState state,
                                                                          Mob body, boolean departing) {
        var declaration = (departing ? FrontierV3ActorCarrierComposition.declaredByUnloading(body)
                : FrontierV3ActorCarrierComposition.declaredBy(body)).orElse(null);
        if (body.level() != level || declaration == null || !recognizesDeclaration(state, declaration))
            return java.util.Optional.empty();
        var indexed = level.getEntity(declaration.entityId());
        if (indexed != null && indexed != body) return java.util.Optional.empty();
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!ledger.permitsRecordedOwner(FrontierV3ActorOwnerBinding.body(declaration))) return java.util.Optional.empty();
        var metadata = body.getPersistentData();
        if (!metadata.contains(RESIDENCE_KEY, net.minecraft.nbt.Tag.TAG_LONG)) return java.util.Optional.empty();
        long residence = metadata.getLong(RESIDENCE_KEY);
        if (!(departing ? ledger.currentBodyResidence(declaration.actorId(), residence)
                : ledger.knownBodyResidence(declaration.actorId(), residence))) return java.util.Optional.empty();
        var position = departing ? FrontierV3SupportedBodyCapture.observeDeparting(level, body)
                : FrontierV3SupportedBodyCapture.observe(level, body);
        if (position.isEmpty()) return java.util.Optional.empty();
        var off = body.getOffhandItem(); var main = body.getMainHandItem();
        if (!plainHand(off) || !plainHand(main)) return java.util.Optional.empty();
        var actor = state.actorLocations().get(declaration.actorId());
        return java.util.Optional.of(new FrontierV3ActorBodyDeparture(declaration.inactiveCarrier(), residence,
                new io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition(declaration.actorId(), position.orElseThrow(),
                    new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(Math.round(body.getHealth()
                        * (double) io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE))),
                actor.body(), actor.condition().health(), FrontierV3ActorBodyDeparture.execution(state, declaration.actorId()),
                hand(off), hand(main)));
    }
    private static boolean plainHand(net.minecraft.world.item.ItemStack stack) {
        return stack.isEmpty() || net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack,
                new net.minecraft.world.item.ItemStack(stack.getItem(), stack.getCount()));
    }
    private static java.util.Optional<FrontierV3ActorBodyDeparture.HandStack> hand(net.minecraft.world.item.ItemStack stack) {
        return stack.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(new FrontierV3ActorBodyDeparture.HandStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount()));
    }

    /** Body provenance is independent of the currently selected activity and its scene. */
    static boolean recognizes(FrontierWorldState state, net.minecraft.world.entity.Entity entity) {
        if (entity == null || entity.isRemoved()) return false;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(entity).orElse(null);
        return declaration != null && recognizesDeclaration(state, declaration);
    }

    static boolean recognizesDeclaration(FrontierWorldState state, FrontierV3ActorCarrierComposition.Declaration declaration) {
        if (declaration.representation() != FrontierV3ActorCarrierComposition.Representation.LIVE_BODY)
            return false;
        var actor = state.actorLocations().get(declaration.actorId());
        if (actor == null || actor.kind() != declaration.kind()
                || actor.condition().status() != io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.ALIVE
                || !SceneLease.deterministicEntityId(state.bootstrap().worldId(), declaration.actorId()).equals(declaration.entityId()))
            return false;
        try { return ActorBodyAuthority.current(state, declaration.actorId()).physicalEpoch() == declaration.epoch(); }
        catch (IllegalArgumentException absentOrStale) { return false; }
    }

    /** A joining private admission can precede the UUID index, never replace another object. */
    static boolean retainsRecordedBody(ServerLevel level, FrontierWorldState state, net.minecraft.world.entity.Entity entity) {
        if (entity == null || entity.level() != level || !recognizes(state, entity)) return false;
        var binding = FrontierV3ActorOwnerBinding.from(entity).orElseThrow();
        var declaration = binding.declaration();
        var indexed = level.getEntity(declaration.entityId());
        if (indexed != null && indexed != entity) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!hasKnownResidence(ledger, entity, declaration.actorId())) return false;
        if (!ledger.currentBodyResidence(declaration.actorId(), entity.getPersistentData().getLong(RESIDENCE_KEY))) return false;
        if (ledger.hasCarrier(declaration.actorId()) || ledger.hasDepartureConflict(declaration.actorId())) return false;
        return ledger.pendingAdoption(declaration.actorId()).filter(value -> value.matches(binding)).isPresent()
                || ledger.firstAdmission(declaration.actorId())
                    .filter(value -> value.phase() == FrontierV3ActorFirstAdmission.Phase.PENDING
                        && value.attempt().orElseThrow().equals(binding)).isPresent();
    }

    /** Lifetime recognition requires physical history, never an active scene or ambient scope. */
    static boolean recognizesRecordedBody(ServerLevel level, FrontierWorldState state, net.minecraft.world.entity.Entity entity) {
        if (entity == null || entity.level() != level || !recognizes(state, entity)) return false;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(entity).orElseThrow();
        var indexed = level.getEntity(declaration.entityId());
        if (indexed != null && indexed != entity) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!hasKnownResidence(ledger, entity, declaration.actorId())) return false;
        if (ledger.hasCarrier(declaration.actorId()) || ledger.hasBodyDeparture(declaration.actorId())
                || ledger.hasDepartureConflict(declaration.actorId())
                || !ledger.permitsRecordedOwner(FrontierV3ActorOwnerBinding.body(declaration))) return false;
        var phase = ActorBodyAuthority.require(state, ActorBodyAuthority.current(state, declaration.actorId())).phase();
        return phase == FencedRecoveryPhase.RUNNING || phase == FencedRecoveryPhase.AMBIGUOUS
                || phase == FencedRecoveryPhase.PREPARED && retainsRecordedBody(level, state, entity);
    }

    /** Called only after the join firewall has accepted this exact physical object. */
    static void confirmPresent(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                               net.minecraft.world.entity.Entity entity) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || !(entity instanceof Mob body) || !body.isAlive()
                || !recognizesRecordedBody(level, state, entity)) return;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(body).orElseThrow();
        var id = ActorBodyAuthority.current(state, declaration.actorId());
        if (ActorBodyAuthority.require(state, id).phase() == FencedRecoveryPhase.RUNNING) return;
        var observed = FrontierV3SupportedBodyCapture.observe(level, body);
        if (observed.isEmpty()) return;
        var location = state.actorLocations().get(declaration.actorId());
        var health = new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(Math.round(body.getHealth()
                * (double) io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE));
        FrontierV3CommandSubmission.submit(runtime, "actor-body-present", declaration.actorId().value(),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyPresent(id,
                        location.body(), location.condition().health(), observed.orElseThrow(), health));
    }

    /** Common inspection of one indexed resident object; family receipts cannot confer body permission. */
    static boolean inspectCurrent(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE
                || !body.isAlive() || !recognizesRecordedBody(level, state, body)
                || level.getEntity(body.getUUID()) != body) return false;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(body).orElseThrow();
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!ledger.currentBodyResidence(declaration.actorId(), body.getPersistentData().getLong(RESIDENCE_KEY))) return false;
        var id = ActorBodyAuthority.current(state, declaration.actorId());
        var phase = ActorBodyAuthority.require(state, id).phase();
        if (phase != FencedRecoveryPhase.RUNNING && phase != FencedRecoveryPhase.AMBIGUOUS) return false;
        var observed = FrontierV3SupportedBodyCapture.observe(level, body);
        if (observed.isEmpty()) return false;
        var actor = state.actorLocations().get(declaration.actorId());
        var health = new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(Math.round(body.getHealth()
                * (double) io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE));
        if (phase == FencedRecoveryPhase.RUNNING && actor.body().equals(observed.orElseThrow())
                && actor.condition().health().equals(health)) return true;
        var receipt = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected(id,
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.INDEXED_LIVING,
                actor.body(), actor.condition().health(), observed.orElseThrow(), health,
                FrontierV3ActorBodyDeparture.execution(state, declaration.actorId()));
        return FrontierV3CommandSubmission.submitResult(runtime, "actor-body-inspected", declaration.actorId().value(), receipt)
                instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted;
    }

    /** Record a saved physical checkpoint before its process settles hands/effects and closes.
     * The exact unload/save/sync/read fence belongs here, never to a scene's route policy.
     * This retains physical custody and grants no loaded actuator or semantic arrival.
     */
    static boolean checkpointSavedDeparture(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                             io.farfrontier.palemirror.frontier.v3.api.SubjectId actorId) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var physical = ledger.bodyDeparture(actorId).orElse(null);
        if (physical == null || !physical.current(state) || !ledger.savedBodyDeparture(physical)
                || !ledger.currentBodyResidence(actorId, physical.residenceGeneration())
                || level.getEntity(physical.identity().entityId()) != null
                || FrontierV3AmbientPendingAdmissions.get(runtime, physical.identity().entityId()) != null) return false;
        var id = ActorBodyAuthority.current(state, actorId);
        var phase = ActorBodyAuthority.require(state, id).phase();
        if (phase != FencedRecoveryPhase.RUNNING && phase != FencedRecoveryPhase.AMBIGUOUS) return false;
        var actor = state.actorLocations().get(actorId);
        if (!actor.body().equals(physical.observed().body()) || !actor.condition().health().equals(physical.observed().health())) {
            var receipt = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected(id,
                    io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.SAVED_DEPARTURE,
                    actor.body(), actor.condition().health(), physical.observed().body(), physical.observed().health(),
                    FrontierV3ActorBodyDeparture.execution(state, actorId));
            if (!(FrontierV3CommandSubmission.submitResult(runtime, "actor-body-saved-checkpoint", actorId.value(), receipt)
                    instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)) return false;
        }
        if (!ledger.fence(physical.identity(), id.physicalEpoch(), 0L)) return false;
        ledger.persist(level, state.bootstrap().worldId());
        return true;
    }

    /** Read-only conversion of explicit disk body identity; no scene/activity supplies an epoch or pose. */
    static java.util.Optional<FrontierV3ActorBodyDeparture> storedDeparture(FrontierWorldState state,
            ActorBodyId body, FrontierV3SceneDeparturePersistence.SavedBody saved) {
        try {
            var actor = state.actorLocations().get(body.actorId());
            var phase = ActorBodyAuthority.require(state, body).phase();
            if (actor == null || actor.condition().status() != io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.ALIVE
                    || phase != FencedRecoveryPhase.RUNNING && phase != FencedRecoveryPhase.AMBIGUOUS) return java.util.Optional.empty();
            var kind = io.farfrontier.palemirror.frontier.v3.model.ActorKind.valueOf(saved.kind());
            var inactive = FrontierV3ActorCarrierComposition.fromCanonical(state, body.actorId(), kind,
                    FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, ActorBodyId.entityId(state.bootstrap().worldId(), body.actorId()),
                    FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, 0L, body.physicalEpoch());
            var receipt = new FrontierV3ActorBodyDeparture(inactive, saved.residenceGeneration(),
                    new io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition(body.actorId(), saved.body(), saved.health()),
                    actor.body(), actor.condition().health(), FrontierV3ActorBodyDeparture.execution(state, body.actorId()),
                    saved.offhand(), saved.mainhand());
            return saved.matches(receipt) ? java.util.Optional.of(receipt) : java.util.Optional.empty();
        } catch (IllegalArgumentException staleOrForeign) { return java.util.Optional.empty(); }
    }

    /** Positive no-load disk proof supplies a missing unload callback, independently of process scopes. */
    static boolean confirmStoredDeparture(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            ActorBodyId body, io.farfrontier.palemirror.frontier.v3.model.ActorLocation expected,
            FrontierV3StoredEntityCensus.Result census, net.minecraft.world.level.ChunkPos column,
            FrontierV3StoredEntityInspection.StampedSnapshot snapshot) {
        var state = runtime.decodedState().orElse(null);
        if (state == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return false;
        var entityId = ActorBodyId.entityId(state.bootstrap().worldId(), body.actorId());
        if (!expected.equals(state.actorLocations().get(body.actorId()))
                || !census.stillCurrent(level) || !census.uniqueAt(entityId, column) || !snapshot.stillCurrent(level, column)
                || level.getEntity(entityId) != null || FrontierV3AmbientPendingAdmissions.get(runtime, entityId) != null) return false;
        var saved = snapshot.snapshot().actors().get(entityId);
        if (saved == null) return false;
        var receipt = storedDeparture(state, body, saved).orElse(null);
        if (receipt == null) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!ledger.currentBodyResidence(body.actorId(), receipt.residenceGeneration())
                || !ledger.permitsRecordedOwner(FrontierV3ActorOwnerBinding.body(
                    receipt.identity().liveBody(FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, 0L, body.physicalEpoch())))
                || !ledger.recordBodyDeparture(receipt) || !ledger.confirmSavedBodyDeparture(receipt)) return false;
        ledger.persist(level, state.bootstrap().worldId());
        return true;
    }

    /** Bounded actor probes supply candidates; historical scene membership never authorizes removal. */
    static void cleanRetired(ServerLevel level, FrontierWorldState state,
                             io.farfrontier.palemirror.frontier.v3.api.SubjectId actorId) {
        var entity = level.getEntity(SceneLease.deterministicEntityId(state.bootstrap().worldId(), actorId));
        if (entity instanceof Mob body) discardRetired(level, state, body);
    }

    static boolean discardRetired(ServerLevel level, FrontierWorldState state, Mob body) {
        if (body.level() != level || body.isRemoved()) return false;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(body).orElse(null);
        if (declaration == null) return false;
        var actor = state.actorLocations().get(declaration.actorId());
        if (actor == null || actor.kind() != declaration.kind()
                || !SceneLease.deterministicEntityId(state.bootstrap().worldId(), declaration.actorId()).equals(declaration.entityId()))
            return false;
        var id = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(declaration.actorId(), declaration.epoch());
        var retired = state.fencedRecovery().tombstones().get(
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.recoveryBindingId(declaration.actorId()));
        boolean dead = actor.condition().status() == io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.DEAD
                && retired != null && retired.retiredEpoch() == declaration.epoch();
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!dead && !ledger.fencesBody(declaration)) return false;
        if (!FrontierV3GoalNavigation.retireIncarnation(body, id)) return false;
        body.discard();
        return true;
    }

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
        var placement = FrontierV3BodyPlacement.select(level, body, request.surfaces(), request.scope(), request.standing());
        if (placement.isEmpty()) return Result.DEFERRED;
        var point = FrontierV3SemanticMovement.point(level, placement.orElseThrow());
        body.setPos(point.x, point.y, point.z);
        FrontierV3BodyObservation.refreshGroundContact(level, body);
        body.setPersistenceRequired();
        body.setNoAi(true);
        request.binding().stamp(body);
        if (!request.projection().initialize(body)) return Result.CONFLICT;
        // A resource projection is not allowed to change the complete physical
        // declaration, and must not have admitted or removed its private body.
        if (body.isRemoved() || !body.isAlive() || !body.position().equals(point) || level.getEntity(declaration.entityId()) != null
                || !FrontierV3ActorOwnerBinding.from(body).filter(request.binding()::equals).isPresent())
            return Result.CONFLICT;
        if (!FrontierV3BodyPlacement.available(level, body, body.getBoundingBox())) return Result.DEFERRED;
        boolean added = admit(ledger, request.binding(), admission,
                () -> ledger.persist(level, state.bootstrap().worldId()), () -> {
                    long residence = ledger.beginBodyResidence(declaration);
                    body.getPersistentData().putLong(RESIDENCE_KEY, residence);
                    ledger.persist(level, state.bootstrap().worldId());
                    return level.addFreshEntity(body);
                });
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
) return Admission.CONFLICT;
        var reconciliation = ledger.reconciliation(declaration, bodyPresent);
        if (reconciliation == FrontierV3AmbientCarrierLedger.Reconciliation.READY) {
            var first = ledger.firstAdmission(declaration.actorId()).orElse(null);
            return first != null && first.identity().matches(declaration)
                    && first.phase() == FrontierV3ActorFirstAdmission.Phase.ESTABLISHED
                    ? Admission.RECONSTRUCTION : Admission.CONFLICT;
        }
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
