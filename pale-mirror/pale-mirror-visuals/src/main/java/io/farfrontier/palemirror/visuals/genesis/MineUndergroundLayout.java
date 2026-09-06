package io.farfrontier.palemirror.visuals.genesis;

import java.util.List;

/** Compact reusable grammar for a branched authored mine below its mountain portal. */
final class MineUndergroundLayout {
    static final Node PORTAL = new Node(0, 0, 0);
    static final Node ADIT = new Node(0, 8, -3);
    static final Node JUNCTION = new Node(2, 18, -7);
    static final Node GALLERY = new Node(10, 27, -10);
    static final Node CONTROLLER = new Node(-8, 34, -12);

    private MineUndergroundLayout() { }

    static List<List<Node>> corridors() {
        return List.of(
                List.of(PORTAL, new Node(0, 6, -2), new Node(0, 13, -5), JUNCTION),
                List.of(JUNCTION, new Node(6, 23, -9), GALLERY),
                List.of(JUNCTION, new Node(-4, 26, -9), CONTROLLER));
    }

    record Node(int right, int inward, int up) { }
}
