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
    public static final String VERSION = "pale-mirror-authored-v37-mountain-1";
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
        values.put(family + "/mine/portal_hoist", "55eb5727cb8ee50190aa939d355be0fa2418f3db94bd0f2fd23aed7ecfb87b67");
        values.put(family + "/mine/processing_hall", "1f2643aaca7b9942d111e9897d6196a6ec546500a3d03e24b91a0c6dcc8087d1");
        values.put(family + "/mine/power_house", "bd30bfd71797536524d47aadf88637ef435476b2a30e6aaf83222a21491038db");
        values.put(family + "/mine/loading_yard", "39101966fac98385536e8623cc8c621269282da3b30aa9083f7b76f2e21bda4c");
        values.put(family + "/mine/entrance_adit", "9f4d2d29abe643289f5ce6dec593e783e713721c6f3eef71ff2219e5e1a426ab");
        values.put(family + "/mine/crew_outpost", "06d5d4e5c0fdb789af54ae3225b9122e4aed69013c74f7208376880ea739e2e8");
        values.put(family + "/mine/iron_gallery", "814e2337188b08f46d65a4c72300ce3f6a1efcaae47e98128716f79940ac856d");
        values.put(family + "/mine/controller_chamber", "3d9ea21e44d55f11821df2221981d28b742719e2fa172deac10b30d516fb153c");
        values.put(family + "/mine/dispatch_foundation", "2cc13887b3a5342d425303d65e5070a6a76aaef6554dba24f28a7389685c359a");
        values.put(family + "/mine/dispatch_shell", "1f2643aaca7b9942d111e9897d6196a6ec546500a3d03e24b91a0c6dcc8087d1");
        values.put(family + "/mine/dispatch_machinery", "c2316b2311e7d67988ae65d04c1c8289989a05b58dce29ac805ad2ab630223c8");
        values.put(family + "/mine/dispatch_commissioning", "39101966fac98385536e8623cc8c621269282da3b30aa9083f7b76f2e21bda4c");
    }
}
