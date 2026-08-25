package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceSimulationEngineTest {
    @Test
    void completeFirstV2DayMatchesThePinnedPythonEngineTrace() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.sourceV2());

        assertEquals(0, world.day());
        assertEquals(1, world.history().size());
        world.tick();

        assertEquals(1, world.day());
        assertEquals(2, world.history().size());
        ReferenceDailyWorldHistory history = world.history().getLast();
        assertEquals(1, history.day());
        assertEquals(12, history.alive());
        assertClose(12749.030993270018d, history.population());
        assertClose(12938.678273469115d, history.cash());
        assertClose(20752.653163760682d, history.privateCash());
        assertClose(.013849431818181818d, history.infection());
        assertEquals(2, history.nests());
        assertClose(267.70389759871694d, history.hiveBiomass());
        assertClose(57.74454449486295d, history.harvestedBiomass());
        assertClose(273788.6724327424d, history.ecologyOrganic());
        assertClose(.19000869291321176d, history.ecologyScar());
        assertClose(1245.4244070135412d, history.trade30d());
        assertEquals(12, world.v2().decisionHistory().size());
        assertEquals(0, world.v2().chrysalises().size());
        assertEquals(0, world.v2().frontCampaigns().size());

        ReferenceSettlement first = world.settlements().get(1);
        assertClose(1225.378343494121d, first.population());
        assertClose(1357.6608862672858d, first.cash());
        assertClose(163.84752169145725d, first.amount(ReferenceResource.ORE));
        assertClose(1051.4462152209587d, first.amount(ReferenceResource.FOOD));
        assertClose(72.33947826163097d, first.dailyProduction().get(ReferenceResource.ORE));
        assertClose(79.63764668011585d, first.dailyConsumption().get(ReferenceResource.FOOD));

        ReferenceSettlement twelfth = world.settlements().get(12);
        assertClose(762.5794411769837d, twelfth.population());
        assertClose(1260.2870454334522d, twelfth.cash());
        assertClose(143.77977321782555d, twelfth.amount(ReferenceResource.ORE));
        assertClose(64.87110505034973d, twelfth.dailyProduction().get(ReferenceResource.ORE));
        assertClose(49.560229642057635d, twelfth.dailyConsumption().get(ReferenceResource.FOOD));

        assertClose(1.0d, world.trade().routes().getFirst().checkpointCapacityMultiplier());
        assertClose(0.0d, world.trade().routes().getFirst().infection());
        assertEquals("spring", world.microeconomy().summary().get("season"));
        assertEquals(70, world.microeconomy().summary().get("companies"));
        assertEquals(52, world.microeconomy().summary().get("active_contracts"));
        assertClose(0.0d, (Double) world.microeconomy().summary().get("credit"));
        assertEquals(0, world.microeconomy().summary().get("construction"));
        assertEquals("D1: sector 0:0 is abandoned: outside sustainable human reach", world.events().getFirst());
        assertEquals("D1: Lakestead-12 upgraded mine site 33", world.events().getLast());
        assertEquals(2, world.settlementHistory().get(1).size());
        assertClose(163.84752169145725d, world.settlementHistory().get(1).getLast().resources().get(ReferenceResource.ORE).amount());
    }

    @Test
    void legacyProfileFailsClosedInsteadOfSilentlyDroppingItsUnportedStrategist() {
        ReferenceWorld world = new ReferenceWorld(new ReferenceWorldConfig(
                64, 44, 12, 42L, 2, false, ReferenceSimulationProfile.SOURCE_V2));

        IllegalStateException error = assertThrows(IllegalStateException.class, world::tick);

        assertTrue(error.getMessage().contains("strategy is not ported"));
        assertEquals(0, world.day());
        assertEquals(1, world.history().size());
    }

    @Test
    void grayboxDayKeepsNamedPeopleUnderOneCustodianAndRejectsAnOrphanedDeployment() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));

        world.tick();

        assertEquals(1, world.day());
        assertEquals(12, world.history().getLast().alive());
        ReferenceSettlement settlement = world.settlements().get(1);
        String residentId = settlement.residents().availableIds().getFirst();
        settlement.residents().deploy(List.of(residentId), 999, Map.of(residentId, "line"));

        IllegalStateException error = assertThrows(IllegalStateException.class, world::assertProfileInvariants);

        assertTrue(error.getMessage().contains("without a custodian"));
    }

    @Test
    void thirtyV2DaysFollowThePinnedPythonTrajectoryAtEveryPhaseBoundary() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.sourceV2());
        Map<Integer, Checkpoint> expected = Map.of(
                5, new Checkpoint(12756.682133157283d, 16399.03438398508d, 34697.88089338411d, .013849431818181818d,
                        273278.40539309196d, .727779777242157d, 426.49364180984907d, 304.4522509701408d,
                        3332.2199906874434d, 0, 0, 2, 52, 0.0d),
                10, new Checkpoint(12766.252515441201d, 17431.0754687519d, 37881.22061107734d, .013849431818181818d,
                        272977.9129644284d, 1.5347058269293907d, 517.2121024927415d, 653.6169218130582d,
                        4732.070243943449d, 1, 0, 2, 52, 0.0d),
                15, new Checkpoint(12775.830077665492d, 16643.973970767573d, 39469.189498807675d, .024502840909090908d,
                        272867.59364104166d, 2.487942831580816d, 609.0898405930036d, 1047.0584660240477d,
                        7421.517582222815d, 2, 0, 2, 98, 0.0d),
                20, new Checkpoint(12785.414825216725d, 16263.505504978919d, 40972.99521926133d, .04580965909090909d,
                        272848.7709439577d, 3.600745598323352d, 611.0964075212636d, 1496.26603380732d,
                        9688.58212380891d, 5, 1, 2, 98, 0.0d),
                25, new Checkpoint(12795.006763485511d, 14678.691428412441d, 42847.167850659054d, .04651988636363636d,
                        272573.9391405604d, 5.3825166966784685d, 508.3886435042293d, 2275.8712794527296d,
                        13316.54997885628d, 5, 1, 6, 154, 10.05d),
                30, new Checkpoint(12804.605897866513d, 14318.594735264869d, 44427.40484190321d, .060369318181818184d,
                        272164.0265906391d, 7.752552336102551d, 824.7901907477968d, 3293.025420823076d,
                        15657.113881511215d, 5, 1, 8, 154, 10.15d));

        for (int day = 1; day <= 30; day++) {
            world.tick();
            Checkpoint checkpoint = expected.get(day);
            if (checkpoint != null) assertCheckpoint(world, checkpoint);
        }

        assertClose(1230.7199482459048d, world.settlements().get(1).population());
        assertClose(1273.9523318338834d, world.settlements().get(1).cash());
        assertClose(765.903637322795d, world.settlements().get(12).population());
        assertClose(1137.7830052222255d, world.settlements().get(12).cash());
        List<String> events = world.events();
        assertEquals(List.of(
                "D29: Oldgate-08 forest-23 Co. is insolvent",
                "D29: Ironreach-01 workshop Co. is stressed",
                "D29: Ashhill-03 workshop Co. is stressed",
                "D29: Highford-10 workshop Co. is stressed",
                "D29: 82 trades, value=345; largest tools Lakestead-07->Ironreach-01 x4.8 @ 5.06",
                "D30: sector 5:7 is contested: neither side can hold the frontier",
                "D30: Ashwatch-02 clinic Co. entered municipal receivership",
                "D30: creditors restructured Ashwatch-02 armory Co.",
                "D30: creditors restructured Blackfield-05 workshop Co.",
                "D30: creditors restructured Stonehaven-11 armory Co.",
                "D30: Lakestead-07 power-19 Co. is insolvent",
                "D30: 80 trades, value=340; largest tools Lakestead-07->Ironreach-01 x4.8 @ 5.06"),
                events.subList(events.size() - 12, events.size()));
    }

    @Test
    void publicV2SnapshotMatchesPinnedPythonReadModelAtEveryCheckpoint() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.sourceV2());
        Map<Integer, String> expectedDigests = Map.of(
                0, "e83c55604acc10b1de850be6021554b26be4ce4f1c755d7dae98ad06838a55f6",
                1, "6d0161e3fb9a0fe46c3216b431073089fe7e13c17baba16789e76c2a10ebc038",
                5, "3d48cce7b68e4044d1ce9aca088d79887daac88f6f6c7fdb79f3f747fdf3ef8d",
                10, "a4e35cbca132be953e922dee08128333a401caac34b80040f380e612e6b43131",
                15, "266b97428ccd0c26b0b4da1ce2e1c509e95b6950bed6dddb264ade1b9e89e8ad",
                20, "dfdf81df56dd8ac788b061ab3094aad956e609c074ad3e953ced6c87a3cdb5c8",
                25, "b57f5ff548126159d3b2c3f0be30a39951b2a18a1cead7106feea8358f50addf",
                30, "bda76a4944b03c5e8467bc6f96888669750b81b1cf96d630cac4104da1def61d");
        Map<String, String> expectedDayZeroComponents = Map.ofEntries(
                Map.entry("companies", "92f3bd9b096653c25bfa9684b34d1fa4da1e7f32d27dc3110685507e5779b0ce"),
                Map.entry("economy", "0dbc3405a12cdb9ca1cbcdd2b7f8fbe8f9127e3a07e119e65ec3a45eaa57f19c"),
                Map.entry("field", "e4df041bb6b634542507d545b74c3a2213eed1de1e923538c15f69725cbcf604"),
                Map.entry("hive", "99addcaa2f0eee293ff5cbbecbe1b8589ecc028c5c800ab3bdfeaebd7e1490e8"),
                Map.entry("infected_fraction", "eb796f33128a3d0a8e3ead887f0bf1354be07bf934bad9173c69983524bd69a0"),
                Map.entry("nests", "479991fde170e3f8054aa49f890af515d3c832eb8fa65b1cdcc7090997382ab2"),
                Map.entry("resource_sites", "fd292934ca1760cf45fd56c1d06e04f5c1e46f3b52239e6c1266364d0fa19cc1"),
                Map.entry("settlements", "b39503a52fd816e4b797ddb539c5a4dfb566c9efb2eb722fd7a7e6d91d8756d4"));
        Map<String, Object> dayZero = ReferenceV2PublicSnapshot.capture(world);
        for (Map.Entry<String, String> expected : expectedDayZeroComponents.entrySet()) {
            assertEquals(expected.getValue(), ReferenceV2PublicSnapshot.sha256(dayZero.get(expected.getKey())),
                    "public snapshot day 0 component " + expected.getKey());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> v2 = (Map<String, Object>) dayZero.get("v2");
        Map<String, String> expectedDayZeroV2Components = Map.of(
                "civics", "6861c0892b4c83e29b5031b2745717968d1d84a95f65ae0b9046748ff4446aca",
                "doctrines", "bcef672161b8125cf17c2a1c4efb2af7f8d44b8225bff35f34b52c399d3c4b24",
                "sector_control", "a9641169147f17782b461e45cd9f577544cb3d8dd8b4239604377efc52828d87",
                "sectors", "4c31e651d941c5a25855a516633177131745d2ffd52f5a7ee82fe51dc65a9b75",
                "summary", "4ed04a9cb4eb56400c86e6a3668e422c0bcb8a7c6e9b81697eaeaa9e7b86b644");
        for (Map.Entry<String, String> expected : expectedDayZeroV2Components.entrySet()) {
            assertEquals(expected.getValue(), ReferenceV2PublicSnapshot.sha256(v2.get(expected.getKey())),
                    "public snapshot day 0 V2 component " + expected.getKey());
        }
        assertEquals("c8ab38b80c91373e50c334dfa3c98c244bc255317d07923fa3fef8280372929d",
                ReferenceV2PublicSnapshot.sha256(v2), "public snapshot day 0 component v2");

        for (int day = 0; day <= 30; day++) {
            String expected = expectedDigests.get(day);
            if (expected != null) assertEquals(expected, ReferenceV2PublicSnapshot.sha256(world), "public snapshot day " + day);
            if (day < 30) world.tick();
        }
    }

    @Test
    void publicV2SnapshotRejectsAnUnportedDiscreteResidentProjection() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> ReferenceV2PublicSnapshot.capture(world));

        assertEquals("graybox resident snapshot is not ported", error.getMessage());
    }

    @Test
    void immutableV2WorldViewMatchesPinnedPythonSystemViewAtEveryCheckpoint() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.sourceV2());
        Map<Integer, String> expectedDigests = Map.of(
                0, "c14db2317465917e701e1459f587e0e5c02698b6b0e6f870ac9c9d9fa86964cc",
                1, "33d1c22ff05b8db0793a960a1f4fd99315c3f19592331e89c3579dfaea39c7da",
                5, "5215611702bd616f3a81d7e5d83dd3f9769f06e162f52bb7e7ad0bc36e1114c7",
                10, "9d97fd6db925ce8a0697070832003ebf1ddf60e09e742ae93aba96296fc90d36",
                15, "6c13be1f37f31e5ae2770c17d98b7380d690eef10df720a6afa5bc1f87b8e86f",
                20, "5ff198ca6fd4a8b4d769a8edbc3ae0a185fb52cacc612eee2d1e9776080b5083",
                25, "8fa5b772b13bb2efe18c88bdb51213f225fe6d24a3afa7e7c6611d0b2676fceb",
                30, "c0b0b2785ca7d3d63972bcd281f03a82856ab75663cfc9311d14aae32c6c6153");

        for (int day = 0; day <= 30; day++) {
            String expected = expectedDigests.get(day);
            if (expected != null) {
                ReferenceWorldView view = ReferenceWorldView.from(world);
                assertEquals(expected, ReferenceV2PublicSnapshot.sha256(view.canonicalProjection()), "WorldView day " + day);
                assertThrows(UnsupportedOperationException.class, () -> view.cells().add(view.cells().getFirst()));
            }
            if (day < 30) world.tick();
        }
    }

    @Test
    void immutableV2WorldViewRejectsLegacyWorldWithoutTerritorialState() {
        ReferenceWorld world = new ReferenceWorld(new ReferenceWorldConfig(
                64, 44, 12, 42L, 2, false, ReferenceSimulationProfile.SOURCE_V2));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> ReferenceWorldView.from(world));

        assertEquals("V2 is disabled by this reference-world config", error.getMessage());
    }

    @Test
    void canonicalJsonUsesPythonBinary64SpellingAndRejectsNonFiniteValues() {
        assertEquals("0.0001", ReferenceV2PublicSnapshot.pythonFloat(1.0e-4d));
        assertEquals("1e-05", ReferenceV2PublicSnapshot.pythonFloat(1.0e-5d));
        assertEquals("10000000.0", ReferenceV2PublicSnapshot.pythonFloat(1.0e7d));
        assertEquals("1e+16", ReferenceV2PublicSnapshot.pythonFloat(1.0e16d));
        assertEquals("-0.0", ReferenceV2PublicSnapshot.pythonFloat(-0.0d));
        assertThrows(IllegalArgumentException.class, () -> ReferenceV2PublicSnapshot.pythonFloat(Double.NaN));
    }

    @Test
    void canonicalStateRootMatchesPinnedPythonOwnersAtEveryCheckpoint() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.sourceV2());
        Map<Integer, RootCheckpoint> expected = Map.of(
                0, new RootCheckpoint(14_025, "6c903a78dae57e51af6bf32e7a7401c9132513016bca2ada99bd558b2678c8d6"),
                1, new RootCheckpoint(26_019, "e803d83e449b4e430a5fa0a278ec9f28ce8befd553a55e82ed2b2bf8561566ef"),
                2, new RootCheckpoint(26_100, "31c63f8a681383368d777b1f57d7089d9398e8cae4b6c3e7aedef53311291c7a"),
                3, new RootCheckpoint(26_180, "1fd692fad6c12892d52cfb6c9f2a1cb2733fba992c8a837f19751700d4e87d8c"),
                5, new RootCheckpoint(26_475, "94dc40393c6228d3ccd1a4128a828dc3c41b12ba95ac762ec70b38baef2205bd"),
                10, new RootCheckpoint(29_360, "d06c52bcf117cec07cd1381d3092b34205c1762a775e54d01aab0fd7bf6bee25"),
                15, new RootCheckpoint(32_354, "b97b06073465891feb748353f58333b1f81533e331072df40a783daa744c0a13"),
                20, new RootCheckpoint(35_318, "82641b1e87a6b3fca1a8495d011fae3d8eac087dda045eacaaaefbc73ad1b928"),
                25, new RootCheckpoint(38_797, "2303f8b5c342f5f86d4ccbca4361cece94cbaa6495dc4b109cf058b59b73da17"),
                30, new RootCheckpoint(41_416, "afe32615b428ea3249f07dd5eac5cfe2cc1cdfcaff7d769dd4a235aada796c98"));

        for (int day = 0; day <= 30; day++) {
            RootCheckpoint checkpoint = expected.get(day);
            if (checkpoint != null) {
                String json = ReferenceV2PublicSnapshot.canonicalJson(ReferenceCanonicalStateRoot.capture(world));
                assertEquals(checkpoint.bytes(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, "root bytes day " + day);
                assertEquals(checkpoint.digest(), ReferenceV2PublicSnapshot.sha256(ReferenceCanonicalStateRoot.capture(world)), "root digest day " + day);
            }
            if (day < 30) world.tick();
        }
    }

    @Test
    void canonicalStateRootRejectsV2DisabledWorld() {
        ReferenceWorld legacy = new ReferenceWorld(new ReferenceWorldConfig(
                64, 44, 12, 42L, 2, false, ReferenceSimulationProfile.SOURCE_V2));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> ReferenceCanonicalStateRoot.capture(legacy));

        assertEquals("canonical state requires V2-enabled reference world", error.getMessage());
    }

    @Test
    void canonicalStateMarketAndResourceSiteOwnersMatchPinnedPythonAtEveryCheckpoint() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.sourceV2());
        Map<Integer, RootCheckpoint> expectedMarkets = Map.of(
                0, new RootCheckpoint(109_599, "7a58339f9d450d636eaa019e0a04f207f7905735dce5d86ae209aebca68bd768"),
                1, new RootCheckpoint(164_777, "b564fe70bf70f07e8cccf2512eea2d76038940adb072eba145027c525c4c300c"),
                2, new RootCheckpoint(165_373, "ddbb9fbc636e9e0aa205e484fa012b109e2d3ffe83fc0aff7019662ba57ad1f1"),
                3, new RootCheckpoint(165_633, "02d4f6b508d7005c018fafb583fa141b2f1a06a493a88e7954f6075447685051"),
                5, new RootCheckpoint(166_138, "d718fecb3ee29eee7e8972ff10ba79750ea6de28c592930de00cdce246e5a853"),
                10, new RootCheckpoint(166_781, "865848f30a20f9bb27c7df2439ae2e933f8fbd96217378c397709b6e547c8075"),
                15, new RootCheckpoint(185_554, "37ef32e907c0f174b7ad1a5d97f93f02958baef30a47de248e85cd4dbef356d6"),
                20, new RootCheckpoint(186_085, "765961d74abb6460b76b4b822842a6db5677cb46962ba11da059cdec776f9475"),
                25, new RootCheckpoint(210_499, "0ed0b91384ac0a1864a8527c5987dbeb48fd9733855cc33ffd526c2757f9983b"),
                30, new RootCheckpoint(211_203, "a5e5669f43361d98c0815216a80eae6bede0e2fdaa8a90a14eda90f53e4b0652"));
        Map<Integer, RootCheckpoint> expectedSites = Map.of(
                0, new RootCheckpoint(31_344, "f38839dd56e89faa2e6970e663a94d3560f2b00ed055536f71a3917fdbd248c1"),
                1, new RootCheckpoint(31_826, "c925ae8ce65aa479cf91efbe96be8b7794011a7cc268c449c7c596e2a2c334a4"),
                2, new RootCheckpoint(31_830, "8e029b9c9bbb37aeeff85b7f3afe48cd2c4939ef13fa8a2814f5c0fa4c145e3b"),
                3, new RootCheckpoint(31_832, "5156efd674378e36f53974c24b9cddfe99fc2c7ae45fd2aa48beb71bb50cdaad"),
                5, new RootCheckpoint(31_834, "359b12f6d69388386c79c35b9fa5747271e684a720559fb822700ef60fa7c265"),
                10, new RootCheckpoint(31_835, "1c29436fa8b753177361da63b8db1aef692e7b0ef7b8a62a9fe5ea5a15b0032f"),
                15, new RootCheckpoint(31_801, "f18a4130f37c6a2ec2229d150ff49811df177ea7ebf55eef21fb799a6284fbab"),
                20, new RootCheckpoint(31_775, "29aa769c8267dbce306838f1d597e4fddb1ca4dbb2af389ba87e07f8b51ca9bd"),
                25, new RootCheckpoint(31_742, "c24d8cdfd46856b8ac175322ef9531eb131618da938b92d86c5300a32ded95f0"),
                30, new RootCheckpoint(31_754, "f65f1b87dfecc3f621992fa33ba64cf5d5f651f264259303eae66280630bfdb7"));

        for (int day = 0; day <= 30; day++) {
            RootCheckpoint market = expectedMarkets.get(day);
            RootCheckpoint sites = expectedSites.get(day);
            if (market != null) assertCanonicalCheckpoint(market, ReferenceCanonicalStateMarket.capture(world), "market", day);
            if (sites != null) assertCanonicalCheckpoint(sites, ReferenceCanonicalStateResourceSites.capture(world), "resource sites", day);
            if (day < 30) world.tick();
        }
    }

    @Test
    void canonicalStateSettlementOwnerMatchesPinnedPythonAtEveryCheckpoint() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.sourceV2());
        Map<Integer, RootCheckpoint> expected = Map.of(
                0, new RootCheckpoint(118_089, "a6756a3cb4f8bf4de483c7436ad1165633597133d51839c05ca2bbd11308a24a"),
                1, new RootCheckpoint(119_931, "279517e46aab333fd39e492e83738b1ca903b8ce26f6150dfb84fa47bcbd887d"),
                2, new RootCheckpoint(119_983, "cc66344fa3e30d74e7eb6dd91e214b4602fbd31b8814c8e53eeb00eda125dbd2"),
                3, new RootCheckpoint(119_998, "65f4bfe7594e291ce18daf4162aa7118faf263e23000c475ce3d915aef6c1eac"),
                5, new RootCheckpoint(119_990, "a91deeb29e09b3000229b9423e8c967943f0c4648e185e1ea5c9ced9237f212e"),
                10, new RootCheckpoint(119_986, "dd03f38f934fa3112bfa3b9a56135fa7ff557485548a2df3c263354621e58bc0"),
                15, new RootCheckpoint(120_029, "7de8f93dab08cf1e5cab9eb0586490801357de0d2610ac0e25a31e0d135c482e"),
                20, new RootCheckpoint(120_074, "12929d60d764997262fd0bb6dccf3abaa0e4a238b12e14e0bf9d2166340aba64"),
                25, new RootCheckpoint(120_086, "0de99df0de902c63e7170b1bace5d14098f5f06e913fc50ad6615b74ac4bc869"),
                30, new RootCheckpoint(120_071, "0b94bc73aefb5ed8c206164fe7efcb552816427947d5a6e529f891805e6e93ed"));

        for (int day = 0; day <= 30; day++) {
            RootCheckpoint checkpoint = expected.get(day);
            if (checkpoint != null) assertCanonicalCheckpoint(checkpoint, ReferenceCanonicalStateSettlements.capture(world), "settlements", day);
            if (day < 30) world.tick();
        }
    }

    @Test
    void canonicalStateInfectionOwnerMatchesPinnedPythonAtEveryCheckpoint() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.sourceV2());
        Map<Integer, RootCheckpoint> expected = Map.of(
                0, new RootCheckpoint(884_466, "2a2cc17309850ad97aeac4c5ea1a897ffca3a21a276ee15eddab7deecde5cfd7"),
                1, new RootCheckpoint(897_567, "921c0e9ba0ef5fc7b3efc4a6e7f8f3a0128428f9dee9213b9c90e8e7f1bbde7b"),
                2, new RootCheckpoint(897_887, "aad42999e6adeac9552bf93b75a07d877e9d6e15af94be8f5319a7c7f8f528f6"),
                3, new RootCheckpoint(898_445, "95067b3e31bf744fe3e0b348665cb69388ccaff2dece06b13268baf49c014805"),
                5, new RootCheckpoint(900_087, "08fac13f6d562259540a1bc706d6d6f7b2ad8bcb9a7d0d5ec7af4b6195dac3d5"),
                10, new RootCheckpoint(903_461, "f964c41f0c2b47f9f30f92a01d7c64597f39c4e7b28b0fc7cc65fe31f6197109"),
                15, new RootCheckpoint(909_889, "8644791484ccb0b86d7056750e30073c4a05ff12d479759166a4065caa07142a"),
                20, new RootCheckpoint(918_712, "04e454944c1c6ee4c3194bb2fb8af855d33e4abaa2ef5c7e640332dcc496d10e"),
                25, new RootCheckpoint(944_824, "01bbb32490a52ac16fa2bf70841b66e0ef85b6eb287b9a476d36d789cfe4dc72"),
                30, new RootCheckpoint(952_705, "d356c43df18f00c27088698459d28a1571af9a27c2b74fad7fe77f70809eb650"));

        for (int day = 0; day <= 30; day++) {
            RootCheckpoint checkpoint = expected.get(day);
            if (checkpoint != null) assertCanonicalCheckpoint(checkpoint, ReferenceCanonicalStateInfection.capture(world), "infection", day);
            if (day < 30) world.tick();
        }
    }

    @Test
    void canonicalStateInfectionRejectsV2DisabledWorld() {
        ReferenceWorld legacy = new ReferenceWorld(new ReferenceWorldConfig(
                64, 44, 12, 42L, 2, false, ReferenceSimulationProfile.SOURCE_V2));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> ReferenceCanonicalStateInfection.capture(legacy));

        assertEquals("canonical state requires V2-enabled reference world", error.getMessage());
    }

    private static void assertCheckpoint(ReferenceWorld world, Checkpoint expected) {
        ReferenceDailyWorldHistory actual = world.history().getLast();
        assertClose(expected.population(), actual.population());
        assertClose(expected.cash(), actual.cash());
        assertClose(expected.privateCash(), actual.privateCash());
        assertClose(expected.infection(), actual.infection());
        assertClose(expected.ecologyOrganic(), actual.ecologyOrganic());
        assertClose(expected.ecologyScar(), actual.ecologyScar());
        assertClose(expected.hiveBiomass(), actual.hiveBiomass());
        assertClose(expected.harvestedBiomass(), actual.harvestedBiomass());
        assertClose(expected.trade30d(), actual.trade30d());
        assertEquals(expected.frontCampaigns(), world.v2().frontCampaigns().size());
        assertEquals(expected.heldSectors(), world.v2().sectorControl().values().stream()
                .filter(control -> control.state() == ReferenceSectorControlState.HUMAN).count());
        assertEquals(expected.hiveSectors(), world.v2().sectorControl().values().stream()
                .filter(control -> control.state() == ReferenceSectorControlState.HIVE).count());
        assertEquals(expected.activeContracts(), world.microeconomy().summary().get("active_contracts"));
        assertClose(expected.credit(), (Double) world.microeconomy().summary().get("credit"));
    }

    private record Checkpoint(
            double population,
            double cash,
            double privateCash,
            double infection,
            double ecologyOrganic,
            double ecologyScar,
            double hiveBiomass,
            double harvestedBiomass,
            double trade30d,
            int frontCampaigns,
            long heldSectors,
            long hiveSectors,
            int activeContracts,
            double credit
    ) { }

    private record RootCheckpoint(int bytes, String digest) { }

    private static void assertCanonicalCheckpoint(RootCheckpoint expected, Map<String, Object> state, String owner, int day) {
        String json = ReferenceV2PublicSnapshot.canonicalJson(state);
        assertEquals(expected.bytes(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, owner + " bytes day " + day);
        assertEquals(expected.digest(), ReferenceV2PublicSnapshot.sha256(state), owner + " digest day " + day);
    }

    private static void assertClose(double expected, double actual) {
        assertEquals(expected, actual, 1.0e-9d);
    }
}
