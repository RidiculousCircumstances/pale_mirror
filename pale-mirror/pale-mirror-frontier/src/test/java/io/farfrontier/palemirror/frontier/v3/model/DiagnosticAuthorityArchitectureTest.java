package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prevents a convenience constructor or wire fallback from recreating diagnostic inference. */
class DiagnosticAuthorityArchitectureTest {
    @Test
    void resourceSiteProducerCannotFallBackFromReasonSourceOrIdentifierShape() throws Exception {
        Path model = repositoryRoot().resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/model");
        String observed = Files.readString(model.resolve("ResourceSiteConflictObserved.java"));
        String producer = Files.readString(model.resolve("ResourceSiteDiagnosticProducer.java"));
        String tuple = Files.readString(model.resolve("DiagnosticTuple.java"));
        String wires = Files.readString(model.resolve("DiagnosticWireTags.java"));

        assertTrue(observed.contains("ResourceSiteDiagnosticProducer producer"));
        assertFalse(observed.contains("ResourceSiteConflictObserved(SubjectId siteId, BlockPosition position, ResourceSiteConflictReason"),
                "an ownerless reason/source constructor would recreate tuple inference");
        assertFalse(producer.contains("require(ResourceSiteConflictReason"),
                "producer admission must not select a compatible declaration from reason/source values");
        assertFalse(producer.contains("stream("), "wire identity decoding must be direct, never candidate scanning");
        assertTrue(tuple.contains("reason.category() != category") && tuple.contains("reason.ownerKind() != owner.kind()")
                        && tuple.contains("reason.subjectKind() != subject.kind()") && tuple.contains("reason.disposition() != disposition"),
                "every tuple dimension is validated before it reaches a conflict incident");
        assertFalse(wires.contains("ordinal()"), "persistent diagnostic identities must never depend on enum order");
    }

    private static Path repositoryRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("pale-mirror-frontier"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("repository root is unavailable");
        return path;
    }
}
