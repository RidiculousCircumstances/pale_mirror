package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Durable physical boundary for one exact hive group leaving cocoon custody.
 *
 * <p>{@link #RELEASING} is durable before any owned cocoon block is removed. A restart in
 * that state is deliberately not interpreted as success: the loaded-world executor must
 * classify the physical result before it may move a body or admit an ambient lease.</p>
 */
public enum HiveMobilizationStatus {
    /** Exact dormant occupants were selected by one current hive task. */
    WAKING,
    /** The durable before-effect boundary has accepted cocoon opening. */
    RELEASING,
    /** Every selected member has an observed opened cocoon and is ready for local assembly. */
    ASSEMBLING,
    /** The retained complete group has atomically transferred to its named operation. */
    DEPARTED,
    /** A resolved child retains each living member's bounded homeward cursor. */
    RETURNING,
    /** The same parent has received its exact child operation's terminal outcome. */
    COMPLETED,
    /** A loaded-world pre/postcondition disagreed; no body is invented or moved. */
    CONFLICT;

    public int wireTag() { return FrontierWireTags.tag(this); }

    /** Departure is a child hand-off, not parent completion. */
    public boolean terminal() { return this == COMPLETED || this == CONFLICT; }
}
