package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.stream.Collectors;

/** Read-only native navigation evidence, not the retired custom-motion diagnostic. */
final class FrontierV3NavigationDiagnosticJson {
    private FrontierV3NavigationDiagnosticJson() { }
    static String fragment(FrontierV3MinecraftGoalNavigation.Observation value) {
        return ",\"navigationStatus\":\"" + escaped(value.status()) + "\",\"navigationReason\":\"" + escaped(value.reason())
                + "\",\"navigationTargets\":[" + value.targets().stream()
                    .map(station -> "{\"x\":" + station.x() + ",\"y\":" + station.y() + ",\"z\":" + station.z() + "}")
                    .collect(Collectors.joining(","))
                + "],\"navigationBlockers\":[" + value.blockers().stream()
                    .map(body -> "{\"actor\":\"" + escaped(body.actorId().value()) + "\",\"physicalEpoch\":"
                        + body.physicalEpoch() + "}").collect(Collectors.joining(",")) + "]";
    }
    private static String escaped(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
