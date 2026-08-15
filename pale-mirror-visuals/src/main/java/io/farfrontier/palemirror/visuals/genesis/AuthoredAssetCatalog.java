package io.farfrontier.palemirror.visuals.genesis;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fail-closed versioned catalog for mandatory private structure assets. */
public final class AuthoredAssetCatalog {
    public static final String VERSION = "pale-mirror-authored-v40-curated-frontier-1";
    private static final Map<String, String> HASHES = hashes();
    private static volatile String cachedFailure;
    private static volatile boolean verified;

    private AuthoredAssetCatalog() { }

    public static synchronized String verify() {
        if (verified) return cachedFailure;
        for (var entry : HASHES.entrySet()) {
            String resource = "/data/pale_mirror_visuals/structure/" + entry.getKey() + ".nbt";
            try (InputStream input = AuthoredAssetCatalog.class.getResourceAsStream(resource)) {
                if (input == null) return finish("Missing mandatory authored asset " + resource);
                String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
                if (!actual.equals(entry.getValue())) return finish("Authored asset checksum mismatch for " + resource);
            } catch (IOException | NoSuchAlgorithmException failure) {
                return finish("Could not verify authored asset " + resource + ": " + failure.getMessage());
            }
        }
        return finish(null);
    }

    private static String finish(String failure) { cachedFailure = failure; verified = true; return failure; }

    private static Map<String, String> hashes() {
        Map<String, String> values = new LinkedHashMap<>();
        addFamily(values, "temperate"); addFamily(values, "cold_taiga"); addFamily(values, "dry_arid");
        return Map.copyOf(values);
    }

    private static void addFamily(Map<String, String> values, String family) {
        values.put(family + "/barracks", "43967f71c3045439d5cd0e32c8b1d01ad9a92f0df1b8794f031fb5dc356ade14");
        values.put(family + "/apothecary", "8a42e130db195a3e4649531c6ca9e1405bdcd9f3b2e3136785b916e8d4779928");
        values.put(family + "/assay_office", "62eb34c83ca4500346d9bdc484882d211acab545fe64a77cffc0bd0cfcf5f139");
        values.put(family + "/bakery", "7c02eaaf412febfa92dbf0bfd4b9db20a8cabadd83af366e41d945d32ea33f5a");
        values.put(family + "/bunkhouse_2", "432509540ef6b67083bc6a5361a74dfe07a823254ba5c3ea5761fd66e4fbecaa");
        values.put(family + "/civic_hall", "259d1e48c6f999d0186495004b4e61c954698814ab3c88a4853b2cc2cbff5c30");
        values.put(family + "/clinic", "cc0b2bdfc4e8209bcdb1fb317fa5f8cd8eb4a6371211ebc49b6df23d15d487fd");
        values.put(family + "/engineer_shop", "ce1666d6d147d1a2c50fcc064d1fb16a483272e04e122336eba5bf2f9b7efe5c");
        values.put(family + "/forge", "8d46b4a1c38f2cfe0df9a0e21d4027e7a1fbe7502eea9119020627a269db59a2");
        values.put(family + "/inn", "b7f45a22923e92258b9d16ad7ab685febf2aabfb013e3e423a00df82b58b94e4");
        values.put(family + "/market", "3fcd1b31686f4844158a593271aaa1b2f0e2c18d15227d941ffc72565b1f457f");
        values.put(family + "/receiving_depot", "6cac39003e585fac59cf6f1d2506f65fc79f63ac6d83e9a74d5897c478aff94a");
        values.put(family + "/residence_1", "dae2b25fc3ef1bbc32342be5a2dec114ad7e992555525c5b7ef43678eb2c75b3");
        values.put(family + "/residence_2", "6a03c6e286e0257a50cb8874bc66560cfbdccaa417c294b101dbd59efaa13d3a");
        values.put(family + "/residence_3", "8a42e130db195a3e4649531c6ca9e1405bdcd9f3b2e3136785b916e8d4779928");
        values.put(family + "/residence_4", "74b75b06de0e2fa7596f43ab1d76e34eb8aaa50081261678f896f9fbc667abb5");
        values.put(family + "/residence_5", "0a6794a68bdd5f3074970b02e2419e7c062a157300b0d2e95f908a2817c5024b");
        values.put(family + "/smeltery", "d997c05a0486579462905f212ac1f3b1915436f253a34833df74d97b3f174664");
        values.put(family + "/stable_yard", "ddd8e6631c2cf4ed3edec5ed2a13e7ed02685a45e6ff2d3f0b259d5902f58f16");
        values.put(family + "/trading_hall", "c2831b2df0479be41ee7175caf2f0ecbbf6f67eb54c2c0dc8434b3601fa76f81");
        values.put(family + "/watch_house", "b38776c258a48aeeaa2e5f5158e2e5068b6439d3b8e95165748414dbde787ce4");
        values.put(family + "/smithy", "b38776c258a48aeeaa2e5f5158e2e5068b6439d3b8e95165748414dbde787ce4");
        values.put(family + "/stable", "3fcd1b31686f4844158a593271aaa1b2f0e2c18d15227d941ffc72565b1f457f");
        values.put(family + "/workshop_1", "a329234accba56c3a3cc9eb195a074f156d5152851f4d2be7899e27d1d986bfe");
        values.put(family + "/workshop_2", "fc54cac05ead56363da4a8c25f8beb5420ec306f6b54505980d95ddc6283d9d1");
        values.put(family + "/mine/portal_hoist", "ce1666d6d147d1a2c50fcc064d1fb16a483272e04e122336eba5bf2f9b7efe5c");
        values.put(family + "/mine/processing_hall", "d997c05a0486579462905f212ac1f3b1915436f253a34833df74d97b3f174664");
        values.put(family + "/mine/power_house", "62eb34c83ca4500346d9bdc484882d211acab545fe64a77cffc0bd0cfcf5f139");
        values.put(family + "/mine/loading_yard", "ddd8e6631c2cf4ed3edec5ed2a13e7ed02685a45e6ff2d3f0b259d5902f58f16");
        values.put(family + "/mine/entrance_adit", "9f4d2d29abe643289f5ce6dec593e783e713721c6f3eef71ff2219e5e1a426ab");
        values.put(family + "/mine/crew_outpost", "8a42e130db195a3e4649531c6ca9e1405bdcd9f3b2e3136785b916e8d4779928");
        values.put(family + "/mine/iron_gallery", "814e2337188b08f46d65a4c72300ce3f6a1efcaae47e98128716f79940ac856d");
        values.put(family + "/mine/controller_chamber", "3d9ea21e44d55f11821df2221981d28b742719e2fa172deac10b30d516fb153c");
        values.put(family + "/mine/dispatch_foundation", "c55f45dd05f5ce5f7c49721b681d2a4d55afd59d17c4f23e71008de6a89f315b");
        values.put(family + "/mine/dispatch_shell", "d997c05a0486579462905f212ac1f3b1915436f253a34833df74d97b3f174664");
        values.put(family + "/mine/dispatch_machinery", "62eb34c83ca4500346d9bdc484882d211acab545fe64a77cffc0bd0cfcf5f139");
        values.put(family + "/mine/dispatch_commissioning", "ddd8e6631c2cf4ed3edec5ed2a13e7ed02685a45e6ff2d3f0b259d5902f58f16");
    }
}
