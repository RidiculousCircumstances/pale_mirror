package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Exact cell generation separates harvest, tillage and later sowing at the same place. */
public final class FieldCellOperation {
    private FieldCellOperation() { }
    public static String id(SubjectId job, long epoch, ResourceFieldLayout.CellId cell, long generation) {
        if (epoch < 1 || generation < 0) throw new IllegalArgumentException("invalid field operation generation");
        return "field-cell:" + job.value() + ":" + epoch + ":" + cell.value() + ":" + generation;
    }
}
