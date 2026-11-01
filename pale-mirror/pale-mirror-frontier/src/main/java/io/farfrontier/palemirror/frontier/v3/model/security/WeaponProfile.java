package io.farfrontier.palemirror.frontier.v3.model.security;
import java.util.*;
/** Native-resolved immutable combat input; item identity/condition remain inventory-owned. */
public record WeaponProfile(String itemKind, double damage, long attackIntervalTicks, double reach, int wear) {
    public WeaponProfile { Objects.requireNonNull(itemKind);
        if (!Double.isFinite(damage) || damage<=0 || damage>1024 || attackIntervalTicks<1 || attackIntervalTicks>12000
                || !Double.isFinite(reach) || reach<=0 || reach>16 || wear<0 || wear>64)
            throw new IllegalArgumentException("unsupported combat weapon properties"); }
}
