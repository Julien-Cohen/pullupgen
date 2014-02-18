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
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 14.06.2002
 * Time: 22:35:19
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.memberPullUp;

import com.intellij.analysis.AnalysisScope;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.genUtils.GenAnalysisUtils;
import com.intellij.refactoring.genUtils.GenBuildUtils;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.impl.JavaRefactoringListenerManagerImpl;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.duplicates.MethodDuplicatesHandler;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PullUpGenProcessor extends BaseRefactoringProcessor implements PullUpGenData {
  private static final Logger LOG = Logger.getInstance(PullUpGenProcessor.class);

  private final PsiClass mySourceClass;
  private final PsiClass myTargetSuperClass;
  private final MemberInfo[] myMembersToMove;       // initialized by constructor ; returned by ???
  private final DocCommentPolicy myJavaDocPolicy;
  private Set<PsiMember> myMembersAfterMove = null; // created by moveMembersToBase,  returned by getMovedMembers !!!
  private Set<PsiMember> myMovedMembers = null;     // created by moveMembersToBase, returned by getMembersToMove in origina intellij!!!
  protected Map<Language, PullUpGenHelper<MemberInfo>> myProcessors = ContainerUtil.newHashMap(); //made protected (J)

  @Nullable private Collection<PsiClass> mySisterClasses ; //(J)
  //private PullUpGenHelper<MemberInfo> myProcessor;

  public PullUpGenProcessor(PsiClass sourceClass, Collection<PsiClass> sisterClasses, PsiClass targetSuperClass, MemberInfo[] membersToMove, DocCommentPolicy javaDocPolicy) {
    super(sourceClass.getProject());
    mySourceClass = sourceClass;
    myTargetSuperClass = targetSuperClass;
    myMembersToMove = membersToMove;
    myJavaDocPolicy = javaDocPolicy;

    //myProcessor = getProcessor(membersToMove[0]); this was an error, you cannot use that now because all instance variables have not been initialized


    mySisterClasses = sisterClasses; //(J)
  }

  public PullUpGenProcessor(PsiClass sourceClass, PsiClass targetSuperClass, MemberInfo[] membersToMove, DocCommentPolicy javaDocPolicy) {
    this(sourceClass, null, targetSuperClass, membersToMove, javaDocPolicy);
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new PullUpUsageViewDescriptor();
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    final List<UsageInfo> result = new ArrayList<UsageInfo>();
    for (MemberInfo memberInfo : myMembersToMove) {
      final PsiMember member = memberInfo.getMember();
      if (member.hasModifierProperty(PsiModifier.STATIC)) {
        for (PsiReference reference : ReferencesSearch.search(member)) {
          result.add(new UsageInfo(reference));
        }
      }
    }
    return result.isEmpty() ? UsageInfo.EMPTY_ARRAY : result.toArray(new UsageInfo[result.size()]);
  }

  protected void performRefactoring(UsageInfo[] usages) {
   try{
    moveMembersToBase(); // initializes myMovedMembers and myMembersAfterMove

    moveFieldInitializations();
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element == null) continue;

      PullUpGenHelper processor = getProcessor(element);
      processor.updateUsage(element);
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        processMethodsDuplicates();
      }
    }, ModalityState.NON_MODAL, myProject.getDisposed());

   }
   catch (GenAnalysisUtils.MemberNotImplemented e) {throw new IncorrectOperationException(e.toString()) ;} // (J)
   catch (GenAnalysisUtils.AmbiguousOverloading e) {throw new IncorrectOperationException(e.toString()) ;} // (J)
  }

  private void processMethodsDuplicates() {
    if (!myTargetSuperClass.isValid()) return;
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        final Query<PsiClass> search = ClassInheritorsSearch.search(myTargetSuperClass);
        final Set<VirtualFile> hierarchyFiles = new HashSet<VirtualFile>();
        for (PsiClass aClass : search) {
          final PsiFile containingFile = aClass.getContainingFile();
          if (containingFile != null) {
            final VirtualFile virtualFile = containingFile.getVirtualFile();
            if (virtualFile != null) {
              hierarchyFiles.add(virtualFile);
            }
          }
        }
        final Set<PsiMember> methodsToSearchDuplicates = new HashSet<PsiMember>();
        for (PsiMember psiMember : myMembersAfterMove) {
          if (psiMember instanceof PsiMethod && psiMember.isValid() && ((PsiMethod)psiMember).getBody() != null) {
            methodsToSearchDuplicates.add(psiMember);
          }
        }

        MethodDuplicatesHandler.invokeOnScope(myProject, methodsToSearchDuplicates, new AnalysisScope(myProject, hierarchyFiles), true);
      }
    }, MethodDuplicatesHandler.REFACTORING_NAME, true, myProject);
  }

  protected String getCommandName() {
    return RefactoringBundle.message("pullUp.command", DescriptiveNameUtil.getDescriptiveName(mySourceClass));
  }



  public void moveMembersToBase()
            throws IncorrectOperationException, GenAnalysisUtils.AmbiguousOverloading, GenAnalysisUtils.MemberNotImplemented {

        myMovedMembers = ContainerUtil.newHashSet();
        myMembersAfterMove = ContainerUtil.newHashSet();
        assert (myMembersAfterMove != null);

        // build aux sets
        for (MemberInfo info : myMembersToMove) {
            myMovedMembers.add(info.getMember());
        }

        final PsiSubstitutor substitutor = upDownSuperClassSubstitutor();    // (rem Julien) a PsiSubstitutor represents a mapping between type parameters and their values


        // correct private member visibility
        for (MemberInfo info : myMembersToMove) {
            PullUpGenHelper<MemberInfo> processor = getProcessor(info);

            if (info.getMember() instanceof PsiClass && info.getOverrides() != null) continue;
              processor.setCorrectVisibility(info);
              processor.encodeContextInfo(info);

            // (Julien) compute the set of sister methods (which have all the compatible members)
            if (mySisterClasses == null )
                mySisterClasses = processor.getSisterClasses(myMembersToMove) ; // processor is a JavaPullUpGenHelper
        }



        assert (mySisterClasses != null);

        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject); // FIXME : pas dans l'original...

        for (PsiClass c : mySisterClasses) GenBuildUtils.alignParameters(myTargetSuperClass, c, elementFactory);

        // do actual move (for each member to move)
        for (MemberInfo info : myMembersToMove) {
            getProcessor(info).move(info, substitutor);
        }

        //ExplicitSuperDeleter explicitSuperDeleter = new ExplicitSuperDeleter();
        for (PsiMember member : myMembersAfterMove) { // FIXME (initialized to empty? yes but filled in JavaPullUpGenHelper)
           getProcessor(member).postProcessMember(member);
           final JavaRefactoringListenerManager listenerManager = JavaRefactoringListenerManager.getInstance(member.getProject());
           ((JavaRefactoringListenerManagerImpl)listenerManager).fireMemberMoved(mySourceClass, member);
        }
        // explicitSuperDeleter.fixSupers(); // J : why is it removed?


    }

  private PullUpGenHelper<MemberInfo> getProcessor(@NotNull PsiElement element) {
    assert (myMembersAfterMove != null) ;
    assert (myMovedMembers != null) ;
    Language language = element.getLanguage();
    return getProcessor(language);
  }




  // changed from private to protected (J)
  protected PullUpGenHelper<MemberInfo> getProcessor(Language language) { // FIXME : Reafactor that code.
      assert (myMembersAfterMove != null) ;
      assert (myMovedMembers != null) ;

    PullUpGenHelper<MemberInfo> helper = myProcessors.get(language);
    if (helper == null) {

        final LanguageExtension<PullUpHelperFactory> instance = PullUpGenHelper.INSTANCE; // to debug (can be inlined)

        // FIXME
        /*System.out.println(instance); // objet
        System.out.println(language); // JAVA
        System.out.println("instance for langage : " + instance.forLanguage(language)); // com.intellij.refactoring.memberPullUp.JavaPullUpHelperFactory@1a01125
        System.out.println("instance for key : " + instance.forKey(language)); // [com.intellij.refactoring.memberPullUp.JavaPullUpHelperFactory@1a01125, com.intellij.refactoring.memberPullUp.JavaPullUpGenHelperFactory@1c59c26]
*/
        @Nullable PullUpHelperFactory factory = null;
        for (Object f : instance.forKey(language)) //  FIXME : find a better solution
            {
               if (f instanceof JavaPullUpGenHelperFactory)
                   factory = (PullUpHelperFactory) f ;
               /* else System.out.println ("Found another factory of type " + f.getClass().getName() + ", not used."); */
            }


        if ( factory == null )  {
                /* System.out.println("helper null (convenient PullUpGenHelperFactory not found).");
                System.out.println(instance); // objet
                System.out.println(language); // JAVA
                System.out.println(instance.forLanguage(language)); // null
                System.out.println(instance.forKey(language)); // [] */
                throw new Error ("helper null (convenient PullUpGenHelperFactory not found).");
        }

        JavaPullUpGenHelperFactory factory2 = null ;
        if (factory instanceof JavaPullUpGenHelperFactory )
            factory2 = (JavaPullUpGenHelperFactory)factory;
        else new Error ("JavaPullUpGenHelperFactory not found.");


        helper = factory2.createPullUpGenHelper(this); // at this point, myMovedMembers and myMembersAfterMove should have been initialized
      myProcessors.put(language, helper);
    }
    return helper;
  }




  private PullUpGenHelper<MemberInfo> getProcessor(@NotNull MemberInfo info) {
    assert (myMembersAfterMove != null) ;
    assert (myMovedMembers != null) ;
    PsiReferenceList refList = info.getSourceReferenceList();
    if (refList != null) {
      return getProcessor(refList.getLanguage());
    }
    return getProcessor(info.getMember());
  }

  // Julien: a PsiSubstitutor represents a mapping between type parameters and their values. (from PsiSubstitutor)
  private PsiSubstitutor upDownSuperClassSubstitutor() {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      // Julien: collect the type parameters of the source class, and map them to null in the result being built.
      // null plays the role of default value (can be overwritten later).
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(mySourceClass)) {
      substitutor = substitutor.put(parameter, null);
    }
    final Map<PsiTypeParameter, PsiType> substitutionMap =
      TypeConversionUtil.getSuperClassSubstitutor(myTargetSuperClass, mySourceClass, PsiSubstitutor.EMPTY).getSubstitutionMap();
    for (PsiTypeParameter parameter : substitutionMap.keySet()) {
      final PsiType type = substitutionMap.get(parameter);
      final PsiClass resolvedClass = PsiUtil.resolveClassInType(type);
      if (resolvedClass instanceof PsiTypeParameter) {
        substitutor = substitutor.put((PsiTypeParameter)resolvedClass, JavaPsiFacade.getElementFactory(myProject).createType(parameter));
      }
    }
    return substitutor;
  }

  public void moveFieldInitializations() throws IncorrectOperationException {
    LOG.assertTrue(myMembersAfterMove != null);

    final LinkedHashSet<PsiField> movedFields = new LinkedHashSet<PsiField>();
    for (PsiMember member : myMembersAfterMove) {
      if (member instanceof PsiField) {
        movedFields.add((PsiField)member);
      }
    }

    if (movedFields.isEmpty()) return;

    getProcessor(myTargetSuperClass).moveFieldInitializations(movedFields);
  }

  public static boolean checkedInterfacesContain(Collection<? extends MemberInfoBase<? extends PsiMember>> memberInfos, PsiMethod psiMethod) {
    for (MemberInfoBase<? extends PsiMember> memberInfo : memberInfos) {
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

  @Override
  public PsiClass getSourceClass() {
    return mySourceClass;
  }

  @Override
  public PsiClass getTargetClass() {
    return myTargetSuperClass;
  }

  @Override
  public DocCommentPolicy getDocCommentPolicy() {
    return myJavaDocPolicy;
  }

    public static Set<PsiMember> convert(MemberInfo[] t){
        Set<PsiMember> s = new HashSet<PsiMember>();
        for (int i = 0 ; i< t.length ; i++ ) s.add(t[i].getMember());
        return s;
    }

  @Override
  @NotNull
  public Set<PsiMember> getMembersToMove() {
      assert (myMembersToMove != null) ;
      return convert(myMembersToMove);
  }


/*
  // Return the set of members selected by the user and that has to be moved.
  @Override
  @NotNull
  public Set<PsiMember> getMembersToMove() {
        return myMovedMembers;
  }*/

  @Override
  @NotNull
  public Set<PsiMember> getMovedMembers() {
    assert(myMembersAfterMove != null)  ;
    return myMembersAfterMove;
  }




  @Override
  public Project getProject() {
    return myProject;
  }

  public Collection<PsiClass> getSisterClasses() {return mySisterClasses ;}


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
}
