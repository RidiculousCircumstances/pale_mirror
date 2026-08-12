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
    public static final String VERSION = "pale-mirror-authored-v35-1";
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
        addFamily(values, "temperate"); addFamily(values, "cold_taiga");
        return Map.copyOf(values);
    }

    private static void addFamily(Map<String, String> values, String family) {
        values.put(family + "/barracks", "43967f71c3045439d5cd0e32c8b1d01ad9a92f0df1b8794f031fb5dc356ade14");
        values.put(family + "/civic_hall", "259d1e48c6f999d0186495004b4e61c954698814ab3c88a4853b2cc2cbff5c30");
        values.put(family + "/clinic", "cc0b2bdfc4e8209bcdb1fb317fa5f8cd8eb4a6371211ebc49b6df23d15d487fd");
        values.put(family + "/inn", "b7f45a22923e92258b9d16ad7ab685febf2aabfb013e3e423a00df82b58b94e4");
        values.put(family + "/market", "3fcd1b31686f4844158a593271aaa1b2f0e2c18d15227d941ffc72565b1f457f");
        values.put(family + "/receiving_depot", "6cac39003e585fac59cf6f1d2506f65fc79f63ac6d83e9a74d5897c478aff94a");
        values.put(family + "/residence_1", "dae2b25fc3ef1bbc32342be5a2dec114ad7e992555525c5b7ef43678eb2c75b3");
        values.put(family + "/residence_2", "6a03c6e286e0257a50cb8874bc66560cfbdccaa417c294b101dbd59efaa13d3a");
        values.put(family + "/residence_3", "dae2b25fc3ef1bbc32342be5a2dec114ad7e992555525c5b7ef43678eb2c75b3");
        values.put(family + "/smithy", "b38776c258a48aeeaa2e5f5158e2e5068b6439d3b8e95165748414dbde787ce4");
        values.put(family + "/stable", "3fcd1b31686f4844158a593271aaa1b2f0e2c18d15227d941ffc72565b1f457f");
        values.put(family + "/workshop_1", "a329234accba56c3a3cc9eb195a074f156d5152851f4d2be7899e27d1d986bfe");
        values.put(family + "/workshop_2", "fc54cac05ead56363da4a8c25f8beb5420ec306f6b54505980d95ddc6283d9d1");
    }
}
