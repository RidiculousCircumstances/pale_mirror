package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TimedKnownRoute;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

/** One bounded wire shape shared by accepted meal and general movement legs. */
final class PedestrianRouteReceiptCodec {
    private PedestrianRouteReceiptCodec() { }
    static void write(DataOutputStream out, Optional<PedestrianRouteReceipt> receipt) throws IOException {
        out.writeBoolean(receipt.isPresent());
        if (receipt.isEmpty()) return;
        var route = receipt.orElseThrow().route();
        FrontierWorldStateCodec.writeCount(out, route.size());
        for (var surface : route) FrontierWorldStateCodec.writePosition(out, surface.support());
    }
    static Optional<PedestrianRouteReceipt> read(DataInputStream in) throws IOException {
        if (!in.readBoolean()) return Optional.empty();
        int length = FrontierWorldStateCodec.readCount(in);
        if (length < 2 || length > TimedKnownRoute.MAX_SURFACES) throw new IllegalArgumentException("invalid accepted navigation leg length");
        var route = new ArrayList<SurfaceAnchor>(length);
        for (int i = 0; i < length; i++) route.add(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)));
        return Optional.of(new PedestrianRouteReceipt(route));
    }
}
