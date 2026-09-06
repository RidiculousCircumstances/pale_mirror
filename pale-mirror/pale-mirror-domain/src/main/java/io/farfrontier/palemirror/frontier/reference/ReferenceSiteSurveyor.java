package io.farfrontier.palemirror.frontier.reference;

/**
 * Source-world authority for selecting and measuring a newly surveyed site.
 *
 * <p>It is deliberately an explicit world dependency: market expansion must
 * not invent an averaged settlement potential while ecology/grid ownership is
 * still being ported.</p>
 */
public interface ReferenceSiteSurveyor {
    ReferenceSitePosition place(ReferenceSettlement host, ReferenceSiteKind kind);

    double quality(ReferenceSiteKind kind, int x, int y);

    record ReferenceSitePosition(int x, int y) {
        public ReferenceSitePosition {
            // Coordinates are arbitrary signed simulation cells.
        }
    }

    static ReferenceSiteSurveyor unavailable() {
        return new ReferenceSiteSurveyor() {
            @Override
            public ReferenceSitePosition place(ReferenceSettlement host, ReferenceSiteKind kind) {
                throw new IllegalStateException("site placement is unavailable until ecology/grid ownership is configured");
            }

            @Override
            public double quality(ReferenceSiteKind kind, int x, int y) {
                throw new IllegalStateException("site quality is unavailable until ecology/grid ownership is configured");
            }
        };
    }
}
