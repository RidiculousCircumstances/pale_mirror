package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.BakeryLabourChange;
import java.io.*;
import java.util.Optional;

final class BakeryLabourChangeCodec {
    private BakeryLabourChangeCodec() { }
    static void write(DataOutputStream out, BakeryLabourChange value) throws IOException {
        WorkStateCodec.writeProgress(out, value.previous());
        WorkStateCodec.writeProgress(out, Optional.of(value.next()));
    }
    static BakeryLabourChange read(DataInputStream in) throws IOException {
        return new BakeryLabourChange(WorkStateCodec.readProgress(in), WorkStateCodec.readProgress(in).orElseThrow());
    }
}
