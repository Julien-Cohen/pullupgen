
/**
 * Created with IntelliJ IDEA.
 * User: cohen-j
 * Date: 20/12/12
 * Time: 15:01
 * To change this template use File | Settings | File Templates.
 */

package fr.polytech.refactoring.genUtils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.*;
import java.util.stream.Collectors;

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

public class GenSubstitutionUtils {


    /** Compute the anti-unifier for a list of methods.
     * Return the elements of the method profile (return type or parameter types) to be generified,
     * with the corresponding type variable to replace each element.
     * Also modifies the dependent-substitution that says how each class instantiates the type parameter
     * of its (direct?) superclass */
    public static ParamSubstitution antiunify(
            List<PsiMethod> lm,                    /* TODO : can lm be empty? */
            DependentSubstitution theSubstitution, /* this parameter indicates the previously existing intanciation of type variables (from extends statements), for potential reuse. */
                                                   /* This parameter can be modified (when new type parameters have to be introduced)   */
            String baseNameForTypeVariables,
            PsiElementFactory factory,             /* this parameter is used to build new type parameters. */
            Collection<String> boundNames
    ){

       assert(lm.size() != 0);

       final ParamSubstitution result = new ParamSubstitution();
        Map<Map<PsiClass, PsiType>, PsiType> substitutions = new HashMap<>();

       // first, check the return type
       final List <PsiType> returnTypes = new Vector<>();
       final Map<PsiClass, PsiType> returnTypesMap = new HashMap<>();
       for (PsiMethod m: lm){  // collect the return types
           returnTypes.add(m.getReturnType());
           returnTypesMap.put(m.getContainingClass(), m.getReturnType());
       }

        // check these types (and modify 'result')
        PsiType returnType = antiunify(returnTypesMap, substitutions, factory, baseNameForTypeVariables, boundNames, -1, theSubstitution);
        if (returnType != null) {
            result.put(-1, returnType);
        }

       // second, check the types of the method parameters
       for (int pos=0; pos<lm.get(0).getParameterList().getParametersCount(); pos++) { // for each position
           List<PsiType> consideredTypes = new Vector<>();
           Map<PsiClass, PsiType> consideredTypesMap = new HashMap<>();
             for (PsiMethod m: lm){ // collect the types at the considered position in the list of methods
               consideredTypes.add(m.getParameterList().getParameters()[pos].getType());
               consideredTypesMap.put(m.getContainingClass(), m.getParameterList().getParameters()[pos].getType());
             }
            // check these types (modify 'result')
            PsiType argumentType = antiunify(consideredTypesMap, substitutions, factory, baseNameForTypeVariables, boundNames, pos, theSubstitution);
            if (argumentType != null) {
               result.put(pos, argumentType);
            }
       }

       return result ;
    }

    private static PsiType antiunify(
            Map<PsiClass, PsiType> types,
            Map<Map<PsiClass, PsiType>, PsiType> substitutions,
            PsiElementFactory factory,
            String baseNameForTypeVariables,
            Collection<String> boundNames,
            int pos,
            DependentSubstitution megaSubs) {
        if (substitutions.containsKey(types)) {
            return substitutions.get(types);
        }
        else {
            List<PsiType> listTypes = new ArrayList<>(types.values());
            if (Comparison.allTypesEquals(listTypes)) {
                substitutions.put(types, null);
                return null;
            }
            else if (!Comparison.allObjectTypes(listTypes)) {
                throw new IncorrectOperationException("Cannot generify primitive type.");
            }
            else if (Comparison.allArrayTypes(listTypes)) {
                final String fresh =  GenBuildUtils.buildTypeParameter(pos, baseNameForTypeVariables, boundNames);
                boundNames.add(fresh);
                PsiTypeParameter typeParameter = factory.createTypeParameterFromText(fresh, null);
                megaSubs.put(typeParameter, types);
                PsiType arrayType = factory.createType(typeParameter, new PsiType[0]).createArrayType();
                substitutions.put(types, arrayType);
                return arrayType;
            }
            else if (!Comparison.allClassTypes(listTypes)) {
                final String fresh =  GenBuildUtils.buildTypeParameter(pos, baseNameForTypeVariables, boundNames);
                boundNames.add(fresh);
                PsiTypeParameter typeParameter = factory.createTypeParameterFromText(fresh, null);
                megaSubs.put(typeParameter, types);
                PsiType type = factory.createType(typeParameter, new PsiType[0]);
                substitutions.put(types, type);
                return type;
            }
            else {
                List<PsiClassType> classTypes = listTypes.stream().map(t -> (PsiClassType) t).collect(Collectors.toList());
                List<PsiClassType> rawTypes = classTypes.stream().map(PsiClassType::rawType).collect(Collectors.toList());
                if (Comparison.allTypesEquals(rawTypes)) {
                    int size = classTypes.get(0).getParameterCount();
                    PsiType[] parameters = new PsiType[size];
                    for (int i = 0; i < size; i++) {
                        Map<PsiClass, PsiType> typeParameters = new HashMap<>();
                        for (PsiClassType t : classTypes) {
                            typeParameters.put(getKeyFor(types, t), t.getParameters()[i]);
                        }
                        PsiType typeParameter = antiunify(typeParameters, substitutions, factory, baseNameForTypeVariables, boundNames, pos, megaSubs);
                        parameters[i] = typeParameter;
                    }
                    PsiClassType type = factory.createType(rawTypes.get(0).resolve(), parameters);
                    substitutions.put(types, type);
                    return type;
                }
                else {
                    final String fresh =  GenBuildUtils.buildTypeParameter(pos, baseNameForTypeVariables, boundNames);
                    boundNames.add(fresh);
                    PsiTypeParameter typeParameter = factory.createTypeParameterFromText(fresh, null);
                    megaSubs.put(typeParameter, types);
                    PsiType type = factory.createType(typeParameter, new PsiType[0]);
                    substitutions.put(types, type);
                    return type;
                }
            }
        }
    }

    static <A,B> A getKeyFor (Map <A, B> m , B v) throws Error {
      for (A s: m.keySet()){
          if (m.get(s).equals(v)) return s;
      }
      throw new Error("Internal error : key not found in map");
    }


    /** Compute the substitution existing before refactoring. */
    public static DependentSubstitution computePreviousSub(
            PsiClass superclass,
            Collection<PsiClass> sisterClasses){

        final DependentSubstitution result = new  DependentSubstitution ();

        final PsiTypeParameter[]    a      = superclass.getTypeParameters();

        final int nbParams = a.length;

        for (int i=0; i< nbParams; i++){
            Map <PsiClass, PsiType> m= new HashMap <PsiClass, PsiType>();
            for (PsiClass c : sisterClasses){
                final PsiType[] parameters = GenBuildUtils.findReferenceToSuperclass(c, superclass).getTypeParameters();
                PsiType t;
                assert (i<parameters.length) ; // because we have aligned the parameters in extensions before.
                t= parameters[i] ;
                m.put(c, t);
            }
            result.put(a[i], m);
        }
        return result;
    }




    public static List<String> boundTypeNames(PsiClass c){
       List<String> l = new ArrayList<String>();
       l.add(c.getName());
       for(PsiTypeParameter p : c.getTypeParameters()) l.add(p.getName());
       for(PsiClass i : c.getAllInnerClasses()) l.add(i.getName());

       return l;
    }
}
