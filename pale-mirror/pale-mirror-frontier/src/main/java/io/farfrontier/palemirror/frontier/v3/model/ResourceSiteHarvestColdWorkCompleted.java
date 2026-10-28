package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Atomic canonical cell work; no external preparation or Minecraft effect is authorized. */
public record ResourceSiteHarvestColdWorkCompleted(ResourceSiteHarvestProgressed receipt) implements FrontierPayload {
    public ResourceSiteHarvestColdWorkCompleted {
        Objects.requireNonNull(receipt, "COLD field work receipt");
        if (receipt.observedHand().isPresent())
            throw new IllegalArgumentException("COLD field work cannot declare a physical hand observation");
    }
    @Override public String type() { return "frontier.resource_site_harvest_cold_work_completed"; }
}
