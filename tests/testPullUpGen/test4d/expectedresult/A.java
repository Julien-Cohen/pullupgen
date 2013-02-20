package testPullUpGen.test4d.expectedresult;

/**
 * Test : pull up m, then n
 */
public class A extends S<Integer, String> {
    @Override
    Integer m(){
        return 1;
    }
    @Override
    String n(){
        return "foo";
    }
}

