package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.persistence.CargoCarrierReleasedPayloadCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEngine;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CargoCarrierReleaseStateTest {
    @Test
    void deadEngagementMemberRecoveryDrainsBeforeColdCombatCanResume() {
        var world = new WorldId("frontier:engagement-death-return");
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(world, 91L));
        var before = state(engine);
        var candidate = before.coldEngagementSceneCandidates().getFirst();
        var lease = FrontierTestSceneLeases.exact(before, new SceneLeaseId("lease:engagement-death-return"),
                candidate.operationId(), candidate.cargoId(), candidate.handoffPosition(), engine.checkpoint().instant(),
                engine.checkpoint().revision().value(), Optional.of(candidate.engagementId()), candidate.actorIds());
        submit(engine, world, "prepare", new SceneLeasePrepared(lease));
        FrontierTestActorBodies.present(engine, world, lease);
        submit(engine, world, "hot", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        var target = before.strategicPlans().routeEngagements().get(candidate.engagementId()).attackerIds().getFirst();
        submit(engine, world, "death", ModeledActorBodyFacts.death(new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState()),
                target, lease.memberBody(state(engine).actorLocations(), target), "test-death"));
        submit(engine, world, "unknown", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART));
        var unknown = state(engine);
        assertEquals(SceneLeaseStatus.DRAINING,
                FrontierSceneBehaviors.recoveredStatus(unknown, unknown.sceneLeases().get(lease.id())));
        submit(engine, world, "returned", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
        var draining = state(engine);
        var survivors = lease.members().stream().filter(member -> !member.actorId().equals(target))
                .map(member -> new SceneMemberPosition(member.actorId(), draining.actorLocations().get(member.actorId()).body(),
                        draining.actorLocations().get(member.actorId()).condition().health())).toList();
        submit(engine, world, "closed", new SceneLeaseReleased(lease.id(), survivors));
        var closed = state(engine);
        assertEquals(SceneLeaseStatus.CLOSED, closed.sceneLeases().get(lease.id()).status());
        assertEquals(ActorLifeStatus.DEAD, closed.actorLocations().get(target).condition().status());
        assertEquals(RouteEngagementStatus.COLD_COMBAT, closed.strategicPlans().routeEngagements().get(candidate.engagementId()).status());
        assertEquals(unknown.inventory(), closed.inventory());
    }

    @Test
    void returnedFailedDeliveryReleasesCustodyWithoutResumingItsOperation() {
        var world = new WorldId("frontier:failed-delivery-return");
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(world, 91L));
        var before = state(engine);
        var operation = FrontierDevelopmentScenarios.initialNorthwatchShipment(before).orElseThrow();
        var lease = FrontierTestSceneLeases.exact(before, new SceneLeaseId("lease:failed-delivery-return"),
                operation.id(), operation.cargoId(), operation.currentPosition(), engine.checkpoint().instant(),
                engine.checkpoint().revision().value(), Optional.empty(), operation.participantIds());
        submit(engine, world, "prepare", new SceneLeasePrepared(lease));
        FrontierTestActorBodies.present(engine, world, lease);
        submit(engine, world, "hot", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        submit(engine, world, "unknown", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART));
        submit(engine, world, "missing", new SceneLeaseRecoveryUnresolved(lease.id(),
                java.util.Set.of(operation.participantIds().getFirst()), false));
        var failed = state(engine);
        assertEquals(OperationStage.FAILED, failed.operations().get(operation.id()).stage());
        assertEquals(SceneLeaseStatus.DRAINING,
                FrontierSceneBehaviors.recoveredStatus(failed, failed.sceneLeases().get(lease.id())));
        submit(engine, world, "returned", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
        var draining = state(engine);
        assertEquals(draining, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(draining)));
        submit(engine, world, "drain-lost", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART));
        submit(engine, world, "still-missing", new SceneLeaseRecoveryUnresolved(lease.id(),
                java.util.Set.of(operation.participantIds().getFirst()), false));
        var missingAgain = state(engine);
        assertEquals(failed.operations(), missingAgain.operations());
        assertThrows(IllegalArgumentException.class,
                () -> missingAgain.transitionSceneLease(lease.id(), SceneLeaseStatus.HOT));
        submit(engine, world, "returned-again", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
        var positions = lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                failed.actorLocations().get(member.actorId()).body(),
                failed.actorLocations().get(member.actorId()).condition().health())).toList();
        submit(engine, world, "closed", new SceneLeaseReleased(lease.id(), positions));
        var closed = state(engine);
        assertEquals(SceneLeaseStatus.CLOSED, closed.sceneLeases().get(lease.id()).status());
        assertEquals(failed.operations(), closed.operations());
        assertEquals(failed.inventory(), closed.inventory());
        assertEquals(failed.strategicPlans(), closed.strategicPlans());
    }

    @Test
    void missingCombatMemberRetainsUncertaintyWithoutInventingAnOutcomeAcrossRecovery() {
        for (boolean cargoReleased : List.of(false, true)) {
            var world = new WorldId("frontier:engagement-missing-" + cargoReleased);
            var transactions = new java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord>();
            var configuration = FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(world, 91L)
                    .withTransactionCommitter((transaction, durability) -> transactions.add(transaction));
            var engine = FrontierEngines.create(configuration);
            var before = state(engine);
            var candidate = before.coldEngagementSceneCandidates().getFirst();
            var lease = FrontierTestSceneLeases.exact(before, new SceneLeaseId("lease:engagement-missing"),
                    candidate.operationId(), candidate.cargoId(), candidate.handoffPosition(),
                    engine.checkpoint().instant(), engine.checkpoint().revision().value(),
                    Optional.of(candidate.engagementId()), candidate.actorIds());
            submit(engine, world, "prepare", new SceneLeasePrepared(lease));
            FrontierTestActorBodies.present(engine, world, lease);
            submit(engine, world, "hot", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
            if (cargoReleased) {
                submit(engine, world, "release", new CargoCarrierReleased(lease.id(), candidate.cargoId(),
                        CargoCarrierIdentity.id(lease), Optional.empty()));
            }
            submit(engine, world, "unknown", new SceneLeaseTransition(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART));
            var unknown = state(engine);
            var checkpoint = engine.checkpoint();
            var missing = java.util.Set.of(candidate.actorIds().getFirst());
            submit(engine, world, "missing", new SceneLeaseRecoveryUnresolved(lease.id(), missing, false));
            var retained = state(engine);
            assertEquals(unknown.actorLocations(), retained.actorLocations());
            assertEquals(unknown.inventory(), retained.inventory());
            assertEquals(unknown.operations(), retained.operations());
            assertEquals(unknown.strategicPlans(), retained.strategicPlans());
            assertEquals(SceneLeaseStatus.UNKNOWN_AFTER_RESTART, retained.sceneLeases().get(lease.id()).status());
            assertTrue(retained.sceneLeases().get(lease.id()).recoveryEvidence().isPresent());
            assertEquals(retained, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(retained)));
            var replayed = FrontierEngines.recover(configuration,
                    new io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage(world,
                            Optional.of(new io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord(checkpoint, 0)),
                            List.of(transactions.getLast())));
            assertEquals(engine.checkpoint(), replayed.checkpoint());
            var recoveryStatus = FrontierSceneBehaviors.recoveredStatus(retained, retained.sceneLeases().get(lease.id()));
            assertEquals(cargoReleased ? SceneLeaseStatus.DRAINING : SceneLeaseStatus.HOT, recoveryStatus);
            submit(replayed, world, "returned", new SceneLeaseTransition(lease.id(), recoveryStatus));
            var returned = state(replayed);
            assertEquals(unknown.actorLocations(), returned.actorLocations());
            assertEquals(unknown.inventory(), returned.inventory());
            assertTrue(returned.sceneLeases().get(lease.id()).recoveryEvidence().isEmpty());
            if (cargoReleased) {
                var positions = lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                        returned.actorLocations().get(member.actorId()).body(),
                        returned.actorLocations().get(member.actorId()).condition().health())).toList();
                submit(replayed, world, "returned-drain", new SceneLeaseReleased(lease.id(), positions));
                var closed = state(replayed);
                assertEquals(SceneLeaseStatus.CLOSED, closed.sceneLeases().get(lease.id()).status());
                assertEquals(OperationStage.INTERRUPTED, closed.operations().get(candidate.operationId()).stage());
                assertEquals(RouteEngagementOutcome.ABORTED,
                        closed.strategicPlans().routeEngagements().get(candidate.engagementId()).outcome().orElseThrow());
                assertEquals(CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY,
                        closed.fencedRecovery().cargoRetirements().pending().get(CargoCarrierIdentity.id(lease)).disposition());
            }
        }
    }

    @Test
    void playerTakingAllCargoBeforeBodyDrainCannotTurnReleasedCartIntoRemovableProjection() {
        var before = FrontierDevelopmentScenarios.hotSceneStrikeState(new WorldId("frontier:cargo-drain-race"), 91L);
        var candidate = before.coldEngagementSceneCandidates().getFirst();
        var lease = FrontierTestSceneLeases.exact(before, new SceneLeaseId("lease:cargo-drain-race"),
                candidate.operationId(), candidate.cargoId(), candidate.handoffPosition(), new SimInstant(2_600L),
                1L, Optional.of(candidate.engagementId()), candidate.actorIds());
        var hot = FrontierTestActorBodies.present(before.prepareSceneLease(lease), lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        var carrier = CargoCarrierIdentity.id(lease);
        var cargoAccount = hot.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody().equals(new ResourceCustody.Cargo(candidate.cargoId())))
                .findFirst().orElseThrow();
        var currentClaim = hot.inventory().fungibleResources().claims().get(cargoAccount.claimQuantities().keySet().iterator().next());
        var wrongPurposeClaims = new java.util.HashMap<>(hot.inventory().fungibleResources().claims());
        wrongPurposeClaims.put(currentClaim.id(), new ClaimAllocation(currentClaim.id(), currentClaim.claimantId(),
                currentClaim.economicOwnerId(), currentClaim.itemKind(), currentClaim.quantity(), currentClaim.lotQuantities(),
                ClaimPurpose.EXTERNAL_RESERVATION));
        var currentResources = hot.inventory().fungibleResources();
        FrontierWorldState wrongPurpose = hot.withInventory(hot.inventory().withFungibleResources(new FungibleResourceLedger(
                currentResources.lots(), wrongPurposeClaims, currentResources.accounts(), currentResources.bindings())));
        assertThrows(IllegalArgumentException.class, () -> wrongPurpose.releaseCargoCarrier(new CargoCarrierReleased(
                lease.id(), candidate.cargoId(), carrier, Optional.empty())));
        var released = hot.releaseCargoCarrier(new CargoCarrierReleased(lease.id(), candidate.cargoId(), carrier, Optional.empty()));
        var codec = new FrontierWorldStateCodec();
        released = codec.decode(codec.encode(released));
        assertEquals(CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY,
                FrontierSceneBehaviors.logistics(released.sceneLeases().get(lease.id())).carrierDisposition());
        var releasedLease = released.sceneLeases().get(lease.id());
        assertThrows(IllegalArgumentException.class, () -> releasedLease.withStatus(SceneLeaseStatus.HOT));
        assertThrows(IllegalArgumentException.class, () -> releasedLease.withStatus(SceneLeaseStatus.PREPARED));
        var inventory = released.inventory();
        var resources = inventory.fungibleResources();
        var account = resources.accounts().values().stream()
                .filter(value -> value.custody().equals(new ResourceCustody.WorldCarrier(carrier))).findFirst().orElseThrow();
        var binding = resources.bindings().values().stream().filter(value -> value.accountId().equals(account.id())).findFirst().orElseThrow();
        var playerId = new UUID(0, 99);
        var player = new CustodyAccount(new SubjectId("custody:drain-race-player"), new ResourceCustody.Player(playerId),
                account.lotQuantities(), account.claimQuantities());
        var held = new PhysicalStackBinding(new SubjectId("binding:drain-race-player"), player.id(),
                new PhysicalStackAddress.PlayerSlot(playerId, 0), 1L, binding.itemKind(), player.lotQuantities(), player.claimQuantities());
        inventory = inventory.withFungibleResources(resources.transferObservedToNewAccount(account.id(), player,
                binding.authorityEpoch(), 1L, account.lotQuantities(), account.claimQuantities(), List.of(), List.of(held)));
        var emptied = released.withInventory(inventory).transitionSceneLease(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
        emptied = codec.decode(codec.encode(emptied)).transitionSceneLease(lease.id(), SceneLeaseStatus.DRAINING);
        assertTrue(!emptied.inventory().hasWorldCarrierCustody(carrier));
        var actors = emptied.actorLocations();
        var positions = lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                actors.get(member.actorId()).body(), actors.get(member.actorId()).condition().health())).toList();
        var closed = emptied.releaseSceneLease(lease.id(), positions);
        assertEquals(CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY,
                closed.fencedRecovery().cargoRetirements().pending().get(carrier).disposition(),
                "accepted carrier release is irreversible even after its last item changes custody");
    }

    @Test
    void ordinarySceneReleaseRetainsCleanupAlongsideColdCargoAndPersistsIt() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(
                new WorldId("frontier:cargo-cleanup"), 91L));
        var before = state(engine);
        var operation = FrontierDevelopmentScenarios.initialNorthwatchShipment(before).orElseThrow();
        var lease = FrontierTestSceneLeases.exact(before, new SceneLeaseId("lease:cargo-cleanup"),
                operation.id(), operation.cargoId(), operation.currentPosition(), engine.checkpoint().instant(),
                engine.checkpoint().revision().value(), Optional.empty(), operation.participantIds());
        var draining = FrontierTestActorBodies.present(before.prepareSceneLease(lease), lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT)
                .transitionSceneLease(lease.id(), SceneLeaseStatus.DRAINING);
        var preparedPayload = new SceneLeasePrepared(lease);
        var payloadCodecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        byte[] encoded = payloadCodecs.encode(preparedPayload);
        assertEquals(preparedPayload, payloadCodecs.decode(preparedPayload.type(), encoded));
        byte[] missingDispositionEnvelope = encoded.clone();
        missingDispositionEnvelope[0] = (byte) 0xff;
        missingDispositionEnvelope[1] = (byte) 0xfe;
        assertThrows(IllegalArgumentException.class, () -> payloadCodecs.decode(preparedPayload.type(), missingDispositionEnvelope));
        var positions = lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                draining.actorLocations().get(member.actorId()).body(),
                draining.actorLocations().get(member.actorId()).condition().health())).toList();
        var closed = draining.releaseSceneLease(lease.id(), positions);
        var obligation = closed.fencedRecovery().cargoRetirements().pending().get(CargoCarrierIdentity.id(lease));
        assertEquals(CargoProjectionRetirement.Disposition.REMOVE_PROJECTION, obligation.disposition());
        assertEquals(operation.cargoId(), obligation.cargoId());
        assertThrows(IllegalArgumentException.class, () -> closed.fencedRecovery().cargoRetirements()
                .validateContext(closed.bootstrap().worldId(), java.util.Map.of(lease.id(), lease.withStatus(SceneLeaseStatus.HOT))));
        var foreignWorld = new WorldId("frontier:foreign-retirement");
        var foreign = new CargoProjectionRetirement(foreignWorld, obligation.leaseId(), obligation.cargoId(),
                CargoCarrierIdentity.id(foreignWorld, obligation.leaseId(), obligation.cargoId()),
                obligation.authorization(), obligation.disposition());
        var forgedRecovery = new FencedRecoveryState(closed.fencedRecovery().current(), closed.fencedRecovery().tombstones(),
                new CargoProjectionRetirements(java.util.Map.of(foreign.entityId(), foreign)));
        assertThrows(IllegalArgumentException.class, () -> closed.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(forgedRecovery)));
        assertTrue(closed.inventory().cargo().containsKey(operation.cargoId()));
        assertEquals(closed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(closed)));
    }

    @Test
    void releaseAtomicallyInterruptsRouteAndKeepsFungibleCargoHotAtItsObservedCarrier() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(
                new WorldId("frontier:cargo-release"), 91L));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = FrontierDevelopmentScenarios.initialNorthwatchShipment(before).orElseThrow();
        SceneLeaseId leaseId = new SceneLeaseId("lease:cargo-release");
        SceneLease lease = FrontierTestSceneLeases.exact(before, leaseId, operation.id(), operation.cargoId(),
                operation.currentPosition(), engine.checkpoint().instant(), engine.checkpoint().revision().value(),
                Optional.empty(), operation.participantIds());
        FrontierWorldState hot = FrontierTestActorBodies.present(before.prepareSceneLease(lease), lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        SubjectId recoveryBinding = FrontierSceneLeaseStateSupport.cargoRecoveryBindingId(operation.cargoId());
        assertEquals(FencedRecoveryPhase.RUNNING, hot.fencedRecovery().current().get(recoveryBinding).phase());
        UUID carrier = CargoCarrierIdentity.id(lease);
        CargoCarrierReleased release = new CargoCarrierReleased(leaseId, operation.cargoId(), carrier, Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000051")));
        FrontierWorldState interrupted = hot.releaseCargoCarrier(release);

        assertTrue(!interrupted.inventory().cargo().containsKey(operation.cargoId()));
        CustodyAccount carrierAccount = interrupted.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody().equals(new ResourceCustody.WorldCarrier(carrier))).findFirst().orElseThrow();
        assertEquals(64, carrierAccount.lotQuantities().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, interrupted.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(carrierAccount.id())
                        && binding.address().equals(new PhysicalStackAddress.WorldEntity(carrier))).count());
        assertEquals(ContractStatus.INTERRUPTED, interrupted.contracts().values().stream().filter(contract -> contract.cargoId().equals(operation.cargoId())).findFirst().orElseThrow().status());
        assertEquals(OperationStage.INTERRUPTED, interrupted.operations().get(operation.id()).stage());
        assertEquals(SceneLeaseStatus.DRAINING, interrupted.sceneLeases().get(leaseId).status());
        assertEquals(FencedRecoveryPhase.OBSERVED, interrupted.fencedRecovery().current().get(recoveryBinding).phase(),
                "world/player-visible cargo cannot be rolled back into the prior shipment");
        assertTrue(interrupted.strategicPlans().routeEngagements().isEmpty());
        assertEquals(release, FrontierWorldRuntimeDefinition.payloadCodecs().decode(release.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(release)));
        assertEquals(interrupted, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(interrupted)));
        assertThrows(IllegalArgumentException.class, () -> hot.releaseCargoCarrier(new CargoCarrierReleased(leaseId, operation.cargoId(), UUID.randomUUID(), release.observerPlayerId())));
    }

    @Test
    void releasePayloadRecoversThePriorRequiredObserverFormat() {
        SceneLeaseId leaseId = new SceneLeaseId("lease:cargo-release-legacy-payload");
        SubjectId cargoId = new SubjectId("cargo:legacy-payload");
        UUID carrierId = UUID.fromString("00000000-0000-0000-0000-000000000053");
        UUID observerId = UUID.fromString("00000000-0000-0000-0000-000000000054");
        byte[] legacy = FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeString(output, leaseId.value());
            FrontierWorldPayloadCodecs.writeSubject(output, cargoId);
            FrontierWorldPayloadCodecs.writeString(output, carrierId.toString());
            FrontierWorldPayloadCodecs.writeString(output, observerId.toString());
        });

        assertEquals(new CargoCarrierReleased(leaseId, cargoId, carrierId, Optional.of(observerId)),
                new CargoCarrierReleasedPayloadCodec().decode(legacy));
    }

    @Test
    void interruptedCargoSceneRecoversOnlyToDrainOriginalBodies() {
        FrontierWorldState before = FrontierDevelopmentScenarios.hotSceneStrikeState(new WorldId("frontier:cargo-release-recovery"), 91L);
        SceneEngagementCandidate candidate = before.coldEngagementSceneCandidates().getFirst();
        SceneLeaseId leaseId = new SceneLeaseId("lease:cargo-release-recovery");
        SceneLease lease = FrontierTestSceneLeases.exact(before, leaseId, candidate.operationId(), candidate.cargoId(),
                candidate.handoffPosition(), new SimInstant(2_600L), 1L, Optional.of(candidate.engagementId()),
                candidate.actorIds());
        FrontierWorldState hot = FrontierTestActorBodies.present(before.prepareSceneLease(lease), lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        CargoCarrierReleased released = new CargoCarrierReleased(leaseId, FrontierSceneBehaviors.logistics(lease).cargoId(),
                CargoCarrierIdentity.id(lease), Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000052")));
        FrontierWorldState interrupted = hot.releaseCargoCarrier(released);

        assertEquals(RouteEngagementStatus.RESOLVED, interrupted.strategicPlans().routeEngagements().get(candidate.engagementId()).status());
        assertEquals(RouteEngagementOutcome.ABORTED, interrupted.strategicPlans().routeEngagements().get(candidate.engagementId()).outcome().orElseThrow());
        FrontierWorldState unknown = interrupted.transitionSceneLease(leaseId, SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
        assertEquals(unknown, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(unknown)));
        assertThrows(IllegalArgumentException.class, () -> unknown.transitionSceneLease(leaseId, SceneLeaseStatus.HOT));
        List<SceneMemberPosition> bodies = lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                FrontierTestPositions.bodyCellOf(unknown.actorLocations().get(member.actorId())), unknown.actorLocations().get(member.actorId()).condition().health())).toList();
        FrontierWorldState closed = unknown.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING).releaseSceneLease(leaseId, bodies);
        assertEquals(SceneLeaseStatus.CLOSED, closed.sceneLeases().get(leaseId).status());
        assertEquals(RouteEngagementOutcome.ABORTED, closed.strategicPlans().routeEngagements().get(candidate.engagementId()).outcome().orElseThrow());
    }

    @Test
    void physicalCargoLossClosesHotSceneWithoutInventingColdCombat() {
        WorldId world = new WorldId("frontier:cargo-release-live-path");
        var transactions = new java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord>();
        var durabilities = new java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.persistence.Durability>();
        var configuration = FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(world, 91L)
                .withTransactionCommitter((transaction, durability) -> { transactions.add(transaction); durabilities.add(durability); });
        FrontierEngine<FrontierWorldProjection> engine = FrontierEngines.create(configuration);
        FrontierWorldState before = state(engine);
        SceneEngagementCandidate candidate = before.coldEngagementSceneCandidates().getFirst();
        SceneLeaseId leaseId = new SceneLeaseId("lease:cargo-release-live-path");
        SceneLease lease = FrontierTestSceneLeases.exact(before, leaseId, candidate.operationId(), candidate.cargoId(),
                candidate.handoffPosition(), engine.checkpoint().instant(), engine.checkpoint().revision().value(),
                Optional.of(candidate.engagementId()), candidate.actorIds());

        submit(engine, world, "prepare", new SceneLeasePrepared(lease));
        FrontierTestActorBodies.present(engine, world, lease);
        submit(engine, world, "hot", new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));
        submit(engine, world, "cargo-loss", new CargoCarrierReleased(leaseId, FrontierSceneBehaviors.logistics(lease).cargoId(), CargoCarrierIdentity.id(lease),
                Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000055"))));
        FrontierWorldState interrupted = state(engine);
        assertEquals(OperationStage.INTERRUPTED, interrupted.operations().get(candidate.operationId()).stage());
        assertEquals(RouteEngagementOutcome.ABORTED,
                interrupted.strategicPlans().routeEngagements().get(candidate.engagementId()).outcome().orElseThrow());
        assertEquals(SceneLeaseStatus.DRAINING, interrupted.sceneLeases().get(leaseId).status());

        List<SceneMemberPosition> survivors = lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                FrontierTestPositions.bodyCellOf(interrupted.actorLocations().get(member.actorId())),
                interrupted.actorLocations().get(member.actorId()).condition().health())).toList();
        submit(engine, world, "drain", new SceneLeaseReleased(leaseId, survivors));

        FrontierWorldState closed = state(engine);
        assertEquals(SceneLeaseStatus.CLOSED, closed.sceneLeases().get(leaseId).status());
        assertEquals(CargoProjectionRetirement.Disposition.RETAIN_WORLD_CUSTODY,
                closed.fencedRecovery().cargoRetirements().pending().get(CargoCarrierIdentity.id(lease)).disposition());
        assertEquals(OperationStage.INTERRUPTED, closed.operations().get(candidate.operationId()).stage());
        assertEquals(RouteEngagementOutcome.ABORTED,
                closed.strategicPlans().routeEngagements().get(candidate.engagementId()).outcome().orElseThrow());
        assertTrue(engine.checkpoint().schedules().stream().noneMatch(action -> action.kind().equals("frontier.hive_route_engagement.combat")),
                "an aborted physical scene must not manufacture a COLD combat continuation");

        // Pure protocol verification: supplies a provider acknowledgement as input, not native save proof.
        var retirement = closed.fencedRecovery().cargoRetirements().pending().get(CargoCarrierIdentity.id(lease));
        var saved = new FencedRecoveryPayloads.CargoCleanupSaved(retirement);
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(saved, codecs.decode(saved.type(), codecs.encode(saved)));
        var beforeAck = engine.checkpoint();
        submit(engine, world, "cleanup-saved", saved);
        assertTrue(state(engine).fencedRecovery().cargoRetirements().pending().isEmpty());
        assertEquals(closed.inventory(), state(engine).inventory(), "ack releases cleanup capacity, never cargo custody");
        assertEquals(io.farfrontier.palemirror.frontier.v3.persistence.Durability.DURABLE_BEFORE_EFFECT,
                durabilities.getLast(), "ack must be durable before provider witness compaction");
        var replayed = FrontierEngines.recover(configuration, new io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage(
                world, Optional.of(new io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord(beforeAck, 0)),
                List.of(transactions.getLast())));
        assertEquals(engine.checkpoint(), replayed.checkpoint());
    }

    private static FrontierWorldState state(FrontierEngine<FrontierWorldProjection> engine) {
        return new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
    }

    private static void submit(FrontierEngine<FrontierWorldProjection> engine, WorldId world, String suffix,
                               FrontierPayload payload) {
        CommandId command = new CommandId("command:cargo-release-live-path-" + suffix);
        var checkpoint = engine.checkpoint();
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(1, command, world,
                checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(command), payload)));
    }
}
