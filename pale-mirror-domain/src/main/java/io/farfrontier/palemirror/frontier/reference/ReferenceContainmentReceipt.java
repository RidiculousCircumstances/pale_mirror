package io.farfrontier.palemirror.frontier.reference;

/** Reporting-only record of one local anti-tissue containment action. */
public record ReferenceContainmentReceipt(
        int day,
        int settlementId,
        int x,
        int y,
        double radius,
        double strength,
        double ammo,
        double removed
) { }
