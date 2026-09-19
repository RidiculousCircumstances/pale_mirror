package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticRuntimeIdentity;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.UUID;

/**
 * NeoForge's explicit provenance owner for the diagnostic capture boundary.
 *
 * <p>The build declares source provenance, this host observes its own packaged artifact, and a
 * fresh server session receives a host-created restart identity.  The canonical reducer receives
 * only the resulting immutable value through {@link FrontierV3ServerRuntime}; it neither probes
 * the classpath nor invents unavailable provenance.</p>
 */
final class FrontierV3DiagnosticRuntimeIdentity {
    private static final String UNAVAILABLE = "unavailable";
    private FrontierV3DiagnosticRuntimeIdentity() { }

    static DiagnosticRuntimeIdentity openServerSession() {
        return new DiagnosticRuntimeIdentity("neoforge", sourceTree(), packagedJar(), "server-session:" + UUID.randomUUID());
    }

    private static String sourceTree() {
        Properties properties = new Properties();
        try (InputStream input = FrontierV3DiagnosticRuntimeIdentity.class.getResourceAsStream("/META-INF/pale-mirror-runtime-identity.properties")) {
            if (input == null) return UNAVAILABLE;
            properties.load(input);
            String declared = properties.getProperty("sourceTree", UNAVAILABLE).trim();
            return declared.isEmpty() ? UNAVAILABLE : declared;
        } catch (Exception ignored) {
            return UNAVAILABLE;
        }
    }

    private static String packagedJar() {
        try {
            URI location = PaleMirrorMod.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path artifact = Path.of(location);
            if (!Files.isRegularFile(artifact)) return UNAVAILABLE;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(artifact)) {
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) >= 0;) digest.update(buffer, 0, count);
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (Exception ignored) {
            return UNAVAILABLE;
        }
    }
}
