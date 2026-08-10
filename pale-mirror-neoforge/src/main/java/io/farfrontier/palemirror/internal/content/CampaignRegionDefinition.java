package io.farfrontier.palemirror.internal.content;

import io.farfrontier.palemirror.domain.InfectionSourceId;
import net.minecraft.resources.ResourceLocation;

/** Immutable authored parameters for one PM-managed living region. */
public record CampaignRegionDefinition(ResourceLocation id, int version, InfectionSourceId infectionSource,
                                       int population, int ironProduction, int ironDemand, int initialIronStock,
                                       int ironStockCapacity, int rationedIronDemand, int defence, long crisisDelaySteps,
                                       long rationReserveSteps, long requestReserveSteps,
                                       int defenceLossPerUnavailableStep, int stableStepsToRecover,
                                       int evacuationDefenceThreshold, long emergencyGraceSteps,
                                       long evacuationDurationSteps, long routeCurrentWindowSteps,
                                       long routeExpiryWindowSteps) { }
