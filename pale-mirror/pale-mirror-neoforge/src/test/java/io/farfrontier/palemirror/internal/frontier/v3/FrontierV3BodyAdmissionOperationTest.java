package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorKind;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3BodyAdmissionOperationTest {
    @Test void exactUndoAndItsClosureRecoverInTheSameActorRow() {
        var actor = new SubjectId("resident:body-operation"); var uuid = UUID.randomUUID();
        var world = new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:body-operation-test");
        var declaration = new FrontierV3ActorCarrierComposition.Declaration(actor, ActorKind.RESIDENT,
                FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, uuid,
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0, 1);
        var binding = FrontierV3ActorOwnerBinding.body(declaration);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(ledger.registerFirstAdmission(FrontierV3ActorFirstAdmission.neverCreated(
                new FrontierV3ActorFirstAdmission.Identity(actor, ActorKind.RESIDENT, uuid))));
        assertTrue(ledger.beginFirstAdmission(binding));
        var undo = new FrontierV3BodyInsertionJournal.First(ledger.firstAdmission(actor).orElseThrow());
        long residence = ledger.beginBodyResidence(declaration);
        var port = new FrontierV3MinecraftOperation.Participant(new FrontierV3OperationReceipt.Participant(
                FrontierV3OperationReceipt.Medium.ENTITIES, uuid.toString(), 12), new CompoundTag(), () -> { });
        var operation = FrontierV3OperationReceipt.prepare(FrontierV3BodyAdmissionOperation.declaration(world, binding, residence, undo, port), 1);
        var link = new FrontierV3BodyAdmissionOperation(world, binding, residence, undo, operation, false);
        // Unload can precede canonical admission: only this exact saved native
        // insertion may acknowledge it, never a prepared/applied-only operation.
        var position = new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(1, 65, 1);
        var health = io.farfrontier.palemirror.frontier.v3.api.FixedScalar.whole(20);
        var departure = new FrontierV3ActorBodyDeparture(declaration.inactiveCarrier(), residence,
                new io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition(actor, position, health),
                position, health, Optional.empty(), Optional.empty(), Optional.empty());
        var nativeReceipt = operation.nativeReceipt(port.identity());
        var applied = operation.applied(nativeReceipt);
        var saved = FrontierV3OperationReceiptCodec.read(FrontierV3OperationReceiptCodec.write(applied.saved(nativeReceipt)));
        assertFalse(FrontierV3BodyOperationIntegration.savedAdmissionMatches(link, departure, operation));
        assertFalse(FrontierV3BodyOperationIntegration.savedAdmissionMatches(link, departure, applied));
        assertTrue(FrontierV3BodyOperationIntegration.savedAdmissionMatches(
                FrontierV3BodyAdmissionOperation.load(link.save()), FrontierV3ActorBodyDeparture.load(departure.save()), saved));
        assertFalse(FrontierV3BodyOperationIntegration.savedAdmissionMatches(link.rejection(), departure, saved));
        assertFalse(FrontierV3BodyOperationIntegration.savedAdmissionMatches(link, departure, operation.cancel()));
        var returned = new FrontierV3ActorBodyDeparture(departure.identity(), residence + 1,
                departure.observed(), position, health, Optional.empty(), Optional.empty(), Optional.empty());
        assertFalse(FrontierV3BodyOperationIntegration.savedAdmissionMatches(link, returned, saved));
        var foreign = new FrontierV3ActorCarrierComposition.Declaration(actor, ActorKind.RESIDENT,
                declaration.owner(), UUID.randomUUID(), declaration.inactiveCarrier().representation(), 0, 1);
        assertFalse(FrontierV3BodyOperationIntegration.savedAdmissionMatches(link,
                new FrontierV3ActorBodyDeparture(foreign, residence, departure.observed(), position, health,
                        Optional.empty(), Optional.empty(), Optional.empty()), saved));
        ledger.retainBodyOperation(link);
        var pending = FrontierV3AmbientCarrierLedger.load(ledger.save(new CompoundTag(), null), null);
        assertEquals(link, pending.bodyOperation(actor).orElseThrow()); assertTrue(undo.current(pending));
        pending.rejectBodyOperation(link);
        var rolledBack = FrontierV3AmbientCarrierLedger.load(pending.save(new CompoundTag(), null), null);
        assertEquals(FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED, rolledBack.firstAdmission(actor).orElseThrow().phase());
        assertEquals(link.rejection(), rolledBack.bodyOperation(actor).orElseThrow());
        rolledBack.rejectBodyOperation(link.rejection()); // Recovered positive rollback is idempotent, not another undo.
        assertThrows(IllegalStateException.class, () -> rolledBack.rejectBodyOperation(link));
        rolledBack.forgetBodyOperation(link.rejection());
        assertTrue(rolledBack.bodyOperations().isEmpty());
        var wrong = link.save(); wrong.putString("actor", "resident:other");
        assertThrows(IllegalArgumentException.class, () -> FrontierV3BodyAdmissionOperation.load(wrong));
        var missing = link.save(); missing.getCompound("undo").remove("kind");
        assertThrows(IllegalArgumentException.class, () -> FrontierV3BodyAdmissionOperation.load(missing));
        var stale = link.save(); stale.putLong("residence", residence + 1);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3BodyAdmissionOperation.load(stale));
    }
}
