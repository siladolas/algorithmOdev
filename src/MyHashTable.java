// ===============================
// MyHashTable.java
// ===============================

public class MyHashTable {
    // Typical default. Below ~0.75 you get fewer collisions but more memory growth.
    private static final double DEFAULT_LOAD_FACTOR = 0.75;
    private static final int DEFAULT_INITIAL_CAPACITY = 16; // must be power-of-two after rounding

    /**
     * Node for separate chaining. We store:
     * - key: original double (for pretty printing / debugging)
     * - keyBits: exact raw bits for equality (no NaN canonicalization surprises)
     * - value: int payload
     * - next: next node in the chain
     */
    private static class Node {
        final double key;
        final long keyBits;   // exact bit pattern (Double.doubleToRawLongBits)
        int value;
        Node next;

        Node(double key, int value, long keyBits, Node next) {
            this.key = key;
            this.keyBits = keyBits;
            this.value = value;
            this.next = next;
        }
    }

    // Buckets are an array of singly linked lists (chains)
    private Node[] buckets;
    // Number of key-value pairs currently stored
    private int size;
    // (capacity - 1), used to mask indices when capacity is a power of two
    private int capacityMask;
    // Target average occupancy per bucket before we resize
    private final double loadFactor;
    // When size exceeds this threshold we double the table
    private int threshold;

    /**
     * Zero-arg constructor: uses default capacity and load factor.
     */
    public MyHashTable() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Main constructor: rounds capacity up to next power of two.
     * Power-of-two capacity lets us replace modulo with a cheap bit mask.
     */
    public MyHashTable(int initialCapacity, double loadFactor) {
        if (initialCapacity < 1) initialCapacity = 1;

        // Round up to next power-of-two (at least 1)
        int cap = 1;
        while (cap < initialCapacity) cap <<= 1;

        this.buckets = new Node[cap];
        this.capacityMask = cap - 1;
        this.loadFactor = loadFactor <= 0 ? DEFAULT_LOAD_FACTOR : loadFactor;
        this.threshold = (int) (cap * this.loadFactor);
        this.size = 0;
    }

    /** @return number of key-value entries currently in the table */
    public int size() {
        return size;
    }

    /** @return current bucket array length (always a power of two) */
    public int capacity() {
        return buckets.length;
    }

    /**
     * Insert or overwrite a (key, value) pair.
     * Uses raw double bits for equality (so +0.0 and -0.0 are different; NaN bits preserved).
     */
    public void put(double key, int value) {
        long bits = Double.doubleToRawLongBits(key);
        int index = hash(bits) & capacityMask; // fast modulo via masking

        // Overwrite if key already exists in this chain
        for (Node cur = buckets[index]; cur != null; cur = cur.next) {
            if (cur.keyBits == bits) {
                cur.value = value;
                return;
            }
        }

        // Otherwise, insert new node at the head (O(1) insertion)
        buckets[index] = new Node(key, value, bits, buckets[index]);

        // Resize if we exceeded the threshold (amortized O(1) put)
        if (++size > threshold) {
            resize();
        }
    }

    /**
     * Lookup the value for a given key.
     * @return Integer value if present; null otherwise.
     */
    public Integer get(double key) {
        long bits = Double.doubleToRawLongBits(key);
        int index = hash(bits) & capacityMask;

        for (Node cur = buckets[index]; cur != null; cur = cur.next) {
            if (cur.keyBits == bits) {
                return cur.value;
            }
        }
        return null;
    }

    /**
     * Remove a key if it exists.
     * @return true if a node was removed, false if key was not present.
     */
    public boolean remove(double key) {
        long bits = Double.doubleToRawLongBits(key);
        int index = hash(bits) & capacityMask;

        Node cur = buckets[index];
        Node prev = null;

        while (cur != null) {
            if (cur.keyBits == bits) {
                // Unlink the node from the chain
                if (prev == null) {
                    buckets[index] = cur.next;
                } else {
                    prev.next = cur.next;
                }
                size--;
                return true;
            }
            prev = cur;
            cur = cur.next;
        }
        return false;
    }

    /**
     * Simple, fast 32-bit hash derived from 64-bit double bits.
     * We xorshift across the upper/lower halves to mix entropy.
     * Not as strong as SplitMix64, but cheaper (fewer ops) and OK for this assignment.
     */
    private static int hash(long bits) {
        // Basic bit mixing. Two rounds keep it cheap but reduce obvious patterns.
        bits ^= (bits >>> 32);
        bits ^= (bits >>> 16);
        return (int) bits; // lower 32 bits
    }

    /**
     * Resize to 2x capacity and rehash nodes.
     * Single pass over old buckets; we reuse Node objects (no allocation of new nodes).
     */
    private void resize() {
        Node[] oldBuckets = this.buckets;
        int newCap = oldBuckets.length << 1; // double capacity
        Node[] newBuckets = new Node[newCap];
        int newMask = newCap - 1;

        // Re-distribute every node according to the new mask
        for (int i = 0; i < oldBuckets.length; i++) {
            Node cur = oldBuckets[i];
            while (cur != null) {
                Node next = cur.next; // save
                int idx = hash(cur.keyBits) & newMask;
                // push-front into new chain
                cur.next = newBuckets[idx];
                newBuckets[idx] = cur;
                cur = next;
            }
        }

        // Publish new table
        this.buckets = newBuckets;
        this.capacityMask = newMask;
        this.threshold = (int) (newCap * loadFactor);
    }

    /**
     * Basic diagnostics: how full the table is and chain statistics.
     * Helpful to check whether the hash function is distributing keys decently.
     */
    public String stats() {
        int maxChain = 0;
        int totalChainLength = 0;
        int nonEmptyBuckets = 0;

        for (Node head : buckets) {
            if (head != null) {
                nonEmptyBuckets++;
                int len = 0;
                for (Node cur = head; cur != null; cur = cur.next) {
                    len++;
                }
                totalChainLength += len;
                if (len > maxChain) {
                    maxChain = len;
                }
            }
        }

        double loadFactor = size / (double) buckets.length;
        double meanChainLength = nonEmptyBuckets > 0
                ? totalChainLength / (double) nonEmptyBuckets
                : 0.0;

        return String.format(
                "M=%d, size=%d, loadFactor=%.3f, maxChainLength=%d, meanChainLength=%.3f",
                buckets.length, size, loadFactor, maxChain, meanChainLength
        );
    }
}
