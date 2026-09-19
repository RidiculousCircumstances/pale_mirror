package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    void everyCurrentTerminalOrExpectedAbsentPayloadIsMechanicallyAdmittedWithoutACuratedSubset() throws Exception {
        Path root = repositoryRoot();
        List<Path> sources;
        try (var paths = Files.walk(root)) {
            sources = paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> !path.getFileName().toString().equals("DiagnosticIncidentExtractor.java"))
                    .filter(path -> !path.getFileName().toString().equals("DiagnosticReason.java"))
                    .toList();
        }
        String extractor = Files.readString(root.resolve("pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/model/DiagnosticIncidentExtractor.java"));
        Set<String> admittedPayloadOwners = new LinkedHashSet<>();
        Set<DiagnosticReason> actualReasons = new LinkedHashSet<>();
        Pattern reason = Pattern.compile("DiagnosticReason\\.([A-Z_]+)");
        Pattern terminalStatus = Pattern.compile("\\b(CONFLICT|UNKNOWN_AFTER_RESTART|QUARANTINED|FAILED)\\b");
        for (Path source : sources) {
            String text = Files.readString(source);
            // The actual source tree, not a manually maintained family list, defines this census.
            // A terminal/non-progress-shaped payload has to be visible even when a future
            // author forgot both its tuple and a reason reference. Traversal observations are
            // inputs; their owning planner emits the later named outcome.
            String owner = source.getFileName().toString().replace(".java", "");
            if (text.contains("implements FrontierPayload") && (namedDiagnosticOutcome(owner) || terminalStatus.matcher(text).find())) {
                admittedPayloadOwners.add(owner);
            }
            Matcher occurrences = reason.matcher(text);
            while (occurrences.find()) actualReasons.add(DiagnosticReason.valueOf(occurrences.group(1)));
        }
        assertFalse(admittedPayloadOwners.isEmpty(), "source census must observe real terminal/non-progress producers");
        for (String owner : admittedPayloadOwners) {
            assertTrue(extractor.contains("case " + owner + " ") || extractor.contains("case " + owner + "."),
                    () -> "terminal/non-progress payload is absent from the retained extractor: " + owner);
        }
        assertTrue(DiagnosticIncidentExtractor.retainedReasons().containsAll(actualReasons),
                () -> "a currently referenced producer reason is absent from the retention registry: " + actualReasons);
        assertFalse(DiagnosticIncident.terminal(DiagnosticCategory.WAIT_OR_BLOCKED),
                "expected-absent wait/block outcomes cannot be promoted into terminal review truth");
        assertFalse(DiagnosticIncident.terminal(DiagnosticCategory.DOMAIN_DISRUPTION),
                "expected-absent domain disruption cannot be promoted into terminal review truth");

        assertTrue(namedDiagnosticOutcome("TuplelessConflictObserved"), "the source census must identify an omitted conflict before it can hide in a registry gap");
        assertTrue(namedDiagnosticOutcome("TuplelessSelectionBlocked"), "the source census must identify an omitted expected-absent outcome before it can hide in a registry gap");
        assertTrue(terminalStatus.matcher("status == UNKNOWN_AFTER_RESTART").find(), "a terminal state field cannot evade the census behind a neutral payload name");
        assertFalse(namedDiagnosticOutcome("TuplelessTraversalBlocked"), "an observed traversal input is not itself an outcome; its owner must emit the later typed result");
    }

    @Test
    void terminalProducerBoundaryFailsClosedForAnOmittedTuplelessPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> DiagnosticProducerBoundary.requireAdmitted(new TuplelessConflictObserved()),
                "a new terminal-shaped payload cannot become canonical truth merely by omitting both a reason and extractor case");
    }

    private record TuplelessConflictObserved() implements io.farfrontier.palemirror.frontier.v3.api.FrontierPayload {
        @Override public String type() { return "frontier.test_tupleless_conflict"; }
    }

    private static boolean namedDiagnosticOutcome(String sourceOwner) {
        return !sourceOwner.endsWith("TraversalBlocked") && (sourceOwner.endsWith("Blocked") || sourceOwner.endsWith("Failed")
                || sourceOwner.endsWith("Conflicted") || sourceOwner.endsWith("ConflictObserved")
                || sourceOwner.endsWith("RecoveryUnresolved") || sourceOwner.endsWith("QuarantineObserved"));
    }

    private static Path repositoryRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("pale-mirror-frontier"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("repository root is unavailable");
        return path;
    }
}
