package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import java.util.*;

/** Extraction supplies its geometry; common physical projection owns the write protocol. */
public final class ExtractionWorksiteBlocks {
    private ExtractionWorksiteBlocks() { }
    public static WorksiteBlock current(ExtractionDeposit deposit, WorksiteBlock declaration) {
        if (!deposit.site().id().equals(declaration.key().owner()) || declaration.key().family() != CellMutationKey.OwnerFamily.EXTRACTIVE_SITE)
            throw new IllegalArgumentException("worksite cell has a foreign owner");
        if (declaration.key().role() == WorksiteBlock.Role.RESOURCE) {
            var cell = deposit.cells().get(declaration.key().cell());
            return new WorksiteBlock(declaration.key(), declaration.position(), cell.revision(), cell.knownBlock());
        }
        var current = deposit.geometry().get(declaration.position());
        return current == null ? declaration : new WorksiteBlock(declaration.key(), declaration.position(), current.revision(), current.block());
    }
    public static List<WorksiteBlock> declared(ExtractionDeposit deposit) {
        var site = deposit.site(); var result = new ArrayList<WorksiteBlock>();
        for (var entry : site.layout().fixedBlocks().entrySet().stream().sorted(Comparator
                .comparingInt((Map.Entry<BlockPosition, BlockExtraction.Block> entry) -> entry.getKey().x())
                .thenComparingInt(entry -> entry.getKey().y()).thenComparingInt(entry -> entry.getKey().z())).toList()) {
            boolean socket = entry.getKey().equals(site.layout().container());
            result.add(new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE, site.id(),
                    socket ? WorksiteBlock.Role.CONTAINER_SOCKET : WorksiteBlock.Role.INFRASTRUCTURE,
                    site.layout().infrastructureIds().get(entry.getKey())),
                    entry.getKey(), 1, socket ? new BlockExtraction.Block("minecraft:air", Map.of()) : entry.getValue()));
        }
        for (var cell : site.layout().cells()) {
            var current = deposit.cells().get(cell.id());
            result.add(new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE, site.id(),
                    WorksiteBlock.Role.RESOURCE, cell.id()), cell.source(), current.revision(), current.knownBlock()));
        }
        return List.copyOf(result);
    }
    public static Optional<WorksiteBlock> support(FrontierWorldState state, ContainerSurface surface) {
        var container = state.inventory().containers().get(surface.containerId());
        if (container == null || container.purpose() != ContainerPurpose.EXTRACTIVE_STORAGE) return Optional.empty();
        return state.extractionSites().deposits().values().stream()
                .filter(deposit -> deposit.site().containerId().equals(container.id()))
                .flatMap(deposit -> declared(deposit).stream())
                .filter(cell -> cell.position().equals(surface.position().offset(0, -1, 0))).findFirst();
    }
    public static Optional<ContainerSocketSupport.Worksite> socketSupport(FrontierWorldState state, ContainerSurface surface) {
        var container = state.inventory().containers().get(surface.containerId());
        if (container == null || container.purpose() != ContainerPurpose.EXTRACTIVE_STORAGE) return Optional.empty();
        var deposit = state.extractionSites().deposits().values().stream()
                .filter(value -> value.site().containerId().equals(container.id())).findFirst().orElseThrow();
        var cells = declared(deposit).stream().map(cell -> current(deposit, cell)).toList();
        var support = cells.stream().filter(cell -> cell.position().equals(surface.position().offset(0, -1, 0))).findFirst();
        var socket = cells.stream().filter(cell -> cell.key().role() == WorksiteBlock.Role.CONTAINER_SOCKET
                && cell.position().equals(surface.position())).findFirst();
        return support.flatMap(cell -> socket.map(opening -> new ContainerSocketSupport.Worksite(cell, opening)));
    }
}
