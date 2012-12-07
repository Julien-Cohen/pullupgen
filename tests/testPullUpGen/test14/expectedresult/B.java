package testPullUpGen.test14.expectedresult;

public class B<T> extends S<T> {
    @Override
    int m(T i){
        return 1;
    }
}

// Remark: there is no reason for T here to be the same as in S (here: T ; in S: T0).