package testPullUpGen.test5.expectedresult;

public abstract class S <T1, T2, T3> {
    abstract T1 m(T2 b, T3 s);
}

// Remark: It seems desirable that the order of the parameters in S <...> is the same as in the declaration of the method, even if it is not theoretically required).