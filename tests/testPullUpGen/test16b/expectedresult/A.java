package testPullUpGen.test16b.expectedresult;

public class A implements I <Integer> {
    @Override
    public int m(Integer i){
        return 1;
    }
}

// Remark: in Java 1.5, no @Override is allowed here (allowed since 1.6)
