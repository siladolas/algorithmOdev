public class MyHashTable {
    private static final double DEFAULT_LOAD_FACTOR = 0.75; // 0.75'ten düşük = daha az collision
    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    private static class Node {
        final double key;
        final long keyBits;
        int value;
        Node next;

        Node(double key, int value, long keyBits, Node next) {
            this.key = key;
            this.keyBits = keyBits;
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

    public int size() {
        return size;
    }

    public int capacity() {
        return buckets.length;
    }

    public void put(double key, int value) {
        long bits = Double.doubleToRawLongBits(key);
        int index = hash(bits) & capacityMask;

        // Check if exists
        for (Node cur = buckets[index]; cur != null; cur = cur.next) {
            if (cur.keyBits == bits) {
                cur.value = value;
                return;
            }
        }

        // Insert at head
        buckets[index] = new Node(key, value, bits, buckets[index]);

        if (++size > threshold) {
            resize();
        }
    }

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

    public boolean remove(double key) {
        long bits = Double.doubleToRawLongBits(key);
        int index = hash(bits) & capacityMask;

        Node cur = buckets[index];
        Node prev = null;

        while (cur != null) {
            if (cur.keyBits == bits) {
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
     * BASIT HASH - Daha hızlı ama yeterince iyi
     * SplitMix64'ten daha az işlem
     */
    private static int hash(long bits) {
        // XORShift - tek round yeterli çoğu durumda
        bits ^= (bits >>> 32);
        bits ^= (bits >>> 16);
        return (int) bits;
    }

    /**
     * HIZLI RESIZE - Tek loop
     */
    private void resize() {
        Node[] oldBuckets = this.buckets;
        int newCap = oldBuckets.length << 1;
        Node[] newBuckets = new Node[newCap];
        int newMask = newCap - 1;

        for (int i = 0; i < oldBuckets.length; i++) {
            Node cur = oldBuckets[i];
            while (cur != null) {
                Node next = cur.next;
                int idx = hash(cur.keyBits) & newMask;
                cur.next = newBuckets[idx];
                newBuckets[idx] = cur;
                cur = next;
            }
        }

        this.buckets = newBuckets;
        this.capacityMask = newMask;
        this.threshold = (int) (newCap * loadFactor);
    }

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