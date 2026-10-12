package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.io.*;
import java.util.*;

/** Stable bounded grammar shared by source-front opening and new excavation columns. */
final class PedestrianAccessReceiptCodec {
    private PedestrianAccessReceiptCodec() { }
    static void write(DataOutputStream out, PedestrianAccessReceipt value) throws IOException {
        FrontierWorldStateCodec.writePosition(out, value.origin().support()); out.writeInt(value.routes().size());
        for (var entry : value.routes().entrySet().stream().sorted(Map.Entry.comparingByKey(
                Comparator.comparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::y).thenComparingInt(SurfaceAnchor::z))).toList()) {
            FrontierWorldStateCodec.writePosition(out, entry.getKey().support()); PedestrianRouteReceiptCodec.write(out, entry.getValue());
        }
    }
    static PedestrianAccessReceipt read(DataInputStream in) throws IOException {
        var origin = new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)); int count = in.readInt();
        if (count < 1 || count > PedestrianAccessReceipt.MAX_DESTINATIONS) throw new IllegalArgumentException("unbounded access evidence wire count");
        var routes = new LinkedHashMap<SurfaceAnchor, Optional<PedestrianRouteReceipt>>();
        for (int index = 0; index < count; index++) {
            var destination = new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in));
            if (routes.putIfAbsent(destination, PedestrianRouteReceiptCodec.read(in)) != null)
                throw new IllegalArgumentException("duplicate access-evidence destination");
        }
        return new PedestrianAccessReceipt(origin, routes);
    }
}
