package io.farfrontier.palemirror.internal.materialization;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.List;

/** Single persisted owner for all long-lived physical work. */
public final class MaterializationJobRegistry {
    public static final int MAX_RECEIPTS = 4_096;
    private final Map<String, MaterializationJob> jobs;
    private final Map<String, MaterializationReceipt> receipts;
    private long compactedReceiptCount;

    public MaterializationJobRegistry() { this(new LinkedHashMap<>(), new LinkedHashMap<>(), 0); }
    public MaterializationJobRegistry(Map<String, MaterializationJob> jobs) { this(jobs, new LinkedHashMap<>(), 0); }
    public MaterializationJobRegistry(Map<String, MaterializationJob> jobs,
                                      Map<String, MaterializationReceipt> receipts,
                                      long compactedReceiptCount) {
        this.jobs = new LinkedHashMap<>(jobs);
        this.receipts = new LinkedHashMap<>(receipts);
        this.compactedReceiptCount = compactedReceiptCount;
        if (compactedReceiptCount < 0) throw new IllegalArgumentException("Negative compacted receipt count");
    }

    public Collection<MaterializationJob> jobs() { return java.util.List.copyOf(jobs.values()); }
    public Optional<MaterializationJob> find(String id) { return Optional.ofNullable(jobs.get(id)); }
    public Optional<MaterializationJob> activeFor(String targetId, String channel) {
        return jobs.values().stream().filter(job -> job.targetId().equals(targetId) && job.channel().equals(channel)
                && job.state() != JobState.CANCELLED)
                .sorted(java.util.Comparator.comparingLong(MaterializationJob::desiredRevision).reversed()
                        .thenComparing(MaterializationJob::jobId)).findFirst();
    }
    public Collection<MaterializationReceipt> receipts() { return java.util.List.copyOf(receipts.values()); }
    public long compactedReceiptCount() { return compactedReceiptCount; }
    public void put(MaterializationJob job) {
        if (jobs.containsKey(job.jobId())) throw new IllegalStateException("Duplicate materialization job " + job.jobId());
        boolean current = !terminal(job.state());
        if (current && jobs.values().stream().anyMatch(existing -> !terminal(existing.state())
                && existing.targetId().equals(job.targetId()) && existing.channel().equals(job.channel()))) {
            throw new IllegalStateException("Current materialization job already exists for "
                    + job.targetId() + "|" + job.channel());
        }
        jobs.put(job.jobId(), job);
    }
    public void clear() { jobs.clear(); receipts.clear(); compactedReceiptCount = 0; }
    public void validateUniqueCurrent(List<String> errors) {
        var keys = new HashSet<String>();
        jobs.values().stream().filter(job -> job.state() != JobState.COMPLETED
                && job.state() != JobState.CANCELLED && job.state() != JobState.FAILED).forEach(job -> {
            String key = job.targetId() + "|" + job.channel();
            if (!keys.add(key)) errors.add("multiple current materialization jobs for " + key);
        });
    }

    public boolean compactTerminalJobs() {
        Map<String, MaterializationJob> latest = new LinkedHashMap<>();
        jobs.values().stream().filter(job -> terminal(job.state())).forEach(job -> latest.merge(
                job.targetId() + "|" + job.channel(), job, (left, right) -> left.desiredRevision() > right.desiredRevision()
                        || left.desiredRevision() == right.desiredRevision() && left.jobId().compareTo(right.jobId()) > 0
                        ? left : right));
        List<MaterializationJob> removable = jobs.values().stream().filter(job -> terminal(job.state()))
                .filter(job -> latest.get(job.targetId() + "|" + job.channel()) != job).toList();
        removable.forEach(job -> {
            jobs.remove(job.jobId());
            receipts.put(job.jobId(), new MaterializationReceipt(job.jobId(), job.targetId(), job.channel(),
                    job.desiredRevision(), job.state(), job.lastError()));
        });
        while (receipts.size() > MAX_RECEIPTS) {
            String oldest = receipts.keySet().iterator().next();
            receipts.remove(oldest);
            compactedReceiptCount++;
        }
        return !removable.isEmpty();
    }

    private static boolean terminal(JobState state) {
        return state == JobState.COMPLETED || state == JobState.CANCELLED || state == JobState.FAILED;
    }
}
