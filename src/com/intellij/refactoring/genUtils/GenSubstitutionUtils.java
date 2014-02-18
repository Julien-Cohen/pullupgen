
/**
 * Created with IntelliJ IDEA.
 * User: cohen-j
 * Date: 20/12/12
 * Time: 15:01
 * To change this template use File | Settings | File Templates.
 */

package com.intellij.refactoring.genUtils;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.util.*;


public class GenSubstitutionUtils {




    /* Compute the antiunification for a list of methods. */
    /* Return the elements of the method profile (return type or parameter types) to be generified, with the corresponding type variable to replace each element. */
    /* Also modifies the dependent-substitution that says how each class instantiates the type parameter of its (direct?) superclass */
    public static ParamSubstitution antiunify(List<PsiMethod> lm, /* TODO : can lm be empty? */
                                       DependentSubstitution theSubstitution, /* this parameter indicated the previously existing intanciation of type variables (from extends statements), for potential reuse. */
                                                                          /* This parameter can be modified (when new type parameters have to be introduced)   */
                                                                          String baseNameForTypeVariables,
                                                                          PsiElementFactory factory,        /* this parameter is used to build new type parameters. */
                                                                          Collection<String> boundNames
    ){

       assert(lm.size() != 0);

        final ParamSubstitution result = new ParamSubstitution();

       // first, check the return type
       final List <PsiType> returnTypes = new Vector<PsiType>();
       final Map<PsiClass, PsiType> returnTypesMap = new HashMap<PsiClass, PsiType>();
       for (PsiMethod m: lm){  // collect the return types
           returnTypes.add(m.getReturnType());
           returnTypesMap.put(m.getContainingClass(), m.getReturnType());
       }
       // check these types (and modify 'result')
       if (!GenAnalysisUtils.allTypesEquals(returnTypes)) {

           if (!GenAnalysisUtils.allObjectTypes(returnTypes)) throw new IncorrectOperationException("Cannot generify primitive type.");


           // we know that we have to generify that position, but we have to check if we can reuse an existing type parameter.
           PsiTypeParameter selected;
           if (theSubstitution.containsValue(returnTypesMap)) selected = getKeyFor(theSubstitution, returnTypesMap);
           else {
                    final String fresh =  GenBuildUtils.buildTypeParameter(-1, baseNameForTypeVariables, boundNames);
                    boundNames.add(fresh);
                    selected = factory.createTypeParameterFromText(fresh, null);
                    theSubstitution.put(selected, returnTypesMap); /* the new association is saved (for potential reuse) */
           }
           result.put(-1, selected);    // -1 represent the return type (by convention)
           // TODO : return also the list of created type parameters
       }
       else {} /* when all the return types are equals, nothing to do. */




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
         if (!GenAnalysisUtils.allTypesEquals(consideredTypes)) {
           if (GenAnalysisUtils.allObjectTypes(consideredTypes)) {
             // we know that we have to generify that position, but we have to check if we can reuse an existing type parameter.
             if(theSubstitution.containsValue(consideredTypesMap)) {
                 result.put(pos, getKeyFor(theSubstitution, consideredTypesMap));} // -1 represent the return type (by convention)
             else {
                 final String fresh = GenBuildUtils.buildTypeParameter(pos, baseNameForTypeVariables, boundNames);
                 boundNames.add(fresh);
                 PsiTypeParameter t = factory.createTypeParameterFromText(fresh, null);
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

    // returns the mappings that are in m1 but not in m2
   public static DependentSubstitution difference(DependentSubstitution m1, DependentSubstitution m2){
       DependentSubstitution result = new DependentSubstitution() ;
       for(PsiTypeParameter t:m1.keySet()){
           if (!m2.containsKey(t)) result.put(t, m1.get(t));
       }
       return result;
   }

    public static DependentSubstitution computeCurrentSub(PsiClass superclass, Collection<PsiClass> sisterClasses, PsiElementFactory factory){
        final DependentSubstitution result = new  DependentSubstitution ();

        final PsiTypeParameter[]    a      = superclass.getTypeParameters();

        final int nbParams = a.length;

        for (int i=0; i< nbParams; i++){
            Map <PsiClass, PsiType> m= new HashMap <PsiClass, PsiType>();
            for (PsiClass c : sisterClasses){
                final PsiType[] parameters = GenBuildUtils.findReferenceToSuperclass(c, superclass).getTypeParameters();
                PsiType t;
                assert (i<parameters.length) ; // because we have aligned the parameters in extensions before.
                /* if (i< parameters.length) */ t= parameters[i] ;
                /* else  t = factory.createTypeFromText("Object",null); */ // TODO : remove that
                m.put(c, t);
            }
            result.put(a[i], m);
        }
        return result;
   }

   /* this is a type alias, for readability. */
   public static class ParamSubstitution  extends HashMap<Integer, PsiTypeParameter> implements Map<Integer, PsiTypeParameter>{}


   public static List<String> boundTypeNames(PsiClass c){
       List<String> l = new ArrayList<String>();
       l.add(c.getName());
       for(PsiTypeParameter p : c.getTypeParameters()) l.add(p.getName());
       for(PsiClass i : c.getAllInnerClasses()) l.add(i.getName());

       return l;
   }
}
