/*
 * Copyright 2000-2009 JetBrains s.r.o.
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


/*
 * Extended by Julien Cohen (Ascola team, Univ. Nantes), Feb/March 2012.
 * Copyright 2012 Université de Nantes for those contributions.            
 */

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.memberPullUp.PullUpHelper;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;



public class ExtractSuperClassMultiUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.refactoring.extractSuperclassMulti.ExtractSuperClassMultiUtil");
  private ExtractSuperClassMultiUtil() {}


  // Modified (Julien)
  public static PsiClass extractSuperClassMulti(final Project project,
                                                final PsiDirectory targetDirectory,
                                                final String superclassName,
                                                final PsiClass subclass,
                                                final MemberInfo[] selectedMemberInfos,
                                                final DocCommentPolicy javaDocPolicy,
                                                final boolean useGenericUnification)
    throws IncorrectOperationException {    
      PsiClass[] a = { subclass } ;
      final Collection<PsiClass> sisterClasses = GenerifyUtils.computeSisterClasses(subclass);
      final String packageName = ((PsiJavaFile)subclass.getContainingFile()).getPackageName();

      final Collection<PsiClass> selectedSisterClasses = new Vector();

      if (!useGenericUnification){
        for (PsiClass c : sisterClasses){
         if (GenerifyUtils.hasMembers(c, selectedMemberInfos)) selectedSisterClasses.add(c);
        }
      }
      else {
        for (PsiClass c : sisterClasses){
         if (GenerifyUtils.hasCompatibleMembers(c, selectedMemberInfos)) selectedSisterClasses.add(c);
        }
      }
      
      if (selectedSisterClasses.isEmpty()) {throw new IncorrectOperationException ("Internal error: no convenient class found (in extractSuperClassMulti).");}
      //System.out.println(selectedSisterClasses);  // debug
      a = (PsiClass[]) selectedSisterClasses.toArray(a);
      
      return extractSuperClass(project, targetDirectory, superclassName, a, selectedMemberInfos, javaDocPolicy, useGenericUnification);
  }

  // Modified (Julien)
  public static PsiClass extractSuperClass(final Project project,
                                           final PsiDirectory targetDirectory,
                                           final String superclassName,
                                           final PsiClass[] subclasses,
                                           final MemberInfo[] selectedMemberInfos,
                                           final DocCommentPolicy javaDocPolicy,
                                           final boolean useGenericUnification)
    throws IncorrectOperationException {
    PsiClass superclass = JavaDirectoryService.getInstance().createClass(targetDirectory, superclassName);
    final PsiModifierList superClassModifierList = superclass.getModifierList();
    assert superClassModifierList != null;
    superClassModifierList.setModifierProperty(PsiModifier.FINAL, false);
    final PsiReferenceList subClassExtends = subclasses[0].getExtendsList(); // TODO : check that other classes do not need to be considered as well
    assert subClassExtends != null: subclasses;
    copyPsiReferenceList(subClassExtends, superclass.getExtendsList());

    // create constructors if neccesary
    /* PsiMethod[] constructors = getCalledBaseConstructors(subclasses);
    if (constructors.length > 0) {
      createConstructorsByPattern(project, superclass, constructors);
    }  */ // TODO : reactivate these lines (and find the test that justifies that)

    // clear original class' "extends" list
    for (PsiClass c: subclasses) {
      clearPsiReferenceList(c.getExtendsList());

      // make original class extend extracted superclass

      PsiJavaCodeReferenceElement ref = createExtendingReference(superclass, c, selectedMemberInfos);
      c.getExtendsList().add(ref);
    }

    // Julien
    if (!useGenericUnification) {
      PullUpHelper pullUpHelper = new PullUpHelper(subclasses[0], superclass, selectedMemberInfos, // TODO: consider other subclasses than [0]  + maybe can use pullUpGenHelper here too?
                                                 javaDocPolicy
                                    );
      pullUpHelper.moveMembersToBase();
      pullUpHelper.moveFieldInitializations();
    }
    else {
      PullUpGenHelper pullUpHelper = new PullUpGenHelper(subclasses[0], superclass, selectedMemberInfos,
                                                 javaDocPolicy
                                    );
      pullUpHelper.moveMembersToBase();                                         // TODO : make that efficient (unifiers are searched twice: one time for computing the sister classes, and one time for the pull-up)
      pullUpHelper.moveFieldInitializations();

    }



    Collection<MethodSignature> toImplement = OverrideImplementUtil.getMethodSignaturesToImplement(superclass);
    if (!toImplement.isEmpty()) {
      superClassModifierList.setModifierProperty(PsiModifier.ABSTRACT, true);
    }
    return superclass;
  }


      private static void createConstructorsByPattern(Project project, final PsiClass superclass, PsiMethod[] patternConstructors) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
    for (PsiMethod baseConstructor : patternConstructors) {
      PsiMethod constructor = (PsiMethod)superclass.add(factory.createConstructor());
      PsiParameterList paramList = constructor.getParameterList();
      PsiParameter[] baseParams = baseConstructor.getParameterList().getParameters();
      @NonNls StringBuilder superCallText = new StringBuilder();
      superCallText.append("super(");
      final PsiClass baseClass = baseConstructor.getContainingClass();
      LOG.assertTrue(baseClass != null);
      final PsiSubstitutor classSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, superclass, PsiSubstitutor.EMPTY);
      for (int i = 0; i < baseParams.length; i++) {
        final PsiParameter baseParam = baseParams[i];
        final PsiParameter newParam = (PsiParameter)paramList.add(factory.createParameter(baseParam.getName(), classSubstitutor.substitute(baseParam.getType())));
        if (i > 0) {
          superCallText.append(",");
        }
        superCallText.append(newParam.getName());
      }
      superCallText.append(");");
      PsiStatement statement = factory.createStatementFromText(superCallText.toString(), null);
      statement = (PsiStatement)styleManager.reformat(statement);
      final PsiCodeBlock body = constructor.getBody();
      assert body != null;
      body.add(statement);
      constructor.getThrowsList().replace(baseConstructor.getThrowsList());
    }
  }

  private static PsiMethod[] getCalledBaseConstructors(final PsiClass subclass) {
    Set<PsiMethod> baseConstructors = new HashSet<PsiMethod>();
    PsiMethod[] constructors = subclass.getConstructors();
    for (PsiMethod constructor : constructors) {
      PsiCodeBlock body = constructor.getBody();
      if (body == null) continue;
      PsiStatement[] statements = body.getStatements();
      if (statements.length > 0) {
        PsiStatement first = statements[0];
        if (first instanceof PsiExpressionStatement) {
          PsiExpression expression = ((PsiExpressionStatement)first).getExpression();
          if (expression instanceof PsiMethodCallExpression) {
            PsiReferenceExpression calledMethod = ((PsiMethodCallExpression)expression).getMethodExpression();
            @NonNls String text = calledMethod.getText();
            if ("super".equals(text)) {
              PsiMethod baseConstructor = (PsiMethod)calledMethod.resolve();
              if (baseConstructor != null) {
                baseConstructors.add(baseConstructor);
              }
            }
          }
        }
      }
    }
    return baseConstructors.toArray(new PsiMethod[baseConstructors.size()]);
  }

  private static void clearPsiReferenceList(PsiReferenceList refList) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement[] refs = refList.getReferenceElements();
    for (PsiJavaCodeReferenceElement ref : refs) {
      ref.delete();
    }
  }

  private static void copyPsiReferenceList(PsiReferenceList sourceList, PsiReferenceList destinationList) throws IncorrectOperationException {
    clearPsiReferenceList(destinationList);
    PsiJavaCodeReferenceElement[] refs = sourceList.getReferenceElements();
    for (PsiJavaCodeReferenceElement ref : refs) {
      destinationList.add(ref);
    }
  }

  public static PsiJavaCodeReferenceElement createExtendingReference(final PsiClass superClass,
                                                                      final PsiClass derivedClass,
                                                                      final MemberInfo[] selectedMembers) throws IncorrectOperationException {
    final PsiManager manager = derivedClass.getManager();
    Set<PsiElement> movedElements = new com.intellij.util.containers.HashSet<PsiElement>();
    for (final MemberInfo info : selectedMembers) {
      movedElements.add(info.getMember());
    }
    final PsiTypeParameterList typeParameterList = RefactoringUtil.createTypeParameterListWithUsedTypeParameters(null,
                                                                                                                 new Condition<PsiTypeParameter>() {
                                                                                                                   @Override
                                                                                                                   public boolean value(
                                                                                                                     PsiTypeParameter parameter) {
                                                                                                                     return
                                                                                                                       findTypeParameterInDerived(
                                                                                                                         derivedClass,
                                                                                                                         parameter
                                                                                                                           .getName()) !=
                                                                                                                       null;
                                                                                                                   }
                                                                                                                 }, PsiUtilBase
      .toPsiElementArray(movedElements));
    final PsiTypeParameterList originalTypeParameterList = superClass.getTypeParameterList();
    assert originalTypeParameterList != null;
    final PsiTypeParameterList newList = typeParameterList != null ? (PsiTypeParameterList)originalTypeParameterList.replace(typeParameterList) : originalTypeParameterList;
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    Map<PsiTypeParameter, PsiType> substitutionMap = new HashMap<PsiTypeParameter, PsiType>();
    for (final PsiTypeParameter parameter : newList.getTypeParameters()) {
      final PsiTypeParameter parameterInDerived = findTypeParameterInDerived(derivedClass, parameter.getName());
      if (parameterInDerived != null) {
        substitutionMap.put(parameter, factory.createType(parameterInDerived));
      }
    }

    final PsiClassType type = factory.createType(superClass, factory.createSubstitutor(substitutionMap));
    return factory.createReferenceElementByType(type);
  }

  @Nullable
  public static PsiTypeParameter findTypeParameterInDerived(final PsiClass aClass, final String name) {
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(aClass)) {
      if (name.equals(typeParameter.getName())) return typeParameter;
    }

    return null;
  }

  public static void checkSuperAccessible(PsiDirectory targetDirectory, MultiMap<PsiElement, String> conflicts, final PsiClass subclass) {
    final VirtualFile virtualFile = subclass.getContainingFile().getVirtualFile();
    if (virtualFile != null) {
      final boolean inTestSourceContent = ProjectRootManager.getInstance(subclass.getProject()).getFileIndex().isInTestSourceContent(virtualFile);
      final Module module = ModuleUtil.findModuleForFile(virtualFile, subclass.getProject());
      if (targetDirectory != null &&
          module != null &&
          !GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, inTestSourceContent).contains(targetDirectory.getVirtualFile())) {
        conflicts.putValue(subclass, "Superclass won't be accessible in subclass");
      }
    }
  }

  public static boolean showConflicts(DialogWrapper dialog, MultiMap<PsiElement, String> conflicts, final Project project) {
    if (!conflicts.isEmpty()) {
      ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
      conflictsDialog.show();
      final boolean ok = conflictsDialog.isOK();
      if (!ok && conflictsDialog.isShowConflicts()) dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
      return ok;
    }
    return true;
  }
}
