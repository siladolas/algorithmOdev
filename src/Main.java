/* 
Hashing strategy:
We treated doubles by their exact raw IEEE-754 bits (Double.doubleToRawLongBits),
then we applied a SplitMix64-style and folded them to 32 bits to produce the bucket index because it is better than using xor.
Equality is checked by raw-bit equality,so +0.0 and -0.0 remain distinct and NaN payloads are preserved. 
This keeps distribution artifacts from the input from directly degrading the index function.


Results:
Uniform inputs yield the shortest chains and highest throughput. Gaussian inputs
produce only slightly longer chains and a small throughput drop. Exponential (skewed)
data increases local clustering and max chain lengths noticeably, which lowers
throughput. Overall, the student table is competitive with Java's default HashMap library:
it approaches HashMap under uniform data and stays robust under skew thanks to
the mixing step, though HashMap retains a small constant throughput advantage.


Distributions and parameters we used:

- Uniform dist. [0.0, 1000.0]
- Gaussian dist. (mean=500.0, stddev=100.0)
- Exponential dist. (lambda=0.005)  (mean = 200)

Seed policy:
We used base seed of 1234 and derive per-trial seeds as 1234 + trialIndex; the
same per-trial seed is used for both implementations to ensure a fair comparison.
*/

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class Main {
    private static final boolean VERBOSE = false; 

    public static void main(String[] args) {
        
        HashTableBenchmark.UniformDistribution uniform =
                new HashTableBenchmark.UniformDistribution(0.0, 1000.0, 42);
        HashTableBenchmark.GaussianDistribution gaussian =
                new HashTableBenchmark.GaussianDistribution(500.0, 100.0, 42);
        HashTableBenchmark.ExponentialDistribution exponential =
                new HashTableBenchmark.ExponentialDistribution(0.005, 42);

      
        System.out.println("Distributions at runtime:");
        System.out.println("  " + uniform);
        System.out.println("  " + gaussian);
        System.out.println("  " + exponential);

    
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
