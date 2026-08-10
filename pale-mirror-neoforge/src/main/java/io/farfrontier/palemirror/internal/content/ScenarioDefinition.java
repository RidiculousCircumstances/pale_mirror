package io.farfrontier.palemirror.internal.content;

import java.util.List;
import java.util.Set;

import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import net.minecraft.resources.ResourceLocation;

/** Compiled immutable datapack definition. Active scenarios pin its version at creation. */
public record ScenarioDefinition(ResourceLocation id, InfectionSourceId infectionSource, int version, Set<Capability> capabilities, List<String> stages,
                                 String policy, long cooldownSteps, String encounterProfileId) { }
