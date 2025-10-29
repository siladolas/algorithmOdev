public class MyHashTable {
    private static final double DEFAULT_LOAD_FACTOR = 0.75;
    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static class Node {
        final double key;
        final long keyBits;
        /*
        key: kullanıcıya gösterilecek “gerçek” double değer (örneğin 3.14)
        keyBits: aynı değerin bit düzeyindeki benzersiz kimliği
        Böylece bit düzeyinde birebir eşitlik kontrol ediliyor.
        */
        int value;
        Node next;

        public Node(double key, int value, long keyBits, Node next) {
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
    //bu, hash table ne kadar dolunca genişletilecek (resize edilecek) belirler.
    private int threshold;
    //bu, kaç elemana kadar büyümeden dayanabiliriz sayısıdır.


    public MyHashTable(){
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }
    public MyHashTable(int initialCapacity, double loadFactor) {
        // capacity'i en yakın power-of-two'ya yuvarla
      if (initialCapacity < 1)
            initialCapacity = 1;


        //negatif veya sıfır kapasiteyi engellemek.
        int cap = 1;
        while (cap < initialCapacity) cap <<= 1;
        this.buckets = new Node[cap];

        /*Bu, mod işlemini hızlandırmak için kullanılır.
        Eğer capacity power-of-two (ör. 16) ise hash & (capacity - 1) aynı sonucu verir ama çok daha hızlıdır.*/
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
    public boolean remove(double key) {
        long bits = Double.doubleToRawLongBits(key);
        int index = indexFor(bits, buckets.length, capacityMask);
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
    public void put(double key, int value) {
        long bits = Double.doubleToRawLongBits(key);
        int index = indexFor(bits, buckets.length, capacityMask);
        Node e = buckets[index];

        for (Node cur = e; cur != null; cur = cur.next) {
            if (cur.keyBits == bits) {
                cur.value = value;
                return;
            }
        }
        buckets[index] = new Node(key, value, bits, e);
        size++;

        if (size > threshold) {
            resize();
        }
    }
    public Integer get(double key) {
        long bits = Double.doubleToRawLongBits(key);
        int index = indexFor(bits, buckets.length, capacityMask);
        for (Node cur = buckets[index]; cur != null; cur = cur.next) {
            if (cur.keyBits == bits) {
                return cur.value;
            }
        }
        return null;
    }
    private static int indexFor(long keyBits, int capacity, int capacityMask) {
        long mixed = myHash(keyBits);
        // capacity power-of-two ise mask hızlıdır; değilse floorMod kullan.
        if ((capacity & capacityMask) == 0) {
            // capacity power-of-two değilse:
            int idx = (int) Math.floorMod(mixed, capacity);
            return idx;
        } else {
            // power-of-two ise:
            return (int) (mixed & capacityMask);
        }
    }
    //Bu fonksiyon, SplitMix64 hash finalizer’ının birebir kopyasıdır.
    //Bitleri defalarca kaydırıp (>>>) XOR’layıp çarparak karıştırır.
    private static long myHash(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return z;
    }
    private void resize() {
        Node[] old = this.buckets;
        int newCap = old.length << 1;
        Node[] neo = new Node[newCap];
        int newMask = newCap - 1;

        for (Node head : old) {
            for (Node cur = head; cur != null; ) {
                Node next = cur.next;
                int idx = (int) (myHash(cur.keyBits) & newMask);
                cur.next = neo[idx];
                neo[idx] = cur;
                cur = next;
            }
        }
        this.buckets = neo;
        this.capacityMask = newMask;
        this.threshold = (int) (newCap * loadFactor);

    }
    public String stats() {
        int maxChain = 0;
        int nonEmpty = 0;
        for (Node head : buckets) {
            if (head != null) {
                nonEmpty++;
                int len = 0;
                for (Node cur = head; cur != null; cur = cur.next) len++;
                if (len > maxChain) maxChain = len;
            }
        }
        double load = size / (double) buckets.length;
        return "M=" + buckets.length +
                ", size=" + size +
                ", loadFactor=" + String.format("%.3f", load) +
                ", nonEmptyBuckets=" + nonEmpty +
                ", maxChainLength=" + maxChain;
    }




}
