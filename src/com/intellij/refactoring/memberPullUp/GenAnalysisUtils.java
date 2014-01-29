package com.intellij.refactoring.memberPullUp;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: cohen-j
 * Date: 20/12/12
 * Time: 14:56
 * To change this template use File | Settings | File Templates.
 */


public class GenAnalysisUtils {

    /** DEFINITIONS :
     * Two types are anti-unifiable when they are instances of more general type (with type variables).
     * Two methods are compatible when they have the same name and their types (return type + parameter types) are anti-unifiable.
     * The sister classes of a class are the direct subclasses of its direct superclass.
     */













    /* ------ Anti-Unification ------  */

    public static Boolean antiUnifiable(PsiMethod m1, PsiMethod m2){

      if (!antiUnifiable(m1.getReturnType(), m2.getReturnType())) return false ;

      return antiUnifiableParameters(m1.getParameterList().getParameters(), m2.getParameterList().getParameters()) ;
    }


    public static Boolean antiUnifiableParameters(PsiParameter[] l1, PsiParameter[] l2){
      final int count1 = l1.length ;
      if (count1 != l2.length) return false ;

      for (int i = 0; i<count1; i++){
          if (!antiUnifiable(l1[i].getType(), l2[i].getType())) return false ;
      }

      return true;
    }

    public static boolean antiUnifiable(PsiType t1, PsiType t2){
        if (t1.equals(t2)) return true ;
        if (t1 instanceof PsiPrimitiveType || t2 instanceof PsiPrimitiveType) return false ;
        return true ;
    }














    /* ------ Type, Field and Method Comparison ------ */

    public static Boolean allTypesEquals (List <PsiType> c){
       if (c.size() < 2) return true ;
        else {
            PsiType t = c.get(0);
            for (PsiType t2 : c){
                if (!t.equals(t2)) return false ;
            }
            return true;
        }
    }

    public static Boolean allObjectTypes(Collection<PsiType> c){
       for (PsiType t : c) {
           if (t instanceof PsiPrimitiveType) return false;
       }
       return true ;
    }

    public static boolean sameFields(PsiField f1, PsiField f2){
        return ( f1.getName().equals(f2.getName()) && f1.getType().equals(f2.getType()));

    }

    // see also sameMethod
    public static boolean hasMethodWithSameType(PsiMethod m, PsiClass c){
        for (PsiMethod m_tmp: c.findMethodsByName(m.getName(), false)) {
            if (sameType(m, m_tmp)) return true;
        }
        return false;
    }


    // TODO : use this instead of checkSubClassesHaveSameMethod
    public static boolean sameMethods(PsiMethod m1, PsiMethod m2){
        return  m1.getName().equals(m2.getName())
                && sameType(m1,m2)  ;
    }

    public static Boolean sameType (PsiMethod m1, PsiMethod m2){
        return m1.getReturnType().equals(m2.getReturnType())
                && sameParameterLists(m1.getParameterList(), m2.getParameterList()) ;
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



    public static boolean hasMembers(PsiClass c, MemberInfo[] infos){
        for (MemberInfo member: infos){
            PsiMember x = member.getMember();

            if (x instanceof PsiMethod){
                PsiMethod xx = (PsiMethod) x ;
                boolean found = false ;
                for (PsiMethod m : c.getMethods()) {
                    if (sameMethods(xx, m)) { found=true; } //else {System.out.println( xx + " and " + m + " found incompatible");}
                }
                if (!found) return false;
            }
            else if (x instanceof PsiField){
                PsiField xx = (PsiField) x;
                boolean found = false ;           // TODO : follow the structure of 'hasCompatibleMembers' and use GenerifyUtils
                for (PsiField m : c.getFields()) {
                    if (sameFields(xx, m)) found=true; // break loop ?
                }
                if (!found) return false;
            }
            else if (x instanceof PsiClass && memberClassComesFromImplements(member)) {  // case "implements x"
                PsiClass xx = (PsiClass) x ;
                boolean found = false;
                for (PsiClassType m : c.getImplementsListTypes()){
                    if (m.resolve().equals(xx)) {found = true;} // not sure
                }
                if (!found) return false;
            }
            else throw new IncorrectOperationException("this type of member not handled yet : " + x);


        }
        return true;
    }



    @Deprecated    // use sameMethods instead
    public static boolean checkSubClassesHaveSameMethod(PsiMethod m, PsiClass superClass){
        final Collection <PsiClass> classes = findDirectSubClasses(superClass);

        for (PsiClass c: classes){
            if (!hasMethodWithSameType(m, c))  {
                if (c.isInterface() || c.hasModifierProperty(PsiModifier.ABSTRACT)) { //the method is not found but it may be in all subclasses, which would be ok.
                    if (!checkSubClassesHaveSameMethod(m, c)) {
                        return false;
                    }
                }
                else { // the method is not found, and since that class is concrete, pull up would introduce an error.
                    return false;
                }
            }
        }
        return true;
    }

    public static GlobalSearchScope getDirScope(PsiClass aClass) {
        return GlobalSearchScopes.directoryScope(getContainingDirectory(aClass), false);
    }

    public static PsiDirectory getContainingDirectory(PsiClass aClass) {
        return ((PsiJavaFile) aClass.getContainingFile()).getContainingDirectory();
    }













     /* ------ Lookup for implements (interfaces) ------ */



    public static class MemberNotImplemented extends Exception{
        PsiClass c ; PsiMember m;
        MemberNotImplemented(PsiMember _m, PsiClass _c){ m = _m ; c = _c ; }
    }




    public static Collection<PsiClass> findDirectSubClassesWithImplements(PsiClass i, PsiClass superClass)
            throws MemberNotImplemented
    {
        assert (i.isInterface());
        final Collection <PsiClass> directSubClasses = findDirectSubClasses(superClass);
        for (PsiClass c: directSubClasses){
            if (! hasImplements(c,i)) throw new MemberNotImplemented(i, c);
        }
        return directSubClasses ;
    }


    public static boolean hasImplements(PsiClass c, PsiClass i){
        assert (i.isInterface());
        for (PsiClass tmp : c.getInterfaces()) {
            if (tmp.equals(i)) return true ;   // not sure that it works.
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

        final PsiMember mem = m.getMember();

        if ( !(mem instanceof PsiClass )) return false;  // TODO : remove that line
        if ( (((PsiClass) mem).isInterface())){
            if (Boolean.FALSE.equals(m.getOverrides())) return true ;               // TODO : simplify this line
        }
        return false;
    }















    /* ------ Exceptions ------ */


    public static class AmbiguousOverloading extends Exception{
        PsiClass c ; PsiMember m;
        AmbiguousOverloading(PsiMember _m, PsiClass _c){ m = _m ; c = _c ; }
        public String toString() { return ("Ambiguity in overloading for sister method " + m + " in class " + c + "." ) ;}
    }











    /* ------ Lookup for subclasses  ------ */


    // direct subclasses  (use findDirectSubClassesInPackage instead)
    @Deprecated
    public static Collection<PsiClass> findDirectSubClasses(@NotNull PsiClass superClass) {
        return ClassInheritorsSearch.search(superClass, false).findAll();     // use default scope
    }
    public static Collection<PsiClass> findDirectSubClassesInDirectory(@NotNull PsiClass superClass, PsiDirectory directory) {
        return ClassInheritorsSearch.search(superClass, GlobalSearchScopes.directoryScope(directory, false), false).findAll();
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

        else if (m instanceof PsiClass && memberClassComesFromImplements(mem)) {
                return findDirectSubClassesWithImplements((PsiClass)m, superClass);}

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
        final Collection <PsiClass> directSubClasses = findDirectSubClasses(superClass); // TODO : limit the scope of search

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
    static List<PsiMethod> findCompatibleMethods(PsiMethod method, Collection<PsiClass> sisterClasses) {
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



    /* see def for compatibility at beginiing of this file : same name + anti-unifiable */
    public static boolean hasCompatibleMembers(PsiClass c, MemberInfo[] infos) throws AmbiguousOverloading {
        boolean ambiguity = false ;
        PsiClass ambiguityclass = null ;
        PsiMember ambiguitymember = null ;
        for (MemberInfo member: infos){
            PsiMember x = member.getMember();

            if (x instanceof PsiMethod){
              PsiMethod xx = (PsiMethod) x ;
              final int count = hasCompatibleMethod(xx, c, false);
              if (count >1) {ambiguity = true ; ambiguityclass = c ; ambiguitymember = xx;}
              else if (count == 0) return false;
            }
            else if (x instanceof PsiField){
             PsiField xx = (PsiField) x;
             boolean found = false ;
              for (PsiField m : c.getFields()) {
                    if (sameFields(xx, m)) found=true;
              }
              if (!found) return false;
            }
            else if (x instanceof PsiClass) {throw new IncorrectOperationException("(hascompatiblemembers) don't know what to do with that class : " + x);}
            else throw new IncorrectOperationException("this kind of member not handled yet : " + x);  // FIXME


        }
        if (ambiguity) throw new AmbiguousOverloading(ambiguitymember, ambiguityclass);
        return true;
    }

    public static List<PsiMethod> findCompatibleMethodsInClass(PsiMethod m, PsiClass c){
        final List <PsiMethod> result = new LinkedList<PsiMethod>();
        for (PsiMethod m_tmp: c.findMethodsByName(m.getName(), false)){
            if (antiUnifiable(m, m_tmp)) result.add(m_tmp);
        }
        return result;
    }

    public static int hasCompatibleMethod(PsiMethod m, PsiClass c, boolean checkpublic){
        if (!checkpublic) return hasCompatibleMethod(m, c);
        else return  hasCompatiblePublicMethod(m, c);
    }



    /* see definition for compatibility at top of this file */
    public static int hasCompatiblePublicMethod(PsiMethod m, PsiClass c) {
        int count = 0;
        for (PsiMethod m_tmp: c.findMethodsByName(m.getName(), false)) {
            if (m_tmp.hasModifierProperty("public") && antiUnifiable(m, m_tmp)) count++;
        }
        return count;
    }

    public static int hasCompatibleMethod(PsiMethod m, PsiClass c) {
        int count = 0;
        for (PsiMethod m_tmp: c.findMethodsByName(m.getName(), false)) {
            if ( (!m_tmp.hasModifierProperty("private")) && antiUnifiable(m, m_tmp)) count++;
        }
        return count;
    }

    /** Same as  hasCompatibleMethod but checks only the types of the parameters, not the return type.
     * Used to detect problematic overloading in target superclass.
     * */
    public static int hasParameterCompatibleMethod(PsiMethod m, PsiClass c) {
        int count = 0;
        for (PsiMethod m_tmp: c.findMethodsByName(m.getName(), false)) {
            if (antiUnifiableParameters(m.getParameterList().getParameters(), m_tmp.getParameterList().getParameters())) count++;
        }
        return count;
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
        PsiMember mem = m.getMember();

        if ( !(mem instanceof PsiClass )) return false;    // TODO : remove that line
        if (m.getOverrides() == null ) return true ;
        else return false;
    }





    /* ------ Analysis : can gen ------ */


    /** returns null if the method cannot be pulled up with generification */
    /** returns the smallest set of classes with compatible method in hiearchy otherwise
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
                    if (hasParameterCompatibleMethod((PsiMethod)member.getMember(),superclass) > 0) return null ; // This avoids a possibly problematic overloading in super class.

                    return res ;
          }
          catch (AmbiguousOverloading e) {
                    System.out.println("ambiguity found (overloading)") ; // debug
                    return null ;
          }
          catch (MemberNotImplemented e)    {
                 System.out.println("missing implementation for" + member.getMember() + "(with exception)") ; // debug
                 return null;
          }

    }

    // This is used for the GUI : the GUI needs just a boolean, so the set of classes is discarded.
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
