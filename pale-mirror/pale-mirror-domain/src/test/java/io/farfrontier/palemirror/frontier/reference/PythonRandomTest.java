package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.Test;

class PythonRandomTest {
    @Test
    void matchesCpythonMt19937WordsForReferenceSeeds() {
        assertWords(0L, new long[] {3_626_764_237L, 1_654_615_998L, 3_255_389_356L, 3_823_568_514L, 1_806_341_205L, 173_879_092L});
        assertWords(7L, new long[] {1_390_851_128L, 4_071_050_724L, 647_892_279L, 1_695_753_998L, 2_795_742_288L, 207_388_624L});
        assertWords(2_000_003L, new long[] {2_932_146_834L, 2_409_250_082L, 170_545_941L, 270_188_133L, 1_751_703_304L, 4_291_093_003L});
    }

    @Test
    void matchesCpythonDoubleAndBoundedDrawSemantics() {
        PythonRandom random = new PythonRandom(7L);
        double[] values = {random.random(), random.random(), random.random(), random.random(), random.random(), random.random()};
        long[] bits = java.util.Arrays.stream(values).mapToLong(Double::doubleToRawLongBits).toArray();
        assertArrayEquals(new long[] {
                Double.doubleToRawLongBits(Double.valueOf("0x1.4b9ad0f953a6ep-2")),
                Double.doubleToRawLongBits(Double.valueOf("0x1.34f0696513270p-3")),
                Double.doubleToRawLongBits(Double.valueOf("0x1.4d474883171ffp-1")),
                Double.doubleToRawLongBits(Double.valueOf("0x1.28b2f3a47e100p-4")),
                Double.doubleToRawLongBits(Double.valueOf("0x1.125f2046063a0p-1")),
                Double.doubleToRawLongBits(Double.valueOf("0x1.767727ca98cc2p-2"))
        }, bits);

        PythonRandom ranges = new PythonRandom(7L);
        int[] randint = new int[8];
        int[] choices = new int[8];
        for (int index = 0; index < randint.length; index++) {
            randint[index] = ranges.randint(-19, 71);
        }
        assertArrayEquals(new int[] {22, 0, 31, 64, -13, -10, 49, -7}, randint);

        ranges = new PythonRandom(7L);
        for (int index = 0; index < choices.length; index++) {
            choices[index] = ranges.randBelow(13);
        }
        assertArrayEquals(new int[] {5, 2, 6, 10, 0, 1, 8, 1}, choices);
    }

    @Test
    void rejectsInvalidSourcePortPrimitiveBounds() {
        PythonRandom random = new PythonRandom(0L);
        assertEquals(0L, random.getRandBits(0));
        assertThrows(IllegalArgumentException.class, () -> random.getRandBits(33));
        assertThrows(IllegalArgumentException.class, () -> random.randBelow(0));
        assertThrows(IllegalArgumentException.class, () -> random.randint(2, 1));
    }

    @Test
    void statePayloadMatchesPythonGetstateAndRestoresExactly() {
        PythonRandom random = new PythonRandom(7L);
        PythonRandom.State state = random.state();
        assertEquals("7b43e30faa834f1a65ee7fd88a1ea6d696ed41192d7e7f981dc6e40c67ac1af0", stateDigest(state));

        long[] expected = new long[] {random.nextUInt32(), random.nextUInt32(), random.nextUInt32()};
        PythonRandom restored = new PythonRandom(0L);
        restored.restore(state);
        long[] actual = new long[] {restored.nextUInt32(), restored.nextUInt32(), restored.nextUInt32()};
        assertArrayEquals(expected, actual);

        long[] malformed = state.words();
        malformed[624] = 625L;
        assertThrows(IllegalArgumentException.class, () -> new PythonRandom.State(malformed));
    }

    private static void assertWords(long seed, long[] expected) {
        PythonRandom random = new PythonRandom(seed);
        long[] actual = new long[expected.length];
        for (int index = 0; index < actual.length; index++) {
            actual[index] = random.nextUInt32();
        }
        assertArrayEquals(expected, actual);
    }

    private static String stateDigest(PythonRandom.State state) {
        StringBuilder serialized = new StringBuilder();
        for (long word : state.words()) {
            if (!serialized.isEmpty()) {
                serialized.append(',');
            }
            serialized.append(word);
        }
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(serialized.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new AssertionError(failure);
        }
    }
}
