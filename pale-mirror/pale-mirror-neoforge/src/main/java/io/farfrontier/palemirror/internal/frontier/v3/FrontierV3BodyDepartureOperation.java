package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyUnloaded;
import net.minecraft.server.level.ServerLevel;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Reuses the body owner's exact native saved-pose/residence/read fence. No new
 * unload, entity removal, marker backfill, UUID-absence inference or body scan. */
final class FrontierV3BodyDepartureOperation {
    private FrontierV3BodyDepartureOperation() { }
    static void confirm(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierV3ActorBodyDeparture receipt, ActorBodyUnloaded consequence, BooleanSupplier currentSavedEvidence) {
        var world = runtime.decodedState().orElseThrow().bootstrap().worldId();
        var image = fingerprint(receipt);
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITY_SNAPSHOT,
                receipt.identity().entityId() + "/epoch:" + receipt.identity().epoch() + "/image:" + image,
                receipt.residenceGeneration());
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.BODY_DEPARTURE,
                receipt.identity().actorId(), world.value() + "/epoch:" + receipt.identity().epoch()
                        + "/residence:" + receipt.residenceGeneration(), image, Set.of(participant));
        var operation = FrontierV3DurableNativeObservation.record(level, declaration, currentSavedEvidence).orElse(null);
        if (operation == null) return;
        FrontierV3OperationConfirmation.submitRecovery(level, runtime, operation.ticket(), consequence, java.util.Optional.empty());
    }
    private static String fingerprint(FrontierV3ActorBodyDeparture receipt) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(FrontierV3CarrierJournalImage.encode(receipt.save()))); }
        catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
}
