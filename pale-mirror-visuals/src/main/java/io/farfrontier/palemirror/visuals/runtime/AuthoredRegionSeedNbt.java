package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.AuthoredMineRole;
import io.farfrontier.palemirror.api.AuthoredMineSitePlan;
import io.farfrontier.palemirror.api.ResidentSeed;
import io.farfrontier.palemirror.api.SemanticVisualVolume;
import io.farfrontier.palemirror.api.StagedVisualModule;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Explicit schema codec for immutable genesis manifests. */
public final class AuthoredRegionSeedNbt {
    private AuthoredRegionSeedNbt() { }

    public static CompoundTag write(AuthoredRegionSeed seed) {
        CompoundTag tag = new CompoundTag();
        tag.putString("planId", seed.planId());
        tag.putString("archetypeId", seed.archetypeId());
        tag.putInt("definitionVersion", seed.definitionVersion());
        tag.putString("contentHash", seed.contentHash());
        tag.putString("dimensionId", seed.dimensionId());
        tag.putString("climate", seed.climate());
        tag.putString("palette", seed.palette());
        tag.put("anchor", point(seed.anchor()));
        tag.put("settlementBounds", bounds(seed.settlementBounds()));
        tag.put("freightGate", point(seed.freightGate()));
        tag.put("receivingDepot", point(seed.receivingDepot()));
        tag.put("primaryMineSite", mine(seed.primaryMineSite()));
        tag.put("alternateMineSite", mine(seed.alternateMineSite()));
        tag.put("baselineRailNodes", points(seed.baselineRailNodes()));
        tag.put("modules", modules(seed.modules()));
        ListTag residents = new ListTag();
        seed.residents().forEach(resident -> {
            CompoundTag value = new CompoundTag();
            value.putString("residentId", resident.residentId());
            value.putString("nameKey", resident.nameKey());
            value.putString("cohort", resident.cohort());
            value.putString("role", resident.role());
            value.put("home", point(resident.home()));
            if (resident.workplace() != null) value.put("workplace", point(resident.workplace()));
            residents.add(value);
        });
        tag.put("residents", residents);
        ListTag plots = new ListTag();
        seed.expansionPlots().forEach(value -> plots.add(bounds(value)));
        tag.put("expansionPlots", plots);
        tag.put("shelterCandidates", points(seed.shelterCandidates()));
        return tag;
    }

    public static AuthoredRegionSeed read(CompoundTag tag) {
        List<VisualModulePlacement> modules = modules(tag.getList("modules", Tag.TAG_COMPOUND));
        List<ResidentSeed> residents = new ArrayList<>();
        for (Tag raw : tag.getList("residents", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw;
            residents.add(new ResidentSeed(value.getString("residentId"), value.getString("nameKey"),
                    value.getString("cohort"), value.getString("role"), point(value.getCompound("home")),
                    value.contains("workplace", Tag.TAG_COMPOUND) ? point(value.getCompound("workplace")) : null));
        }
        List<VisualBounds> plots = new ArrayList<>();
        for (Tag raw : tag.getList("expansionPlots", Tag.TAG_COMPOUND)) plots.add(bounds((CompoundTag) raw));
        return new AuthoredRegionSeed(tag.getString("planId"), tag.getString("archetypeId"),
                tag.getInt("definitionVersion"), tag.getString("contentHash"), tag.getString("dimensionId"),
                tag.getString("climate"), tag.getString("palette"), point(tag.getCompound("anchor")),
                bounds(tag.getCompound("settlementBounds")), point(tag.getCompound("freightGate")),
                point(tag.getCompound("receivingDepot")), mine(tag.getCompound("primaryMineSite")),
                mine(tag.getCompound("alternateMineSite")), points(tag.getList("baselineRailNodes", Tag.TAG_COMPOUND)),
                modules, residents, plots, points(tag.getList("shelterCandidates", Tag.TAG_COMPOUND)));
    }

    private static CompoundTag mine(AuthoredMineSitePlan mine) {
        CompoundTag tag = new CompoundTag();
        tag.putString("siteId", mine.siteId());
        tag.putString("role", mine.role().name());
        tag.put("portal", point(mine.portal()));
        tag.put("loadingEndpoint", point(mine.loadingEndpoint()));
        tag.put("controllerAnchor", point(mine.controllerAnchor()));
        tag.put("bounds", bounds(mine.bounds()));
        tag.putInt("inwardQuarterTurns", mine.inwardQuarterTurns());
        tag.put("initialModules", modules(mine.initialModules()));
        ListTag staged = new ListTag();
        mine.stagedModules().forEach(value -> {
            CompoundTag entry = module(value.module());
            entry.putString("stage", value.stage());
            staged.add(entry);
        });
        tag.put("stagedModules", staged);
        ListTag volumes = new ListTag();
        mine.semanticVolumes().forEach(value -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", value.id());
            entry.putString("purpose", value.purpose());
            entry.put("bounds", bounds(value.bounds()));
            volumes.add(entry);
        });
        tag.put("semanticVolumes", volumes);
        return tag;
    }

    private static AuthoredMineSitePlan mine(CompoundTag tag) {
        List<StagedVisualModule> staged = new ArrayList<>();
        for (Tag raw : tag.getList("stagedModules", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw;
            staged.add(new StagedVisualModule(value.getString("stage"), module(value)));
        }
        List<SemanticVisualVolume> volumes = new ArrayList<>();
        for (Tag raw : tag.getList("semanticVolumes", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw;
            volumes.add(new SemanticVisualVolume(value.getString("id"), value.getString("purpose"),
                    bounds(value.getCompound("bounds"))));
        }
        return new AuthoredMineSitePlan(tag.getString("siteId"), AuthoredMineRole.valueOf(tag.getString("role")),
                point(tag.getCompound("portal")), point(tag.getCompound("loadingEndpoint")),
                point(tag.getCompound("controllerAnchor")), bounds(tag.getCompound("bounds")),
                tag.getInt("inwardQuarterTurns"), modules(tag.getList("initialModules", Tag.TAG_COMPOUND)),
                staged, volumes);
    }

    private static ListTag modules(List<VisualModulePlacement> modules) {
        ListTag values = new ListTag();
        modules.forEach(value -> values.add(module(value)));
        return values;
    }

    private static List<VisualModulePlacement> modules(ListTag tags) {
        List<VisualModulePlacement> values = new ArrayList<>();
        for (Tag raw : tags) values.add(module((CompoundTag) raw));
        return values;
    }

    private static CompoundTag module(VisualModulePlacement module) {
        CompoundTag value = new CompoundTag();
        value.putString("templateId", module.templateId());
        value.putString("role", module.role());
        value.put("origin", point(module.origin()));
        value.putInt("quarterTurns", module.quarterTurns());
        value.put("footprint", bounds(module.footprint()));
        return value;
    }

    private static VisualModulePlacement module(CompoundTag value) {
        return new VisualModulePlacement(value.getString("templateId"), value.getString("role"),
                point(value.getCompound("origin")), value.getInt("quarterTurns"),
                bounds(value.getCompound("footprint")));
    }

    private static CompoundTag point(VisualPoint point) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", point.x()); tag.putInt("y", point.y()); tag.putInt("z", point.z());
        return tag;
    }

    private static VisualPoint point(CompoundTag tag) {
        return new VisualPoint(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    private static CompoundTag bounds(VisualBounds bounds) {
        CompoundTag tag = new CompoundTag();
        tag.put("min", point(bounds.min())); tag.put("max", point(bounds.max()));
        return tag;
    }

    private static VisualBounds bounds(CompoundTag tag) {
        return new VisualBounds(point(tag.getCompound("min")), point(tag.getCompound("max")));
    }

    private static ListTag points(List<VisualPoint> points) {
        ListTag values = new ListTag(); points.forEach(value -> values.add(point(value))); return values;
    }

    private static List<VisualPoint> points(ListTag tags) {
        List<VisualPoint> values = new ArrayList<>();
        for (Tag raw : tags) values.add(point((CompoundTag) raw));
        return values;
    }
}
