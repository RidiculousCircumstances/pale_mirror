package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.api.FoundryAuditPhase;
import io.farfrontier.palemirror.api.FoundryAuditReport;
import io.farfrontier.palemirror.api.FoundryFinding;
import io.farfrontier.palemirror.api.FoundrySeverity;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Bounded read-only v3 diagnostic summary for the traversal Foundry phases. */
final class FrontierV3TraversalFoundryDiagnostic {
    private static final String VIEW = "traversal_foundry";

    private FrontierV3TraversalFoundryDiagnostic() { }

    static String render(CheckpointImage checkpoint, FrontierWorldState state, ServerLevel level, String requestedPhase) {
        Objects.requireNonNull(checkpoint, "Foundry diagnostic checkpoint");
        Objects.requireNonNull(state, "Foundry diagnostic state");
        Objects.requireNonNull(level, "Foundry diagnostic level");
        Requested requested;
        try {
            requested = Requested.parse(requestedPhase);
        } catch (IllegalArgumentException invalid) {
            return FrontierV3DiagnosticJson.bounded(VIEW, requestedPhase, checkpoint,
                    "{\"schema\":1,\"kind\":\"traversal_foundry\",\"id\":\"" + quote(requestedPhase)
                            + "\",\"status\":\"unavailable\",\"reason\":\"unknown_phase\"}");
        }
        FoundryAuditPhase phase = requested.phase();
        if (phase != FoundryAuditPhase.COMPILED && phase != FoundryAuditPhase.SETTLED && phase != FoundryAuditPhase.RELOADED) {
            return FrontierV3DiagnosticJson.bounded(VIEW, requestedPhase, checkpoint,
                    "{\"schema\":1,\"kind\":\"traversal_foundry\",\"id\":\"" + quote(requestedPhase)
                            + "\",\"status\":\"unavailable\",\"reason\":\"unsupported_phase\"}");
        }
        if (phase == FoundryAuditPhase.COMPILED && requested.facilityScope().isPresent()) {
            return FrontierV3DiagnosticJson.bounded(VIEW, requestedPhase, checkpoint,
                    "{\"schema\":1,\"kind\":\"traversal_foundry\",\"id\":\"" + quote(requestedPhase)
                            + "\",\"status\":\"unavailable\",\"reason\":\"scope_requires_runtime\"}");
        }
        FoundryAuditReport report;
        try {
            report = phase == FoundryAuditPhase.COMPILED
                    ? FrontierV3TraversalFoundryAudit.auditCompiled(state)
                    : FrontierV3TraversalFoundryAudit.audit(state, level, phase, requested.facilityScope());
        } catch (IllegalArgumentException invalidScope) {
            return FrontierV3DiagnosticJson.bounded(VIEW, requestedPhase, checkpoint,
                    "{\"schema\":1,\"kind\":\"traversal_foundry\",\"id\":\"" + quote(requestedPhase)
                            + "\",\"status\":\"unavailable\",\"reason\":\"unknown_scope\"}");
        }
        double runtimePending = metric(report, "frontier.traversal.runtime_pending");
        double runtimeUnverified = metric(report, "frontier.traversal.runtime_unverified");
        boolean proven = report.passed() && (phase == FoundryAuditPhase.COMPILED || (runtimePending == 0D && runtimeUnverified == 0D));
        return FrontierV3DiagnosticJson.bounded(VIEW, requestedPhase, checkpoint,
                "{\"schema\":1,\"kind\":\"traversal_foundry\",\"id\":\"" + quote(requestedPhase)
                        + "\",\"status\":\"ok\",\"phase\":\"" + report.phase()
                        + "\",\"passed\":" + proven + ",\"auditPassed\":" + report.passed()
                        + ",\"scope\":\"" + quote(requested.scopeText()) + "\",\"blockers\":" + report.count(io.farfrontier.palemirror.api.FoundrySeverity.BLOCKER)
                        + ",\"errors\":" + report.count(io.farfrontier.palemirror.api.FoundrySeverity.ERROR)
                        + ",\"warnings\":" + report.count(io.farfrontier.palemirror.api.FoundrySeverity.WARNING)
                        + ",\"topologies\":" + metric(report, "frontier.traversal.topologies")
                        + ",\"ports\":" + metric(report, "frontier.port.count")
                        + ",\"runtimeChecked\":" + metric(report, "frontier.traversal.runtime_checked")
                        + ",\"runtimeUnverified\":" + runtimeUnverified
                        + ",\"runtimePending\":" + runtimePending
                        + ",\"runtimeMismatch\":" + metric(report, "frontier.traversal.runtime_mismatch")
                        + ",\"blockedThroats\":" + metric(report, "frontier.port.runtime_blocked")
                        + ",\"firstFailure\":" + firstFailure(report) + "}");
    }

    private static double metric(FoundryAuditReport report, String id) {
        return report.metrics().stream().filter(value -> value.id().equals(id)).findFirst().map(value -> value.value()).orElse(0D);
    }

    /** One locatable sample keeps the diagnostic bounded while avoiding opaque error counters. */
    private static String firstFailure(FoundryAuditReport report) {
        return report.findings().stream()
                .filter(finding -> finding.severity() == FoundrySeverity.BLOCKER || finding.severity() == FoundrySeverity.ERROR)
                .findFirst().map(FrontierV3TraversalFoundryDiagnostic::failureJson).orElse("null");
    }

    private static String failureJson(FoundryFinding finding) {
        return "{\"rule\":\"" + quote(finding.ruleId()) + "\",\"target\":\"" + quote(finding.targetId())
                + "\",\"position\":{\"x\":" + finding.position().x() + ",\"y\":" + finding.position().y()
                + ",\"z\":" + finding.position().z() + "},\"message\":\"" + quote(finding.message()) + "\"}";
    }

    /** `settled@structure:1-infirmary` is one read-only naturally visited facility scope. */
    private record Requested(FoundryAuditPhase phase, Optional<SubjectId> facilityScope, String scopeText) {
        static Requested parse(String requested) {
            int separator = requested.indexOf('@');
            String phaseText = (separator < 0 ? requested : requested.substring(0, separator)).trim();
            String scope = separator < 0 ? "world" : requested.substring(separator + 1).trim();
            if (phaseText.isEmpty() || scope.isEmpty() || scope.contains("@")) throw new IllegalArgumentException("invalid traversal Foundry request");
            FoundryAuditPhase phase = FoundryAuditPhase.valueOf(phaseText.toUpperCase(Locale.ROOT));
            return "world".equals(scope) ? new Requested(phase, Optional.empty(), scope)
                    : new Requested(phase, Optional.of(new SubjectId(scope)), scope);
        }
    }

    private static String quote(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
