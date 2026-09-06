package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.ProductionWorkProgress;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Current-schema binary boundary for the durable workshop work cycle. */
final class ProductionWorkProgressStateCodec {
    private ProductionWorkProgressStateCodec() { }

    static void write(DataOutputStream output, ProductionWorkProgress progress) throws IOException {
        output.writeByte(progress.stage().wireTag()); output.writeByte(progress.completedTicks());
    }

    static ProductionWorkProgress read(DataInputStream input) throws IOException {
        return new ProductionWorkProgress(ProductionWorkProgress.Stage.fromWireTag(input.readUnsignedByte()), input.readUnsignedByte());
    }
}
