package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianPathProof;
import java.io.*;
import java.util.ArrayList;

/** Whole-path proof has its own grammar; a timed movement leg retains its smaller independent bound. */
final class PedestrianPathProofCodec {
    private PedestrianPathProofCodec() { }
    static void write(DataOutputStream out, PedestrianPathProof proof) throws IOException {
        FrontierWorldStateCodec.writePosition(out, proof.origin().support());
        out.writeInt(proof.runs().size());
        for (var run : proof.runs()) {
            out.writeByte(run.dx()); out.writeByte(run.dy()); out.writeByte(run.dz()); out.writeInt(run.edges());
        }
    }
    static PedestrianPathProof read(DataInputStream in) throws IOException {
        var origin = new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in));
        int count = in.readInt();
        if (count < 0 || count > PedestrianPathProof.MAX_EDGES) throw new IllegalArgumentException("invalid itinerary run count");
        var runs = new ArrayList<PedestrianPathProof.Run>(count);
        for (int i = 0; i < count; i++) runs.add(new PedestrianPathProof.Run(in.readByte(), in.readByte(), in.readByte(), in.readInt()));
        return new PedestrianPathProof(origin, runs);
    }
}
