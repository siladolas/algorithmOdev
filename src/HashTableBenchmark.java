// ===============================
// HashTableBenchmark.java
// ===============================

import java.util.SplittableRandom;
import java.util.HashMap;
import java.util.HashSet;

public class HashTableBenchmark {

    // ===== Distributions (SplittableRandom tabanlı) =====
    public interface Dist { double next(); void reseed(long seed); }

    public static class UniformDistribution implements Dist {
        private final double min, span; private SplittableRandom rng;
        public UniformDistribution(double min, double max, long seed){ this.min=min; this.span=max-min; this.rng=new SplittableRandom(seed); }
        public void reseed(long seed){ this.rng = new SplittableRandom(seed); }
        public double next(){ return min + span * rng.nextDouble(); }
        public double min(){ return min; } public double max(){ return min+span; }
        @Override public String toString(){ return String.format("Uniform[%.1f;%.1f]", min, min+span); }
    }

    public static class ExponentialDistribution implements Dist {
        private final double lambda; private SplittableRandom rng;
        public ExponentialDistribution(double lambda, long seed){ this.lambda=lambda; this.rng=new SplittableRandom(seed); }
        public void reseed(long seed){ this.rng = new SplittableRandom(seed); }
        public double next(){ double u = 1.0 - rng.nextDouble(); return -Math.log(u) / lambda; }
        public double lambda(){ return lambda; }
        @Override public String toString(){ return String.format("Exponential(λ=%.4f; mean=%.1f)", lambda, 1.0/lambda); }
    }

    /** Gaussian via Box–Muller (spare cache’li). */
    public static class GaussianDistribution implements Dist {
        private final double mean, std; private SplittableRandom rng;
        private boolean hasSpare=false; private double spare=0.0;
        public GaussianDistribution(double mean, double std, long seed){ this.mean=mean; this.std=std; this.rng=new SplittableRandom(seed); }
        public void reseed(long seed){ this.rng = new SplittableRandom(seed); hasSpare=false; }
        public double next(){
            if (hasSpare){ hasSpare=false; return mean + std * spare; }
            double u,v,s; do { u = rng.nextDouble()*2-1; v = rng.nextDouble()*2-1; s = u*u+v*v; } while (s<=1e-16 || s>=1.0);
            double mul = Math.sqrt(-2.0*Math.log(s)/s);
            spare = v*mul; hasSpare=true; return mean + std*(u*mul);
        }
        public double mean(){ return mean; } public double std(){ return std; }
        @Override public String toString(){ return String.format("Gaussian(μ=%.1f; σ=%.1f)", mean, std); }
    }

    // ===== Key generation =====

    /** Hızlı üretim (distinct zorunlu değil) — mixed workload için yeterli. */
    public static double[] generateKeysFast(Dist d, int n, long seed) {
        d.reseed(seed);
        double[] arr = new double[n];
        for (int i = 0; i < n; i++) arr[i] = d.next();
        return arr;
    }

    /** Build-only için YÖNERGE gereği: n adet **distinct** key. */
    public static double[] generateDistinctKeys(Dist d, int n, long seed) {
        d.reseed(seed);
        HashSet<Long> seen = new HashSet<>(Math.max(16, n * 2));
        double[] out = new double[n];
        int i = 0;
        while (i < n) {
            double x = d.next();
            long bits = Double.doubleToRawLongBits(x);
            if (seen.add(bits)) out[i++] = x; // duplicate gelirse atla (resample)
        }
        return out;
    }

    // ===== Workloads =====

    public static void buildOnlyWorkload(MyHashTable table, double[] keys) {
        for (int i = 0; i < keys.length; i++) table.put(keys[i], i);
    }
    public static void buildOnlyWorkloadBaseline(HashMap<Double,Integer> map, double[] keys) {
        for (int i = 0; i < keys.length; i++) map.put(keys[i], i);
    }

    public static void mixedWorkload(MyHashTable table, double[] keys, int nOps, long seed) {
        SplittableRandom rng = new SplittableRandom(seed);
        double[] cur = new double[Math.min(keys.length, nOps)];
        int curSize = 0, keyIdx = 0;
        for (int op = 0; op < nOps; op++) {
            double r = rng.nextDouble();
            if (r < 0.50) { // put
                if (keyIdx < keys.length) {
                    double k = keys[keyIdx++];
                    table.put(k, keyIdx);
                    if (curSize < cur.length) cur[curSize++] = k;
                }
            } else if (r < 0.75) { // get
                if (curSize > 0) {
                    int idx = rng.nextInt(curSize);
                    table.get(cur[idx]);
                }
            } else { // remove
                if (curSize > 0) {
                    int idx = rng.nextInt(curSize);
                    double k = cur[idx];
                    cur[idx] = cur[curSize - 1];
                    curSize--;
                    table.remove(k);
                }
            }
        }
    }
    public static void mixedWorkloadBaseline(HashMap<Double,Integer> map, double[] keys, int nOps, long seed) {
        SplittableRandom rng = new SplittableRandom(seed);
        double[] cur = new double[Math.min(keys.length, nOps)];
        int curSize = 0, keyIdx = 0;
        for (int op = 0; op < nOps; op++) {
            double r = rng.nextDouble();
            if (r < 0.50) {
                if (keyIdx < keys.length) {
                    double k = keys[keyIdx++];
                    map.put(k, keyIdx);
                    if (curSize < cur.length) cur[curSize++] = k;
                }
            } else if (r < 0.75) {
                if (curSize > 0) {
                    int idx = rng.nextInt(curSize);
                    map.get(cur[idx]);
                }
            } else {
                if (curSize > 0) {
                    int idx = rng.nextInt(curSize);
                    double k = cur[idx];
                    cur[idx] = cur[curSize - 1];
                    curSize--;
                    map.remove(k);
                }
            }
        }
    }

    // ===== Benchmark runner (5 deneme, distinct kuralı build-only’de) =====

    public static void runBenchmark(String distName, Dist distribution, int n, String workloadType,
                                    java.io.PrintWriter csvWriter) {

        final int NUM_TRIALS = 5;        // yönergeye göre 5
        final long BASE_SEED  = 1234L;   // trialSeed = 1234 + t

        long[] myTimes = new long[NUM_TRIALS];
        long[] baseTimes = new long[NUM_TRIALS];
        String myStats = "";

        for (int t = 0; t < NUM_TRIALS; t++) {
            long trialSeed = BASE_SEED + t;

            // --- Key üretimi ---
            final double[] keys;
            if (workloadType.equals("build-only")) {
                keys = generateDistinctKeys(distribution, n, trialSeed); // DISTINCT
            } else {
                keys = generateKeysFast(distribution, n, trialSeed);     // hızlı, distinct gerekmez
            }

            // --- MyHashTable ---
            MyHashTable my = new MyHashTable();
            long t0 = System.nanoTime();
            if (workloadType.equals("build-only")) buildOnlyWorkload(my, keys);
            else                                   mixedWorkload(my, keys, n, trialSeed);
            long t1 = System.nanoTime();
            myTimes[t] = (t1 - t0);
            if (t == NUM_TRIALS - 1) myStats = my.stats();

            // --- Baseline HashMap ---
            HashMap<Double,Integer> hm = new HashMap<>();
            t0 = System.nanoTime();
            if (workloadType.equals("build-only")) buildOnlyWorkloadBaseline(hm, keys);
            else                                   mixedWorkloadBaseline(hm, keys, n, trialSeed);
            t1 = System.nanoTime();
            baseTimes[t] = (t1 - t0);
        }

        long avgMy   = average(myTimes);
        long avgBase = average(baseTimes);
        int ops = n;
        double thrMy   = (ops * 1_000_000_000.0) / avgMy;
        double thrBase = (ops * 1_000_000_000.0) / avgBase;

        String[] statParts = parseStats(myStats);
        String distParams  = getDistributionParams(distribution);

        csvWriter.printf("\"%s\",\"%s\",%d,%s,%s,%d,%d,%.2f,%s,%s,%s\n",
                distName, distParams, n, workloadType, "student",
                avgMy, ops, thrMy, statParts[0], statParts[1], statParts[2]);

        csvWriter.printf("\"%s\",\"%s\",%d,%s,%s,%d,%d,%.2f,%s,%s,%s\n",
                distName, distParams, n, workloadType, "hashmap",
                avgBase, ops, thrBase, "-1", "-1", "-1");

        csvWriter.flush();
    }

    private static long average(long[] a){ long s=0; for(long v:a) s+=v; return s/a.length; }

    private static String[] parseStats(String stats) {
        String[] result = new String[]{"0","0.0","0.0"};
        if (stats == null) return result;
        try {
            String[] parts = stats.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("maxChainLength=")) result[0] = part.substring("maxChainLength=".length());
                else if (part.startsWith("meanChainLength=")) result[1] = part.substring("meanChainLength=".length());
                else if (part.startsWith("loadFactor="))     result[2] = part.substring("loadFactor=".length());
            }
        } catch (Exception ignore) {}
        return result;
    }

    private static String getDistributionParams(Object d) {
        if (d instanceof UniformDistribution) {
            UniformDistribution u = (UniformDistribution) d;
            return String.format("min=%.1f max=%.1f", u.min(), u.max());
        } else if (d instanceof GaussianDistribution) {
            GaussianDistribution g = (GaussianDistribution) d;
            return String.format("mean=%.1f stddev=%.1f", g.mean(), g.std());
        } else if (d instanceof ExponentialDistribution) {
            ExponentialDistribution e = (ExponentialDistribution) d;
            return String.format("lambda=%.4f", e.lambda());
        }
        return d.toString();
    }
}
