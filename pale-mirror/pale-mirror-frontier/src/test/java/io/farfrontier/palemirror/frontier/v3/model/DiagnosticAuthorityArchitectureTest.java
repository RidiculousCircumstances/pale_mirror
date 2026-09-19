package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void inventoryAndRecoveryAuthoritiesCannotClassifyFromStatusOrConflictKind() throws Exception {
        Path root = repositoryRoot();
        String inventory = Files.readString(root.resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/model/InventoryConflict.java"));
        String storage = Files.readString(root.resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/model/PhysicalIntentTransitionStorage.java"));
        String capabilities = Files.readString(root.resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/process/PhysicalIntentLifecycleCapabilities.java"));
        String executor = Files.readString(root.resolve("pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3InventoryObservationExecutor.java"));
        String production = Files.readString(root.resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/process/ProductionProcess.java"));
        String patrol = Files.readString(root.resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/process/RoutePatrolProcess.java"));
        assertTrue(inventory.contains("DiagnosticTuple diagnostic") && inventory.contains("DiagnosticReason.INVENTORY_CONFLICT"));
        assertTrue(storage.contains("transition.diagnostic().orElseThrow"), "storage must reject a recovery status that was not already stamped");
        assertTrue(capabilities.contains("capability.recoveryUnknownDiagnostic(intent)"), "closed owner capability must supply recovery authority before reduction");
        assertFalse(executor.contains("kind.ordinal()"), "inventory diagnostic identity must not depend on enum order");
        assertFalse(executor.contains("new InventoryConflict("), "executor must use a named stamped inventory producer");
        assertTrue(production.contains("ProductionDiagnosticProducer."), "production non-progress must stamp a named producer rather than emit a bare local reason");
        assertFalse(production.contains("new ProductionBlocked("), "production reducers must use their named tuple producer");
        assertTrue(patrol.contains("RoutePatrolDiagnosticProducer."));
        assertFalse(patrol.contains("new RoutePatrolBlocked("), "patrol reducers must use their named tuple producer");
    }

    @Test
    void everyCurrentPayloadCodecHasOneExplicitNominalDiagnosticContract() {
        DiagnosticProducerContract.requireCompleteInventory(FrontierWorldPayloadCodecs.create());
        assertTrue(DiagnosticIncidentExtractor.retainedReasons().containsAll(EnumSet.allOf(DiagnosticReason.class)),
                "every declared nominal reason is retained by either a payload or explicit SavedData bridge");
        assertFalse(DiagnosticIncident.terminal(DiagnosticCategory.WAIT_OR_BLOCKED),
                "expected-absent wait/block outcomes cannot be promoted into terminal review truth");
        assertFalse(DiagnosticIncident.terminal(DiagnosticCategory.DOMAIN_DISRUPTION),
                "expected-absent domain disruption cannot be promoted into terminal review truth");

    }

    @Test
    void nominalContractCannotClassifyFromPayloadShapeOrNames() throws Exception {
        String contract = Files.readString(repositoryRoot().resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/model/DiagnosticProducerContract.java"));
        assertFalse(contract.contains("getClass") || contract.contains("getSimpleName") || contract.contains("Pattern.compile")
                        || contract.contains("endsWith(") || contract.contains("status()"),
                "producer applicability must come only from the closed nominal type inventory");
        assertTrue(contract.contains("declared.equals(codecs.types())"), "codec composition must reject an omitted nominal declaration before reduction");
    }

    @Test
    void nominalProducerContractFailsClosedForBothAnUnknownKindAndMissingStampedAuthority() {
        assertThrows(IllegalArgumentException.class,
                () -> DiagnosticProducerContract.requireAdmitted(new UnspecifiedPayload()),
                "a new codec kind cannot become canonical truth without an explicit nominal inventory declaration");
        assertThrows(IllegalArgumentException.class,
                () -> DiagnosticProducerContract.requireAdmitted(new UnstampedRegisteredPayload()),
                "a declared diagnostic kind cannot reduce without its producer-stamped authority");
    }

    private record UnspecifiedPayload() implements io.farfrontier.palemirror.frontier.v3.api.FrontierPayload {
        @Override public String type() { return "frontier.test_unlisted_payload"; }
    }
    private record UnstampedRegisteredPayload() implements io.farfrontier.palemirror.frontier.v3.api.FrontierPayload {
        @Override public String type() { return "frontier.route_patrol_failed"; }
    }

    private static Path repositoryRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("pale-mirror-frontier"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("repository root is unavailable");
        return path;
    }
}
