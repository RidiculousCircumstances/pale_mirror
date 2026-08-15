package io.farfrontier.palemirror.visuals.foundry;

import io.farfrontier.palemirror.api.FoundryAuditReport;
import io.farfrontier.palemirror.api.FoundryFinding;
import io.farfrontier.palemirror.api.FoundrySeverity;
import io.farfrontier.palemirror.api.VisualAuditView;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.ArrayList;
import java.util.List;

/** Converts severe findings into repeatable close-range screenshot poses. */
public final class FoundryAuditCameras {
    private static final int MAX_CAMERAS = 24;

    private FoundryAuditCameras() { }

    public static List<VisualAuditView> views(FoundryAuditReport report) {
        List<VisualAuditView> result = new ArrayList<>();
        int ordinal = 0;
        for (FoundryFinding finding : report.findings()) {
            if (finding.severity() != FoundrySeverity.BLOCKER && finding.severity() != FoundrySeverity.ERROR) continue;
            VisualPoint focus = finding.position();
            VisualPoint camera = new VisualPoint(focus.x() + 9, focus.y() + 6, focus.z() + 9);
            double horizontal = Math.sqrt(9D * 9D + 9D * 9D);
            float yaw = 135F;
            float pitch = (float) Math.toDegrees(Math.atan2(camera.y() + 1.62D - focus.y(), horizontal));
            String id = "foundry/" + finding.ruleId().replace('.', '_') + "/" + ordinal++;
            result.add(new VisualAuditView(id, finding.targetKind(), finding.targetId(), finding.dimensionId(),
                    camera, yaw, pitch));
            if (result.size() == MAX_CAMERAS) break;
        }
        return List.copyOf(result);
    }
}
