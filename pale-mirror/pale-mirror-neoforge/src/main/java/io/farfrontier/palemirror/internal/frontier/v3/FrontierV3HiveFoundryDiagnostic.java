package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.api.FoundryAuditPhase;
import io.farfrontier.palemirror.api.FoundryAuditReport;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;
import java.util.Objects;

/** Bounded read-only Foundry diagnostic for one named hive organ. */
final class FrontierV3HiveFoundryDiagnostic {
    private static final String VIEW = "hive_foundry";

    private FrontierV3HiveFoundryDiagnostic() { }

    static String render(CheckpointImage checkpoint, FrontierWorldState state, ServerLevel level, String requested) {
        Objects.requireNonNull(checkpoint, "hive Foundry checkpoint");
        Objects.requireNonNull(state, "hive Foundry state");
        Objects.requireNonNull(level, "hive Foundry level");
        Requested request;
        try {
            request = Requested.parse(requested);
        } catch (IllegalArgumentException invalid) {
            return unavailable(checkpoint, requested, "invalid_request");
        }
        if (request.phase() != FoundryAuditPhase.COMPILED && request.phase() != FoundryAuditPhase.SETTLED && request.phase() != FoundryAuditPhase.RELOADED) {
            return unavailable(checkpoint, requested, "unsupported_phase");
        }
        FoundryAuditReport report;
        try {
            report = request.phase() == FoundryAuditPhase.COMPILED
                    ? FrontierV3HiveFoundryAudit.auditCompiled(state, request.organId())
                    : FrontierV3HiveFoundryAudit.audit(state, request.organId(), level, request.phase());
        } catch (IllegalArgumentException unknownOrgan) {
            return unavailable(checkpoint, requested, "unknown_organ");
        }
        double pending = metric(report, "frontier.hive.organ.runtime_pending");
        double unverified = metric(report, "frontier.hive.organ.runtime_unverified");
        boolean proven = report.passed() && (request.phase() == FoundryAuditPhase.COMPILED || (pending == 0D && unverified == 0D));
        return FrontierV3DiagnosticJson.bounded(VIEW, requested, checkpoint,
                "{\"schema\":1,\"kind\":\"hive_foundry\",\"id\":\"" + quote(requested)
                        + "\",\"status\":\"ok\",\"phase\":\"" + report.phase() + "\",\"passed\":" + proven
                        + ",\"auditPassed\":" + report.passed() + ",\"organ\":\"" + quote(request.organId().value())
                        + "\",\"cells\":" + metric(report, "frontier.hive.organ.cells")
                        + ",\"hiverootCells\":" + metric(report, "frontier.hive.hiveroot.cells")
                        + ",\"runtimeCurrent\":" + metric(report, "frontier.hive.organ.runtime_current")
                        + ",\"runtimePending\":" + pending + ",\"runtimeMismatch\":" + metric(report, "frontier.hive.organ.runtime_mismatch")
                        + ",\"runtimeUnverified\":" + unverified + ",\"blockers\":" + report.count(io.farfrontier.palemirror.api.FoundrySeverity.BLOCKER)
                        + ",\"errors\":" + report.count(io.farfrontier.palemirror.api.FoundrySeverity.ERROR) + "}");
    }

    private static double metric(FoundryAuditReport report, String id) {
        return report.metrics().stream().filter(metric -> metric.id().equals(id)).findFirst().map(metric -> metric.value()).orElse(0D);
    }

    private static String unavailable(CheckpointImage checkpoint, String requested, String reason) {
        return FrontierV3DiagnosticJson.bounded(VIEW, requested, checkpoint,
                "{\"schema\":1,\"kind\":\"hive_foundry\",\"id\":\"" + quote(requested)
                        + "\",\"status\":\"unavailable\",\"reason\":\"" + reason + "\"}");
    }

    private record Requested(FoundryAuditPhase phase, SubjectId organId) {
        static Requested parse(String text) {
            int separator = Objects.requireNonNull(text, "hive Foundry request").indexOf('@');
            if (separator <= 0 || separator != text.lastIndexOf('@')) throw new IllegalArgumentException("hive Foundry request needs phase@organ");
            String phase = text.substring(0, separator).trim();
            String organ = text.substring(separator + 1).trim();
            if (phase.isEmpty() || organ.isEmpty()) throw new IllegalArgumentException("hive Foundry request is blank");
            return new Requested(FoundryAuditPhase.valueOf(phase.toUpperCase(Locale.ROOT)), new SubjectId(organ));
        }
    }

    private static String quote(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
