package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ConfirmedNativeEventTest {
    @BeforeAll static void version(){net.minecraft.SharedConstants.tryDetectVersion();}
    @Test void aConfirmedFactSurvivesPredecessorWaitAndRestartAndCommitsOnlyOnce() {
        var home=new SubjectId("settlement:test");
        var fact=new SecurityEvent.Incident(home,new ActorBodyId(new SubjectId("resident:test"),7),new CombatTarget(CombatTarget.Kind.PLAYER,new UUID(11,12),"pale_mirror:frontier_graybox"),
                new BlockPosition(0,63,0),Optional.empty(),1);
        var ledger=FrontierV3OperationLedger.inMemory();var operation=FrontierV3ConfirmedNativeEvent.record(ledger,home,fact);
        var before=ledger.save(new CompoundTag(),null);var ready=new AtomicBoolean();var submissions=new AtomicInteger();
        var receipts=new HashMap<CommandId,CommandReceipt>();var pins=new HashSet<CommandId>();
        var canonical=new FrontierV3OperationConfirmation.Canonical(){
            public void pin(CommandId id){pins.add(id);} public void release(CommandId id){pins.remove(id);}
            public Optional<CommandReceipt> receipt(CommandId id){return Optional.ofNullable(receipts.get(id));}
            public CommandResult submit(CommandId id,SubjectId owner,FrontierPayload payload){
                assertEquals(home,owner);assertEquals(fact,payload);
                if(!ready.get())return new CommandResult.Pending(id,Revision.ZERO,CommandResult.Pending.Reason.PREDECESSOR);
                submissions.incrementAndGet();var result=new CommandResult.Accepted(id,new TransactionId("test:fact"),new Revision(1));
                receipts.put(id,new CommandReceipt(id,SimInstant.ZERO,result.transactionId(),result.revision()));return result;
            }
        };
        assertEquals(FrontierV3OperationConfirmation.Outcome.AWAITING_CANONICAL_COMMIT,
                FrontierV3OperationConfirmation.submitOutcome(ledger,()->{},canonical,operation.ticket(),fact));
        assertTrue(pins.isEmpty());assertEquals(0,submissions.get());assertTrue(ledger.find(operation.ticket()).isPresent());
        var recovered=FrontierV3OperationLedger.load(before,null);ready.set(true);
        assertEquals(fact,recovered.ownerCommand(operation.ticket()).orElseThrow().consequence());
        assertEquals(FrontierV3OperationConfirmation.Outcome.CONFIRMED,
                FrontierV3OperationConfirmation.submitOutcome(recovered,()->{},canonical,operation.ticket(),fact));
        assertEquals(1,submissions.get());assertTrue(recovered.find(operation.ticket()).isEmpty());assertTrue(pins.isEmpty());
        var repeat=FrontierV3OperationLedger.load(before,null);FrontierV3OperationConfirmation.recover(repeat,()->{},canonical);
        assertEquals(1,submissions.get());assertTrue(repeat.find(operation.ticket()).isEmpty());
    }
    @Test void eventEvidenceCannotBecomeANativeMutationPermission() {
        var owner=new SubjectId("settlement:test");
        var witness=new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.OBSERVED_EVENT,"event:test",0);
        assertThrows(IllegalArgumentException.class,()->new FrontierV3OperationReceipt.Declaration(
                FrontierV3OperationReceipt.Protocol.MELEE_ATTACK,owner,"event","exact",Set.of(witness)));
        var entity=new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITIES,"entity:test",1);
        assertThrows(IllegalArgumentException.class,()->new FrontierV3OperationReceipt.Declaration(
                FrontierV3OperationReceipt.Protocol.NATIVE_OBSERVATION,owner,"event","exact",Set.of(entity)));
    }
}
