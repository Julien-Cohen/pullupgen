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

    static String buildTypeParameter(int pos, String name, Collection<String> boundNames){
        String tentative =  (pos == -1) ? ("T" + name + "RET") : ("T" + name + pos) ;
        while (boundNames.contains(tentative)) tentative= tentative+"x" ;
        return tentative;

    }



    static void updateExtendsStatementsInSisterClasses(
            DependentSubstitution megasub,
            PsiClass superClass,
            PsiElementFactory factory) {


      for (PsiTypeParameter t: megasub.keySet()){
        final Map<PsiClass,PsiType> m = megasub.get(t);
        for (PsiClass c : m.keySet()){
          final PsiJavaCodeReferenceElement extendsRef = findReferenceToSuperclass(c, superClass);
          addTypeParameterToReference(extendsRef, megasub.get(t,c), factory);
          }
      }
    }

    /** Replace "class A extends S" by "class A extends S < Object >" when S has a type parameter */
    static void alignParameters(PsiClass superclass, PsiClass toBeAligned, PsiElementFactory factory){
        final int l_super      = superclass.getTypeParameters().length;
        final int l_class      = toBeAligned.getTypeParameters().length;
        final PsiType ob = factory.createTypeFromText("Object", null);
        final PsiJavaCodeReferenceElement extendsRef = findReferenceToSuperclass(toBeAligned, superclass);

        for (int i=0; i< l_super - l_class; i++ )  addTypeParameterToReference(extendsRef, ob, factory);

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
