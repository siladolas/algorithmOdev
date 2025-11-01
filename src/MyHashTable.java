// ===============================
// MyHashTable.java
// ===============================

public class MyHashTable {
    private static final double DEFAULT_LOAD_FACTOR = 0.75;
    private static final int DEFAULT_INITIAL_CAPACITY = 16; // power of two

    /** Separate chaining node with precomputed 32-bit hash. */
    private static class Node {
        final double key;     // for debug/pretty
        final long keyBits;   // exact raw bits
        final int hash32;     // precomputed mixed hash
        int value;
        Node next;

        Node(double key, int value, long keyBits, int hash32, Node next) {
            this.key = key;
            this.keyBits = keyBits;
            this.hash32 = hash32;
            this.value = value;
            this.next = next;
        }
    }

    private Node[] buckets;
    private int size;
    private int capacityMask;
    private final double loadFactor;
    private int threshold;

    public MyHashTable() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    public MyHashTable(int initialCapacity, double loadFactor) {
        if (initialCapacity < 1) initialCapacity = 1;
        int cap = 1;
        while (cap < initialCapacity) cap <<= 1;

        this.buckets = new Node[cap];
        this.capacityMask = cap - 1;
        this.loadFactor = loadFactor <= 0 ? DEFAULT_LOAD_FACTOR : loadFactor;
        this.threshold = (int) (cap * this.loadFactor);
        this.size = 0;
    }

    public int size() { return size; }
    public int capacity() { return buckets.length; }

    /** SplitMix64-style mix then fold to 32-bit. Better distribution than trivial xors. */
    private static int mix32(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (int) (z ^ (z >>> 32));
    }

    public void put(double key, int value) {
        long bits = Double.doubleToRawLongBits(key);
        int h = mix32(bits);
        int index = h & capacityMask;

        for (Node cur = buckets[index]; cur != null; cur = cur.next) {
            if (cur.keyBits == bits) {
                cur.value = value;
                return;
            }
        }
        buckets[index] = new Node(key, value, bits, h, buckets[index]);
        if (++size > threshold) resize();
    }

    public Integer get(double key) {
        long bits = Double.doubleToRawLongBits(key);
        int h = mix32(bits);
        int index = h & capacityMask;

        for (Node cur = buckets[index]; cur != null; cur = cur.next) {
            if (cur.keyBits == bits) return cur.value;
        }
        return null;
    }

    public boolean remove(double key) {
        long bits = Double.doubleToRawLongBits(key);
        int h = mix32(bits);
        int index = h & capacityMask;

        Node cur = buckets[index], prev = null;
        while (cur != null) {
            if (cur.keyBits == bits) {
                if (prev == null) buckets[index] = cur.next;
                else prev.next = cur.next;
                size--;
                return true;
            }
            prev = cur;
            cur = cur.next;
        }
        return false;
    }

    /** Double capacity; reuse nodes; bucket index= node.hash32 & newMask (no recompute). */
    private void resize() {
        Node[] old = this.buckets;
        int newCap = old.length << 1;
        Node[] neo = new Node[newCap];
        int newMask = newCap - 1;

        for (Node head : old) {
            Node cur = head;
            while (cur != null) {
                Node nxt = cur.next;
                int idx = cur.hash32 & newMask;
                cur.next = neo[idx];
                neo[idx] = cur;
                cur = nxt;
            }
        }
        this.buckets = neo;
        this.capacityMask = newMask;
        this.threshold = (int) (newCap * loadFactor);
    }

    public String stats() {
        int maxChain = 0, totalChain = 0, nonEmpty = 0;
        for (Node head : buckets) {
            if (head != null) {
                nonEmpty++;
                int len = 0;
                for (Node c = head; c != null; c = c.next) len++;
                totalChain += len;
                if (len > maxChain) maxChain = len;
            }
        }
        double lf = size / (double) buckets.length;
        double mean = nonEmpty > 0 ? totalChain / (double) nonEmpty : 0.0;
        return String.format("M=%d, size=%d, loadFactor=%.3f, maxChainLength=%d, meanChainLength=%.3f",
                buckets.length, size, lf, maxChain, mean);
    }
}
