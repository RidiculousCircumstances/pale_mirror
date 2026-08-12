package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.ResidentSeed;
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
        tag.put("primaryMine", point(seed.primaryMine()));
        tag.put("alternateMine", point(seed.alternateMine()));
        tag.put("baselineRailNodes", points(seed.baselineRailNodes()));
        ListTag modules = new ListTag();
        seed.modules().forEach(module -> {
            CompoundTag value = new CompoundTag();
            value.putString("templateId", module.templateId());
            value.putString("role", module.role());
            value.put("origin", point(module.origin()));
            value.putInt("quarterTurns", module.quarterTurns());
            modules.add(value);
        });
        tag.put("modules", modules);
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
        return tag;
    }

    public static AuthoredRegionSeed read(CompoundTag tag) {
        List<VisualModulePlacement> modules = new ArrayList<>();
        for (Tag raw : tag.getList("modules", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw;
            modules.add(new VisualModulePlacement(value.getString("templateId"), value.getString("role"),
                    point(value.getCompound("origin")), value.getInt("quarterTurns")));
        }
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
                point(tag.getCompound("receivingDepot")), point(tag.getCompound("primaryMine")),
                point(tag.getCompound("alternateMine")), points(tag.getList("baselineRailNodes", Tag.TAG_COMPOUND)),
                modules, residents, plots);
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
