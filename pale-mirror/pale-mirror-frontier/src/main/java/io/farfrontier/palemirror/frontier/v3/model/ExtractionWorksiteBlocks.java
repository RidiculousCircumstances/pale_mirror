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
            result.add(declaredAt(deposit, entry.getKey()).orElseThrow());
        }
        for (var cell : site.layout().cells()) {
            var current = deposit.cells().get(cell.id());
            result.add(new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE, site.id(),
                    WorksiteBlock.Role.RESOURCE, cell.id()), cell.source(), current.revision(), current.knownBlock()));
        }
        var sources = site.layout().cellsByPosition().keySet();
        site.layout().terrainSupports().entrySet().stream().filter(entry -> !sources.contains(entry.getKey()))
                .sorted(Comparator.comparingLong(entry -> site.layout().terrainIds().get(entry.getKey())))
                .forEach(entry -> result.add(new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE,
                        site.id(), WorksiteBlock.Role.NATURAL_TERRAIN, site.layout().terrainIds().get(entry.getKey())),
                        entry.getKey(), 1, entry.getValue())));
        return List.copyOf(result);
    }
    /** A point query uses the layout's retained indexes, never materializes the whole mine. */
    public static Optional<WorksiteBlock> declaredAt(ExtractionDeposit deposit, BlockPosition position) {
        var site = deposit.site(); var layout = site.layout();
        var fixed = layout.fixedBlocks().get(position);
        if (fixed != null) {
            boolean socket = position.equals(layout.container());
            return Optional.of(new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE, site.id(),
                    socket ? WorksiteBlock.Role.CONTAINER_SOCKET : WorksiteBlock.Role.INFRASTRUCTURE,
                    layout.infrastructureIds().get(position)), position, 1,
                    socket ? new BlockExtraction.Block("minecraft:air", Map.of()) : fixed));
        }
        var cell = layout.cellsByPosition().get(position);
        if (cell != null) {
            var state = deposit.cells().get(cell.id());
            return Optional.of(new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE,
                    site.id(), WorksiteBlock.Role.RESOURCE, cell.id()), position, state.revision(), state.knownBlock()));
        }
        var terrain = layout.terrainSupports().get(position);
        return terrain == null ? Optional.empty() : Optional.of(new WorksiteBlock(new WorksiteBlock.Key(
                CellMutationKey.OwnerFamily.EXTRACTIVE_SITE, site.id(), WorksiteBlock.Role.NATURAL_TERRAIN,
                layout.terrainIds().get(position)), position, 1, terrain));
    }
    /** Natural ground becomes a source only through this site's admitted, append-only declaration. */
    public static Optional<WorksiteBlockTransition> reclassification(ExtractionDeposit deposit,
            WorksiteBlock predecessor, WorksiteBlock successor) {
        var layout = deposit.site().layout();
        if (predecessor.key().family() != CellMutationKey.OwnerFamily.EXTRACTIVE_SITE
                || successor.key().family() != CellMutationKey.OwnerFamily.EXTRACTIVE_SITE
                || !predecessor.key().owner().equals(deposit.site().id()) || !successor.key().owner().equals(deposit.site().id())
                || predecessor.key().role() != WorksiteBlock.Role.NATURAL_TERRAIN
                || successor.key().role() != WorksiteBlock.Role.RESOURCE
                || !predecessor.position().equals(successor.position())
                || !Objects.equals(layout.terrainIds().get(predecessor.position()), predecessor.key().cell())) return Optional.empty();
        var natural = layout.terrainSupports().get(predecessor.position());
        if (natural == null || !current(deposit, new WorksiteBlock(predecessor.key(), predecessor.position(), 1, natural)).equals(predecessor))
            return Optional.empty();
        var cell = layout.require(successor.key().cell());
        var state = deposit.cells().get(cell.id());
        if (!cell.source().equals(successor.position()) || !cell.definition().before().equals(predecessor.block())
                || !successor.equals(new WorksiteBlock(successor.key(), cell.source(), state.revision(), state.knownBlock())))
            return Optional.empty();
        return Optional.of(new WorksiteBlockTransition(predecessor, successor));
    }
    public static Optional<WorksiteBlock> support(FrontierWorldState state, ContainerSurface surface) {
        var container = state.inventory().containers().get(surface.containerId());
        if (container == null || container.purpose() != ContainerPurpose.EXTRACTIVE_STORAGE) return Optional.empty();
        return declaredAt(attachment(state, container), surface.position().offset(0, -1, 0));
    }
    public static Optional<ContainerSocketSupport.Worksite> socketSupport(FrontierWorldState state, ContainerSurface surface) {
        var container = state.inventory().containers().get(surface.containerId());
        if (container == null || container.purpose() != ContainerPurpose.EXTRACTIVE_STORAGE) return Optional.empty();
        var deposit = attachment(state, container);
        var support = declaredAt(deposit, surface.position().offset(0, -1, 0)).map(cell -> current(deposit, cell));
        var socket = declaredAt(deposit, surface.position()).map(cell -> current(deposit, cell))
                .filter(cell -> cell.key().role() == WorksiteBlock.Role.CONTAINER_SOCKET);
        return support.flatMap(cell -> socket.map(opening -> new ContainerSocketSupport.Worksite(cell, opening)));
    }
    private static ExtractionDeposit attachment(FrontierWorldState state, ContainerRecord container) {
        if (!(container.storage().orElseThrow().endpoint() instanceof ShipmentEndpoint.ExtractiveSite endpoint))
            throw new IllegalArgumentException("authored extractive container has no explicit worksite attachment");
        ShipmentEndpointComposition.validate(state, endpoint);
        return Objects.requireNonNull(state.extractionSites().deposits().get(endpoint.siteId()), "container worksite attachment");
    }
}
