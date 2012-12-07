package testPullUpGen.test7.possibleresult;

public class B extends S <String> {
   @Override
   int m(String i){
        return 1;
    }
   int m(Boolean i){
        return 1;
    }
}

// The other possible result is S <Boolean> and @Override on m(Boolean).