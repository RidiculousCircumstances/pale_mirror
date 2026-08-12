package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.List;

/** Resolves one bounded deterministic mine-candidate list to a verified physical anchor. */
@FunctionalInterface
public interface MineAnchorResolver {
    VisualPoint resolve(List<VisualPoint> candidates);
}
