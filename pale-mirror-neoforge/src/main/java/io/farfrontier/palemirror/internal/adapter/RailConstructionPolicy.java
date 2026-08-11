package io.farfrontier.palemirror.internal.adapter;

/** Source-neutral policy controlling whether physical rail work may cause chunk generation. */
public enum RailConstructionPolicy {
    LOADED_CHUNKS_ONLY,
    AUTONOMOUS_DEV,
    LEGACY
}
