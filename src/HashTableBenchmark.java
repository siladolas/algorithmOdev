// ===============================
// HashTableBenchmark.java
// ===============================

import java.util.*;

/**
 * Benchmark driver for the custom chaining hash table.
 * What it does:
 *  - Generates double keys from three distributions (Uniform, Gaussian, Exponential).
 *  - Runs two workloads:
 *      (1) build-only (pure inserts)
 *      (2) mixed (50% insert, 25% get, 25% remove)
 *  - Compares our table vs Java's HashMap<Double, Integer>.
 *  - Prints basic stats and writes CSV-friendly lines (handled by Main).
 *
 * Notes :
 *  - We keep RNG seeds fixed so runs are reproducible (good for debugging).
 *  - Distinctness: we ensure unique double bit patterns using a HashSet<Long>.
 *    This avoids accidental duplicates that could hide collision behavior.
 */
public class HashTableBenchmark {

    // ========== Distributions ==========
    // Each distribution wraps a java.util.Random with a fixed seed.

    /** Uniform over [min, max]. */
    public static class UniformDistribution {
        public final Random rand;
        public final double min;
        public final double max;

        public UniformDistribution(double min, double max, long seed) {
            this.rand = new Random(seed);
            this.min = min;
            this.max = max;
        }

        /** Next uniform sample in [min, max]. */
        public double next() {
            return min + (max - min) * rand.nextDouble();
        }

        @Override
        public String toString() {
            return String.format("Uniform[%.1f, %.1f]", min, max);
        }
    }

    /** Gaussian (Normal) with mean and stddev. Uses Random.nextGaussian(). */
    public static class GaussianDistribution {
        private final Random rand;
        private final double mean;
        private final double stdDev;

        public GaussianDistribution(double mean, double stdDev, long seed) {
            this.rand = new Random(seed);
            this.mean = mean;
            this.stdDev = stdDev;
        }

        /** Next Gaussian sample (unbounded support). */
        public double next() {
            return mean + stdDev * rand.nextGaussian();
        }

        @Override
        public String toString() {
            return String.format("Gaussian(μ=%.1f, σ=%.1f)", mean, stdDev);
        }
    }

    /** Exponential with rate lambda (λ). Mean = 1/λ. */
    public static class ExponentialDistribution {
        private final Random rand;
        private final double lambda;

        public ExponentialDistribution(double lambda, long seed) {
            this.rand = new Random(seed);
            this.lambda = lambda;
        }

        /** Inverse CDF sampling: -ln(1 - U) / λ. */
        public double next() {
            return -Math.log(1.0 - rand.nextDouble()) / lambda;
        }

        @Override
        public String toString() {
            return String.format("Exponential(λ=%.4f, mean=%.1f)", lambda, 1.0/lambda);
        }
    }

    /**
     * Generate n distinct double keys (bit-wise) from a distribution.
     * We use a HashSet<Long> (raw bits) to ensure exact uniqueness.
     * A safety cap stops pathological infinite loops if the distribution
     * keeps producing duplicates (unlikely but safe to guard against).
     */
    public static List<Double> generateDistinctKeys(Object distribution, int n, long seed) {
        Set<Long> seenBits = new HashSet<Long>();
        List<Double> keys = new ArrayList<Double>(n);
        int attempts = 0;
        int maxAttempts = n * 10; // Prevent infinite loop

        while (keys.size() < n && attempts < maxAttempts) {
            double key;
            if (distribution instanceof UniformDistribution) {
                key = ((UniformDistribution) distribution).next();
            } else if (distribution instanceof GaussianDistribution) {
                key = ((GaussianDistribution) distribution).next();
            } else if (distribution instanceof ExponentialDistribution) {
                key = ((ExponentialDistribution) distribution).next();
            } else {
                throw new IllegalArgumentException("Unknown distribution type");
            }

            long bits = Double.doubleToRawLongBits(key);
            if (seenBits.add(bits)) {
                keys.add(key);
            }
            attempts++;
        }

        if (keys.size() < n) {
            throw new RuntimeException("Could not generate " + n + " distinct keys after " + attempts + " attempts");
        }

        return keys;
    }

    /**
     * Build-only workload: just insert every key once.
     * This shows pure insertion throughput and final chain stats.
     */
    public static void buildOnlyWorkload(MyHashTable table, List<Double> keys) {
        for (int i = 0; i < keys.size(); i++) {
            table.put(keys.get(i), i);
        }
    }

    /** Same build-only workload but using HashMap as a baseline. */
    public static void buildOnlyWorkloadBaseline(HashMap<Double, Integer> table, List<Double> keys) {
        for (int i = 0; i < keys.size(); i++) {
            table.put(keys.get(i), i);
        }
    }

    /**
     * Mixed workload: inserts/searches/deletes interleaved randomly.
     * Ratios: 50% put, 25% get, 25% remove.
     * We keep a dynamic list of current keys to sample from.
     */
    public static void mixedWorkload(MyHashTable table, List<Double> keys, int n, long seed) {
        Random rand = new Random(seed);
        List<Double> currentKeys = new ArrayList<Double>();
        int keyIndex = 0;

        for (int op = 0; op < n; op++) {
            double r = rand.nextDouble();

            if (r < 0.5) {
                // Insert (50%)
                if (keyIndex < keys.size()) {
                    double key = keys.get(keyIndex);
                    table.put(key, keyIndex);
                    currentKeys.add(key);
                    keyIndex++;
                }
            } else if (r < 0.75) {
                // Search (25%)
                if (!currentKeys.isEmpty()) {
                    int idx = rand.nextInt(currentKeys.size());
                    table.get(currentKeys.get(idx));
                }
            } else {
                // Delete (25%)
                if (!currentKeys.isEmpty()) {
                    int idx = rand.nextInt(currentKeys.size());
                    double key = currentKeys.remove(idx);
                    table.remove(key);
                }
            }
        }
    }

    /** Mixed workload baseline using HashMap. */
    public static void mixedWorkloadBaseline(HashMap<Double, Integer> table, List<Double> keys, int n, long seed) {
        Random rand = new Random(seed);
        List<Double> currentKeys = new ArrayList<Double>();
        int keyIndex = 0;

        for (int op = 0; op < n; op++) {
            double r = rand.nextDouble();

            if (r < 0.5) {
                if (keyIndex < keys.size()) {
                    double key = keys.get(keyIndex);
                    table.put(key, keyIndex);
                    currentKeys.add(key);
                    keyIndex++;
                }
            } else if (r < 0.75) {
                if (!currentKeys.isEmpty()) {
                    int idx = rand.nextInt(currentKeys.size());
                    table.get(currentKeys.get(idx));
                }
            } else {
                if (!currentKeys.isEmpty()) {
                    int idx = rand.nextInt(currentKeys.size());
                    double key = currentKeys.remove(idx);
                    table.remove(key);
                }
            }
        }
    }

    /**
     * Run 5 trials for a (distribution, n, workload) triple.
     * Records average times and basic table stats (from the last trial).
     * Also emits CSV rows via the provided PrintWriter.
     */
    public static void runBenchmark(String distName, Object distribution, int n, String workloadType,
                                    java.io.PrintWriter csvWriter) {
        System.out.println("\n================================================================================");
        System.out.println("Distribution: " + distName);
        System.out.println("Problem size: n=" + n);
        System.out.println("Workload: " + workloadType);
        System.out.println("================================================================================");

        final int NUM_TRIALS = 5;
        final long BASE_SEED = 1234; // trial seed = BASE_SEED + trial

        long[] myTableTimes = new long[NUM_TRIALS];
        long[] baselineTimes = new long[NUM_TRIALS];
        String myTableStats = "";

        for (int trial = 0; trial < NUM_TRIALS; trial++) {
            long trialSeed = BASE_SEED + trial;
            System.out.printf("\n--- Trial %d/%d (seed=%d) ---\n", trial + 1, NUM_TRIALS, trialSeed);

            // We generate 2n keys so the mixed workload has room to insert later
            List<Double> keys = generateDistinctKeys(distribution, n * 2, trialSeed);

            // ---- Custom table ----
            MyHashTable myTable = new MyHashTable();
            long startTime = System.nanoTime();

            if (workloadType.equals("build-only")) {
                buildOnlyWorkload(myTable, keys.subList(0, n));
            } else {
                mixedWorkload(myTable, keys, n, trialSeed);
            }

            long endTime = System.nanoTime();
            myTableTimes[trial] = endTime - startTime;

            if (trial == NUM_TRIALS - 1) {
                myTableStats = myTable.stats(); // capture stats from last run
            }

            System.out.printf("MyHashTable: %.2f ms\n", myTableTimes[trial] / 1_000_000.0);

            // ---- Baseline (HashMap) ----
            HashMap<Double, Integer> baselineTable = new HashMap<Double, Integer>();
            startTime = System.nanoTime();

            if (workloadType.equals("build-only")) {
                buildOnlyWorkloadBaseline(baselineTable, keys.subList(0, n));
            } else {
                mixedWorkloadBaseline(baselineTable, keys, n, trialSeed);
            }

            endTime = System.nanoTime();
            baselineTimes[trial] = endTime - startTime;

            System.out.printf("HashMap: %.2f ms\n", baselineTimes[trial] / 1_000_000.0);
        }

        // Average over 5 trials
        long avgMyTableTime = average(myTableTimes);
        long avgBaselineTime = average(baselineTimes);

        System.out.println("\n--- AVERAGE RESULTS (5 trials) ---");
        System.out.println("MyHashTable:");
        System.out.println("  " + myTableStats);
        System.out.printf("  Avg Time: %.2f ms\n", avgMyTableTime / 1_000_000.0);

        System.out.println("\nHashMap:");
        System.out.printf("  Avg Time: %.2f ms\n", avgBaselineTime / 1_000_000.0);

        // ops equals n here (both workloads execute n operations "units")
        int ops = n;

        // Throughput = operations per second
        double myThroughput = (ops * 1_000_000_000.0) / avgMyTableTime;
        double baselineThroughput = (ops * 1_000_000_000.0) / avgBaselineTime;

        System.out.printf("\nThroughput:\n");
        System.out.printf("  MyHashTable: %.0f ops/sec\n", myThroughput);
        System.out.printf("  HashMap: %.0f ops/sec\n", baselineThroughput);

        // Parse stats for CSV columns
        String[] statParts = parseStats(myTableStats);
        String distParams = getDistributionParams(distribution);

        // Emit two CSV rows: our table and the baseline
        csvWriter.printf("%s,\"%s\",%d,%s,%s,%d,%d,%.2f,%s,%s,%s\n",
                distName, distParams, n, workloadType, "student",
                avgMyTableTime, ops, myThroughput,
                statParts[0], statParts[1], statParts[2]);

        csvWriter.printf("%s,\"%s\",%d,%s,%s,%d,%d,%.2f,%s,%s,%s\n",
                distName, distParams, n, workloadType, "hashmap",
                avgBaselineTime, ops, baselineThroughput,
                "-1", "-1", "-1"); // baseline has no chain stats

        csvWriter.flush(); // make sure data hits disk
    }

    /** Simple integer average for long array (no overflow worries here). */
    private static long average(long[] values) {
        long sum = 0;
        for (long v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    /**
     * Extract maxChain, meanChain, and loadFactor substrings from stats().
     * Expected format:
     * "M=X, size=Y, loadFactor=Z, maxChainLength=A, meanChainLength=B"
     */
    private static String[] parseStats(String stats) {
        String[] result = new String[3];

        try {
            String[] parts = stats.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("maxChainLength=")) {
                    result[0] = part.substring("maxChainLength=".length());
                } else if (part.startsWith("meanChainLength=")) {
                    result[1] = part.substring("meanChainLength=".length());
                } else if (part.startsWith("loadFactor=")) {
                    result[2] = part.substring("loadFactor=".length());
                }
            }
        } catch (Exception e) {
            // Fallbacks keep CSV shape stable if parsing fails
            result[0] = "0";
            result[1] = "0.0";
            result[2] = "0.0";
        }

        return result;
    }

    /** Pretty-print parameters for CSV, per distribution type. */
    private static String getDistributionParams(Object dist) {
        if (dist instanceof UniformDistribution) {
            UniformDistribution u = (UniformDistribution) dist;
            return String.format("min=%.1f max=%.1f", u.min, u.max);
        } else if (dist instanceof GaussianDistribution) {
            GaussianDistribution g = (GaussianDistribution) dist;
            return String.format("mean=%.1f stddev=%.1f", g.mean, g.stdDev);
        } else if (dist instanceof ExponentialDistribution) {
            ExponentialDistribution e = (ExponentialDistribution) dist;
            return String.format("lambda=%.4f", e.lambda);
        }
        return "unknown";
    }

    // Helper to emulate String.repeat for Java 8 compatibility.
    public static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
}
