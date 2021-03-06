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

/* Up-to-date w.r.t. commit on 18 Sep 2012. */
/* Up-to-date w.r.t. commit on 11 Oct 2012. */
/* Up-to-date w.r.t. commit on 7  Nov 2012. */
/* Up-to-date w.r.t. commit on 17 Dec 2012. */
/* Up-to-date w.r.t. commit on 8  May 2013. */
/* Up-to-date w.r.t. commit on 3  Jun 2013. */
/* Up-to-date w.r.t. commit on 23 Oct 2013. */
/* Up-to-date w.r.t. commit on 16 Jul 2014. */

package fr.polytech.refactoring.memberPullUp;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashMap;
import fr.polytech.refactoring.genUtils.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;


/**
 * Created by Max Medvedev on 10/3/13
 */
public class JavaPullUpGenHelper implements PullUpGenHelper<MemberInfo> {
  private static final Logger LOG = Logger.getInstance(JavaPullUpGenHelper.class);

  private static final Key<Boolean> PRESERVE_QUALIFIER = Key.create("PRESERVE_QUALIFIER");


  private final PsiClass mySourceClass;
  private final PsiClass myTargetSuperClass;
  private final boolean myIsTargetInterface;
  private final DocCommentPolicy myJavaDocPolicy;
  private Set<PsiMember> myMembersAfterMove = null; // Set of members that have been already moved (name seems to be badly chosen, but comes from intelliJ).
  private Set<PsiMember> myMembersToMove;
  private Project myProject;

  private final QualifiedThisSuperAdjuster myThisSuperAdjuster;
  private final ExplicitSuperDeleter myExplicitSuperDeleter;

  protected Collection<PsiClass> sisterClasses = null;   // julien

  public JavaPullUpGenHelper(PullUpGenData data) {
    myProject = data.getProject();
    myMembersToMove = data.getMembersToMove();
    myMembersAfterMove = data.getMovedMembers();
    myTargetSuperClass = data.getTargetClass();
    mySourceClass = data.getSourceClass();
    myJavaDocPolicy = data.getDocCommentPolicy();
    myIsTargetInterface = myTargetSuperClass.isInterface();

    myThisSuperAdjuster = new QualifiedThisSuperAdjuster();
    myExplicitSuperDeleter = new ExplicitSuperDeleter();
    sisterClasses = data.getSisterClasses();
  }

  @Override
  public void encodeContextInfo(MemberInfo info) {
    ChangeContextUtil.encodeContextInfo(info.getMember(), true);
  }

  @Override
  public void move(MemberInfo info, PsiSubstitutor substitutor) {
    if (info.getMember() instanceof PsiMethod) {
      doMoveMethod(substitutor, info);
    }
    else if (info.getMember() instanceof PsiField) {
      doMoveField(substitutor, info);
    }
    else if (info.getMember() instanceof PsiClass) {
      doMoveClass(substitutor, info);
    }
  }

  @Override
  public void postProcessMember(PsiMember member) {
    member.accept(myExplicitSuperDeleter);
    member.accept(myThisSuperAdjuster);

    ChangeContextUtil.decodeContextInfo(member, null, null);

    member.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiExpression qualifierExpression = expression.getQualifierExpression();
        if (qualifierExpression != null) {
          final Boolean preserveQualifier = qualifierExpression.getCopyableUserData(PRESERVE_QUALIFIER);
          if (preserveQualifier != null && !preserveQualifier) {
            qualifierExpression.delete();
            return;
          }
        }
        super.visitReferenceExpression(expression);
      }
    });

  }

  @Override
  public void setCorrectVisibility(MemberInfo info) {
    PsiModifierListOwner modifierListOwner = info.getMember();

    // Julien : I change this because when a method is package, it has to be intensionally changed into public (interface members are public)
    if (myIsTargetInterface && !modifierListOwner.hasModifierProperty("public")){ // TODO : change "public" into *.PUBLIC
      throw new IncorrectOperationException("Please change the visibility of the method to PUBLIC before moving it into an interface.");   // Julien
    }
    else if (modifierListOwner.hasModifierProperty(PsiModifier.PRIVATE)) {
      if (info.isToAbstract() || willBeUsedInSubclass(modifierListOwner, myTargetSuperClass, mySourceClass)) {
        PsiUtil.setModifierProperty(modifierListOwner, PsiModifier.PROTECTED, true);
      }
      if (modifierListOwner instanceof PsiClass) {
        modifierListOwner.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitMethod(PsiMethod method) {
            check(method);
          }

          @Override
          public void visitField(PsiField field) {
            check(field);
          }

          @Override
          public void visitClass(PsiClass aClass) {
            check(aClass);
            super.visitClass(aClass);
          }

          private void check(PsiMember member) {
            if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
              if (willBeUsedInSubclass(member, myTargetSuperClass, mySourceClass)) {
                PsiUtil.setModifierProperty(member, PsiModifier.PROTECTED, true);
              }
            }
          }
        });
      }
    }
  }

  private void doMoveClass(PsiSubstitutor substitutor, MemberInfo info) {
    // (rem Julien) case where the member to pull-up is a class/interface, internal or declared as superclass/interface
    if (Comparison.memberClassComesFromImplements(info)){
      throw new IncorrectOperationException("pull-up classes not supported.");
    }
    else {
      // (rem Julien) the member refers to a class, but not from 'implements' : either from 'extends', either from inner-class. I guess 'extends' are out of scope of the pull-up operation.
      assert(GenAnalysisUtils.memberClassIsInnerClass(info));
      PsiClass aClass = (PsiClass)info.getMember();
      throw new IncorrectOperationException("inner classes not handled yet : " + aClass);

      // TODO : handle inner classes
    }
  }

  private void doMoveField(PsiSubstitutor substitutor, MemberInfo info) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    PsiField field = (PsiField)info.getMember();
    field.normalizeDeclaration();
    RefactoringUtil.replaceMovedMemberTypeParameters(field, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
    fixReferencesToStatic(field);
    if (myIsTargetInterface) {
      PsiUtil.setModifierProperty(field, PsiModifier.PUBLIC, true);
    }
    final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(convertFieldToLanguage(field, myTargetSuperClass.getLanguage()));
    myMembersAfterMove.add(movedElement);
    field.delete();
  }

  private  void doMoveMethod(PsiSubstitutor substitutor, MemberInfo info) {
    List<String> boundNames =  GenSubstitutionUtils.boundTypeNames(myTargetSuperClass);
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    PsiMethod method = (PsiMethod)info.getMember();
    PsiMethod sibling = method;
    PsiMethod anchor = null;
    while (sibling != null) {
      sibling = PsiTreeUtil.getNextSiblingOfType(sibling, PsiMethod.class);
      if (sibling != null) {
        anchor = MethodSignatureUtil
          .findMethodInSuperClassBySignatureInDerived(method.getContainingClass(), myTargetSuperClass,
                                                      sibling.getSignature(PsiSubstitutor.EMPTY), false);
        if (anchor != null) {
          break;
        }
      }
    }
    PsiMethod methodCopy = (PsiMethod)method.copy();
    Language language = myTargetSuperClass.getLanguage();
    final PsiMethod superClassMethod = myTargetSuperClass.findMethodBySignature(methodCopy, false);
    if (superClassMethod != null && superClassMethod.findDeepestSuperMethods().length == 0 ||
        method.findSuperMethods(myTargetSuperClass).length == 0) {
      deleteOverrideAnnotationIfFound(methodCopy);
    }
    boolean isOriginalMethodAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT) || method.hasModifierProperty(PsiModifier.DEFAULT);


    // (rem Julien) case isToAbstract  (case of interest for pull-up-gen)

    if (myIsTargetInterface || info.isToAbstract()) {
      assert(this.myTargetSuperClass.hasModifierProperty(PsiModifier.ABSTRACT));  // and the interface case?
      ChangeContextUtil.clearContextInfo(method);

      // begin Julien

      // 1) collect the sister methods (methods with the same type skeleton in sister classes).
      final List<PsiMethod> sisterMethods =
              // TODO : make that efficient (classes are traversed twice)
              GenAnalysisUtils.findCompatibleMethods(method, sisterClasses);



      // 2)find which types have to be parameterized

      final DependentSubstitution initialParameters = GenSubstitutionUtils.computePreviousSub(this.myTargetSuperClass, sisterClasses); // used later to know the type parameters of the initial clases TODO: improve that
      final DependentSubstitution theMegaSubst      = GenSubstitutionUtils.computePreviousSub(this.myTargetSuperClass, sisterClasses);   // TODO: clone initialParameters above?


      final ParamSubstitution sub = GenSubstitutionUtils.antiunify(sisterMethods, theMegaSubst, method.getName(), elementFactory, boundNames); // TODO : fix that (empty substitution)


      // 3) build the abstract method
      RefactoringUtil.makeMethodAbstract(myTargetSuperClass, methodCopy);


      // 4) generify the abstract method
      GenBuildUtils.generifyAbstractMethod(methodCopy, sub);

      // 5) ??
      RefactoringUtil.replaceMovedMemberTypeParameters(methodCopy, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);

      // 6) process javadoc
      myJavaDocPolicy.processCopiedJavaDoc(methodCopy.getDocComment(), method.getDocComment(), isOriginalMethodAbstract);


      // 7) Add type parameters to the superclass
      DependentSubstitution newParameters = DependentSubstitution.difference(theMegaSubst, initialParameters);
      for (PsiTypeParameter t: newParameters.keySet()){
        myTargetSuperClass.getTypeParameterList().add(t);
      }


      // 8) Add the new abstract method in the superclass (and get the new resulting method).
      final PsiMember movedElement;
      if (superClassMethod != null && superClassMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        movedElement = (PsiMember)superClassMethod.replace(convertMethodToLanguage(methodCopy, language));
      }
      else {
        movedElement =
          anchor != null ? (PsiMember)myTargetSuperClass.addBefore(methodCopy, anchor) : (PsiMember)myTargetSuperClass.add(methodCopy);
      }

      // 9) Add the parameters in sisterclasses extends statements
      GenBuildUtils.updateExtendsStatementsInSisterClasses(newParameters, myTargetSuperClass, elementFactory);


      // end Julien

      // 10) Manage override annotations
      // (rem Julien) We are still in the case where the superclass is an interface or the method is pulled up to an abstract method.
      CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(method.getProject());
      if (styleSettings.INSERT_OVERRIDE_ANNOTATION) {
        // (rem Julien) @override appears in 1.5 but applies to methods coming from interfaces only since 1.6
        if (PsiUtil.isLanguageLevel5OrHigher(mySourceClass) && !myIsTargetInterface || PsiUtil.isLanguageLevel6OrHigher(mySourceClass)) {
          for (PsiMethod m : sisterMethods){ //Julien : added annotation for all sister methods
            new AddAnnotationFix(Override.class.getName(), m).invoke(method.getProject(), null, mySourceClass.getContainingFile());
          }
        }
      }
      if (!PsiUtil.isLanguageLevel6OrHigher(mySourceClass) && myIsTargetInterface) {
        // (rem Julien) the pulled-up method gets into an interface so that @override annotations must be deleted (Java 1.5 only)
        if (isOriginalMethodAbstract) {
          for (PsiMethod oMethod : OverridingMethodsSearch.search(method)) {
            deleteOverrideAnnotationIfFound(oMethod);
          }
        }
        deleteOverrideAnnotationIfFound(method);
      }
      // (rem Julien) The processing of the current element to pull-up is finished (other elements to pull-up can be processed).
      myMembersAfterMove.add(movedElement);
      if (isOriginalMethodAbstract) {
        method.delete();
        // FIXME : do the same in sister classes ?
      }
    }
    else {
      // (rem Julien) Here, the target superclass is neither abstract nor an interface
      if (isOriginalMethodAbstract) {
        PsiUtil.setModifierProperty(myTargetSuperClass, PsiModifier.ABSTRACT, true);
      }
      RefactoringUtil.replaceMovedMemberTypeParameters(methodCopy, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
      fixReferencesToStatic(methodCopy);

      if (superClassMethod != null && superClassMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        superClassMethod.replace(convertMethodToLanguage(methodCopy, language));
      }
      else {
        final PsiMember movedElement =
          anchor != null ? (PsiMember)myTargetSuperClass.addBefore(convertMethodToLanguage(methodCopy,
                                                                                           language), anchor) : (PsiMember)myTargetSuperClass.add(
            convertMethodToLanguage(
              methodCopy, language));
        myMembersAfterMove.add(movedElement);
      }
      method.delete();
    }
  }

  private static PsiMethod convertMethodToLanguage(PsiMethod method, Language language) {
    if (method.getLanguage().equals(language)) {
      return method;
    }
    return JVMElementFactories.getFactory(language, method.getProject()).createMethodFromText(method.getText(), null);
  }

  private static PsiField convertFieldToLanguage(PsiField field, Language language) {
    if (field.getLanguage().equals(language)) {
      return field;
    }
    return JVMElementFactories.getFactory(language, field.getProject()).createField(field.getName(), field.getType());
  }

  private static PsiClass convertClassToLanguage(PsiClass clazz, Language language) {
    //if (clazz.getLanguage().equals(language)) {
    //  return clazz;
    //}
    //PsiClass newClass = JVMElementFactories.getFactory(language, clazz.getProject()).createClass(clazz.getName());
    return clazz;
  }

  @Deprecated
  void initSisterClasses(MemberInfo[] membersToMove)
            throws MemberNotImplemented, AmbiguousOverloading {
    sisterClasses = computeSisterClasses(membersToMove);
  }

  public Collection<PsiClass> getSisterClasses(MemberInfo[] membersToMove)
            throws MemberNotImplemented, AmbiguousOverloading {
    if (sisterClasses == null) initSisterClasses(membersToMove);
    return sisterClasses;
  }

  @Deprecated
  Collection<PsiClass> computeSisterClasses(MemberInfo[] membersToMove)
            throws MemberNotImplemented, AmbiguousOverloading {
        if (membersToMove.length == 0) return new HashSet(); // Empty

        Collection <PsiClass> baseSet = GenAnalysisUtils.findSubClassesWithCompatibleMember(membersToMove[0], this.myTargetSuperClass);

        Collection <PsiClass> tmpSet;
        for(MemberInfo m : membersToMove){
            tmpSet = GenAnalysisUtils.findSubClassesWithCompatibleMember(m, this.myTargetSuperClass);
            if (!baseSet.equals(tmpSet))
                throw new IncorrectOperationException("These members cannot be pulled-up from the same set of sister classes, please pull them up with two separate refactoring operations."); // FIXME : should be less strict.
            // question: que faire si les listes sont différentes pour différentes membres?
            // Peut-on prendre l'intersection? (réfléchir)
        }
        return baseSet;
  }


  private static void deleteOverrideAnnotationIfFound(PsiMethod oMethod) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(oMethod, Override.class.getName());
    if (annotation != null) {
      annotation.delete();
    }
  }

  @Override
  public void moveFieldInitializations(LinkedHashSet<PsiField> movedFields) {
    PsiMethod[] constructors = myTargetSuperClass.getConstructors();

    if (constructors.length == 0) {
      constructors = new PsiMethod[]{null};
    }

    HashMap<PsiMethod,HashSet<PsiMethod>> constructorsToSubConstructors = buildConstructorsToSubConstructorsMap(constructors);
    for (PsiMethod constructor : constructors) {
      HashSet<PsiMethod> subConstructors = constructorsToSubConstructors.get(constructor);
      tryToMoveInitializers(constructor, subConstructors, movedFields);
    }
  }

  @Override
  public void updateUsage(PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      PsiExpression qualifierExpression = ((PsiReferenceExpression)element).getQualifierExpression();
      if (qualifierExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifierExpression).resolve() == mySourceClass) {
        ((PsiReferenceExpression)qualifierExpression).bindToElement(myTargetSuperClass);
      }
    }
  }

  private static class Initializer {
    public final PsiStatement initializer;
    public final Set<PsiField> movedFieldsUsed;
    public final Set<PsiParameter> usedParameters;
    public final List<PsiElement> statementsToRemove;

    private Initializer(PsiStatement initializer, Set<PsiField> movedFieldsUsed, Set<PsiParameter> usedParameters, List<PsiElement> statementsToRemove) {
      this.initializer = initializer;
      this.movedFieldsUsed = movedFieldsUsed;
      this.statementsToRemove = statementsToRemove;
      this.usedParameters = usedParameters;
    }
  }

  private void tryToMoveInitializers(PsiMethod constructor, HashSet<PsiMethod> subConstructors, LinkedHashSet<PsiField> movedFields) throws IncorrectOperationException {
    final LinkedHashMap<PsiField, Initializer> fieldsToInitializers = new LinkedHashMap<PsiField, Initializer>();
    boolean anyFound = false;

    for (PsiField field : movedFields) {
      PsiStatement commonInitializer = null;
      final ArrayList<PsiElement> fieldInitializersToRemove = new ArrayList<PsiElement>();
      for (PsiMethod subConstructor : subConstructors) {
        commonInitializer = hasCommonInitializer(commonInitializer, subConstructor, field, fieldInitializersToRemove);
        if (commonInitializer == null) break;
      }
      if (commonInitializer != null) {
        final ParametersAndMovedFieldsUsedCollector visitor = new ParametersAndMovedFieldsUsedCollector(movedFields);
        commonInitializer.accept(visitor);
        fieldsToInitializers.put(field, new Initializer(commonInitializer,
                                                        visitor.getUsedFields(), visitor.getUsedParameters(), fieldInitializersToRemove));
        anyFound = true;
      }
    }

    if (!anyFound) return;



    {
      final Set<PsiField> initializedFields = fieldsToInitializers.keySet();
      Set<PsiField> unmovable = RefactoringUtil.transitiveClosure(
              new RefactoringUtil.Graph<PsiField>() {
                public Set<PsiField> getVertices() {
                  return initializedFields;
                }

                public Set<PsiField> getTargets(PsiField source) {
                  return fieldsToInitializers.get(source).movedFieldsUsed;
                }
              },
              new Condition<PsiField>() {
                public boolean value(PsiField object) {
                  return !initializedFields.contains(object);
                }
              }
      );

      for (PsiField psiField : unmovable) {
        fieldsToInitializers.remove(psiField);
      }
    }

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);

    if (constructor == null) {
      constructor = (PsiMethod) myTargetSuperClass.add(factory.createConstructor());
      final String visibilityModifier = VisibilityUtil.getVisibilityModifier(myTargetSuperClass.getModifierList());
      PsiUtil.setModifierProperty(constructor, visibilityModifier, true);
    }


    ArrayList<PsiField> initializedFields = new ArrayList<PsiField>(fieldsToInitializers.keySet());

    Collections.sort(initializedFields, new Comparator<PsiField>() {
      public int compare(PsiField field1, PsiField field2) {
        Initializer i1 = fieldsToInitializers.get(field1);
        Initializer i2 = fieldsToInitializers.get(field2);
        if(i1.movedFieldsUsed.contains(field2)) return 1;
        if(i2.movedFieldsUsed.contains(field1)) return -1;
        return 0;
      }
    });

    for (final PsiField initializedField : initializedFields) {
      Initializer initializer = fieldsToInitializers.get(initializedField);

      //correct constructor parameters and subConstructors super calls
      final PsiParameterList parameterList = constructor.getParameterList();
      for (final PsiParameter parameter : initializer.usedParameters) {
        parameterList.add(parameter);
      }

      for (final PsiMethod subConstructor : subConstructors) {
        modifySuperCall(subConstructor, initializer.usedParameters);
      }

      PsiStatement assignmentStatement = (PsiStatement)constructor.getBody().add(initializer.initializer);

      PsiManager manager = PsiManager.getInstance(myProject);
      ChangeContextUtil.decodeContextInfo(assignmentStatement, myTargetSuperClass, RefactoringChangeUtil.createThisExpression(manager, null));
      for (PsiElement psiElement : initializer.statementsToRemove) {
        psiElement.delete();
      }
    }
  }

  private static void modifySuperCall(final PsiMethod subConstructor, final Set<PsiParameter> parametersToPassToSuper) {
    final PsiCodeBlock body = subConstructor.getBody();
    if (body != null) {
      PsiMethodCallExpression superCall = null;
      final PsiStatement[] statements = body.getStatements();
      if (statements.length > 0) {
        if (statements[0] instanceof PsiExpressionStatement) {
          final PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
          if (expression instanceof PsiMethodCallExpression) {
            final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
            if ("super".equals(methodCall.getMethodExpression().getText())) {
              superCall = methodCall;
            }
          }
        }
      }

      final PsiElementFactory factory = JavaPsiFacade.getInstance(subConstructor.getProject()).getElementFactory();
      try {
        if (superCall == null) {
            PsiExpressionStatement statement =
              (PsiExpressionStatement)factory.createStatementFromText("super();", null);
            statement = (PsiExpressionStatement)body.addAfter(statement, null);
            superCall = (PsiMethodCallExpression)statement.getExpression();
        }

        final PsiExpressionList argList = superCall.getArgumentList();
        for (final PsiParameter parameter : parametersToPassToSuper) {
          argList.add(factory.createExpressionFromText(parameter.getName(), null));
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  @Nullable
  private PsiStatement hasCommonInitializer(PsiStatement commonInitializer, PsiMethod subConstructor, PsiField field, ArrayList<PsiElement> statementsToRemove) {
    final PsiCodeBlock body = subConstructor.getBody();
    if (body == null) return null;
    final PsiStatement[] statements = body.getStatements();

    // Algorithm: there should be only one write usage of field in a subConstructor,
    // and in that usage field must be a target of top-level assignment, and RHS of assignment
    // should be the same as commonInitializer if latter is non-null.
    //
    // There should be no usages before that initializer, and there should be
    // no write usages afterwards.
    PsiStatement commonInitializerCandidate = null;
    for (PsiStatement statement : statements) {
      final HashSet<PsiStatement> collectedStatements = new HashSet<PsiStatement>();
      collectPsiStatements(statement, collectedStatements);
      boolean doLookup = true;
      for (PsiStatement collectedStatement : collectedStatements) {
        if (collectedStatement instanceof PsiExpressionStatement) {
          final PsiExpression expression = ((PsiExpressionStatement)collectedStatement).getExpression();
          if (expression instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
            final PsiExpression lExpression = assignmentExpression.getLExpression();
            if (lExpression instanceof PsiReferenceExpression) {
              final PsiReferenceExpression lRef = (PsiReferenceExpression)lExpression;
              if (lRef.getQualifierExpression() == null || lRef.getQualifierExpression() instanceof PsiThisExpression) {
                final PsiElement resolved = lRef.resolve();
                if (resolved == field) {
                  doLookup = false;
                  if (commonInitializerCandidate == null) {
                    final PsiExpression initializer = assignmentExpression.getRExpression();
                    if (initializer == null) return null;
                    if (commonInitializer == null) {
                      final IsMovableInitializerVisitor visitor = new IsMovableInitializerVisitor();
                      statement.accept(visitor);
                      if (visitor.isMovable()) {
                        ChangeContextUtil.encodeContextInfo(statement, true);
                        PsiStatement statementCopy = (PsiStatement)statement.copy();
                        ChangeContextUtil.clearContextInfo(statement);
                        statementsToRemove.add(statement);
                        commonInitializerCandidate = statementCopy;
                      }
                      else {
                        return null;
                      }
                    }
                    else {
                      if (PsiEquivalenceUtil.areElementsEquivalent(commonInitializer, statement)) {
                        statementsToRemove.add(statement);
                        commonInitializerCandidate = commonInitializer;
                      }
                      else {
                        return null;
                      }
                    }
                  }
                  else if (!PsiEquivalenceUtil.areElementsEquivalent(commonInitializerCandidate, statement)){
                    return null;
                  }
                }
              }
            }
          }
        }
      }
      if (doLookup) {
        final PsiReference[] references =
          ReferencesSearch.search(field, new LocalSearchScope(statement), false).toArray(new PsiReference[0]);
        if (commonInitializerCandidate == null && references.length > 0) {
          return null;
        }

        for (PsiReference reference : references) {
          if (RefactoringUtil.isAssignmentLHS(reference.getElement())) return null;
        }
      }
    }
    return commonInitializerCandidate;
  }

  private static void collectPsiStatements(PsiElement root, Set<PsiStatement> collected) {
    if (root instanceof PsiStatement){
      collected.add((PsiStatement)root);
    }

    for (PsiElement element : root.getChildren()) {
      collectPsiStatements(element, collected);
    }
  }

  private static class ParametersAndMovedFieldsUsedCollector extends JavaRecursiveElementWalkingVisitor {
    private final Set<PsiField> myMovedFields;
    private final Set<PsiField> myUsedFields;

    private final Set<PsiParameter> myUsedParameters = new LinkedHashSet<PsiParameter>();

    private ParametersAndMovedFieldsUsedCollector(HashSet<PsiField> movedFields) {
      myMovedFields = movedFields;
      myUsedFields = new HashSet<PsiField>();
    }

    public Set<PsiParameter> getUsedParameters() {
      return myUsedParameters;
    }

    public Set<PsiField> getUsedFields() {
      return myUsedFields;
    }

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      final PsiExpression qualifierExpression = expression.getQualifierExpression();
      if (qualifierExpression != null
              && !(qualifierExpression instanceof PsiThisExpression)) {
        return;
      }
      final PsiElement resolved = expression.resolve();
      if (resolved instanceof PsiParameter) {
        myUsedParameters.add((PsiParameter)resolved);
      } else if (myMovedFields.contains(resolved)) {
        myUsedFields.add((PsiField)resolved);
      }
    }
  }

  private class IsMovableInitializerVisitor extends JavaRecursiveElementWalkingVisitor {
    private boolean myIsMovable = true;

    public boolean isMovable() {
      return myIsMovable;
    }

    @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    @Override public void visitReferenceElement(PsiJavaCodeReferenceElement referenceElement) {
      if (!myIsMovable) return;
      final PsiExpression qualifier;
      if (referenceElement instanceof PsiReferenceExpression) {
        qualifier = ((PsiReferenceExpression) referenceElement).getQualifierExpression();
      } else {
        qualifier = null;
      }
      if (qualifier == null || qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
        final PsiElement resolved = referenceElement.resolve();
        if (!(resolved instanceof PsiParameter)) {
          if (resolved instanceof PsiClass && (((PsiClass) resolved).hasModifierProperty(PsiModifier.STATIC) || ((PsiClass)resolved).getContainingClass() == null)) {
            return;
          }
          PsiClass containingClass = null;
          if (resolved instanceof PsiMember && !((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC)) {
            containingClass = ((PsiMember) resolved).getContainingClass();
          }
          myIsMovable = containingClass != null && InheritanceUtil.isInheritorOrSelf(myTargetSuperClass, containingClass, true);
        }
      } else {
        qualifier.accept(this);
      }
    }

    @Override public void visitElement(PsiElement element) {
      if (myIsMovable) {
        super.visitElement(element);
      }
    }
  }

  private HashMap<PsiMethod,HashSet<PsiMethod>> buildConstructorsToSubConstructorsMap(final PsiMethod[] constructors) {
    final HashMap<PsiMethod,HashSet<PsiMethod>> constructorsToSubConstructors = new HashMap<PsiMethod, HashSet<PsiMethod>>();
    for (PsiMethod constructor : constructors) {
      final HashSet<PsiMethod> referencingSubConstructors = new HashSet<PsiMethod>();
      constructorsToSubConstructors.put(constructor, referencingSubConstructors);
      if (constructor != null) {
        // find references
        for (PsiReference reference : ReferencesSearch.search(constructor, new LocalSearchScope(mySourceClass), false)) {
          final PsiElement element = reference.getElement();
          if (element != null && "super".equals(element.getText())) {
            PsiMethod parentMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            if (parentMethod != null && parentMethod.isConstructor()) {
              referencingSubConstructors.add(parentMethod);
            }
          }
        }
      }

      // check default constructor
      if (constructor == null || constructor.getParameterList().getParametersCount() == 0) {
        RefactoringUtil.visitImplicitSuperConstructorUsages(mySourceClass, new RefactoringUtil.ImplicitConstructorUsageVisitor() {
          public void visitConstructor(PsiMethod constructor, PsiMethod baseConstructor) {
            referencingSubConstructors.add(constructor);
          }

          public void visitClassWithoutConstructors(PsiClass aClass) {
          }
        }, myTargetSuperClass);

      }
    }
    return constructorsToSubConstructors;
  }

  private void fixReferencesToStatic(PsiElement classMember) throws IncorrectOperationException {
    final StaticReferencesCollector collector = new StaticReferencesCollector();
    classMember.accept(collector);
    ArrayList<PsiJavaCodeReferenceElement> refs = collector.getReferences();
    ArrayList<PsiElement> members = collector.getReferees();
    ArrayList<PsiClass> classes = collector.getRefereeClasses();
    PsiElementFactory factory = JavaPsiFacade.getInstance(classMember.getProject()).getElementFactory();

    for (int i = 0; i < refs.size(); i++) {
      PsiJavaCodeReferenceElement ref = refs.get(i);
      PsiElement namedElement = members.get(i);
      PsiClass aClass = classes.get(i);

      if (namedElement instanceof PsiNamedElement) {
        PsiReferenceExpression newRef =
                (PsiReferenceExpression) factory.createExpressionFromText
                ("a." + ((PsiNamedElement) namedElement).getName(),
                        null);
        PsiExpression qualifierExpression = newRef.getQualifierExpression();
        assert qualifierExpression != null;
        qualifierExpression = (PsiExpression)qualifierExpression.replace(factory.createReferenceExpression(aClass));
        qualifierExpression.putCopyableUserData(PRESERVE_QUALIFIER, ref.isQualified());
        ref.replace(newRef);
      }
    }
  }

  private class StaticReferencesCollector extends ClassMemberReferencesVisitor {
    private ArrayList<PsiJavaCodeReferenceElement> myReferences;
    private ArrayList<PsiElement> myReferees;
    private ArrayList<PsiClass> myRefereeClasses;

    private StaticReferencesCollector() {
      super(mySourceClass);
      myReferees = new ArrayList<PsiElement>();
      myRefereeClasses = new ArrayList<PsiClass>();
      myReferences = new ArrayList<PsiJavaCodeReferenceElement>();
    }

    public ArrayList<PsiElement> getReferees() {
      return myReferees;
    }

    public ArrayList<PsiClass> getRefereeClasses() {
      return myRefereeClasses;
    }

    public ArrayList<PsiJavaCodeReferenceElement> getReferences() {
      return myReferences;
    }

    protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
      if (classMember.hasModifierProperty(PsiModifier.STATIC)) {
        if (!myMembersToMove.contains(classMember) &&
            RefactoringHierarchyUtil.isMemberBetween(myTargetSuperClass, mySourceClass, classMember)) {
          myReferences.add(classMemberReference);
          myReferees.add(classMember);
          myRefereeClasses.add(classMember.getContainingClass());
        }
        else if (myMembersToMove.contains(classMember) || myMembersAfterMove.contains(classMember)) {
          myReferences.add(classMemberReference);
          myReferees.add(classMember);
          myRefereeClasses.add(myTargetSuperClass);
        }
      }
    }
  }

  private class QualifiedThisSuperAdjuster extends JavaRecursiveElementVisitor {
    @Override public void visitThisExpression(PsiThisExpression expression) {
      super.visitThisExpression(expression);
      final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
      if (qualifier != null && qualifier.isReferenceTo(mySourceClass)) {
        try {
          qualifier.bindToElement(myTargetSuperClass);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }

    @Override public void visitSuperExpression(PsiSuperExpression expression) {
      super.visitSuperExpression(expression);
      final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
      if (qualifier != null && qualifier.isReferenceTo(mySourceClass)) {
        try {
          expression.replace(JavaPsiFacade.getElementFactory(myProject).createExpressionFromText(myTargetSuperClass.getName() + ".this", null));
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }

  private class ExplicitSuperDeleter extends JavaRecursiveElementWalkingVisitor {
    private final PsiExpression myThisExpression = JavaPsiFacade.getElementFactory(myProject).createExpressionFromText("this", null);

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if(expression.getQualifierExpression() instanceof PsiSuperExpression) {
        PsiElement resolved = expression.resolve();
        if (resolved == null || resolved instanceof PsiMethod && shouldFixSuper((PsiMethod) resolved)) {
          expression.getQualifierExpression().delete();
        }
      }
    }

    @Override
    public void visitSuperExpression(PsiSuperExpression expression) {
      expression.replace(myThisExpression);
    }

    @Override
    public void visitClass(PsiClass aClass) {
      // do nothing
    }

    private boolean shouldFixSuper(PsiMethod method) {
      for (PsiMember element : myMembersAfterMove) {
        if (element instanceof PsiMethod) {
          PsiMethod member = (PsiMethod)element;
          // if there is such member among moved members, super qualifier
          // should not be removed
          final PsiManager manager = method.getManager();
          if (manager.areElementsEquivalent(member.getContainingClass(), method.getContainingClass()) &&
              MethodSignatureUtil.areSignaturesEqual(member, method)) {
            return false;
          }
        }
      }

      final PsiMethod methodFromSuper = myTargetSuperClass.findMethodBySignature(method, false);
      return methodFromSuper == null;
    }
  }

  private boolean willBeUsedInSubclass(PsiElement member, PsiClass superclass, PsiClass subclass) {
    for (PsiReference ref : ReferencesSearch.search(member, new LocalSearchScope(subclass), false)) {
      PsiElement element = ref.getElement();
      if (!RefactoringHierarchyUtil.willBeInTargetClass(element, myMembersToMove, superclass, false)) {
        return true;
      }
    }
    return false;
  }
}
