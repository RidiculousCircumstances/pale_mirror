package io.farfrontier.palemirror.internal.frontier.v3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Optional read-only detail cannot displace the caller's essential operator receipt. */
final class FrontierV3DiagnosticDetailBudget {
    record Section(String name, String json) { }
    private FrontierV3DiagnosticDetailBudget() { }

    static String finish(String essential, List<Section> sections) {
        StringBuilder value = new StringBuilder(essential);
        List<String> omitted = new ArrayList<>();
        // Reserve the largest possible omission footer before selecting any detail.
        String reserve = footer(sections.stream().map(Section::name).toList());
        for (Section section : sections) {
            String detail = ",\"" + section.name() + "\":" + section.json();
            if (bytes(value.toString()) + bytes(detail) + bytes(reserve) <= FrontierV3DiagnosticJson.MAX_BYTES)
                value.append(detail);
            else omitted.add(section.name());
        }
        return value.append(footer(omitted)).toString();
    }
    private static String footer(List<String> omitted) {
        return ",\"omittedSections\":[" + omitted.stream().map(name -> "\"" + name + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + "]}";
    }
    private static int bytes(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
}
