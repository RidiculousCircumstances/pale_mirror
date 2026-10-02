package io.farfrontier.palemirror.frontier.v3.model;

/** Pure effective-stat calculation. It neither selects jobs nor accrues work. */
public final class ResidentWorkStatistics {
    private ResidentWorkStatistics() { }
    public static int speedPermille(ResidentProfile resident, WorkCatalog.Definition operation, WorkCatalog catalog) {
        long speed = catalog.baseSpeedPermille() + (long) resident.capability(operation.capability()) * catalog.skillGainPermille();
        for (var modifier : resident.characteristics().workModifiers().modifiers().values().stream()
                .sorted(java.util.Comparator.comparing(ResidentWorkModifiers.Modifier::sourceId)).toList()) {
            if (modifier.capability() == operation.capability())
                speed = Math.multiplyExact(speed, modifier.factorPermille()) / 1_000L;
        }
        return (int) Math.clamp(speed, catalog.minSpeedPermille(), catalog.maxSpeedPermille());
    }
}
