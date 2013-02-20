import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;
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
     * Two methods are compatible when they have the same name and their type (return type + parameter types) are anti-unifiable.
     * The sister classes of a class c are the direct (or indirect?) subclasses of the direct superclass (or interface?) of m.
     */

    static List<PsiMethod> findCompatibleMethodsInClass(PsiMethod m, PsiClass c){
      final List <PsiMethod> result = new LinkedList<PsiMethod>();
        for (PsiMethod m_tmp: c.findMethodsByName(m.getName(), false)){
            if (antiUnifiable(m, m_tmp)) result.add(m_tmp);
        }
      return result;
    }

    static int hasCompatibleMethod(PsiMethod m, PsiClass c, boolean checkpublic){
        if (!checkpublic) return hasCompatibleMethod(m, c);
        else return  hasCompatiblePublicMethod(m, c);
    }

    static int hasCompatibleMethod(PsiMethod m, PsiClass c) {
        int count = 0;
        for (PsiMethod m_tmp: c.findMethodsByName(m.getName(), false)) {
            if (antiUnifiable(m, m_tmp)) count++;
        }
        return count;
    }

    static boolean hasImplements(PsiClass c, PsiClass i){
        assert (i.isInterface());
        for (PsiClass tmp : c.getInterfaces()) {
          if (tmp.equals(i)) return true ;   // not sure that it works.
        }
        return false ;
    }

    static int hasCompatiblePublicMethod(PsiMethod m, PsiClass c) {
        int count = 0;
        for (PsiMethod m_tmp: c.findMethodsByName(m.getName(), false)) {
            if (m_tmp.hasModifierProperty("public") && antiUnifiable(m, m_tmp)) count++;
        }
        return count;
    }

    static boolean hasMethodWithSameType(PsiMethod m, PsiClass c){
        for (PsiMethod m_tmp: c.findMethodsByName(m.getName(), false)) {
            if (sameType(m, m_tmp)) return true;   // warning, concept of "same type" not defined
        }
        return false;
    }


    static Boolean antiUnifiable(PsiMethod m1, PsiMethod m2){
      final PsiParameter[] l1=m1.getParameterList().getParameters();
      final PsiParameter[] l2=m2.getParameterList().getParameters();
      final int count1 = m1.getParameterList().getParametersCount() ;
      if (count1 != m2.getParameterList().getParametersCount()) return false ;
      if (!antiUnifiable(m1.getReturnType(), m2.getReturnType())) return false ;
      for (int i = 0; i<count1; i++){
          if (!antiUnifiable(l1[i].getType(), l2[i].getType())) return false ;
      }

      return true;
    }

    static boolean antiUnifiable(PsiType t1, PsiType t2){
        if (t1.equals(t2)) return true ;
        if (t1 instanceof PsiPrimitiveType || t2 instanceof PsiPrimitiveType) return false ;
        return true ;
    }

    static Boolean sameType (PsiMethod m1, PsiMethod m2){

      if (!m1.getReturnType().equals(m2.getReturnType())) return false ;
      //if (m1.getParameterList().getParametersCount() == 0 &&  m2.getParameterList().getParametersCount() == 0) return true;
      //else {
          if (m1.getParameterList().getParametersCount() != m2.getParameterList().getParametersCount()) return false ;

          for (int i = 0 ; i < m1.getParameterList().getParametersCount(); i++)  {
            if (!m1.getParameterList().getParameters()[i].equals(m2.getParameterList().getParameters()[i])) return false ;
          }
      //}

      return true;
    }

    static Boolean allTypesEquals (Collection<PsiType> c, PsiType t){
       for (PsiType t2 : c){
         if (!t.equals(t2)) return false ;
       }
       return true;
    }

    static Boolean allObjectTypes(Collection<PsiType> c){
       for (PsiType t : c) {
           if (t instanceof PsiPrimitiveType) return false;
       }
       return true ;
    }

    static class MemberNotImplemented extends Exception{
        PsiClass c ; PsiMember m;
        MemberNotImplemented(PsiMember _m, PsiClass _c){ m = _m ; c = _c ; }
    }



    static class AmbiguousOverloading extends Exception{
        PsiClass c ; PsiMember m;
        AmbiguousOverloading(PsiMember _m, PsiClass _c){ m = _m ; c = _c ; }
        public String toString() { return ("Ambiguity in overloading for sister method " + m + " in class " + c + "." ) ;}
    }

    // must find an implementation of the method on all the branches of the tree of subclasses
    // Otherwise: throws an exception.
    static Collection<PsiClass> findSubClassesWithCompatibleMethod(PsiMethod m, PsiClass superClass, boolean checkpublic)
            throws AmbiguousOverloading, MemberNotImplemented {

      final Collection<PsiClass> res = new LinkedList<PsiClass>();
      final Collection <PsiClass> directSubClasses = findDirectSubClasses(superClass); // TODO : should limit the scope of search


//      System.out.println("considered subclasses :" + directSubClasses); // debug

      for (PsiClass c: directSubClasses){
          final int count = hasCompatibleMethod(m, c, checkpublic);
//          System.out.println("for " + c + " count = " + count); // debug
          if (count>1) throw new AmbiguousOverloading(m,c);
          if (count==1)  res.add(c); // the method is found       // TODO : check that there is only one anti-unifiable method

          else { // no compatible method. Two cases.

                if (c.isInterface() || c.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    //the method is not found but it may be in all subclasses, which would be ok.

                    try {
                        Collection<PsiClass> localresult = findSubClassesWithCompatibleMethod(m, c, checkpublic);
                        res.addAll(localresult);
                        }
                    catch (MemberNotImplemented e) { throw e ;}
                    }
                 else {
                    // the method is not found, and since that class is concrete, pull up would introduce an error.
                    throw new MemberNotImplemented(m,c);

                 }

          }
      }


      return res;
    }
    static Collection<PsiClass> findDirectSubClassesWithImplements(PsiClass i, PsiClass superClass)
        throws MemberNotImplemented
    {
        assert (i.isInterface());
        final Collection <PsiClass> directSubClasses = findDirectSubClasses(superClass);
        for (PsiClass c: directSubClasses){
            if (! hasImplements(c,i)) throw new MemberNotImplemented(i, c);
        }
        return directSubClasses ;
    }

    static Collection <PsiClass> findSubClassesWithCompatibleMember(MemberInfo mem, PsiClass superClass) throws MemberNotImplemented, AmbiguousOverloading {
        PsiMember m = mem.getMember();
        if (m instanceof PsiMethod){
            return findSubClassesWithCompatibleMethod((PsiMethod) m, superClass, false);
            }

        else if (m instanceof PsiClass && memberIsImplements(mem)) { return findDirectSubClassesWithImplements((PsiClass)m, superClass);}
        else if (m instanceof PsiClass) { return null ; } // TODO : check that
        else if (m instanceof PsiField) { throw new IncorrectOperationException("implement me : pull up field");}
        else throw new IncorrectOperationException("cannot find convenient subclasses for this kind of member:" + m);
    }




    @Deprecated
    static boolean checkSubClassesHaveSameMethod(PsiMethod m, PsiClass superClass){
      final Collection <PsiClass> classes = findAllSubClasses(superClass);
      final Collection <PsiClass> result = new LinkedList<PsiClass>();
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

    // direct subclasses of superclass
    static Collection <PsiClass> findSisterClasses(PsiClass aClass){
      SearchScope scope = GlobalSearchScopes.directoryScope(((PsiJavaFile) aClass.getContainingFile()).getContainingDirectory(), false) ;
      return ClassInheritorsSearch.search(aClass.getSuperClass(), scope,false).findAll(); // use project scope  / false means direct inheritance
    }

    // direct AND indirect subclasses
    static Collection<PsiClass> findAllSubClasses(PsiClass superClass) {
        return ClassInheritorsSearch.search(superClass, true).findAll();     // use default scope
    }

    // direct subclasses
    static Collection<PsiClass> findDirectSubClasses(PsiClass superClass) {
        return ClassInheritorsSearch.search(superClass, false).findAll();     // use default scope
    }




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

    // TODO : use this in check...
    @Deprecated
    static boolean sameMethods(PsiMethod m1, PsiMethod m2){
        return ( m1.getName().equals(m2.getName())
                && m1.getReturnType().getCanonicalText().equals(m2.getReturnType().getCanonicalText())
                && sameParameterLists(m1.getParameterList(), m2.getParameterList())) ;
    }

    static boolean sameFields(PsiField f1, PsiField f2){
        return ( f1.getName().equals(f2.getName()) && f1.getType().equals(f2.getType()));

    }

    static boolean hasCompatibleMembers(PsiClass c, MemberInfo[] infos) throws AmbiguousOverloading {
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
            else throw new IncorrectOperationException("this kind of member not handled yet : " + x);


        }
        if (ambiguity) throw new AmbiguousOverloading(ambiguitymember, ambiguityclass);
        return true;
    }

    // TODO : ce code est déjà dans compatibleMethods au quelque-chose comme ça
    // TODO : remplacer par equals?
    static boolean sameParameterLists(PsiParameterList l1, PsiParameterList l2){
        if (l1.getParametersCount() != l2.getParametersCount()) return false ;
        PsiParameter[] a1 = l1.getParameters();
        PsiParameter[] a2 = l2.getParameters();

        for (int i = 0; i< a1.length; i++){
            if (! a1[i].getTypeElement().getType().getCanonicalText().equals(a2[i].getTypeElement().getType().getCanonicalText())) return false ;   // TODO : remove getCanonicalText?
        }
        return true ;

    }

    static boolean hasMembers(PsiClass c, MemberInfo[] infos){
        System.out.println("invocation of hasMembers(" + c + ",...)");
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
            else if (x instanceof PsiClass && memberIsImplements(member)) {  // case "implements x"
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

    static boolean memberIsImplements(MemberInfo m){

           /** ************ THIS IS A COMMENT FROM MemberInfoBase.getOverrides ********************
            * Returns Boolean.TRUE if getMember() overrides something, Boolean.FALSE if getMember()
            * implements something, null if neither is the case.
            * If getMember() is a PsiClass, returns Boolean.TRUE if this class comes
            * from 'extends', Boolean.FALSE if it comes from 'implements' list, null
            * if it is an inner class.
            */

        PsiMember mem = m.getMember();

        if ( !(mem instanceof PsiClass )) return false;
        if ( (((PsiClass) mem).isInterface())){
                if (Boolean.FALSE.equals(m.getOverrides())) return true ;
        }
        return false;
    }
    static boolean memberIsInnerClass(MemberInfo m){

           /** ************ THIS IS A COMMENT FROM MemberInfoBase.getOverrides ********************
            * Returns Boolean.TRUE if getMember() overrides something, Boolean.FALSE if getMember()
            * implements something, null if neither is the case.
            * If getMember() is a PsiClass, returns Boolean.TRUE if this class comes
            * from 'extends', Boolean.FALSE if it comes from 'implements' list, null
            * if it is an inner class.
            */
        PsiMember mem = m.getMember();

        if ( !(mem instanceof PsiClass )) return false;
        if (m.getOverrides() == null ) return true ;
        else return false;
    }




    // only valid for methods (used for GUI)
    @Nullable
    static Collection<PsiClass> canGenMethod(MemberInfo member, PsiClass superclass){
          assert(member.getMember() instanceof PsiMethod);

          //final PsiMember m = member.getMember();
          //if (m instanceof PsiMethod){
          //  PsiMethod method = (PsiMethod) m ; // cannot fail


            // check that a compatible method is found in all the hierarchy
            //if(!checkSubClassesHaveOneCompatibleMethod(method, superclass)) return false ;

            try { return findSubClassesWithCompatibleMember(member, superclass);}
             catch (AmbiguousOverloading e) {
                    System.out.println("ambiguity found") ; // debug
                    return null ;
                }
             catch (MemberNotImplemented e)    {
                 System.out.println("missing implementation for" + member.getMember() + "(with exception)") ; // debug
                 return null;
             }

            // DONE .check that we are not in the case of an interface as super type when one of the methods to be pulled up is not public
            //if (superclass.isInterface()) {
            //    final List<PsiMethod> l = findCompatibleMethodsInSubclasses(method, superclass);
            //    for (PsiMethod met : l) { if (!met.isPublic()) return false;}
            //}

            // check that the comaptible methods are not nastily overloaded TODO
            //return true;
          //}
          //else return false ; // TODO : generify fields ?
    }
}
