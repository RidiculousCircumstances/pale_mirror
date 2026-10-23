package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;

final class WorkEquipmentStepCodec {
    static void write(DataOutputStream out, WorkEquipmentStep step) throws IOException {
        ActorHotObservationCodec.write(out, step.observation()); ExactItemStateCodec.write(out, step.item());
        FrontierWorldStateCodec.writeCustody(out, step.slot()); out.writeLong(step.containerEpoch());
    }
    static WorkEquipmentStep read(DataInputStream in) throws IOException {
        var observation = ActorHotObservationCodec.read(in); var item = ExactItemStateCodec.read(in);
        var custody = FrontierWorldStateCodec.readCustody(in);
        if (!(custody instanceof InventoryCustody.ContainerSlot slot)) throw new IllegalArgumentException("equipment transfer lacks container slot");
        return new WorkEquipmentStep(observation, item, slot, in.readLong());
    }
    private WorkEquipmentStepCodec() { }
}
