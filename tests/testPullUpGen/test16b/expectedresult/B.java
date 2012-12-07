package testPullUpGen.test16b.expectedresult;

public class B implements I <String> {
    @Override
    public int m(String i){
        return 1;
    }
}

 // Remark: in Java 1.5, no @Override is allowed here (allowed since 1.6)
