package com.intellij.refactoring.genUtils;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Created by julien on 02/05/16.
 */
public class SisterClassesUtil {

    /* ------ Lookup for sub-classes / sister-classes  ------ */
    /* We make the assumption that the superclass and the sisterclass are all in the same directory */


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
