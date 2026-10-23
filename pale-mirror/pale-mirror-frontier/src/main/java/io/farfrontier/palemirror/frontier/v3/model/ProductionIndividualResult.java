package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.tooling.ToolCatalog;
import java.util.*;

/** Recipe identity policy, independent of labour, routes, Minecraft and storage admission. */
public final class ProductionIndividualResult {
    public static ExactItemStack resolve(ProductionRecipe recipe, ToolCatalog catalog, SubjectId outputId,
            SubjectId owner, InventoryCustody.ContainerSlot output, Optional<ExactItemStack> target) {
        Objects.requireNonNull(recipe); Objects.requireNonNull(catalog); Objects.requireNonNull(outputId);
        Objects.requireNonNull(owner); Objects.requireNonNull(output); Objects.requireNonNull(target);
        return switch (recipe.outcome()) {
            case ProductionRecipeOutcome.Fungible ignored -> throw new IllegalArgumentException("resource recipe does not create individual equipment");
            case ProductionRecipeOutcome.Individual ignored -> {
                if (target.isPresent()) throw new IllegalArgumentException("manufacture cannot consume a maintenance target");
                var definition = catalog.require(recipe.product().itemKind());
                yield new ExactItemStack(outputId, owner, definition.itemKind(), 1, output, new ItemCondition(definition.durability(), 0));
            }
            case ProductionRecipeOutcome.Repair repair -> {
                var item = target.orElseThrow(() -> new IllegalArgumentException("repair requires an exact retained target"));
                if (!item.id().equals(outputId) || !item.economicOwnerId().equals(owner) || item.count() != 1
                        || !item.itemKind().equals(recipe.product().itemKind())
                        || catalog.require(item.itemKind()).durability() != item.condition().maximumDamage())
                    throw new IllegalArgumentException("repair cannot substitute identity, ownership, kind or equipment lifetime");
                yield item.withCondition(repair.repaired(item.condition())).withCustody(output);
            }
        };
    }
    private ProductionIndividualResult() { }
}
