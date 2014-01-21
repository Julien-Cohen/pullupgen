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

package com.intellij.refactoring.memberPullUp;

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaPullUpGenHelper implements PullUpGenHelper<MemberInfo> {

  private static final Logger LOG = Logger.getInstance(JavaPullUpGenHelper.class);
  private static final Key<Boolean> PRESERVE_QUALIFIER = Key.<Boolean>create("PRESERVE_QUALIFIER");
  private final PsiClass mySourceClass;
  private final PsiClass myTargetSuperClass;
  private final boolean myIsTargetInterface;
  //private final MemberInfo[] myMembersToMove;
  private final DocCommentPolicy myJavaDocPolicy;
  private Set<PsiMember> myMembersAfterMove ; // Set of members that have been already moved (name seems to be badly chosen, but comes from intelliJ).
  @NotNull private Set<PsiMember> myMembersToMove;
  //private final PsiManager myManager;

  @Nullable
  protected Collection<PsiClass> sisterClasses = null;   // julien




  private Project myProject;



  private final QualifiedThisSuperAdjuster myThisSuperAdjuster;

  private final ExplicitSuperDeleter myExplicitSuperDeleter;



  public JavaPullUpGenHelper(PullUpGenData data) {
    this ((PullUpData) data);
    sisterClasses = data.getSisterClasses();
  }

  public JavaPullUpGenHelper(PullUpData data) {
        myProject = data.getProject();
        myMembersToMove = data.getMembersToMove();
        myMembersAfterMove = data.getMovedMembers();
        myTargetSuperClass = data.getTargetClass();
        mySourceClass = data.getSourceClass();
        myJavaDocPolicy = data.getDocCommentPolicy();
        myIsTargetInterface = myTargetSuperClass.isInterface();


        myThisSuperAdjuster = new QualifiedThisSuperAdjuster();
        myExplicitSuperDeleter = new ExplicitSuperDeleter();
  }


  @Override
  public void encodeContextInfo(MemberInfo info) {
    ChangeContextUtil.encodeContextInfo(info.getMember(), true);
  }


// FIXME Deplace vers PullUpProcessor, mais garder du code perso
/*
  protected void performRefactoring(UsageInfo[] usages) {
      try {
          moveMembersToBase();
      } catch (GenAnalysisUtils.AmbiguousOverloading e) {
          throw new IncorrectOperationException(e.toString());
      } catch (GenAnalysisUtils.MemberNotImplemented e) {
          throw new IncorrectOperationException(e.toString());
      }
      moveFieldInitializations();
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element instanceof PsiReferenceExpression) {
        PsiExpression qualifierExpression = ((PsiReferenceExpression)element).getQualifierExpression();
        if (qualifierExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifierExpression).resolve() == mySourceClass) {
          ((PsiReferenceExpression)qualifierExpression).bindToElement(myTargetSuperClass);
        }
      }
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      // @Override
      public void run() {
        processMethodsDuplicates();
      }
    }, ModalityState.NON_MODAL, myProject.getDisposed());
  }
*/

    /*
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
  */

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

      /* Julien : I change this because when a method is package, it has to be intensionally changed into public (interface members are public)
      if (myIsTargetInterface) {
        PsiUtil.setModifierProperty(modifierListOwner, PsiModifier.PUBLIC, true); // TODO: if you do this, do it for all sister methods (or package?)
      } */
        if (myIsTargetInterface && !modifierListOwner.hasModifierProperty("public")){ // TODO : change "public" into *.PUBLIC
            throw new IncorrectOperationException("Please change the visibility of the method to PUBLIC before moving it into an interface.");   // Julien
        }
        else  if (modifierListOwner.hasModifierProperty(PsiModifier.PRIVATE)) {
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

    /*
  // FIXME : deplacer vers PullUpProcessor
  public void moveMembersToBase()
          throws IncorrectOperationException, GenAnalysisUtils.AmbiguousOverloading, GenAnalysisUtils.MemberNotImplemented {
    final HashSet<PsiMember> movedMembers = new HashSet<PsiMember>();
    myMembersAfterMove = new HashSet<PsiMember>();

    // build aux sets
    for (MemberInfo info : myMembersToMove) {
      movedMembers.add(info.getMember());
    }

    // correct private member visibility
    for (MemberInfo info : myMembersToMove) {
      if (info.getMember() instanceof PsiClass && info.getOverrides() != null) continue;
      setCorrectVisibility(movedMembers, info);
      ChangeContextUtil.encodeContextInfo(info.getMember(), true);
    }

    final PsiSubstitutor substitutor = upDownSuperClassSubstitutor();    // (rem Julien) a PsiSubstitutor represents a mapping between type parameters and their values
    //final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);

    // (Julien) compute the set of sister methods (which have all the compatible members)
    if (sisterClasses == null ) initSisterClasses(myMembersToMove[0]) ;
    assert (sisterClasses != null);

    for (PsiClass c : sisterClasses) GenBuildUtils.alignParameters(myTargetSuperClass, c, elementFactory);

    // do actual move (for each member to move)
    for (MemberInfo info : myMembersToMove) {

        move(info, substitutor);
    }

    ExplicitSuperDeleter explicitSuperDeleter = new ExplicitSuperDeleter();
    for (PsiMember member : myMembersAfterMove) {
      member.accept(explicitSuperDeleter);
    }
    explicitSuperDeleter.fixSupers();

    final QualifiedThisSuperAdjuster qualifiedThisSuperAdjuster = new QualifiedThisSuperAdjuster();
    for (PsiMember member : myMembersAfterMove) {
      member.accept(qualifiedThisSuperAdjuster);
    }

    ChangeContextUtil.decodeContextInfo(myTargetSuperClass, null, null);

    for (final PsiMember movedMember : myMembersAfterMove) {
      movedMember.accept(new JavaRecursiveElementWalkingVisitor() {
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
      final JavaRefactoringListenerManager listenerManager = JavaRefactoringListenerManager.getInstance(movedMember.getProject());
      ((JavaRefactoringListenerManagerImpl)listenerManager).fireMemberMoved(mySourceClass, movedMember);
    }
  }
*/

    private void doMoveClass(PsiSubstitutor substitutor, MemberInfo info) {
        // (rem Julien) case where the member to pull-up is a class/interface, internal or declared as superclass/interface
        if (GenAnalysisUtils.memberClassComesFromImplements(info)){
              doMoveImplementedClass(substitutor, info);
          }

          else { // (rem Julien) the member refers to a class, but not from 'implements' : either from 'extends', either from inner-class. I guess 'extends' are out of scope of the pull-up operation.
            assert(GenAnalysisUtils.memberClassIsInnerClass(info));
            PsiClass aClass = (PsiClass)info.getMember();
            throw new IncorrectOperationException("inner classes not handled yet : " + aClass);  /*
            RefactoringUtil.replaceMovedMemberTypeParameters(aClass, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
            fixReferencesToStatic(aClass, movedMembers);
            final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(aClass);
            myMembersAfterMove.add(movedElement);
            aClass.delete();  */
            // TODO : handle inner classes
          }
    }

    private void doMoveImplementedClass(PsiSubstitutor substitutor, MemberInfo info) {
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
        //System.out.println("pull-up-gen/moveMembersToBase : case 'implements'");
        PsiClass implementedIntf = (PsiClass) info.getMember();

        final PsiReferenceList sourceReferenceList = info.getSourceReferenceList();    // the class containing the member source code
        //System.out.println(sourceReferenceList); // debug


        LOG.assertTrue(sourceReferenceList != null);

        //System.out.println(mySourceClass + " eq " + sourceReferenceList.getParent() + " : " + mySourceClass.equals(sourceReferenceList.getParent())); // debug  : true on A false on B

        // Warning : the member might not be extracted from 'mySourceClass'. This is tested by  mySourceClass.equals(sourceReferenceList.getParent()) .

        PsiJavaCodeReferenceElement ref = mySourceClass.equals(sourceReferenceList.getParent()) ?                   // debug : true in the test
                                          RefactoringUtil.removeFromReferenceList(sourceReferenceList, implementedIntf) : // remove returns a copy
                                          (PsiJavaCodeReferenceElement)RefactoringUtil.findReferenceToClass   (sourceReferenceList, implementedIntf).copy();     // Julien : make a copy because will be deleted
        //System.out.println(ref); // debug   ( "I" in the test)

        // added Julien
        for (PsiClass c : sisterClasses) {
            RefactoringUtil.removeFromReferenceList(c.getImplementsList(), implementedIntf);
            // TODO: since mySourclass is in sisterClasses, don't need to removeFromReference... above.
        }


        if (ref == null) { throw new IncorrectOperationException("ref to 'implements' not found in "+mySourceClass)  ; }

        // TODO : this should be done for all sister classes
        RefactoringUtil.replaceMovedMemberTypeParameters(ref, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);

        // find the target statement in the superclass/interface
        final PsiReferenceList targetReferenceList =
                 myTargetSuperClass.isInterface() ? myTargetSuperClass.getExtendsList() : myTargetSuperClass.getImplementsList();
        LOG.assertTrue (targetReferenceList != null);

        if (targetReferenceList == null) { throw new IncorrectOperationException("reference list not found in "+myTargetSuperClass)  ; }

        targetReferenceList.add(ref);
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
        final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(field);
        myMembersAfterMove.add(movedElement);
        field.delete();
    }

    private void doMoveMethod(PsiSubstitutor substitutor, MemberInfo info) {
        List<String> boundNames =  GenSubstitutionUtils.boundTypeNames(myTargetSuperClass);
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
        PsiMethod method = (PsiMethod)info.getMember();
        PsiMethod methodCopy = (PsiMethod)method.copy();
        if (method.findDeepestSuperMethods().length == 0) {
          deleteOverrideAnnotationIfFound(methodCopy);
        }
        final boolean isOriginalMethodAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);


        // (rem Julien) case isToAbstract  (case of interest for pull-up-gen)

        if (myIsTargetInterface || info.isToAbstract()) {
          assert(this.myTargetSuperClass.hasModifierProperty(PsiModifier.ABSTRACT));  // and the interface case?
          ChangeContextUtil.clearContextInfo(method);

          // begin Julien
          // 1) collect the sister methods (methods with the same type skeleton in sister classes).
          final List<PsiMethod> sisterMethods = GenAnalysisUtils.findCompatibleMethods(method, sisterClasses);    // TODO : make that efficient (classes are traversed twice)



          //System.out.println("Selected methods:");
          //for (PsiMethod m: sisterMethods) System.out.println(m);

          // 2)find which types have to be parameterized

          final DependentSubstitution initialParameters = GenSubstitutionUtils.computeCurrentSub(this.myTargetSuperClass, sisterClasses, elementFactory); // used later to know the type parameters of the initial clases TODO: improve that
          DependentSubstitution       theMegaSubst      = GenSubstitutionUtils.computeCurrentSub(this.myTargetSuperClass, sisterClasses, elementFactory);   // TODO: clone initialParameters above?

          // final List <Integer> positions = GenerifyUtils.positionsToUnify(sisterMethods); // -1 represent the return type position
          // final Map<Integer, PsiTypeParameter> sub = GenerifyUtils.unify(sisterMethods,computecurrentsubstitution(), method.getName(), JavaPsiFacade.getElementFactory(method.getProject()));


          final GenSubstitutionUtils.ParamSubstitution sub = GenSubstitutionUtils.antiunify(sisterMethods, theMegaSubst, method.getName(), elementFactory, boundNames); // TODO : fix that (empty substitution)
          // System.out.println("Positions to generify:" + positions );



          // 3) build the abstract method
          RefactoringUtil.makeMethodAbstract(myTargetSuperClass, methodCopy);
          //System.out.println("Copy of selected method created and abstracted: " + methodCopy) ;


          // 4) generify the abstract method

          GenBuildUtils.generifyAbstractMethod(methodCopy, sub);
          //PsiTypeParameterList l = RefactoringUtil.createTypeParameterListWithUsedTypeParameters(methodCopy) ;
          //System.out.println ("Abstract method generified (new type parameters:" + lt + ").") ;



          // 5) ??
          RefactoringUtil.replaceMovedMemberTypeParameters(methodCopy, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);


          // 6) process javadoc
          myJavaDocPolicy.processCopiedJavaDoc(methodCopy.getDocComment(), method.getDocComment(), isOriginalMethodAbstract);


          // 7) Add type parameters to the superclass
          DependentSubstitution newParameters = GenSubstitutionUtils.difference(theMegaSubst, initialParameters);
          for (PsiTypeParameter t: newParameters.keySet()){
              myTargetSuperClass.getTypeParameterList().add(t);
          }

          // 8) Add the new abstract method in the superclass (and get the new resulting method).
          //System.out.println ("Going to add the new method in the superclass.");
          //GenerifyUtils.showMethods(myTargetSuperClass) ;  //debug
          final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(methodCopy);



          // 9) Add the parameters in sisterclasses extends statements
          GenBuildUtils.updateExtendsStatementsInSisterClasses(newParameters, myTargetSuperClass, elementFactory);
          //System.out.println("Extends statements updated in sister classes.");

          // end Julien

          // 10) Manage override annotations
          // (rem Julien) We are still in the case where the superclass is an interface or the method is pulled up to an abstract method.
          CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(method.getProject());
          if (styleSettings.INSERT_OVERRIDE_ANNOTATION) {
            // (rem Julien) @override appears in 1.5 but applies to methods coming from interfaces only since 1.6
            if (PsiUtil.isLanguageLevel5OrHigher(mySourceClass) && !myTargetSuperClass.isInterface() || PsiUtil.isLanguageLevel6OrHigher(mySourceClass)) {
              for (PsiMethod m : sisterMethods){ //Julien : added annotation for all sister methods
                new AddAnnotationFix(Override.class.getName(), m).invoke(method.getProject(), null, mySourceClass.getContainingFile());
              }
            }
          }
          if (!PsiUtil.isLanguageLevel6OrHigher(mySourceClass) && myTargetSuperClass.isInterface()) {
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
            method.delete(); // TODO : do the same in sister classes ?
          }
        } // (rem Julien) endif


        else { // (rem Julien) Here, the target superclass is neither abstract nor an interface
          if (isOriginalMethodAbstract) {
            PsiUtil.setModifierProperty(myTargetSuperClass, PsiModifier.ABSTRACT, true);
          }
          RefactoringUtil.replaceMovedMemberTypeParameters(methodCopy, PsiUtil.typeParametersIterable(mySourceClass), substitutor, elementFactory);
          fixReferencesToStatic(methodCopy);
          final PsiMethod superClassMethod = myTargetSuperClass.findMethodBySignature(methodCopy, false);
          if (superClassMethod != null && superClassMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
            superClassMethod.replace(methodCopy);
          }
          else {
            final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(methodCopy);
            myMembersAfterMove.add(movedElement);
          }
          method.delete();
        }
    }

    @Deprecated
    void initSisterClasses(MemberInfo[] membersToMove)
            throws GenAnalysisUtils.MemberNotImplemented, GenAnalysisUtils.AmbiguousOverloading {
        sisterClasses = computeSisterClasses(membersToMove);
    }

    public Collection<PsiClass> getSisterClasses(MemberInfo[] membersToMove)
            throws GenAnalysisUtils.MemberNotImplemented, GenAnalysisUtils.AmbiguousOverloading {
        if (sisterClasses == null) initSisterClasses(membersToMove);
        return sisterClasses;
    }

    @Deprecated
    @NotNull
    Collection<PsiClass> computeSisterClasses(@NotNull MemberInfo[] membersToMove)
            throws GenAnalysisUtils.MemberNotImplemented, GenAnalysisUtils.AmbiguousOverloading {
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



  private static void deleteOverrideAnnotationIfFound(PsiMethod oMethod) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(oMethod, Override.class.getName());
    if (annotation != null) {
      annotation.delete();
    }
  }
/*
  public void moveFieldInitializations() throws IncorrectOperationException {
    LOG.assertTrue(myMembersAfterMove != null);

    final LinkedHashSet<PsiField> movedFields = new LinkedHashSet<PsiField>();
    for (PsiMember member : myMembersAfterMove) {
      if (member instanceof PsiField) {
        movedFields.add((PsiField)member);
      }
    }

    if (movedFields.isEmpty()) return;
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
*/
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
    //private final Set<PsiMember> myMovedMembers;

    private StaticReferencesCollector() {
      super(mySourceClass);
      //myMovedMembers = movedMembers;
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
    //private final ArrayList<PsiExpression> mySupersToDelete = new ArrayList<PsiExpression>();
    //private final ArrayList<PsiSuperExpression> mySupersToChangeToThis = new ArrayList<PsiSuperExpression>();
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

    /*
    public void fixSupers() throws IncorrectOperationException {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
      PsiThisExpression thisExpression = (PsiThisExpression) factory.createExpressionFromText("this", null);
      for (PsiExpression psiExpression : mySupersToDelete) {
        psiExpression.delete();
      }

      for (PsiSuperExpression psiSuperExpression : mySupersToChangeToThis) {
        psiSuperExpression.replace(thisExpression);
      }
    }*/
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

  /*
  public static boolean checkedInterfacesContain(Collection<MemberInfo> memberInfos, PsiMethod psiMethod) {
    for (MemberInfo memberInfo : memberInfos) {
      if (memberInfo.isChecked() &&
          memberInfo.getMember() instanceof PsiClass &&
          Boolean.FALSE.equals(memberInfo.getOverrides())) {
        if (((PsiClass)memberInfo.getMember()).findMethodBySignature(psiMethod, true) != null) {
          return true;
        }
      }
    }
    return false;
  }
 */

  /*
  private class PullUpUsageViewDescriptor implements UsageViewDescriptor {
    public String getProcessedElementsHeader() {
      return "Pull up members from";
    }

    @NotNull
    public PsiElement[] getElements() {
      return new PsiElement[]{mySourceClass};
    }

    public String getCodeReferencesText(int usagesCount, int filesCount) {
      return "Class to pull up members to \"" + RefactoringUIUtil.getDescription(myTargetSuperClass, true) + "\"";
    }

    public String getCommentReferencesText(int usagesCount, int filesCount) {
      return null;
    }
  }
  */
}
