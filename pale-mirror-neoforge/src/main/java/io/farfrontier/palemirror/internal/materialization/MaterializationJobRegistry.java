package io.farfrontier.palemirror.internal.materialization;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Single persisted owner for all long-lived physical work. */
public final class MaterializationJobRegistry {
    private final Map<String, MaterializationJob> jobs;

    public MaterializationJobRegistry() { this(new LinkedHashMap<>()); }
    public MaterializationJobRegistry(Map<String, MaterializationJob> jobs) { this.jobs = new LinkedHashMap<>(jobs); }

    public Collection<MaterializationJob> jobs() { return jobs.values(); }
    public Optional<MaterializationJob> find(String id) { return Optional.ofNullable(jobs.get(id)); }
    public Optional<MaterializationJob> activeFor(String targetId, String channel) {
        return jobs.values().stream().filter(job -> job.targetId().equals(targetId) && job.channel().equals(channel)
                && job.state() != JobState.CANCELLED).findFirst();
    }
    public void put(MaterializationJob job) { jobs.put(job.jobId(), job); }
    public void clear() { jobs.clear(); }
    public void removeTerminalFor(String targetId, String channel) {
        jobs.values().removeIf(job -> job.targetId().equals(targetId) && job.channel().equals(channel)
                && (job.state() == JobState.COMPLETED || job.state() == JobState.CANCELLED));
    }
}
