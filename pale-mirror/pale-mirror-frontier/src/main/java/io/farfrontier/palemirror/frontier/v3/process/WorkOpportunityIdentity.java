package io.farfrontier.palemirror.frontier.v3.process;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Bounded deterministic correlation spelling, never discovery of an owner or work kind. */
final class WorkOpportunityIdentity {
    private WorkOpportunityIdentity() { }
    static String digest(String cause) {
        try {
            // Reserve room for task/job/schedule and scheduler-cause envelopes. A full
            // 64-character digest fits the first ID but can overflow a downstream ID.
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(cause.getBytes(StandardCharsets.UTF_8)), 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
