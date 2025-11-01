/**
 * CENG303 HW1 – Summary (≤250 words)
 * Hashing strategy: We hash doubles by taking their exact raw bits
 * (Double.doubleToRawLongBits), then apply a SplitMix64-style mixing to
 * decorrelate bit patterns. We fold to 32-bit and index by a power-of-two mask.
 * Equality uses raw-bit equality, so +0.0 and -0.0 are different and NaN payloads
 * are preserved. This strategy avoids modulo on doubles and keeps chains short
 * even under non-uniform inputs.
 *
 * Distributions & params (data are doubles only):
 *   • Uniform[0, 1000]
 *   • Gaussian(μ=500, σ=100)
 *   • Exponential(λ=0.005)  (mean = 200)
 *
 * Seed policy: baseSeed = 1234; per-trial seed = 1234 + trialIndex (0-based).
 * The SAME per-trial seed is used for both implementations within a trial to
 * ensure fairness.
 *
 * Workloads:
 *   • Build-only: insert n DISTINCT keys (duplicates by raw-bit equality are
 *     resampled).
 *   • Mixed: exactly n ops from empty: 50% insert, 25% search, 25% delete; targets
 *     chosen uniformly from currently present keys.
 *
 * Sizes: n = 10^k, k ∈ {3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0}.
 * Timing: System.nanoTime(), 5 trials averaged per (distribution, n, workload).
 *
 * Results (expected trends): Non-uniform shapes (e.g., Exponential) increase
 * local clustering; our mix function keeps max/mean chain lengths moderate and
 * throughput competitive. HashMap remains a strong baseline; our table approaches
 * it under uniform data and stays robust under skew.
 *
 * Java 8, standard library only. Program is headless; all results go to CSV.
 */

import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;

public class Main {
    private static final boolean VERBOSE = false; // no console I/O during timing; print outside only

    public static void main(String[] args) {
        // === Define distributions (PRINT PARAMS AT RUNTIME, before any timing) ===
        HashTableBenchmark.UniformDistribution uniform =
                new HashTableBenchmark.UniformDistribution(0.0, 1000.0, 42);
        HashTableBenchmark.GaussianDistribution gaussian =
                new HashTableBenchmark.GaussianDistribution(500.0, 100.0, 42);
        HashTableBenchmark.ExponentialDistribution exponential =
                new HashTableBenchmark.ExponentialDistribution(0.005, 42);

        // Required by spec: print params at runtime (outside timing windows)
        System.out.println("Distributions at runtime:");
        System.out.println("  " + uniform);
        System.out.println("  " + gaussian);
        System.out.println("  " + exponential);

        // === Open CSV in APPEND mode; write header only if file absent/empty ===
        File file = new File("benchmark_results.csv");
        boolean writeHeader = !file.exists() || file.length() == 0;
        PrintWriter csvWriter;
        try {
            csvWriter = new PrintWriter(new FileWriter(file, /*append*/ true));
            if (writeHeader) {
                csvWriter.println("distribution,params,n,workload,impl,avg_time_ns,ops,throughput_ops_per_s,max_chain,mean_chain,load_factor");
                csvWriter.flush();
            }
        } catch (IOException e) {
            return;
        }

        Object[][] distributions = {
                {"Uniform", uniform},
                {"Gaussian", gaussian},
                {"Exponential", exponential}
        };

        String[] workloads = {"build-only", "mixed"};
        double[] exponents = {3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0};

        int[] problemSizes = new int[exponents.length];
        for (int i = 0; i < exponents.length; i++) {
            problemSizes[i] = (int) Math.round(Math.pow(10, exponents[i]));
        }

        int[] testSizes = problemSizes; // full run

        long overallStart = System.currentTimeMillis();
        int total = distributions.length * testSizes.length * workloads.length;
        int done = 0;

        for (Object[] dist : distributions) {
            String distName = (String) dist[0];
            HashTableBenchmark.Dist distObj = (HashTableBenchmark.Dist) dist[1];
            long distStart = System.currentTimeMillis();

            for (int n : testSizes) {
                for (String workload : workloads) {
                    try {
                        // NOTE: runBenchmark() performs all timing with System.nanoTime()
                        // and does not write to console during timing.
                        HashTableBenchmark.runBenchmark(distName, distObj, n, workload, csvWriter);
                        done++;
                        if (VERBOSE) {
                            long elapsed = System.currentTimeMillis() - overallStart;
                            double avgPer = elapsed / (double) done;
                            int remain = total - done;
                            double eta = (avgPer * remain) / 1000.0;
                            System.out.printf("Progress: %d/%d (%.1f%%), Elapsed=%.1fs, ETA=%.1fs%n",
                                    done, total, 100.0 * done / total, elapsed / 1000.0, eta);
                        }
                    } catch (Exception e) {
                        if (VERBOSE) {
                            System.out.println("Error in benchmark: " + distName + ", n=" + n + ", workload=" + workload + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                        done++;
                    }
                }
            }
            if (VERBOSE) {
                long distEnd = System.currentTimeMillis();
                System.out.printf("Completed %s in %.1fs%n", distName, (distEnd - distStart) / 1000.0);
            }
        }

        csvWriter.flush();
        csvWriter.close();

        if (VERBOSE) {
            long overallEnd = System.currentTimeMillis();
            double sec = (overallEnd - overallStart) / 1000.0;
            System.out.printf("%nTotal execution time: %.1f seconds (%.1f minutes)%n", sec, sec / 60.0);
        }
    }
}
