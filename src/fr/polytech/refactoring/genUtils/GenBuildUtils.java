package fr.polytech.refactoring.genUtils;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.util.Collection;
import java.util.List;
import java.util.Map;


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



public class GenBuildUtils {


    public static void generifyAbstractMethod(PsiMethod m, ParamSubstitution lp){
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(m.getProject());
        
        for (Integer pos : lp.keySet()){
            final PsiType      t1     = lp.get(pos);
            final PsiElement   e      = factory.createTypeElement(t1);

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



    public static void updateExtendsStatementsInSisterClasses(
            DependentSubstitution megasub,
            PsiClass superClass,
            PsiElementFactory factory) {


      for (PsiTypeParameter theTypeParameter: megasub.keySet()){
        final Map<PsiClass,PsiType> m = megasub.get(theTypeParameter);
        for (PsiClass c : m.keySet()){
            final PsiJavaCodeReferenceElement extendsRef = findReferenceToSuperclass(c, superClass);
            PsiType concreteType = megasub.getConcretes(theTypeParameter, c);
            addTypeParameterToReference(extendsRef, concreteType, factory);
        }
      }
    }

    /** Replace "class A extends S" by "class A extends S < Object >" when S has a type parameter.
     *
     * @param superclass
     * @param toBeAligned
     * @param factory
     *
     * Modifies toBeAligned.
     * If the extends is already aligned with the superclass, does nothing.
     *
     */
    public static void alignParameters(PsiClass superclass, PsiClass toBeAligned, PsiElementFactory factory){
        final PsiJavaCodeReferenceElement extendsRef = findReferenceToSuperclass(toBeAligned, superclass);

        final int len = extendsRef.getParameterList().getTypeParameterElements().length;

        final int len_super      = superclass.getTypeParameters().length;
        final PsiType ob = factory.createTypeFromText("Object", null);

        for (int i=0; i< len_super - len; i++ ) {
            addTypeParameterToReference(extendsRef, ob, factory);
        }

    }

    static PsiJavaCodeReferenceElement findReferenceToSuperclass(PsiClass c, PsiClass s){
        final String superName = s.getQualifiedName();
        if (s.isInterface()){
        for (PsiJavaCodeReferenceElement elem : c.getImplementsList().getReferenceElements()) {
          if ((elem.getQualifiedName()).equals(superName)) return elem ;
        }
      }
      //else {
        for (PsiJavaCodeReferenceElement elem : c.getExtendsList().getReferenceElements()) {  // for classes and interfaces
          if ((elem.getQualifiedName()).equals(superName)) return elem ;
        }
      //}
      throw new IncorrectOperationException("Internal error: Super class extends/implement statement not found.");
        
    }
    
    static void addTypeParameterToReference(PsiJavaCodeReferenceElement r, PsiType t, PsiElementFactory factory ){
        r.getParameterList().add(factory.createTypeElement(t));
    }


}
