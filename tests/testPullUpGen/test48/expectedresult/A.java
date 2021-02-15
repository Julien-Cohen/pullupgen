package testPullUpGen.test48.expectedresult;

import java.util.function.Function;

/**
 * Test : pull up m
 */
public class A extends S<Boolean, Integer, String> {
    @Override
    void m(Function<Integer,String> f){}

    @Override
    Boolean n() {
        return true;
    }

}
