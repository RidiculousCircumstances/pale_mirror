package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionDeposit;
import java.util.stream.Collectors;

/** Read-only declaration versus admitted-work history. An unopened volume is not a proven accessible reserve. */
final class FrontierV3MineralDiagnosticJson {
    private FrontierV3MineralDiagnosticJson() { }
    static String forSite(FrontierWorldState state, ExtractionDeposit worksite) {
        return "[" + worksite.site().mineralDeposits().stream().sorted().map(id -> {
            var mineral = state.extractionSites().geology().deposits().get(id);
            var cells = worksite.site().layout().cells().stream().filter(cell -> mineral.sections().stream()
                    .anyMatch(section -> section.volume().contains(cell.source()))).toList();
            long mined = cells.stream().filter(cell -> worksite.cells().get(cell.id()).disposition() == ExtractionDeposit.Disposition.EXTRACTED).count();
            long external = cells.stream().filter(cell -> worksite.cells().get(cell.id()).disposition() == ExtractionDeposit.Disposition.EXTERNALLY_CHANGED).count();
            var sections = mineral.sections().stream().map(section -> "{\"material\":" + string(section.material().kind())
                    + ",\"min\":" + FrontierV3DiagnosticJson.position(section.volume().min())
                    + ",\"max\":" + FrontierV3DiagnosticJson.position(section.volume().max())
                    + ",\"initialVolumeBlocks\":" + section.volume().blocks() + "}").collect(Collectors.joining(","));
            return "{\"id\":" + string(id.value()) + ",\"revision\":" + mineral.revision()
                    + ",\"known\":" + state.extractionSites().knowledge().knows(worksite.site().settlementId(), mineral)
                    + ",\"sections\":[" + sections + "],\"admittedCells\":" + cells.size()
                    + ",\"extractedCells\":" + mined + ",\"externalCells\":" + external
                    + ",\"remainingAdmittedCells\":" + (cells.size() - mined - external) + "}";
        }).collect(Collectors.joining(",")) + "]";
    }
    private static String string(String value) { return "\"" + FrontierV3DiagnosticJson.quote(value) + "\""; }
}
