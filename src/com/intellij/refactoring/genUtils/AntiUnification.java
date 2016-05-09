package com.intellij.refactoring.genUtils;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;

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

/**
 * Created by julien on 09/05/16.
 */
public class AntiUnification {

    /** DEFINITION :
     * Two types are anti-unifiable when they are instances of more general type (with type variables).
     */


    public static Boolean antiUnifiable(PsiMethod m1, PsiMethod m2){

      if (!antiUnifiable(m1.getReturnType(), m2.getReturnType()))
          return false ;

      else
          return antiUnifiableParameters(m1.getParameterList().getParameters(),
                                         m2.getParameterList().getParameters()) ;
    }

    public static Boolean antiUnifiableParameters(PsiParameter[] l1, PsiParameter[] l2){
      final int count1 = l1.length ;
      if (count1 != l2.length)
          return false ;

      for (int i = 0; i<count1; i++){
          if (!antiUnifiable(l1[i].getType(), l2[i].getType()))
              return false ;
      }

      return true;
    }

    public static boolean antiUnifiable(PsiType t1, PsiType t2){
        if (t1.equals(t2))
            return true ;
        if (t1 instanceof PsiPrimitiveType || t2 instanceof PsiPrimitiveType)
            return false ;
        return true ;
    }
}
