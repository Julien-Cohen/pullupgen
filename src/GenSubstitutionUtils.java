import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cohen-j
 * Date: 20/12/12
 * Time: 15:01
 * To change this template use File | Settings | File Templates.
 */
public class GenSubstitutionUtils {
    // modifies the parameter 'theSubstitution'
    static ParamSubstitution unify(List<PsiMethod> lm,  Map<PsiTypeParameter,Map<PsiClass,PsiType>> theSubstitution, String basename, PsiElementFactory factory){
       final ParamSubstitution result = new ParamSubstitution();

        {
         // first, check the return type
         final List <PsiType> returnTypes = new Vector<PsiType>();
         final Map<PsiClass, PsiType> returnTypesMap = new HashMap<PsiClass, PsiType>();
         for (PsiMethod m: lm){  // collect the return types
           returnTypes.add(m.getReturnType());
           returnTypesMap.put(m.getContainingClass(), m.getReturnType());
         }
         // check these types (and modify 'result')
         if (!GenAnalysisUtils.allTypesEquals(returnTypes, returnTypes.get(0))) {
           if (GenAnalysisUtils.allObjectTypes(returnTypes)) {
               // we know that we have to generify that position, but we have to check if we can reuse an existing type parameter.
               if (theSubstitution.containsValue(returnTypesMap)) result.put(-1, getKeyFor(theSubstitution, returnTypesMap)); // -1 represent the return type (by convention)
               else {
                    PsiTypeParameter t = factory.createTypeParameterFromText(GenBuildUtils.buildTypeParameter(-1, basename, factory), null);
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
         if (!GenAnalysisUtils.allTypesEquals(consideredTypes, consideredTypes.get(0))) {
           if (GenAnalysisUtils.allObjectTypes(consideredTypes)) {
             // we know that we have to generify that position, but we have to check if we can reuse an existing type parameter.
             if(theSubstitution.containsValue(consideredTypesMap)) {
                 result.put(pos, getKeyFor(theSubstitution, consideredTypesMap));} // -1 represent the return type (by convention)
             else {
                 PsiTypeParameter t = factory.createTypeParameterFromText(GenBuildUtils.buildTypeParameter(pos, basename, factory), null);
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
         for (PsiClass c : sisterClasses){ m.put(c, GenBuildUtils.findReferenceToSuperclass(c, superclass).getTypeParameters()[i]);}
         result.put(a[i], m);
       }
       return result;
   }

    public static class ParamSubstitution extends HashMap<Integer, PsiTypeParameter> {
    }
}
