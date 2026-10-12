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
        writeRoute(out, receipt.orElseThrow().route());
    }
    static void writeRoute(DataOutputStream out, java.util.List<SurfaceAnchor> route) throws IOException {
        if (route.isEmpty() || route.size() > TimedKnownRoute.MAX_SURFACES)
            throw new IllegalArgumentException("invalid accepted route length");
        FrontierWorldStateCodec.writeCount(out, route.size());
        for (var surface : route) FrontierWorldStateCodec.writePosition(out, surface.support());
    }
    static Optional<PedestrianRouteReceipt> read(DataInputStream in) throws IOException {
        if (!in.readBoolean()) return Optional.empty();
        return Optional.of(new PedestrianRouteReceipt(readRoute(in)));
    }
    static java.util.List<SurfaceAnchor> readRoute(DataInputStream in) throws IOException {
        int length = FrontierWorldStateCodec.readCount(in);
        if (length < 1 || length > TimedKnownRoute.MAX_SURFACES) throw new IllegalArgumentException("invalid accepted navigation leg length");
        var route = new ArrayList<SurfaceAnchor>(length);
        for (int i = 0; i < length; i++) route.add(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)));
        return java.util.List.copyOf(route);
    }
}
