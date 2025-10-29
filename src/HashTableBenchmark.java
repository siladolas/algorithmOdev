import java.util.*;

/**
 * Benchmark program for custom hash table implementation.
 * Tests with three distributions and compares against Java's HashMap.
 * 
 * DISTRIBUTION PARAMETERS:
 * ------------------------
 * 1. Uniform: [0.0, 1000.0]
 * 2. Gaussian: μ=500.0, σ=100.0
 * 3. Exponential: λ=0.005 (mean=200.0)
 * 
 * PROBLEM SIZES:
 * n = 10^k where k ∈ {3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0}
 * i.e., [1000, 3162, 10000, 31622, 100000, 316227, 1000000]
 */
public class HashTableBenchmark {
    
    // Distribution generators - PUBLIC olmalı ki Main erişebilsin
    public static class UniformDistribution {
        public final Random rand;
        public final double min;
        public final double max;
        
        public UniformDistribution(double min, double max, long seed) {
            this.rand = new Random(seed);
            this.min = min;
            this.max = max;
        }
        
        public double next() {
            return min + (max - min) * rand.nextDouble();
        }
        
        @Override
        public String toString() {
            return String.format("Uniform[%.1f, %.1f]", min, max);
        }
    }
    
    public static class GaussianDistribution {
        private final Random rand;
        private final double mean;
        private final double stdDev;
        
        public GaussianDistribution(double mean, double stdDev, long seed) {
            this.rand = new Random(seed);
            this.mean = mean;
            this.stdDev = stdDev;
        }
        
        public double next() {
            return mean + stdDev * rand.nextGaussian();
        }
        
        @Override
        public String toString() {
            return String.format("Gaussian(μ=%.1f, σ=%.1f)", mean, stdDev);
        }
    }
    
    public static class ExponentialDistribution {
        private final Random rand;
        private final double lambda;
        
        public ExponentialDistribution(double lambda, long seed) {
            this.rand = new Random(seed);
            this.lambda = lambda;
        }
        
        public double next() {
            return -Math.log(1.0 - rand.nextDouble()) / lambda;
        }
        
        @Override
        public String toString() {
            return String.format("Exponential(λ=%.4f, mean=%.1f)", lambda, 1.0/lambda);
        }
    }
    
    /**
     * Generate n distinct keys from given distribution.
     * Uses HashSet to ensure uniqueness at bit level.
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
     * Build-only workload: insert n distinct keys
     */
    public static void buildOnlyWorkload(MyHashTable table, List<Double> keys) {
        for (int i = 0; i < keys.size(); i++) {
            table.put(keys.get(i), i);
        }
    }
    
    public static void buildOnlyWorkloadBaseline(HashMap<Double, Integer> table, List<Double> keys) {
        for (int i = 0; i < keys.size(); i++) {
            table.put(keys.get(i), i);
        }
    }
    
    /**
     * Mixed workload: 50% insert, 25% search, 25% delete
     * Operations are randomly interleaved.
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
     * Run complete benchmark for one configuration with 5 trials and CSV output
     */
    public static void runBenchmark(String distName, Object distribution, int n, String workloadType,
                                   java.io.PrintWriter csvWriter) {
        System.out.println("\n================================================================================");
        System.out.println("Distribution: " + distName);
        System.out.println("Problem size: n=" + n);
        System.out.println("Workload: " + workloadType);
        System.out.println("================================================================================");
        
        final int NUM_TRIALS = 5;
        final long BASE_SEED = 1234;
        
        // Arrays to store trial results
        long[] myTableTimes = new long[NUM_TRIALS];
        long[] baselineTimes = new long[NUM_TRIALS];
        String myTableStats = "";
        
        // Run 5 trials
        for (int trial = 0; trial < NUM_TRIALS; trial++) {
            long trialSeed = BASE_SEED + trial;
            System.out.printf("\n--- Trial %d/%d (seed=%d) ---\n", trial + 1, NUM_TRIALS, trialSeed);
            
            // Generate keys with trial-specific seed
            List<Double> keys = generateDistinctKeys(distribution, n * 2, trialSeed);
            
            // Test custom hash table
            MyHashTable myTable = new MyHashTable();
            long startTime = System.nanoTime();
            
            if (workloadType.equals("build-only")) {
                buildOnlyWorkload(myTable, keys.subList(0, n));
            } else {
                mixedWorkload(myTable, keys, n, trialSeed);
            }
            
            long endTime = System.nanoTime();
            myTableTimes[trial] = endTime - startTime;
            
            // Store stats from last trial
            if (trial == NUM_TRIALS - 1) {
                myTableStats = myTable.stats();
            }
            
            System.out.printf("MyHashTable: %.2f ms\n", myTableTimes[trial] / 1_000_000.0);
            
            // Test baseline HashMap with SAME seed
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
        
        // Calculate averages
        long avgMyTableTime = average(myTableTimes);
        long avgBaselineTime = average(baselineTimes);
        
        System.out.println("\n--- AVERAGE RESULTS (5 trials) ---");
        System.out.println("MyHashTable:");
        System.out.println("  " + myTableStats);
        System.out.printf("  Avg Time: %.2f ms\n", avgMyTableTime / 1_000_000.0);
        
        System.out.println("\nHashMap:");
        System.out.printf("  Avg Time: %.2f ms\n", avgBaselineTime / 1_000_000.0);
        
        // Calculate operations count
        int ops = workloadType.equals("build-only") ? n : n;
        
        // Calculate throughput (operations per second)
        double myThroughput = (ops * 1_000_000_000.0) / avgMyTableTime;
        double baselineThroughput = (ops * 1_000_000_000.0) / avgBaselineTime;
        
        System.out.printf("\nThroughput:\n");
        System.out.printf("  MyHashTable: %.0f ops/sec\n", myThroughput);
        System.out.printf("  HashMap: %.0f ops/sec\n", baselineThroughput);
        
        // Parse stats for CSV
        String[] statParts = parseStats(myTableStats);
        
        // Get distribution parameters
        String distParams = getDistributionParams(distribution);
        
        // Write to CSV - MyHashTable
        csvWriter.printf("%s,\"%s\",%d,%s,%s,%d,%d,%.2f,%s,%s,%s\n",
            distName, distParams, n, workloadType, "student",
            avgMyTableTime, ops, myThroughput,
            statParts[0], statParts[1], statParts[2]);
        
        // Write to CSV - HashMap baseline
        csvWriter.printf("%s,\"%s\",%d,%s,%s,%d,%d,%.2f,%s,%s,%s\n",
            distName, distParams, n, workloadType, "hashmap",
            avgBaselineTime, ops, baselineThroughput,
            "-1", "-1", "-1");
        
        csvWriter.flush(); // Make sure data is written
    }
    
    /**
     * Calculate average of array
     */
    private static long average(long[] values) {
        long sum = 0;
        for (long v : values) {
            sum += v;
        }
        return sum / values.length;
    }
    
    /**
     * Parse stats string to extract maxChain, meanChain, loadFactor
     */
    private static String[] parseStats(String stats) {
        // Format: "M=X, size=Y, loadFactor=Z, maxChainLength=A, meanChainLength=B"
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
            result[0] = "0";
            result[1] = "0.0";
            result[2] = "0.0";
        }
        
        return result;
    }
    
    /**
     * Get distribution parameters as string
     */
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
    
    // Helper method for String.repeat() (Java 8 compatible)
    public static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
}