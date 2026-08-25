package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceCanonicalStateMultiSeedTest {
    private static final Map<String, String> SEED_SEVEN_DAY_SIXTY_V2_FIELDS = Map.ofEntries(
            Map.entry("_next_charter_id", "6b86b273ff34fce19d6b804eff5a3f5747ada4eaa22f1d49c01e52ddb7875b4b"),
            Map.entry("_next_claim_id", "6b86b273ff34fce19d6b804eff5a3f5747ada4eaa22f1d49c01e52ddb7875b4b"),
            Map.entry("_next_front_campaign_id", "4fc82b26aecb47d2868c4efbe3581732a3e7cbcc6c2efb32062c08170a05eeb8"),
            Map.entry("_next_procurement_id", "6b86b273ff34fce19d6b804eff5a3f5747ada4eaa22f1d49c01e52ddb7875b4b"),
            Map.entry("charters", "5c80091b8d37df60ea1985001a1d892f5abdc528680fc11148ce70852fe2d03e"),
            Map.entry("chrysalises", "5c80091b8d37df60ea1985001a1d892f5abdc528680fc11148ce70852fe2d03e"),
            Map.entry("civic_site_projects", "e077d95d0a741b475302cf586394a8f038762af5ff9b4bbf30cf95ec6e23cf06"),
            Map.entry("civics", "a87a6c936985a88f49f523ea6b94833295590dd4da86ee1d09e1203d3196568f"),
            Map.entry("compensation", "5c80091b8d37df60ea1985001a1d892f5abdc528680fc11148ce70852fe2d03e"),
            Map.entry("decision_history", "30b818e85ba508ca958cc12b49f3d7ab641e5ee01080c1093ed7d85681498b02"),
            Map.entry("doctrines", "07eeea43d53a3f1b4af39f02dd8477e5515c36e4b1e7ec569f0a936480f96c89"),
            Map.entry("emergency_regimes", "5c80091b8d37df60ea1985001a1d892f5abdc528680fc11148ce70852fe2d03e"),
            Map.entry("front_campaigns", "830b7e2ab766cad8dc85856c4f36a11d04af431e449c4b1e5f394d56c7193e2f"),
            Map.entry("frontier_cooldown_until", "8124dd9d0b0f6b3d0337e29a558e233ae0a6307c26715ef23c91cb6a2287ceca"),
            Map.entry("hive_lifecycle", "78dba9a49d230310045bd4d40ad6cdd22aa1908b16c306d2c3461322d27ea2b8"),
            Map.entry("hive_perception", "fb206686dac2cca5ea1e1ecc10408a9cee4d8c11bfbfcbcdb532b0f456966137"),
            Map.entry("human_perceptions", "a22c2990ac6bf3c6b0b2e186d9b355b3811809f2df06907a94962de5119ea9fd"),
            Map.entry("last_civic_work_day", "23b084d8c8113fc2d66bc761d444fbbe86ff72d0373d266aab4b74160e5e1a2b"),
            Map.entry("procurements", "5c80091b8d37df60ea1985001a1d892f5abdc528680fc11148ce70852fe2d03e"),
            Map.entry("profile", "a69e7862d777a00f36826c0a01d96222c44c6ee78922fdb5ab52ba66ea872c5a"),
            Map.entry("ration_plans", "ee5a4b1002e00ac5932316448ebe8a5ebab29dd8b5548d0f9498aa1b3c358bbc"),
            Map.entry("reserve_policies", "b90707c366d8f02688363ee2cffd0828c4206f82a356b0617fd9a53249ad9c54"),
            Map.entry("rng", "cacfdeb61d10fb7456f373aeb06c856accfe90d9c4e591a2928cface75786db4"),
            Map.entry("route_insurance", "5c80091b8d37df60ea1985001a1d892f5abdc528680fc11148ce70852fe2d03e"),
            Map.entry("sector_control", "45052d22e8b93ae11c44e1a9e1efb62d820a1a86029455c7a5516baf05ed54e3"),
            Map.entry("sector_engagements", "32e93015efb8499dd5d2a829f71aa2561b6d55194f875eb05f0c731d3d1025f2"),
            Map.entry("sectors", "d98ad8bec7d93d5b03f8fd2c0efa0fb0874e70fb24d5f47df2344a7efc538976"),
            Map.entry("supply_lines", "562105886d7bef72b41b1c4e4b238646ad519681e32ae49e672e09f3b89a3cfe"));
    private static final Map<String, String> SEED_SEVEN_DAY_SIXTY_COMPONENTS = Map.of(
            "world_root", "fb7f6e4dd86f2a1a2a012d04cb3d0f698a26241ba5c6823211b512f27a3db5d1",
            "diagnostics", "06601039cabce9eedae6032c9aa94eebcde051622c1bc62e1ca141e1f9211c03",
            "settlements", "f457dff5031d0e70ee5bff067298acaab029a76ecd257244183dd03e0724670d",
            "resource_sites", "f392030a6e6b61d463355b912a921cfdaa764119aa05b12b63fe93b859b4f168",
            "trade", "cf1bb028b2164f5e04ec6421288c2c525ed5e1a5200ae8c719a8d725d544ced6",
            "market", "fba82942f01c73ee77fc11a8f566e0804f7c6cd54592bef6bf207d98b73189f2",
            "infection", "fda60069343a88d0935fb05c535ac9bea3c1f520cf2c7a53db91b87713bf4d7f",
            "operations", "bf8b7a045f29053784d92c0b132cbeaa32c9e15bc930494a3c95599686894a9c",
            "field", "ed6c954ab944e37d7fc9f4368b016a9f4641e79036f403c4eec6e6c77c6841bc",
            "v2", "eb9e0c5893f3dd044aee4e9f5b7f06e7707dcf7513e52635f4219b491ca30c11");
    private static final Map<Long, Map<Integer, String>> CONFORMANCE = Map.of(
            7L, Map.of(
                    0, "b870541916b8b48009f706fa2eb22d1039d2621bff7ccdca8036a47a1a806b3c",
                    1, "578e5c416aac81f36e16f501ede00b702089713700a10aac224054f50877e9db",
                    10, "394638f253b41334432dc21f7df0b59e4b7dca7c22653c9fcd2f6a8ca8f8db90",
                    30, "9e7d47bdc9d19c4268b475161950c864234907957f1ce497f672bb4fe665c60c",
                    60, "41bca6b49a0e3a41b0afd0b7fcd5c7ef028aa2553ae00643a056fc0f2c677e3d"),
            17L, Map.of(
                    0, "0539e33bec3e897107c12a702fd19c6f873e2afec44ca58c40eaa0ede11e68a0",
                    1, "692ade7ac798adcfeeade8b1e4fcd575d7ee1f90e1b6430e8928c2cae21ee565",
                    10, "572920080977019c5d5484834719bf270ac0168a32757d1ebf916ee5ee3fc06b",
                    30, "28736cb03d9a80a458866a9c4951bb3667612c553fa22dc5013108dc34bd7348",
                    60, "7fcfcb9e73de6b6e282511e0eba4b0a57606c67fce5ad02836c194303ff57b01"),
            41L, Map.of(
                    0, "c79ed03a101218b40d0d1e8b9673b9f1da5529815001a0b58ac5306fab028995",
                    1, "d53950c3fc90b7ed17f58e132fdb8f5ce49e6df647b8d56908d36e9404deb401",
                    10, "cc6b81162d68d09e3482de6326d58fe5d38dab3b76dd4ba148b90ab2386ec671",
                    30, "40431b34a9fdb33ee991ae04d045996e3407c1ab03d2bb0f73d78c3dc35d0ce8",
                    60, "1d6bdda3f855c988b8a81575957278808b35fe7f070ab1440a73dde8666ab5cb"),
            73L, Map.of(
                    0, "3b9c564b0ea5c409bf0ca7a44933d35de34598d03cf786c513f38ee35e115e22",
                    1, "f30d5982474790d76d6590510f69c26dbb3a7344acacf95e861d1bba5c474b27",
                    10, "c8a34f872cfeb487a6a7c6d538f55a6d6f74aa00c7b0de96705b315419537c80",
                    30, "4fe8ac283a8884a32349c097002377f6d41e184b28d25218e37e981da2c333ba",
                    60, "fb92d193087e7f288483c00880ff2a69a63370cd4347a4df411b61fe33561c4a"));

    @Test
    void completeV2StateFollowsIndependentSourceGenerationStreamsThroughDaySixty() {
        for (long seed : List.of(7L, 17L, 41L, 73L)) {
            Map<Integer, String> checkpoints = CONFORMANCE.get(seed);
            ReferenceWorld world = sourceWorld(seed);
            for (int day = 0; day <= 60; day++) {
                String expected = checkpoints.get(day);
                if (expected != null) {
                    if (seed == 7L && day == 60) {
                        assertEquals(SEED_SEVEN_DAY_SIXTY_V2_FIELDS, v2FieldConformance(world),
                                "seed 7 day 60 V2 field conformance");
                        assertEquals(SEED_SEVEN_DAY_SIXTY_COMPONENTS, componentConformance(world),
                                "seed 7 day 60 owner conformance");
                    }
                    assertEquals(expected, ReferenceCanonicalStateConformance.sha256(ReferenceCanonicalState.capture(world)),
                            "full canonical state conformance seed " + seed + " day " + day);
                }
                if (day < 60) world.tick();
            }
        }
    }

    private static ReferenceWorld sourceWorld(long seed) {
        return new ReferenceWorld(new ReferenceWorldConfig(64, 44, 12, seed, 2, true, ReferenceSimulationProfile.SOURCE_V2));
    }

    private static Map<String, String> componentConformance(ReferenceWorld world) {
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("world_root", ReferenceCanonicalStateRoot.capture(world));
        components.put("diagnostics", ReferenceCanonicalStateDiagnostics.capture(world));
        components.put("settlements", ReferenceCanonicalStateSettlements.capture(world));
        components.put("resource_sites", ReferenceCanonicalStateResourceSites.capture(world));
        components.put("trade", ReferenceCanonicalStateTrade.capture(world));
        components.put("market", ReferenceCanonicalStateMarket.capture(world));
        components.put("infection", ReferenceCanonicalStateInfection.capture(world));
        components.put("operations", ReferenceCanonicalStateOperations.capture(world));
        components.put("field", ReferenceCanonicalStateField.capture(world));
        components.put("v2", ReferenceCanonicalStateV2.capture(world));
        Map<String, String> hashes = new LinkedHashMap<>();
        components.forEach((name, state) -> hashes.put(name, ReferenceCanonicalStateConformance.sha256(state)));
        return hashes;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> v2FieldConformance(ReferenceWorld world) {
        Map<String, Object> encoded = ReferenceCanonicalStateV2.capture(world);
        Map<String, Object> fields = (Map<String, Object>) encoded.get("attributes");
        Map<String, String> hashes = new LinkedHashMap<>();
        fields.forEach((name, value) -> hashes.put(name, ReferenceCanonicalStateConformance.sha256(value)));
        return hashes;
    }
}
