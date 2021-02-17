package fr.polytech.refactoring.genUtils;

//import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Copyright 2012, 2016 Université de Nantes
 * Contributor : Julien Cohen (Ascola team, Univ. Nantes)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



    /** A dependent-substitution indicates how some type parameters have to to be instanciated in a set of classes.
     * It can be seen as a substitution with two parameters (the type variable and the considered class) instead of one (the type variable) in PsiSubstitutor.
     * Exemple : [ T1 : {in A replaced by C ; in B replaced by D}, T2 : {in A replaced by E ; in B replaced by C}] */

public class DependentSubstitution
            extends HashMap<PsiTypeParameter, Map<PsiClass,PsiType>>
            implements Map<PsiTypeParameter, Map<PsiClass,PsiType>> {

    /**  Returns the mappings that are in m1 but not in m2. This is needed to be able to add only
     *   the new part of the substitution to the final source code.
     *   Exemple :
     *    - initial code S<T> and A extends S<B>
     *    - initial substitution : (T->B in A)
     *    - final substitution : (T->B, T2->C in A)
     *    - we only add (T2->C) to get S<T,T2> and  A extends S(B,C)
     *    Here, we have used the difference between the final substitution and the initial
     *    one.                                                                                   */
    public static DependentSubstitution difference(DependentSubstitution m1, DependentSubstitution m2){
           DependentSubstitution result = new DependentSubstitution() ;
           for(PsiTypeParameter t:m1.keySet()){
               if (!m2.containsKey(t)) result.put(t, m1.get(t));
           }
           return result;
    }

        public PsiType getConcretes(PsiTypeParameter t, PsiClass c){
        return this.get(t).get(c);
    }

}
