package io.farfrontier.palemirror.frontier.reference;

/** Later V2 civic authority may cap a settlement's requested food issue. */
@FunctionalInterface
public interface ReferenceRationAuthority {
    double issue(ReferenceMarketWorld world, int settlementId, double requestedFood);
}
