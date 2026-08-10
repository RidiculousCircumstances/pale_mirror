package io.farfrontier.palemirror.gametest;

/** Keeps external-mod profiles with client payloads free of incompatible GameTest mock players. */
final class GameTestProfiles {
    private GameTestProfiles() { }
    static boolean createAdapterOnly() {
        return Boolean.getBoolean("pale_mirror.create_adapter_only")
                || Boolean.getBoolean("pale_mirror.ftb_presentation_only")
                || Boolean.getBoolean("pale_mirror.millenaire_adapter_only");
    }
}
