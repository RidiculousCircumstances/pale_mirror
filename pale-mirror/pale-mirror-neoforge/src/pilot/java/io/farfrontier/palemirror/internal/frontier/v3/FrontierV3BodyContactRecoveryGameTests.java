package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Native placement of an explicitly retained unsupported pose; not a live-world incident replay. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3BodyContactRecoveryGameTests {
    private FrontierV3BodyContactRecoveryGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-body-lifetime", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void fencedReconstructionSettlesOneKnownStepWithoutGrantingBirthFromAbsence(GameTestHelper helper) {
        var level = helper.getLevel(); var actor = new SubjectId("resident:1-1");
        var state = FrontierV3AmbientActorGameTests.configurationAt(helper,
                new WorldId("frontier:known-contact-reconstruction"), 91L, actor).initialState();
        var standing = state.actorLocations().get(actor).body();
        var pose = new BodyPosition(standing.x(), standing.y() + 1, standing.z());
        state = state.withActorBody(actor, pose);
        var presence = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, presence).commit(state, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor);
        var first = ActorBodyAuthority.current(state, actor);
        var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state, actor,
                FrontierV3AmbientActorExecutor.entityId(state, actor), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY,
                first.physicalEpoch());
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        helper.assertTrue(ledger.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(
                new FrontierV3ActorFirstAdmission.Identity(actor, ActorKind.RESIDENT, declaration.entityId()))), "declare fixture history");
        helper.assertTrue(ledger.beginFirstAdmission(FrontierV3ActorOwnerBinding.body(declaration))
                && ledger.fence(declaration.inactiveCarrier(), 1, 1), "fixture retains typed absence of the earlier incarnation");
        state = ActorBodyAuthority.demand(ActorBodyAuthority.released(state, first), actor);
        var next = ActorBodyAuthority.current(state, actor);
        var binding = FrontierV3ActorOwnerBinding.body(declaration.liveBody(declaration.owner(), 0, next.physicalEpoch()));
        var ground = standing.supportingSurface();
        FrontierV3AmbientActorGameTests.prepareFloor(level, new BlockPos(standing.x(), standing.y(), standing.z()));
        var scope = new FrontierV3NavigationScope.Restricted(LocalNavigationEnvelope.around(standing, pose));
        var request = new FrontierV3ActorBodyController.BirthRequest(FrontierV3ActorCarrierComposition.InventoryEntry.SCENE_BODY,
                binding, List.of(pose.supportingSurface()), scope, FrontierV3StandingPosition::aboveExactFloor,
                surface -> surface.equals(pose.supportingSurface()), body -> true, Optional.of(pose));
        helper.assertValueEqual(FrontierV3ActorBodyController.admission(state, binding, ledger, false),
                FrontierV3ActorBodyController.Admission.RECONSTRUCTION, "exact history is reconstruction, never first birth");
        helper.assertTrue(FrontierV3ActorBodyController.resumableAdmission(level, state, binding), "whole-set scene recovery can resume it");
        helper.assertTrue(FrontierV3ActorBodyController.reconstructionContact(state, pose).filter(ground::equals).isPresent(),
                "known contact differs from the retained airborne pose");
        helper.assertValueEqual(FrontierV3BodyAdmissionGameTestFixture.body(level, state, request),
                FrontierV3ActorBodyController.Result.APPLIED, "durable common admission uses the real supported column");
        var body = level.getEntity(declaration.entityId());
        helper.assertTrue(body != null && body.blockPosition().equals(new BlockPos(standing.x(), standing.y(), standing.z())),
                "same UUID is physically supported without horizontal relocation");
        helper.assertValueEqual(state.actorLocations().get(actor).body(), pose, "placement cannot invent canonical goal arrival");
        helper.assertValueEqual(FrontierV3ActorBodyController.materialize(level, state, request),
                FrontierV3ActorBodyController.Result.CONFLICT, "indexed presence forbids another reconstruction");
        body.discard(); helper.succeed();
    }
}
