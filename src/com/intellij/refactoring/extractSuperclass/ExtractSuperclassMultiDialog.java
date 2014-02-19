/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoChange;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.genUtils.GenAnalysisUtils;
import com.intellij.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.ui.ShortClassCellRenderer;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.InterfaceContainmentVerifier;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.UsesAndInterfacesDependencyMemberInfoModel;
import com.intellij.ui.SeparatorFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

class ExtractSuperclassMultiDialog extends JavaExtractSuperBaseDialog {
  private final InterfaceContainmentVerifier myContainmentVerifier = new InterfaceContainmentVerifier() {
    public boolean checkedInterfacesContain(PsiMethod psiMethod) {
      return PullUpProcessor.checkedInterfacesContain(myMemberInfos, psiMethod);
    }
  };

  public interface Callback {
    boolean checkConflicts(ExtractSuperclassMultiDialog dialog);
  }

  final boolean withGenerification;

  private final Callback myCallback;

  public ExtractSuperclassMultiDialog(Project project, PsiClass sourceClass, List<MemberInfo> selectedMembers, Callback callback, boolean generification) {
    super(project, sourceClass, selectedMembers, ExtractSuperclassMultiHandler.REFACTORING_NAME);
    myCallback = callback;
    withGenerification=generification;
    init();
  }

  InterfaceContainmentVerifier getContainmentVerifier() {
    return myContainmentVerifier;
  }

  protected String getClassNameLabelText() {
    return isExtractSuperclass()
           ? RefactoringBundle.message("superclass.name")
           : RefactoringBundle.message("extractSuper.rename.original.class.to");
  }

  @Override
  protected String getPackageNameLabelText() {
    return isExtractSuperclass()
           ? RefactoringBundle.message("package.for.new.superclass")
           : RefactoringBundle.message("package.for.original.class");
  }

  protected String getEntityName() {
    return RefactoringBundle.message("ExtractSuperClass.superclass");
  }

  @Override
  protected String getTopLabelText() {
    return RefactoringBundle.message("extract.superclass.from");
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    final MemberSelectionPanel memberSelectionPanel = new MemberSelectionPanel(RefactoringBundle.message("members.to.form.superclass"),
                                                                               myMemberInfos, RefactoringBundle.message("make.abstract"));
    panel.add(memberSelectionPanel, BorderLayout.CENTER);
    final MemberInfoModel<PsiMember, MemberInfo> memberInfoModel =
      new UsesAndInterfacesDependencyMemberInfoModel(mySourceClass, null, false, myContainmentVerifier) {
        public Boolean isFixedAbstract(MemberInfo member) {
          return Boolean.TRUE;
        }

        // JULIEN : to update the sister classes when a checkbox is modified by the user
        @Override
        public void memberInfoChanged(MemberInfoChange event){
            super.memberInfoChanged(event);
            updateSisterClassDisplay();
        }
      };
    memberInfoModel.memberInfoChanged(new MemberInfoChange<PsiMember, MemberInfo>(myMemberInfos));
    memberSelectionPanel.getTable().setMemberInfoModel(memberInfoModel);
    memberSelectionPanel.getTable().addMemberInfoChangeListener(memberInfoModel);


    JPanel eastPanel = new JPanel(new BorderLayout());
    panel.add (eastPanel, BorderLayout.EAST);

    eastPanel.add(myDocCommentPanel, BorderLayout.NORTH);

    // new
    eastPanel.add(createSisterPanel(), BorderLayout.SOUTH);
    return panel;
  }

  // to display the list of sister classes.
  JList mySisterClassList = new JBList();

  // new
  protected JPanel createSisterPanel() {
      JPanel thePanel = new JPanel(new BorderLayout());
      mySisterClassList.setCellRenderer(new ShortClassCellRenderer(mySisterClassList.getCellRenderer()));
      thePanel.add(SeparatorFactory.createSeparator("Future sub-classes", mySisterClassList), BorderLayout.NORTH);
      thePanel.add(mySisterClassList, BorderLayout.CENTER);
      //updateSisterClassDisplay(getSisterClasses());
      updateSisterClassDisplay();
      return thePanel;
  }


  // Julien
  protected Collection<PsiClass> getSisterClasses() {
        return GenAnalysisUtils.findSisterClassesInDirectory(mySourceClass);
  }


  //Julien
  void updateSisterClassDisplay(Collection<PsiClass> l){
      mySisterClassList.setListData(l.toArray());
  }

  //Julien
  void updateSisterClassDisplay(){
      Collection<PsiClass> l = ExtractSuperClassMultiUtil.filterSisterClasses(getSelectedMemberInfos(), withGenerification, getSisterClasses());
      updateSisterClassDisplay(l);
  }


  @Override
  protected String getDocCommentPanelName() {
    return RefactoringBundle.message("javadoc.for.abstracts");
  }

  @Override
  protected String getExtractedSuperNameNotSpecifiedMessage() {
    return RefactoringBundle.message("no.superclass.name.specified");
  }

  @Override
  protected boolean checkConflicts() {
    return myCallback.checkConflicts(this);
  }

  @Override
  protected int getDocCommentPolicySetting() {
    return JavaRefactoringSettings.getInstance().EXTRACT_SUPERCLASS_JAVADOC;
  }

  @Override
  protected void setDocCommentPolicySetting(int policy) {
    JavaRefactoringSettings.getInstance().EXTRACT_SUPERCLASS_JAVADOC = policy;
  }


  @Override
  protected ExtractSuperBaseProcessor createProcessor() {
    return new ExtractSuperClassMultiProcessor(myProject, getTargetDirectory(), getExtractedSuperName(),
                                          mySourceClass, ArrayUtil.toObjectArray(getSelectedMemberInfos(), MemberInfo.class), false,
                                          new DocCommentPolicy(getDocCommentPolicy()));
  }

  @Override
  protected String getHelpId() {
    return HelpID.EXTRACT_SUPERCLASS;
  }
}
