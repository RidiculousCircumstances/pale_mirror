package io.farfrontier.palemirror.frontier.v3.model;

/** Durable COLD/HOT-neutral lifecycle of an identified route operation. */
public enum OperationStage { ASSEMBLING, EN_ROUTE, ARRIVED, FAILED, INTERRUPTED }
