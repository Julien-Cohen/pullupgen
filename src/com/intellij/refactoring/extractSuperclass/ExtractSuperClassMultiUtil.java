/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.refactoring.extractSuperclass;

import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
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
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.genUtils.SisterClassesUtil;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.memberPullUp.PullUpGenProcessor; // (J)
import com.intellij.refactoring.genUtils.GenAnalysisUtils;       // (J)
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Arrays; // J
import java.util.Vector; // J

/**
 *
 */
public class ExtractSuperClassMultiUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.refactoring.extractSuperclassMulti.ExtractSuperClassMultiUtil");
  public static final String REFACTORING_EXTRACT_SUPER_ID = "refactoring.extractSuper";

  private ExtractSuperClassMultiUtil() {}


  public static PsiClass extractSuperClassMulti(final Project project,
                                                final PsiDirectory targetDirectory,
                                                final String superclassName,
                                                final PsiClass subclass,
                                                final MemberInfo[] selectedMemberInfos,
                                                final DocCommentPolicy javaDocPolicy,
                                                final boolean useGenericUnification)
    throws IncorrectOperationException {

    // Modified (Julien)

    project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
      .refactoringStarted(REFACTORING_EXTRACT_SUPER_ID, createBeforeData(subclass, selectedMemberInfos));

    PsiClass myfreshsuperclass = JavaDirectoryService.getInstance().createClass(targetDirectory, superclassName);

    try {

      final Collection<PsiClass> sisterClasses = SisterClassesUtil.findSisterClassesInDirectory(subclass);
      // Rem : In extract super-class, we are only interested in classes at the same level.
      // For instance, if we have A->Object, B->Object, C->B->Object, we are not interested in C
      // (unlike in pull-up abstract).
      System.out.println(sisterClasses);
      final String packageName = ((PsiJavaFile) subclass.getContainingFile()).getPackageName();

      final Collection<PsiClass> selectedSisterClasses = filterSisterClasses(Arrays.asList(selectedMemberInfos), useGenericUnification, sisterClasses);

      if (selectedSisterClasses.isEmpty()) {
        throw new IncorrectOperationException("Internal error: no convenient class found (in extractSuperClassMulti).");
      }

      return extractSuperClassMulti(selectedSisterClasses, selectedMemberInfos, javaDocPolicy, useGenericUnification, myfreshsuperclass);
    }
    finally {
      project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(REFACTORING_EXTRACT_SUPER_ID, createAfterData(myfreshsuperclass));
    }
  }



  // Modified (Julien)
  public static PsiClass extractSuperClassMulti(final Collection<PsiClass> subclasses,
                                                final MemberInfo[] selectedMemberInfos,
                                                final DocCommentPolicy javaDocPolicy,
                                                final boolean useGenericUnification,
                                                PsiClass thefreshsuperclass)
    throws IncorrectOperationException {

    assert (!subclasses.isEmpty());
    PsiClass aSubClass = subclasses.iterator().next();

    // 1 : Configure the new superclass class
    final PsiModifierList superClassModifierList = thefreshsuperclass.getModifierList();
    assert superClassModifierList != null;
    superClassModifierList.setModifierProperty(PsiModifier.FINAL, false);
    superClassModifierList.setModifierProperty(PsiModifier.ABSTRACT, true);


    // 2 : make the correct 'extends' for the new class

    final PsiReferenceList subClassExtends = aSubClass.getExtendsList(); // TODO : check that other classes do not need to be considered as well
    assert subClassExtends != null: subclasses;
    copyPsiReferenceList(subClassExtends, thefreshsuperclass.getExtendsList());


    // 3 : create constructors if neccesary
    /* PsiMethod[] constructors = getCalledBaseConstructors(subclasses);
    if (constructors.length > 0) {
      createConstructorsByPattern(project, superclass, constructors);
    }  */ // TODO : reactivate these lines (and find the test that justifies that)


    // 4 : create new 'extends' links
    for (PsiClass c: subclasses) {
      clearPsiReferenceList(c.getExtendsList());
      PsiJavaCodeReferenceElement ref = createExtendingReference(thefreshsuperclass, c, selectedMemberInfos);
      c.getExtendsList().add(ref);
    }


    // 5 : pull-up selected members (and 'implements')

    /*if (!useGenericUnification) {

      PullUpHelper pullUpHelper = new PullUpHelper(aSubClass, myfreshsuperclass, selectedMemberInfos, // TODO: consider other subclasses than [0]  + maybe can use pullUpGenHelper here too?
                                                 javaDocPolicy
                                    );
      pullUpHelper.moveMembersToBase();
      pullUpHelper.moveFieldInitializations();
    }
    else {  */
      PullUpGenProcessor pullUpHelper = new PullUpGenProcessor(aSubClass, subclasses, thefreshsuperclass, selectedMemberInfos, javaDocPolicy);
        try {
            pullUpHelper.moveMembersToBase();                // TODO : make that efficient (unifiers are searched twice: one time for computing the sister classes, and one time for the pull-up)
        } catch (GenAnalysisUtils.AmbiguousOverloading ambiguousOverloading) {
            throw new IncorrectOperationException(ambiguousOverloading.toString()) ;
        } catch (GenAnalysisUtils.MemberNotImplemented notImplemented) {
            throw new IncorrectOperationException(notImplemented.toString()) ;
        }
        pullUpHelper.moveFieldInitializations();

   /* } */


    // 6 : make the superclass abstract if needed
    Collection<MethodSignature> toImplement = OverrideImplementExploreUtil.getMethodSignaturesToImplement(thefreshsuperclass);
    if (!toImplement.isEmpty()) {
      superClassModifierList.setModifierProperty(PsiModifier.ABSTRACT, true);
    }

    // finished
    return thefreshsuperclass;
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
    final Condition<PsiTypeParameter> filter = new Condition<PsiTypeParameter>() {
      @Override
      public boolean value(PsiTypeParameter parameter) {
        return findTypeParameterInDerived(derivedClass, parameter.getName()) == parameter;
      }
    };
    final PsiTypeParameterList typeParameterList = RefactoringUtil.createTypeParameterListWithUsedTypeParameters(null, filter, PsiUtilCore.toPsiElementArray(movedElements));
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
      fireConflictsEvent(conflicts, project);
      ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
      conflictsDialog.show();
      final boolean ok = conflictsDialog.isOK();
      if (!ok && conflictsDialog.isShowConflicts()) dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
      return ok;
    }
    return true;
  }

  private static void fireConflictsEvent(MultiMap<PsiElement, String> conflicts, Project project) {
    final RefactoringEventData conflictUsages = new RefactoringEventData();
    conflictUsages.putUserData(RefactoringEventData.CONFLICTS_KEY, conflicts.values());
    project.getMessageBus()
      .syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
      .conflictsDetected(REFACTORING_EXTRACT_SUPER_ID, conflictUsages);
  }

  public static RefactoringEventData createBeforeData(final PsiClass subclassClass, final MemberInfo[] members) {
    RefactoringEventData data = new RefactoringEventData();
    data.addElement(subclassClass);
    data.addMembers(members, new Function<MemberInfo, PsiElement>() {
      @Override
      public PsiElement fun(MemberInfo info) {
        return info.getMember();
      }
    });
    return data;
  }

  public static RefactoringEventData createAfterData(final PsiClass subClass) {
    RefactoringEventData data = new RefactoringEventData();
    data.addElement(subClass);
    return data;
  }

  // Filter sister classes which have the selected members
  public static Collection<PsiClass> filterSisterClasses(Iterable<MemberInfo> selectedMemberInfos, boolean useGenericUnification, Collection<PsiClass> sisterClasses) {
    Collection<PsiClass> selectedClasses = new Vector();

    if (!useGenericUnification){
      for (PsiClass c : sisterClasses){
        if (GenAnalysisUtils.hasMembers(c, selectedMemberInfos)) selectedClasses.add(c);
      }
    }

    else {
      for (PsiClass c : sisterClasses){
        try {
          if (GenAnalysisUtils.hasCompatibleMembers(c, selectedMemberInfos))
            selectedClasses.add(c);
        } catch (GenAnalysisUtils.AmbiguousOverloading ambiguousOverloading) {
          throw new IncorrectOperationException(ambiguousOverloading.toString()) ;
        }
      }
    }

    return selectedClasses;
  }

}
