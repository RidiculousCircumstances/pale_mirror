package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/**
 * Exact physical ingress into an active Frontier container.
 *
 * <p>The physical stack is first given this stable identity by the loaded-chunk observer. The
 * canonical registration is then durable; an interrupted marked admission resumes from that
 * same physical stack rather than inventing another resource.</p>
 */
public record ResourceDeposited(ExactItemStack item) implements FrontierPayload {
    public ResourceDeposited {
        Objects.requireNonNull(item, "deposited item");
        if (!(item.custody() instanceof InventoryCustody.ContainerSlot)) {
            throw new IllegalArgumentException("a deposited resource must enter an exact container slot");
        }
    }

    @Override public String type() { return "frontier.resource_deposited"; }
}
