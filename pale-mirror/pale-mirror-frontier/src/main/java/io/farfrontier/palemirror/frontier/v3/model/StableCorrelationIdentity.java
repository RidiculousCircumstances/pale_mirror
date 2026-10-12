package io.farfrontier.palemirror.frontier.v3.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Bounded deterministic spelling of already declared facts; never discovers semantic ownership. */
public final class StableCorrelationIdentity {
    private StableCorrelationIdentity() { }
    public static String digest(String declaration) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(declaration.getBytes(StandardCharsets.UTF_8)), 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
