// ===============================
// Main.java
// ===============================

import java.io.*;

public class Main {
    /**
     * Entry point: sets up distributions, picks problem sizes, runs both workloads,
     * and writes results to a CSV file (benchmark_results.csv).
     *
     * Why CSV? Easy to import into Excel/Sheets/Pandas and plot later.
     */
    public static void main(String[] args) {
        // ===== Banner =====
        System.out.println("================================================================================");
        System.out.println("        Hash Table Performance Benchmark");
        System.out.println("        Custom Implementation vs Java HashMap");
        System.out.println("        Running 5 trials per configuration");
        System.out.println("================================================================================");

        // ===== CSV setup =====
        PrintWriter csvWriter = null;
        try {
            csvWriter = new PrintWriter(new FileWriter("benchmark_results.csv"));
            // Column header
            csvWriter.println("distribution,params,n,workload,impl,avg_time_ns,ops,throughput_ops_per_s,max_chain,mean_chain,load_factor");
        } catch (IOException e) {
            System.err.println("Error creating CSV file: " + e.getMessage());
            return;
        }

        // ===== Define distributions (seeded for reproducibility) =====
        System.out.println("\nDISTRIBUTION PARAMETERS:");
        System.out.println("----------------------------------------------------------------------");

        HashTableBenchmark.UniformDistribution uniform =
                new HashTableBenchmark.UniformDistribution(0.0, 1000.0, 42);
        HashTableBenchmark.GaussianDistribution gaussian =
                new HashTableBenchmark.GaussianDistribution(500.0, 100.0, 42);
        HashTableBenchmark.ExponentialDistribution exponential =
                new HashTableBenchmark.ExponentialDistribution(0.005, 42);

        System.out.println("1. " + uniform);
        System.out.println("2. " + gaussian);
        System.out.println("3. " + exponential);

        // ===== Compute problem sizes n = 10^k =====
        System.out.println("\nPROBLEM SIZES (n = 10^k):");
        System.out.println("----------------------------------------------------------------------");

        double[] exponents = {3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0};
        int[] problemSizes = new int[exponents.length];

        for (int i = 0; i < exponents.length; i++) {
            problemSizes[i] = (int) Math.round(Math.pow(10, exponents[i]));
            System.out.printf("  k=%.1f -> n=%,7d%n", exponents[i], problemSizes[i]);
        }

        // ===== What we are going to test =====
        System.out.println("\nTEST CONFIGURATION:");
        System.out.println("----------------------------------------------------------------------");

        Object[][] distributions = {
                {"Uniform[0, 1000]", uniform},
                {"Gaussian(μ=500, σ=100)", gaussian},
                {"Exponential(λ=0.005)", exponential}
        };

        String[] workloads = {"build-only", "mixed"};

        // For a quick run, test a subset; for the full experiment, use all problemSizes.
        int[] testSizes = {problemSizes[0], problemSizes[2], problemSizes[4]};
        // FULL TEST: int[] testSizes = problemSizes;

        System.out.println("Distributions: " + distributions.length);
        System.out.println("Workloads: " + workloads.length);
        System.out.println("Test sizes: " + testSizes.length);
        System.out.println("Trials per config: 5");
        System.out.println("Base seed: 1234 (trial seed = 1234 + trialIndex)");
        System.out.println("\nTesting with sizes:");
        for (int size : testSizes) {
            System.out.printf("  * %,d%n", size);
        }

        int totalTests = distributions.length * testSizes.length * workloads.length;
        System.out.println("\nTotal configurations: " + totalTests);
        System.out.println("Total runs (with 5 trials each): " + (totalTests * 5));

        // ===== Run all benchmarks =====
        System.out.println("\n" + HashTableBenchmark.repeat("=", 80));
        System.out.println("STARTING BENCHMARKS...");
        System.out.println(HashTableBenchmark.repeat("=", 80));

        int configCount = 0;
        long overallStartTime = System.currentTimeMillis();

        for (Object[] dist : distributions) {
            String distName = (String) dist[0];
            Object distObj = dist[1];

            for (int n : testSizes) {
                for (String workload : workloads) {
                    configCount++;
                    System.out.println("\n[Configuration " + configCount + "/" + totalTests + "]");
                    try {
                        HashTableBenchmark.runBenchmark(distName, distObj, n, workload, csvWriter);
                    } catch (Exception e) {
                        // We keep going so one bad config doesn't kill the whole run
                        System.err.println("ERROR in benchmark: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }

        long overallEndTime = System.currentTimeMillis();
        double totalTimeSeconds = (overallEndTime - overallStartTime) / 1000.0;

        // ===== Summary =====
        System.out.println("\n================================================================================");
        System.out.println("BENCHMARK COMPLETED!");
        System.out.println("Total configurations run: " + configCount);
        System.out.println("Total time: " + String.format("%.2f", totalTimeSeconds) + " seconds");
        System.out.println("Results saved to: benchmark_results.csv");
        System.out.println("================================================================================");

        // Always close your writers :)
        csvWriter.close();
    }
}
