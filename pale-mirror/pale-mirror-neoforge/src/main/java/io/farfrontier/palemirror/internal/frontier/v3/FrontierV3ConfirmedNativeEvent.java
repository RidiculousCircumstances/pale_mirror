package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Delivery of an already confirmed native fact through the existing operation journal.
 * It never invokes, replays or authorizes a Minecraft mutation and owns no domain interpretation. */
final class FrontierV3ConfirmedNativeEvent {
    static FrontierV3OperationReceipt record(FrontierV3OperationLedger ledger, SubjectId owner, FrontierPayload fact) {
        if(!fact.requiresDurableBeforeEffect())throw new IllegalArgumentException("native fact requires durable canonical acceptance");
        var id=UUID.randomUUID().toString();
        var witness=new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.OBSERVED_EVENT,id,0);
        var operation=ledger.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.NATIVE_OBSERVATION,
                owner,id,FrontierV3OperationConfirmation.fingerprint(fact),Set.of(witness)));
        // The positive native event has already occurred. Its witness is saved with this journal,
        // not fabricated in a player/entity image and never usable as a mutation port.
        ledger.saved(operation.nativeReceipt(witness));
        ledger.bindOwnerCommand(operation.ticket(),new FrontierV3OperationLedger.OwnerCommand(
                new CommandId("physical-operation:"+operation.ticket()),fact));
        return ledger.require(operation.ticket());
    }
    static UUID capture(ServerLevel level, SubjectId owner, FrontierPayload fact) {
        if(!level.getServer().isSameThread())throw new IllegalStateException("native fact outside its server thread");
        var ledger=FrontierV3OperationLedger.get(level);var operation=record(ledger,owner,fact);ledger.persistAsync(level);
        io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info("PMV3_NATIVE_FACT_CAPTURED ticket={} owner={} type={} nativeTick={}",
                operation.ticket(),owner.value(),fact.type(),level.getGameTime());
        return operation.ticket();
    }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<?,?> runtime) {
        var ledger=FrontierV3OperationLedger.get(level);
        for(var operation:ledger.probe(FrontierV3OperationReceipt.Protocol.NATIVE_OBSERVATION,16)) {
            var command=ledger.ownerCommand(operation.ticket()).orElseThrow();
            if(!FrontierV3OperationConfirmation.submitRequired(level,runtime,operation.ticket(),command.consequence()))return;
        }
    }
    private FrontierV3ConfirmedNativeEvent(){}
}
