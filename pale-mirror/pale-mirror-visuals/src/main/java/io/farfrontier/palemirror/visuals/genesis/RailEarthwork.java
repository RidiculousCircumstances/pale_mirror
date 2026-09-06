package io.farfrontier.palemirror.visuals.genesis;

/** Pure current-column classification; it never inspects or resolves neighbouring chunks. */
enum RailEarthwork {
    CUT, GROUND, BRIDGE;

    static RailEarthwork resolve(int firstAirY, int railY) {
        if (firstAirY > railY) return CUT;
        return railY - firstAirY > 3 ? BRIDGE : GROUND;
    }
}
