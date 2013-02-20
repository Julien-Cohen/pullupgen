import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.util.*;


/**
 * Copyright 2012 Université de Nantes
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



class GenBuildUtils {


    static void generifyAbstractMethod(PsiMethod m, GenSubstitutionUtils.ParamSubstitution lp){
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(m.getProject());
        
        for (Integer pos : lp.keySet()){
            final PsiTypeParameter t1 = lp.get(pos);
            final String     typename = t1.getName();
            final PsiType      t2     = factory.createTypeFromText(typename, null);
            final PsiElement   e      = factory.createTypeElement(t2);

            if (pos == -1) {
              m.getReturnTypeElement().replace(e);
            }
            else {
              m.getParameterList().getParameters()[pos].getTypeElement().replace(e);
            }
        }
    }

    static String buildTypeParameter(int pos, String name, PsiElementFactory factory){
        if (pos == -1){
            return "T" + name + "RET" ; // Todo: check that the type name is fresh
        }
        else {
            return "T" + name + pos   ; // Todo: check that the type name is fresh
        }
    }


    // TODO : (0.2b) I have made the concept of conpatibility stronger than in 0.2a, so check if some later tests have became unnecessary.


    static void updateExtendsStatementsInSisterClasses(List<PsiMethod> sisterMethods, Map<PsiTypeParameter, Map<PsiClass,PsiType>> sub, PsiClass superClass, PsiElementFactory factory) {
          
      for (PsiTypeParameter t: sub.keySet()){
        Map<PsiClass,PsiType> m = sub.get(t);
        for (PsiClass c : m.keySet()){
          final PsiJavaCodeReferenceElement r = findReferenceToSuperclass(c, superClass);
          addTypeParameterToReference(r, m.get(c), factory);
          }
      }
    }

    static PsiJavaCodeReferenceElement findReferenceToSuperclass(PsiClass c, PsiClass s){
      if (s.isInterface()){
        for (PsiJavaCodeReferenceElement elem : c.getImplementsList().getReferenceElements()) {
          if ((elem.getQualifiedName()).equals(s.getQualifiedName())) return elem ;
        }
      }
      else {
        for (PsiJavaCodeReferenceElement elem : c.getExtendsList().getReferenceElements()) {
          if ((elem.getQualifiedName()).equals(s.getQualifiedName())) return elem ;
        }
      }
      throw new IncorrectOperationException("Internal error: Super class extends/implement statement not found.");
        
    }
    
    static void addTypeParameterToReference(PsiJavaCodeReferenceElement r, PsiType t, PsiElementFactory factory ){
      r.getParameterList().add(factory.createTypeElement(t));  
    }


}
