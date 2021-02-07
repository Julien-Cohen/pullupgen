package testPullUpGen.test54.test;

import java.util.List;

/**
 * Test : pull up m
 * error : A extend S au lieu de extends S<Integer>
 */
public class A extends S {
    void m(List<Integer> l){ }
}
