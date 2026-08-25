package io.farfrontier.palemirror.frontier.reference;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Bit-exact subset of CPython 3.11 {@code random.Random} for the source-port.
 *
 * <p>The reference world owns several independent MT19937 streams. Java's
 * {@link java.util.Random} and {@link java.util.random.RandomGenerator} have
 * different seed and bit-extraction rules, so substituting either one would
 * alter a same-seed settlement decision before any domain rule is evaluated.</p>
 */
public final class PythonRandom {
    private static final int STATE_SIZE = 624;
    private static final int TWIST_OFFSET = 397;
    private static final long UINT_MASK = 0xffff_ffffL;
    private static final long UPPER_MASK = 0x8000_0000L;
    private static final long LOWER_MASK = 0x7fff_ffffL;
    private static final long MATRIX_A = 0x9908_b0dfL;

    private final int[] state = new int[STATE_SIZE];
    private int index = STATE_SIZE;

    public PythonRandom(long seed) {
        this(BigInteger.valueOf(seed));
    }

    public PythonRandom(BigInteger seed) {
        seed(Objects.requireNonNull(seed, "seed"));
    }

    /** Equivalent to {@code Random.random()} for the seeded source profiles. */
    public double random() {
        long high = nextUInt32() >>> 5;
        long low = nextUInt32() >>> 6;
        return (high * 67_108_864.0 + low) * 0x1.0p-53;
    }

    /** Equivalent to {@code Random.uniform(lower, upper)} for finite bounds. */
    public double uniform(double lower, double upper) {
        return lower + (upper - lower) * random();
    }

    /** Equivalent to {@code Random.getrandbits(k)} for {@code 0 <= k <= 32}. */
    public long getRandBits(int bits) {
        if (bits < 0 || bits > 32) {
            throw new IllegalArgumentException("bit count must be between zero and 32");
        }
        if (bits == 0) {
            return 0L;
        }
        return nextUInt32() >>> (32 - bits);
    }

    /** Equivalent to {@code Random.randrange(bound)} for a positive int bound. */
    public int randBelow(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        int bits = Integer.SIZE - Integer.numberOfLeadingZeros(bound);
        long candidate;
        do {
            candidate = getRandBits(bits);
        } while (candidate >= bound);
        return (int) candidate;
    }

    /** Equivalent to inclusive {@code Random.randint(lower, upper)} for int bounds. */
    public int randint(int lower, int upper) {
        if (lower > upper) {
            throw new IllegalArgumentException("lower bound exceeds upper bound");
        }
        long size = (long) upper - lower + 1L;
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("range is too wide for the current source-port primitive");
        }
        return lower + randBelow((int) size);
    }

    /** Exposes one MT19937 word for source-conformance tests and trace tooling. */
    public long nextUInt32() {
        if (index >= STATE_SIZE) {
            twist();
        }
        long value = unsigned(state[index++]);
        value ^= value >>> 11;
        value ^= (value << 7) & 0x9d2c_5680L;
        value ^= (value << 15) & 0xefc6_0000L;
        value ^= value >>> 18;
        return value & UINT_MASK;
    }

    /**
     * Exact payload of Python {@code Random.getstate()[1]}: 624 unsigned words
     * followed by the next-word index. It is deliberately independent from a
     * Java object-serialization format so persistence can validate it itself.
     */
    public State state() {
        long[] words = new long[STATE_SIZE + 1];
        for (int stateIndex = 0; stateIndex < STATE_SIZE; stateIndex++) {
            words[stateIndex] = unsigned(state[stateIndex]);
        }
        words[STATE_SIZE] = index;
        return new State(words);
    }

    /** Restores a previously validated Python MT19937 state without reseeding. */
    public void restore(State restored) {
        Objects.requireNonNull(restored, "restored");
        long[] words = restored.words();
        for (int stateIndex = 0; stateIndex < STATE_SIZE; stateIndex++) {
            state[stateIndex] = (int) words[stateIndex];
        }
        index = (int) words[STATE_SIZE];
    }

    private void seed(BigInteger supplied) {
        BigInteger magnitude = supplied.abs();
        int words = Math.max(1, (magnitude.bitLength() + 31) / 32);
        long[] key = new long[words];
        for (int word = 0; word < words; word++) {
            key[word] = magnitude.shiftRight(word * 32).longValue() & UINT_MASK;
        }
        initByArray(key);
    }

    private void initByArray(long[] key) {
        initGenRand(19_650_218L);
        int stateIndex = 1;
        int keyIndex = 0;
        int steps = Math.max(STATE_SIZE, key.length);
        for (; steps > 0; steps--) {
            long previous = unsigned(state[stateIndex - 1]);
            long mixed = unsigned(state[stateIndex]) ^ ((previous ^ (previous >>> 30)) * 1_664_525L);
            state[stateIndex] = (int) ((mixed + key[keyIndex] + keyIndex) & UINT_MASK);
            stateIndex++;
            keyIndex++;
            if (stateIndex >= STATE_SIZE) {
                state[0] = state[STATE_SIZE - 1];
                stateIndex = 1;
            }
            if (keyIndex >= key.length) {
                keyIndex = 0;
            }
        }
        for (steps = STATE_SIZE - 1; steps > 0; steps--) {
            long previous = unsigned(state[stateIndex - 1]);
            long mixed = unsigned(state[stateIndex]) ^ ((previous ^ (previous >>> 30)) * 1_566_083_941L);
            state[stateIndex] = (int) ((mixed - stateIndex) & UINT_MASK);
            stateIndex++;
            if (stateIndex >= STATE_SIZE) {
                state[0] = state[STATE_SIZE - 1];
                stateIndex = 1;
            }
        }
        state[0] = (int) UPPER_MASK;
    }

    private void initGenRand(long seed) {
        state[0] = (int) (seed & UINT_MASK);
        for (int stateIndex = 1; stateIndex < STATE_SIZE; stateIndex++) {
            long previous = unsigned(state[stateIndex - 1]);
            state[stateIndex] = (int) ((1_812_433_253L * (previous ^ (previous >>> 30)) + stateIndex) & UINT_MASK);
        }
        index = STATE_SIZE;
    }

    private void twist() {
        for (int stateIndex = 0; stateIndex < STATE_SIZE; stateIndex++) {
            long mixed = (unsigned(state[stateIndex]) & UPPER_MASK)
                    | (unsigned(state[(stateIndex + 1) % STATE_SIZE]) & LOWER_MASK);
            long source = unsigned(state[(stateIndex + TWIST_OFFSET) % STATE_SIZE]);
            state[stateIndex] = (int) (source ^ (mixed >>> 1) ^ ((mixed & 1L) == 0L ? 0L : MATRIX_A));
        }
        index = 0;
    }

    private static long unsigned(int value) {
        return value & UINT_MASK;
    }

    /** Immutable, validated unsigned MT19937 word payload. */
    public record State(long[] words) {
        public State {
            words = words == null ? null : words.clone();
            if (words == null || words.length != STATE_SIZE + 1) {
                throw new IllegalArgumentException("Python MT19937 state must contain 625 words");
            }
            for (int stateIndex = 0; stateIndex < STATE_SIZE; stateIndex++) {
                if (words[stateIndex] < 0L || words[stateIndex] > UINT_MASK) {
                    throw new IllegalArgumentException("Python MT19937 state contains a non-unsigned word");
                }
            }
            if (words[STATE_SIZE] < 0L || words[STATE_SIZE] > STATE_SIZE) {
                throw new IllegalArgumentException("Python MT19937 state has an invalid index");
            }
        }

        @Override
        public long[] words() {
            return words.clone();
        }
    }
}
