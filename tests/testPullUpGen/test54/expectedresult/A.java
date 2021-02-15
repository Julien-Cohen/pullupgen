package testPullUpGen.test54.expectedresult;

import java.util.List;

/**
 * Test : pull up m
 * error : A extend S au lieu de extends S<Integer>
 */
public class A extends S<Integer> {
    @Override
    void m(List<Integer> l){ }
}
