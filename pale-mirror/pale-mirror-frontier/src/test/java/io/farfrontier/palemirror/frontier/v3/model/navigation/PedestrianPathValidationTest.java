package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianPathValidationTest {
    @Test void certifiedRunsMatchFullReplayAndWithdrawTheirProofForLocalChanges() {
        var reads = new AtomicInteger(); var revision = PedestrianGeometryRevision.initial();
        var path = PedestrianPathProof.from(java.util.stream.IntStream.rangeClosed(-31, 48)
                .mapToObj(x -> SurfaceAnchor.at(x, 0, 0)).toList());
        var clear = certified(revision, false, reads);
        assertTrue(PedestrianPathValidation.supports(clear, path));
        assertEquals(0, reads.get(), "explicit horizontal-area proof supplies every support and directed edge");
        assertTrue(ImmutableValidationScope.full(() -> PedestrianPathValidation.supports(clear, path)));
        assertTrue(reads.get() > 0, "the independent full checker still surveys the actual path");
        var changed = revision.changed(java.util.Set.of(new PedestrianGeometryRevision.Region(1, 0)));
        var blocked = certified(changed, true, reads);
        assertFalse(PedestrianPathValidation.supports(blocked, path), "a withdrawn interior certificate triggers exact obstacle checks");
        assertFalse(ImmutableValidationScope.full(() -> PedestrianPathValidation.supports(blocked, path)));
        var foreignHeight = PedestrianPathProof.from(List.of(SurfaceAnchor.at(0, 1, 0), SurfaceAnchor.at(1, 1, 0)));
        assertFalse(PedestrianPathValidation.supports(clear, foreignHeight), "a certificate for one level grants no support above it");
        var outside = PedestrianPathProof.from(List.of(SurfaceAnchor.at(63, 0, 0), SurfaceAnchor.at(64, 0, 0)));
        assertFalse(PedestrianPathValidation.supports(clear, outside), "even a permissive area producer cannot extend world bounds");
    }
    @Test void slopedAndDirectedUncertifiedEdgesRetainExactPermissionChecks() {
        var reads = new AtomicInteger();
        PedestrianRouteGeometry slope = new PedestrianRouteGeometry() {
            public WorldBounds bounds() { return new WorldBounds(0, 0, 8, 8); }
            public SurfaceAnchor supportAt(int x, int z) { return SurfaceAnchor.at(x, x, z); }
            public boolean blocked(SurfaceAnchor surface) { return false; }
            public java.util.OptionalInt uniformSupport(WorldBounds area) { fail("a sloped run cannot request a horizontal-plane proof"); return java.util.OptionalInt.empty(); }
            public boolean canTraverse(SurfaceAnchor from, SurfaceAnchor to) { reads.incrementAndGet(); return false; }
        };
        var path = PedestrianPathProof.from(List.of(SurfaceAnchor.at(0, 0, 0), SurfaceAnchor.at(1, 1, 0)));
        assertFalse(PedestrianPathValidation.supports(slope, path)); assertTrue(reads.get() > 0);
        assertFalse(PedestrianPathValidation.supportsRoute(geometry(new Object(), false, reads), List.of()), "empty hints grant no path");
    }
    private static PedestrianRouteGeometry certified(PedestrianGeometryRevision revision, boolean blocked, AtomicInteger reads) {
        return new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public java.util.Optional<PedestrianGeometryRevision> regionalRevision() { return java.util.Optional.of(revision); }
            public WorldBounds bounds() { return new WorldBounds(-64, -4, 128, 8); }
            public SurfaceAnchor supportAt(int x, int z) { reads.incrementAndGet(); return SurfaceAnchor.at(x, 0, z); }
            public boolean blocked(SurfaceAnchor surface) { reads.incrementAndGet(); return blocked && surface.x() == 17; }
            public java.util.OptionalInt uniformSupport(WorldBounds area) {
                return blocked && area.minX() <= 17 && area.maxXExclusive() > 17
                        ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(0);
            }
        };
    }
    @Test void exactVersionReusesValidationButChangedGeometryAndDifferentPathDoNot() {
        var reads = new AtomicInteger(); var version = new Object();
        var path = PedestrianPathProof.from(List.of(SurfaceAnchor.at(0, 0, 0), SurfaceAnchor.at(1, 0, 0)));
        var clear = geometry(version, false, reads);
        PedestrianPathValidation.require(clear, path);
        int initial = reads.get(); assertTrue(initial > 0);
        PedestrianPathValidation.require(geometry(version, false, reads), path);
        assertEquals(initial, reads.get(), "recreated immutable view shares the exact version, not new traversal work");
        KnownPedestrianNavigation.requireRoute(clear, path.surfaces());
        assertEquals(initial, reads.get(), "cell-list replay uses the same exact proof instead of resurveying its edges");
        ImmutableValidationScope.full(() -> { KnownPedestrianNavigation.requireRoute(clear, path.surfaces()); return null; });
        assertTrue(reads.get() > initial, "explicit full ingress audits still inspect every accepted route");
        assertThrows(IllegalArgumentException.class, () -> PedestrianPathValidation.require(geometry(new Object(), true, reads), path));
        assertTrue(reads.get() > initial);
        var unknown = PedestrianPathProof.from(List.of(SurfaceAnchor.at(0, 1, 0), SurfaceAnchor.at(1, 1, 0)));
        assertThrows(IllegalArgumentException.class, () -> PedestrianPathValidation.require(clear, unknown));
        assertFalse(PedestrianPathValidation.supportsRoute(clear, List.of(path.origin(), SurfaceAnchor.at(2, 0, 0))),
                "a disconnected advisory hint must not become an accepted path");
        assertThrows(IllegalArgumentException.class, () -> KnownPedestrianNavigation.requireRoute(clear,
                List.of(path.origin(), SurfaceAnchor.at(2, 0, 0))));
    }
    @Test void remoteChangeDoesNotResurveyAValidatedJourneyButPristineLocalAndForeignChangesDo() {
        var reads = new AtomicInteger(); var revision = PedestrianGeometryRevision.initial();
        var path = PedestrianPathProof.from(List.of(SurfaceAnchor.at(0, 0, 0), SurfaceAnchor.at(1, 0, 0)));
        PedestrianPathValidation.require(regional(revision, false, reads), path);
        int initial = reads.get(); assertTrue(initial > 0);
        var distant = revision.changed(java.util.Set.of(new PedestrianGeometryRevision.Region(100, 100)));
        PedestrianPathValidation.require(regional(distant, false, reads), path);
        assertEquals(initial, reads.get(), "unrelated world edits cannot rescan every edge of this journey");
        KnownPedestrianNavigation.requireRoute(regional(distant, false, reads), path.surfaces());
        assertEquals(initial, reads.get(), "cell-list replay retains the same regional footprint proof");
        var local = distant.changed(java.util.Set.of(new PedestrianGeometryRevision.Region(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> PedestrianPathValidation.require(regional(local, true, reads), path));
        assertTrue(reads.get() > initial, "a formerly pristine region is still an exact dependency");
        assertThrows(IllegalArgumentException.class, () -> PedestrianPathValidation.require(
                regional(PedestrianGeometryRevision.initial(), true, reads), path));
        PedestrianPathValidation.require(regional(revision, false, reads), path);
    }
    private static PedestrianRouteGeometry regional(PedestrianGeometryRevision revision, boolean blocked, AtomicInteger reads) {
        var base = geometry(revision, blocked, reads);
        return new PedestrianRouteGeometry() {
            public Object version() { return revision; }
            public java.util.Optional<PedestrianGeometryRevision> regionalRevision() { return java.util.Optional.of(revision); }
            public WorldBounds bounds() { return base.bounds(); }
            public SurfaceAnchor supportAt(int x, int z) { return base.supportAt(x, z); }
            public boolean blocked(SurfaceAnchor surface) { return base.blocked(surface); }
        };
    }
    private static PedestrianRouteGeometry geometry(Object version, boolean blocked, AtomicInteger reads) {
        return new PedestrianRouteGeometry() {
            public Object version() { return version; }
            public WorldBounds bounds() { return new WorldBounds(-2, -2, 8, 8); }
            public SurfaceAnchor supportAt(int x, int z) { reads.incrementAndGet(); return SurfaceAnchor.at(x, 0, z); }
            public boolean blocked(SurfaceAnchor surface) { reads.incrementAndGet(); return blocked && surface.x() == 1; }
        };
    }
}
