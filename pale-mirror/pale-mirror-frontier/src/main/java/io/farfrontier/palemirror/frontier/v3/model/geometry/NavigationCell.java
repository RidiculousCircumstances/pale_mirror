package io.farfrontier.palemirror.frontier.v3.model.geometry;

import java.util.Objects;

/** Shape and pedestrian safety are independent: empty collision permits neither lava nor fire.
 * dry is the retained dry/safe pedestrian-medium verdict, not a shape inference. */
public record NavigationCell(NavigationShape shape, boolean dry, java.util.Optional<NavigationBlockState> observedState, java.util.Optional<NavigationShape> passageShape) {
    public static final NavigationCell AIR = new NavigationCell(NavigationShape.EMPTY, true);
    public static final NavigationCell SOLID = new NavigationCell(NavigationShape.FULL, true);
    public NavigationCell(NavigationShape shape, boolean dry) { this(shape, dry, java.util.Optional.empty()); }
    public NavigationCell(NavigationShape shape, boolean dry, java.util.Optional<NavigationBlockState> observedState) {
        this(shape, dry, observedState, java.util.Optional.empty());
    }
    public NavigationCell passageTransit() {
        return passageShape.map(open -> new NavigationCell(open, dry, observedState)).orElse(this);
    }
    public NavigationCell { Objects.requireNonNull(shape); Objects.requireNonNull(observedState); Objects.requireNonNull(passageShape); }
}
