package testPullUpGen.test48.test;

import java.util.function.Function;

/**
 * Test : pull up m
 */
public class A extends S<Boolean> {
    void m(Function<Integer,String> f){}

    @Override
    Boolean n() {
        return true;
    }

}
