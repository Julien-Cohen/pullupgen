package fr.polytech.refactoring.genUtils;

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

import com.intellij.psi.*;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.Collection;
import java.util.List;

public class Comparison {

    /** ------ Type, Field and Method Comparison ------ */



    public static Boolean allTypesEquals (List<? extends PsiType> c){
       if (c.size() < 2) return true ;
        else {
            PsiType t = c.get(0);
            for (PsiType t2 : c){
                if (!t.equals(t2)) return false ;
            }
            return true;
        }
    }



    public static boolean haveSameName(PsiField f1, PsiField f2) {
        return f1.getName().equals(f2.getName());
    }

    public static boolean haveSameName(PsiMethod m1, PsiMethod m2) {
        return m1.getName().equals(m2.getName());
    }



    public static Boolean allObjectTypes(Collection<PsiType> c){
       for (PsiType t : c) {
           if (t instanceof PsiPrimitiveType) return false;
       }
       return true ;
    }


    public static boolean sameFields(PsiField f1, PsiField f2){
        return     haveSameName(f1, f2)
                && haveSameType(f1, f2);

    }

    public static boolean haveSameType(PsiField f1, PsiField f2) {
        return f1.getType().equals(f2.getType());
    }

    public static Boolean haveSameType(PsiMethod m1, PsiMethod m2){
        return m1.getReturnType().equals(m2.getReturnType())
                && sameParameterLists(m1.getParameterList(), m2.getParameterList()) ;
    }

    // see also sameMethods
    public static boolean hasSameMethod(PsiMethod m, PsiClass c){
        for (PsiMethod m_tmp: c.findMethodsByName(m.getName(), false)) {
            if (haveSameType(m, m_tmp)) return true;
        }
        return false;
    }

    /** Check that two methods have the same name and the same type. */
    public static boolean sameMethods(PsiMethod m1, PsiMethod m2){
        return  haveSameName(m1, m2) && haveSameType(m1, m2)  ;
    }



    public static boolean sameParameterLists(PsiParameterList l1, PsiParameterList l2){
        if (l1.getParametersCount() != l2.getParametersCount()) return false ;
        PsiParameter[] a1 = l1.getParameters();
        PsiParameter[] a2 = l2.getParameters();

        for (int i = 0; i< a1.length; i++){
            if (! a1[i].getTypeElement().getType().equals(a2[i].getTypeElement().getType())) return false ;
        }
        return true ;

    }

    @Deprecated // Used only in ExtractSuperClassMultiUtils ; does not take branches into account, only subclasses. See checkSubclassesHaveSameMethod
    public static boolean hasMembers(PsiClass c, Iterable<MemberInfo> infos){
        // FORALL
        for (MemberInfo member: infos){
            if (!hasMember(c, member)) return false;
        }
        return true;
    }

    public static boolean hasField (PsiField m, PsiClass c){
        for (PsiField aField : c.getFields()) {
                if (sameFields(m, aField))
                    return true;
            }
        return false ;
    }

    @Deprecated // used only in hasMembers
    public static boolean hasMember(PsiClass c, MemberInfo member) {
        boolean result = false;
        PsiMember x = member.getMember();

        if (x instanceof PsiMethod){
            return hasSameMethod((PsiMethod) x, c);
        }

        else if (x instanceof PsiField){
            return hasField((PsiField) x, c) ;
        }

        // *) Implements interface
        else if (x instanceof PsiClass && memberClassComesFromImplements(member)) {
            PsiClass xx = (PsiClass) x ;
            for (PsiClassType m : c.getImplementsListTypes()){
                if (m.resolve().equals(xx)) result = true; // not sure
            }
        }

        // *) Other cases
        else throw new IncorrectOperationException("this type of member not handled yet : " + x);
        return result;
    }

    public static boolean isAbstract(PsiClass c) {
        return c.hasModifierProperty(PsiModifier.ABSTRACT);
    }


    /** Check whether a class c implements a type t (exemple for t : I<String>). */
    public static boolean hasSameImplements(PsiClass c, PsiClassType t){

        // EXISTS
        for (PsiClassType ty : c.getImplementsList().getReferencedTypes()){
            System.out.println (ty + " / " + t + " => " + ty.equals(t));
            if (ty.equals(t)) return true;
        }
        return false ;
    }

    public static boolean memberClassComesFromImplements(MemberInfo m){

        /** ************ THIS IS A COMMENT FROM MemberInfoBase.getOverrides ********************
         * Returns Boolean.TRUE if getMember() overrides something, Boolean.FALSE if getMember()
         * implements something, null if neither is the case.
         * If getMember() is a PsiClass, returns Boolean.TRUE if this class comes
         * from 'extends', Boolean.FALSE if it comes from 'implements' list, null
         * if it is an inner class.
         */
        assert (m.getMember() instanceof PsiClass) ;

        return   ((PsiClass) m.getMember()).isInterface() && !(m.getOverrides()) ;

    }

    public static boolean allArrayTypes(List<PsiType> types) {
        for (PsiType t : types) {
            if (!(t instanceof PsiArrayType)) return false;
        }
        return true ;
    }

    public static boolean allClassTypes(List<PsiType> types) {
        for (PsiType t : types) {
            if (!(t instanceof PsiClassType)) return false;
        }
        return true ;
    }
}
