package com.intellij.refactoring.genUtils;

//import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;

import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: cohen-j
 * Date: 18/04/13
 * Time: 16:25
 * To change this template use File | Settings | File Templates.
 */


    /* A dependent-substitution indicates how some type parameters have to to be instanciated in a set of classes. */
    /* It can be seen as a substitution with two parameters (the type variable and the considered class) instead of one (the type variable) in PsiSubstitutor. */
    /* Exemple : [ T1 : {in A replaced by C ; in B replaced by D}, T2 : {in A replaced by E ; in B replaced by C}] */
public class DependentSubstitution
        extends HashMap<PsiTypeParameter,Map<PsiClass,PsiType>>
        implements Map<PsiTypeParameter,Map<PsiClass,PsiType>> {


    public PsiType get(PsiTypeParameter t, PsiClass c){ return this.get(t).get(c); }




};
