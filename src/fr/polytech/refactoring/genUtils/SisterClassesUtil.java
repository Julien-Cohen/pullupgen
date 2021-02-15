package fr.polytech.refactoring.genUtils;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

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

/**
 * Created by julien on 02/05/16.
 */
public class SisterClassesUtil {

    /* ------ Lookup for sub-classes / sister-classes  ------ */


    public static Collection<PsiClass> findDirectSubClassesInDirectory(@NotNull PsiClass superClass) {
        final PsiDirectory superDir = getDirectoryOfClass (superClass);
        return findDirectSubClassesInDirectory(superClass, superDir);
    }

    public static Collection<PsiClass> findDirectSubClassesInDirectory(@NotNull PsiClass superClass, PsiDirectory dir) {

        final GlobalSearchScope dirScope = GlobalSearchScopes.directoryScope(dir, false);
        return ClassInheritorsSearch.search(superClass, dirScope, false).findAll();
    }

    public static Collection<PsiClass> findSisterClassesInDirectory(PsiClass subclass) {
        final PsiClass theSuperClass = subclass.getSuperClass() ;
        final PsiDirectory dir = getDirectoryOfClass(subclass);
        return findDirectSubClassesInDirectory(theSuperClass,dir);
    }

    public static PsiDirectory getDirectoryOfClass (PsiClass c){
        return ((PsiJavaFile) c.getContainingFile()).getContainingDirectory();
    }

}
