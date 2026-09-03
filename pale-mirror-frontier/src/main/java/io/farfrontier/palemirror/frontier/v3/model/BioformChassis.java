package io.farfrontier.palemirror.frontier.v3.model;

/**
 * The physical body plan of one exact hive organism.
 *
 * <p>Chassis is capability, never a task assignment. New chassis kinds may be
 * added only with an explicit stable wire tag and an owning physiology process.</p>
 */
public enum BioformChassis {
    RUNT,
    OVERSEER,
    SENTINEL;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
