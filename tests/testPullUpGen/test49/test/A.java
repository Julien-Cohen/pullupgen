package testPullUpGen.test49.test;

import java.util.function.Function;

/**
 * Test : pull up m
 */
public class A extends S<Integer> {
    void m(Function<Integer,String> f){}

    @Override
    Integer n() {
        return 1;
    }

}
