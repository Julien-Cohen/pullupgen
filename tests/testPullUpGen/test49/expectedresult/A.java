package testPullUpGen.test49.expectedresult;

import java.util.function.Function;

/**
 * Test : pull up m
 */
public class A extends S<Integer, String> {
    @Override
    void m(Function<Integer,String> f){}

    @Override
    Integer n() {
        return 1;
    }

}
