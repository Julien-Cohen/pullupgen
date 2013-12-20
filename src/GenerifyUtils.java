import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.*;


/*
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


public class GenerifyUtils {

    
    static void generifyAbstractMethod(PsiMethod m, Map<Integer, PsiTypeParameter> lp){
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
    
    
    static List <PsiMethod> computeSisterMethods(PsiMethod m, PsiClass c){
      final List <PsiMethod> result = new LinkedList<PsiMethod>();
        for (PsiMethod m_tmp: c.findMethodsByName(m.getName(), false)){ if (compatibleArity(m, m_tmp)) {result.add(m_tmp);}}
      return result;
    }

    static boolean hasMethodWithSameArity(PsiMethod m, PsiClass c){
        for (PsiMethod m_tmp: c.findMethodsByName(m.getName(), false)) {if (compatibleArity(m, m_tmp)) return true; }
        return false;        
    }
    
    
    static Boolean compatibleArity (PsiMethod m1, PsiMethod m2){
      return (m1.getParameterList().getParametersCount() == m2.getParameterList().getParametersCount());
    }

    
    // modifies the parameter 'theSubstitution'
    static Map <Integer, PsiTypeParameter> unify(List<PsiMethod> lm,  Map<PsiTypeParameter,Map<PsiClass,PsiType>> theSubstitution, String basename, PsiElementFactory factory){
       final HashMap<Integer, PsiTypeParameter> result = new HashMap<Integer, PsiTypeParameter>();

        {
         // first, check the return type
         final List <PsiType> returnTypes = new Vector<PsiType>();
         final Map<PsiClass, PsiType> returnTypesMap = new HashMap<PsiClass, PsiType>();   
         for (PsiMethod m: lm){  // collect the return types
           returnTypes.add(m.getReturnType()); 
           returnTypesMap.put(m.getContainingClass(), m.getReturnType());  
         }
         // check these types (and modify 'result')
         if (!allTypesEquals(returnTypes, returnTypes.get(0))) {
           if (allObjectTypes(returnTypes)) {
               // we know that we have to generify that position, but we have to check if we can reuse an existing type parameter.
               if (theSubstitution.containsValue(returnTypesMap)) result.put(-1, getKeyFor(theSubstitution, returnTypesMap)); // -1 represent the return type (by convention)
               else {
                    PsiTypeParameter t = factory.createTypeParameterFromText(buildTypeParameter(-1, basename, factory), null);
                    result.put(-1, t);
                    theSubstitution.put(t, returnTypesMap);
               } // TODO : return also the list of created type parameters
           }
           else throw new IncorrectOperationException("Cannot generify primitive type.");
         } 
        }



       // second, check the parameters
       List<PsiType> consideredTypes;
       Map<PsiClass, PsiType> consideredTypesMap;
       for (int pos=0; pos<lm.get(0).getParameterList().getParametersCount(); pos++) { // for each position
         consideredTypes = new Vector<PsiType>();
         consideredTypesMap = new HashMap<PsiClass, PsiType>();     
         for (PsiMethod m: lm){ // collect the types at the considered position in the list of methods
           consideredTypes.add(m.getParameterList().getParameters()[pos].getType());
           consideredTypesMap.put(m.getContainingClass(), m.getParameterList().getParameters()[pos].getType());    
         }
         // check these types (modify 'result')
         if (!allTypesEquals(consideredTypes, consideredTypes.get(0))) {
           if (allObjectTypes(consideredTypes)) {
             // we know that we have to generify that position, but we have to check if we can reuse an existing type parameter.
             if(theSubstitution.containsValue(consideredTypesMap)) {
                 result.put(pos, getKeyFor(theSubstitution, consideredTypesMap));} // -1 represent the return type (by convention)
             else {
                 PsiTypeParameter t = factory.createTypeParameterFromText(buildTypeParameter(pos, basename, factory), null);
                 result.put(pos, t);
                 theSubstitution.put(t, consideredTypesMap);
             }

           }
           else throw new IncorrectOperationException("Cannot generify primitive type.");
         }

       }

       return result ;
    }

    static <A,B> A getKeyFor (Map <A, B> m , B v) throws Error {
      for (A s: m.keySet()){if (m.get(s).equals(v)) return s;}
      throw new Error("Internal error : key not found in map");
    }
    
    static Boolean allTypesEquals (Collection <PsiType> c, PsiType t){
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


    static Collection <PsiClass> computeSubClassesWithCompatibleMethod(PsiMethod m, PsiClass superClass){
      final Collection <PsiClass> classes = ClassInheritorsSearch.search(superClass).findAll(); // TODO : should limit the scope of search
      final Collection <PsiClass> result = new LinkedList<PsiClass>();
      for (PsiClass c: classes){
          if (hasMethodWithSameArity(m, c))  result.add(c); // the method is found
          
          else { if (c.isInterface() || c.hasModifierProperty(PsiModifier.ABSTRACT)) { //the method is not found but it may be in all subclasses, which would be ok.
                    Collection <PsiClass> localresult = computeSubClassesWithCompatibleMethod(m, c); // that will throw an exception if the method is not implemented in all the subclasses
                    result.addAll(localresult);
                    }
                  else { // the method is not found, and since that class is concrete, pull up would introduce an error.
                            throw new IncorrectOperationException("The method is not implemented by all sister classes.");

                  }
          
          }
      }
        
      return result;
    }


    // for extract super class
    static Collection <PsiClass> computeSisterClasses(PsiClass aClass){

      SearchScope scope = GenAnalysisUtils.getDirScope(aClass);
        
      return ClassInheritorsSearch.search(aClass.getSuperClass(), scope,false).findAll(); // With that, get all the classes in the project (all packages)

    }


    static List<PsiMethod> computeSisterMethods(PsiMethod method, Collection<PsiClass> sisterClasses) {
        final List<PsiMethod> sisterMethods = new LinkedList<PsiMethod>();
        for (PsiClass c:sisterClasses){
              final List<PsiMethod> localSisterMethods = computeSisterMethods(method, c);
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

    protected static void updateExtendsStatementsInSisterClasses(List<PsiMethod> sisterMethods, Map<PsiTypeParameter, Map<PsiClass,PsiType>> sub, PsiClass superClass, PsiElementFactory factory) {
          
      for (PsiTypeParameter t: sub.keySet()){
        Map<PsiClass,PsiType> m = sub.get(t);
        for (PsiClass c : m.keySet()){
          final PsiJavaCodeReferenceElement r = findReferenceToSuperclass(c, superClass);
          addTypeParameterToReference(r, m.get(c), factory);
          }
      }
    }

    static private PsiJavaCodeReferenceElement findReferenceToSuperclass(PsiClass c, PsiClass s){
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
    
    private static void addTypeParameterToReference(PsiJavaCodeReferenceElement r, PsiType t, PsiElementFactory factory ){
      r.getParameterList().add(factory.createTypeElement(t));  
    }



    static boolean directlyCompatibleMethods(PsiMethod m1, PsiMethod m2){
        return ( m1.getName().equals(m2.getName())
                && m1.getReturnType().getCanonicalText().equals(m2.getReturnType().getCanonicalText())
                && compareParameterLists(m1.getParameterList(), m2.getParameterList())) ;
    }

    static boolean directlyCompatibleFields(PsiField f1, PsiField f2){
        return ( f1.getName().equals(f2.getName()) && f1.getType().equals(f2.getType()));

    }

    static boolean hasCompatibleMembers(PsiClass c, MemberInfo[] infos){
        for (MemberInfo member: infos){
            PsiMember x = member.getMember();

            if (x instanceof PsiMethod){
              PsiMethod xx = (PsiMethod) x ;
              if (!hasMethodWithSameArity(xx, c)) return false;
            }
            else if (x instanceof PsiField){
             PsiField xx = (PsiField) x;
             boolean found = false ;
              for (PsiField m : c.getFields()) {
                    if (directlyCompatibleFields(xx, m)) found=true;
              }
              if (!found) return false;
            }
            else throw new IncorrectOperationException("this kind of member not handled yet : " + x);


        }
        return true;
    }

    static boolean compareParameterLists (PsiParameterList l1, PsiParameterList l2){
        if (l1.getParametersCount() != l2.getParametersCount()) return false ;
        PsiParameter[] a1 = l1.getParameters();
        PsiParameter[] a2 = l2.getParameters();

        for (int i = 0; i< a1.length; i++){
            if (! a1[i].getTypeElement().getType().getCanonicalText().equals(a2[i].getTypeElement().getType().getCanonicalText())) return false ;
        }
        return true ;

    }

    static boolean hasMembers(PsiClass c, MemberInfo[] infos){
        for (MemberInfo member: infos){
            PsiMember x = member.getMember();

            if (x instanceof PsiMethod){
              PsiMethod xx = (PsiMethod) x ;
              boolean found = false ;
              for (PsiMethod m : c.getMethods()) {
                    if (directlyCompatibleMethods(xx, m)) { found=true; } //else {System.out.println( xx + " and " + m + " found incompatible");}
              }
              if (!found) return false;
            }
            else if (x instanceof PsiField){
             PsiField xx = (PsiField) x;
             boolean found = false ;           // TODO : follow the structure of 'hasCompatibleMembers' and use GenerifyUtils
              for (PsiField m : c.getFields()) {
                    if (directlyCompatibleFields(xx, m)) found=true;
              }
              if (!found) return false;
            }
            else throw new IncorrectOperationException("this type of member not handled yet : " + x);


        }
        return true;
    }
    
   // returns the mappings that are in m1 but not in m2
   static Map<PsiTypeParameter,Map<PsiClass,PsiType>> difference(Map<PsiTypeParameter,Map<PsiClass,PsiType>> m1, Map<PsiTypeParameter,Map<PsiClass,PsiType>> m2){
       HashMap<PsiTypeParameter,Map<PsiClass,PsiType>> result = new HashMap<PsiTypeParameter, Map<PsiClass, PsiType>>() ;
       for(PsiTypeParameter t:m1.keySet()){
           if (!m2.containsKey(t)) result.put(t, m1.get(t));
       }
       return result;
   }
    
   static Map<PsiTypeParameter, Map<PsiClass,PsiType>> computeCurrentSub(PsiClass superclass, Collection<PsiClass> sisterClasses){
       Map<PsiTypeParameter, Map<PsiClass,PsiType>> result= new HashMap<PsiTypeParameter, Map<PsiClass,PsiType>>();
       PsiTypeParameter[] a = superclass.getTypeParameters();
       
       for (int i=0; i<a.length; i++){
         Map <PsiClass, PsiType> m= new HashMap <PsiClass, PsiType>();
         for (PsiClass c : sisterClasses){ m.put(c, findReferenceToSuperclass(c,superclass).getTypeParameters()[i]);}
         result.put(a[i], m);
       }
       return result;
   }
}
