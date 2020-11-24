package fr.polytech.refactoring.genUtils;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

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


public class Compatibility {

    /** DEFINITION :
     * Two types are anti-unifiable when they are instances of more general type (with type variables).
     * Two methods are compatible when they have the same name and their types (return type + parameter types) are anti-unifiable.
     * (Private methods are not considered compatibles.)
     */


    public static Boolean antiUnifiable(PsiMethod m1, PsiMethod m2){

      return antiUnifiable(m1.getReturnType(), m2.getReturnType())
                && semiAntiUnifiable(m1, m2);
    }


    @NotNull
    public static Boolean semiAntiUnifiable(PsiMethod m1, PsiMethod m2) {
        return antiUnifiableParameters(m1.getParameterList().getParameters(),
                                       m2.getParameterList().getParameters());
    }

    static Boolean antiUnifiableParameters(PsiParameter[] l1, PsiParameter[] l2){
      final int nb = l1.length ;
      if (nb != l2.length)
          return false ;

      for (int i = 0; i<nb; i++){
          if (!antiUnifiable(l1[i].getType(), l2[i].getType()))
              return false ;
      }

      return true;
    }

    static boolean antiUnifiable(PsiType t1, PsiType t2){
        if (t1.equals(t2))
            return true ;
        if (t1 instanceof PsiPrimitiveType || t2 instanceof PsiPrimitiveType)
            return false ;
        return true ;
    }

    public static boolean isCompatible(PsiMethod m1, PsiMethod m2){
        assert(!m1.hasModifierProperty("private"));
        return
                (!m2.hasModifierProperty("private"))
                        && Comparison.haveSameName(m1,m2)
                        && antiUnifiable(m1,m2) ;

    }

    public static boolean isSemiCompatible(PsiMethod m1, PsiMethod m2){

        return
                Comparison.haveSameName(m1,m2)
                        && semiAntiUnifiable(m1,m2) ;

    }
}
