package testPullUpGen.test21b.expectedresult;

public class A implements I <Integer> {
    @Override
    public int m(Integer i){
        return 1;
    }
}

// Rem : @Override only since Java 1.6 for interfaces.