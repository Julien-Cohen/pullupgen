package com.intellij.refactoring.genUtils;

import com.intellij.psi.*;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

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



public class GenAnalysisUtils {

    /** DEFINITIONS :
     *
     *
     * The sister classes of a class are the direct subclasses of its direct superclass.
     */

    public static boolean checkSubClassesHaveSameMethod(PsiMethod m, PsiClass superClass){

        // Algorithmic skeleton : FORALL_BRANCHES : hasSameMethod
        for (PsiClass c: SisterClassesUtil.findDirectSubClassesInDirectory(superClass)){
            if (!Comparison.hasSameMethod(m, c))  {
                // OK only if interface/abstract-class and the method is in all subclasses
                if ( ! ( (c.isInterface() || Comparison.isAbstract(c)) && checkSubClassesHaveSameMethod(m, c)))
                    return false;
            }
        }
        return true;
    }



    public static boolean checkSubClassesImplementInterface(PsiClassType t, PsiClass superClass){

        // Algorithmic skeleton : FORALL_BRANCHES : hasSameImplements
        for (PsiClass c: SisterClassesUtil.findDirectSubClassesInDirectory(superClass)){
            if (!Comparison.hasSameImplements(c, t))  {
                // OK only if interface/abstract-class and the method is in all subclasses
                if ( ! ( (c.isInterface() || Comparison.isAbstract(c)) && checkSubClassesImplementInterface(t, c))) return false;
            }
        }
        return true;
    }















     /* ------ Lookup for implements (interfaces) ------ */


    public static Collection<PsiClass> findDirectSubClassesWithCompatibleImplements(PsiClass i, PsiClass superClass)
            throws MemberNotImplemented
    {
        assert (i.isInterface());
        final Collection <PsiClass> directSubClasses = SisterClassesUtil.findDirectSubClassesInDirectory(superClass);
        for (PsiClass c: directSubClasses){
            if (! hasCompatibleImplements(c, i)) throw new MemberNotImplemented(i, c);
        }
        return directSubClasses ;
    }


    // FIXME
    public static boolean hasCompatibleImplements(PsiClass c, PsiClass i){
        assert (i.isInterface());
        for (PsiClass tmp : c.getInterfaces()) {
            // Don't care of the type parameters (see hasSameImplements)
            if (tmp.equals(i)) return true ;   // not sure that it works.
        }
        return false ;
    }




    /* ------ Lookup for "compatible" members ------ */


    @NotNull
    public static Collection <PsiClass> findSubClassesWithCompatibleMember(MemberInfo mem, PsiClass superClass)
            throws MemberNotImplemented, AmbiguousOverloading {

        PsiMember m = mem.getMember();

        if (m instanceof PsiMethod){
            boolean mustBePublic = superClass.isInterface()  ;
            return findDirectSubClassesWithCompatibleMethod((PsiMethod) m, superClass, mustBePublic);   // can throw MemberNotImplemented exception but cannot be null
        }

        else if (m instanceof PsiClass && Comparison.memberClassComesFromImplements(mem)) {
                return findDirectSubClassesWithCompatibleImplements((PsiClass) m, superClass);}

        else if (m instanceof PsiClass) {
                 throw new IncorrectOperationException("implement me : pull up class " + m);} // FIXME

        else if (m instanceof PsiField) {
                throw new IncorrectOperationException("implement me : pull up field");} // FIXME

        else    throw new IncorrectOperationException("cannot handle this kind of member:" + m); // FIXME
    }

    // Must find an implementation in each direct subclass.
    // Otherwise: throws an exception.
    @NotNull
    public static Collection<PsiClass> findDirectSubClassesWithCompatibleMethod(PsiMethod m, PsiClass superClass, boolean checkpublic)
            throws AmbiguousOverloading, MemberNotImplemented {

        final Collection<PsiClass> res = new LinkedList<PsiClass>();
        final Collection <PsiClass> directSubClasses = SisterClassesUtil.findDirectSubClassesInDirectory(superClass);

        for (PsiClass c: directSubClasses){
            final int count = hasCompatibleMethod(m, c, checkpublic);
            if (count>1) throw new AmbiguousOverloading(m,c);
            if (count==1)  res.add(c); // the method is found

            else { // no compatible method.

                    // The method is not found.
                    // If that class is concrete, pull up would introduce an error.
                    // If that class is abstract and the method is implemented in subclasses, the user has to first make a pull-up to that abstract class.
                    throw new MemberNotImplemented(m,c);
            }
        }
        return res;
    }


    /* see definition of compatibility at the beginning of the file */
    public static List<PsiMethod> findCompatibleMethods(PsiMethod method, Collection<PsiClass> sisterClasses) {
        final List<PsiMethod> sisterMethods = new LinkedList<PsiMethod>();
        for (PsiClass c:sisterClasses){
              final List<PsiMethod> localSisterMethods = findCompatibleMethodsInClass(method, c);
              if (localSisterMethods.size() == 0) {
                  throw new IncorrectOperationException("The method is not implemented by all sister classes.");
              }
              if (localSisterMethods.size() > 1) {
                  throw new IncorrectOperationException("The method is overloaded (with the same number of arguments).");
              }
            sisterMethods.add(localSisterMethods.get(0));
        }
        return sisterMethods;
    }




    public static boolean hasCompatibleMembers(PsiClass c, Iterable<MemberInfo> membersToPullUp)
            throws AmbiguousOverloading {

        for (MemberInfo member: membersToPullUp){
            final PsiMember theMember = member.getMember();

            if (theMember instanceof PsiMethod){
              PsiMethod theMethod = (PsiMethod) theMember ;
              final int count = hasCompatibleMethod(theMethod, c);
              if (count >1) {
                  throw new AmbiguousOverloading(theMethod, c);
              }
              else
              if (count == 0)
                  return false;
            }

            else
            if (theMember instanceof PsiField){
              PsiField theField = (PsiField) theMember;
              if  (!Comparison.hasField(theField, c))
                  return false ;
            }

            else
            if (theMember instanceof PsiClass) {
                throw new IncorrectOperationException("(hasCompatibleMembers) don't know what to do with that class : " + theMember);
            }

            else throw new IncorrectOperationException("This kind of member not handled yet : " + theMember);  // FIXME


        }

        return true;
    }






    public static List<PsiMethod> findCompatibleMethodsInClass(PsiMethod m, PsiClass c){
        final List <PsiMethod> result = new LinkedList<PsiMethod>();
        for (PsiMethod m_tmp: c.getMethods()){
            if (Compatibility.isCompatible(m, m_tmp))
                result.add(m_tmp);
        }
        return result;
    }

    public static List<PsiMethod> findCompatiblePublicMethodsInClass(PsiMethod m, PsiClass c){
        final List <PsiMethod> result = new LinkedList<PsiMethod>();
        for (PsiMethod m_tmp: c.getMethods()){
            if (Compatibility.isCompatible(m, m_tmp) && m_tmp.hasModifierProperty("public"))
                result.add(m_tmp);
        }
        return result;
    }

    public static List<PsiMethod> findSemiCompatibleMethodsInClass(PsiMethod m, PsiClass c){
        final List <PsiMethod> result = new LinkedList<PsiMethod>();
        for (PsiMethod m_tmp: c.getMethods()){
            if (Compatibility.isSemiCompatible(m, m_tmp))
                result.add(m_tmp);
        }
        return result;
    }





    public static int hasCompatiblePublicMethod(PsiMethod m, PsiClass c) {
        return findCompatiblePublicMethodsInClass(m, c).size();
    }

    public static int hasCompatibleMethod(PsiMethod m, PsiClass c) {
        return findCompatibleMethodsInClass(m,c).size();
    }

    /** Same as  hasCompatibleMethod but checks only the types of the parameters, not the return type.
     * Used to detect problematic overloading in target superclass.
     * */
    public static int hasSemiCompatibleMethod(PsiMethod m, PsiClass c) {
        return findSemiCompatibleMethodsInClass(m, c).size();
    }

    public static int hasCompatibleMethod(PsiMethod m, PsiClass c, boolean checkpublic){
        if (!checkpublic) return hasCompatibleMethod(m, c);
        else return  hasCompatiblePublicMethod(m, c);
    }












    public static boolean memberClassIsInnerClass(MemberInfo m){

           /** ************ THIS IS A COMMENT FROM MemberInfoBase.getOverrides ********************
            * Returns Boolean.TRUE if getMember() overrides something, Boolean.FALSE if getMember()
            * implements something, null if neither is the case.
            * If getMember() is a PsiClass, returns Boolean.TRUE if this class comes
            * from 'extends', Boolean.FALSE if it comes from 'implements' list, null
            * if it is an inner class.
            */

        assert (m.getMember() instanceof PsiClass) ;

        if (m.getOverrides() == null ) return true ;
        else return false;
    }





    /* ------ Analysis : can gen ------ */


    /** returns null if the method cannot be pulled up with generification,
     *  returns the smallest set of classes with compatible method in hiearchy otherwise
     *  (the smallest set with compatible methods that covers all the branches of the hierarchy) */
    // only valid for methods (used for GUI)
    @Nullable
    public static Collection<PsiClass> canGenMethod(MemberInfo member, PsiClass superclass){

          assert(member.getMember() instanceof PsiMethod);

          try {
                    Collection<PsiClass> res =  findSubClassesWithCompatibleMember(member, superclass);
                    // no exception raised means there is a compatible member in all branches of the hierarchy.

                    // TODO : check that the method is not already overriding a method.
                    // TODO: There might be also a problem when introducing the method in super class introduces a nasty overloading.
                    if (hasSemiCompatibleMethod((PsiMethod) member.getMember(), superclass) > 0)
                        return null ; // This avoids a possibly problematic overloading in super class.

                    return res ;
          }

          catch (AmbiguousOverloading e) {
                    System.out.println("(debug) ambiguity found (overloading)") ; // debug
                    return null ;
          }

          catch (MemberNotImplemented e)    {
                 System.out.println("(debug) missing implementation for " + member.getMember() + " (exception)") ; // debug
                 return null;
          }

    }

    // This is used for the GUI : the GUI just needs a boolean, so the set of classes is discarded.
    // TODO : avoid to lose the information on the set of classes to avoid to have to compute it again later.
    public static boolean computeCanGenMember(MemberInfo member, PsiClass sup) {
        boolean result;
        PsiMember m = member.getMember();

        if(!(m instanceof PsiMethod)) {
            result = false ;
        }
        else{
            Collection<PsiClass> compatClasses = canGenMethod(member, sup);

            if (compatClasses != null)  result = true ;
            else  result = false ;
        }
        return result;
    }
}
