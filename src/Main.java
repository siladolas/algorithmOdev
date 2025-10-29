// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {
    public static void main(String[] args) {
        MyHashTable ht = new MyHashTable(8, 0.75);

        // put
        ht.put(3.14, 314);
        ht.put(-0.0, 1);
        ht.put(+0.0, 2); // bit-eşitliği gereği -0.0 ile +0.0 AYNI DEĞİL (farklı bit pattern)
        ht.put(Double.NaN, 7);
        // farklı NaN pattern'leri farklı key kabul edilir:
        long nanBitsA = Double.doubleToRawLongBits(Double.NaN);
        double otherNaN = Double.longBitsToDouble(nanBitsA ^ 0x0008000000000000L);
        ht.put(otherNaN, 9);

        // get
        System.out.println("get(3.14) = " + ht.get(3.14));
        System.out.println("get(-0.0) = " + ht.get(-0.0));
        System.out.println("get(+0.0) = " + ht.get(+0.0));
        System.out.println("get(NaN)  = " + ht.get(Double.NaN));
        System.out.println("get(otherNaN) = " + ht.get(otherNaN));

        // remove
        System.out.println("remove(3.14) = " + ht.remove(3.14));
        System.out.println("get(3.14) = " + ht.get(3.14));

        // stats
        System.out.println(ht.stats());

    }
}