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

/* Up-to-date w.r.t. commit on 10 Apr 2012. */
/* Up-to-date w.r.t. commit on 14 Feb 2013. */
/* Up-to-date w.r.t. commit on 30 Aug 2013. */
/* Up-to-date w.r.t. commit on 14 Jul 2013. (not completely) : createNorthPanel and createCenterPanel not moved */
/* Up-to-date w.r.t. commit on 24 Sep 2013. */
/* Up-to-date w.r.t. commit on 1  Oct 2013. */
/* Up-to-date w.r.t. commit on 23 Oct 2013. */
/* Up-to-date w.r.t. commit on 7  Feb 2014. */
/* Up-to-date w.r.t. commit on 18 Jul 2014. */
/* Up-to-date w.r.t. commit on 5  Dec 2014. */
package com.intellij.refactoring.memberPullUp;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.classMembers.MemberInfoChange;
import com.intellij.refactoring.ui.ClassCellRenderer;
import com.intellij.refactoring.ui.DocCommentPanel;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.classMembers.InterfaceContainmentVerifier;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.refactoring.util.classMembers.UsesAndInterfacesDependencyMemberInfoModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

import com.intellij.ui.SeparatorFactory;
import com.intellij.ui.components.JBList;
import com.intellij.usageView.UsageViewUtil;

import java.util.Collection;
import com.intellij.refactoring.genUtils.GenAnalysisUtils;
import com.intellij.refactoring.ui.CustomMemberSelectionPanel;
import com.intellij.refactoring.ui.CustomMemberSelectionTable;
import com.intellij.refactoring.ui.ShortClassCellRenderer;
/**
 * @author dsl
 * Date: 18.06.2002
 */
public class PullUpGenDialog extends PullUpDialogBase<MemberInfoStorage, MemberInfo, PsiMember, PsiClass> {
  private final Callback myCallback;
  private DocCommentPanel myJavaDocPanel;
  CustomMemberSelectionPanel myMemberSelectionPanel;    // rem : initialized by createCenterPanel()  // TODO : check that for the type (J) // WARNING : hides the inherited myMemberSelectionPanel
  JComboBox mySecondClassCombo; // FIXME : temporary, just to hide the private combo


  private final InterfaceContainmentVerifier myInterfaceContainmentVerifier = new InterfaceContainmentVerifier() {
    public boolean checkedInterfacesContain(PsiMethod psiMethod) {
      return PullUpGenProcessor.checkedInterfacesContain(myMemberInfos, psiMethod); // TODO : check that (J)
    }
  };

  private static final String PULL_UP_STATISTICS_KEY = "pull.up##";

  //Julien : to display the list of sister classes.
  JList mySisterClassList;

  public interface Callback {
    boolean checkConflicts(PullUpGenDialog dialog);
  }

  public PullUpGenDialog(Project project, PsiClass aClass, List<PsiClass> superClasses, MemberInfoStorage memberInfoStorage, Callback callback) {
    super(project, aClass, superClasses, memberInfoStorage, JavaPullUpGenHandler.REFACTORING_NAME); // TODO : check that (J)
    myCallback = callback;

    init();

    // Julien
    fillAllAnalyses();
  }


  // removed in community by commit on 14 Jul 2013 but I keep it to branch on the second class combo.
  @Override
  @NotNull
  public PsiClass getSuperClass() {
    if (mySecondClassCombo != null) {
      return (PsiClass) mySecondClassCombo.getSelectedItem(); // J
    }
    else {
      return null; // FIXME
    }
  }


  public int getJavaDocPolicy() {
    return myJavaDocPanel.getPolicy();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.memberPullUp.PullUpGenDialog";
  }

  InterfaceContainmentVerifier getContainmentVerifier() {
    return myInterfaceContainmentVerifier;
  }

  // The north panel contains the selector for the target super class (but not the member selection panel).
  // I (Julien) add the sister classes panel.

  // Removed in community commit 14 Jul 2013 (moved to PullUpDialogBase).
  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel();


    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 0, 4, 8); // external paddings
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridy = 0;  // index of te first element to be displayed
    gbConstraints.gridwidth = 1 ;//GridBagConstraints.REMAINDER; (CHANGED JULIEN)
    gbConstraints.fill = GridBagConstraints.NONE ;// GridBagConstraints.BOTH;(CHANGED JULIEN)
    gbConstraints.anchor = GridBagConstraints.WEST;
    final JLabel classComboLabel = new JLabel();
    panel.add(classComboLabel, gbConstraints);

    mySecondClassCombo = new JComboBox(mySuperClasses.toArray());  // cop (1)
    mySecondClassCombo.setRenderer(new ClassCellRenderer(mySecondClassCombo.getRenderer())); //cop (2)
    classComboLabel.setText(RefactoringBundle.message("pull.up.members.to", UsageViewUtil.getLongName(myClass)));   // cop (3)
    classComboLabel.setLabelFor(mySecondClassCombo);  // cop (4)
    final PsiClass superClassPreselection = getPreselection();
    int indexToSelect = 0;
    if (superClassPreselection != null) {
      indexToSelect = mySuperClasses.indexOf(superClassPreselection);
    }
    mySecondClassCombo.setSelectedIndex(indexToSelect);
    mySecondClassCombo.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                updateMemberInfo();
                if (myMemberSelectionPanel != null) {
                    ((MyMemberInfoModel) myMemberInfoModel).setSuperClass(getSuperClass());
                    getCustomTable().setMemberInfos(myMemberInfos);
                    getCustomTable().fireExternalDataChange();
                }

                // Julien: Update sister classes and analyses
                setSisterClassDisplay(); // Julien
                fillAllAnalyses();       // Julien
                myMemberSelectionPanel.repaint();

            }
        }
    });
    gbConstraints.gridy++;
    panel.add(mySecondClassCombo, gbConstraints);

    // new (Julien)
    createSisterPanel(panel, gbConstraints);

    return panel;
  }

    // TODO : replace the sister panel creation

  @Override
  protected void initClassCombo(JComboBox classCombo) {
    classCombo.setRenderer(new ClassCellRenderer(classCombo.getRenderer()));
    classCombo.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          if (myMemberSelectionPanel != null) {
            ((MyMemberInfoModel)myMemberInfoModel).setSuperClass(getSuperClass());
            getCustomTable().setMemberInfos(myMemberInfos);
            getCustomTable().fireExternalDataChange();
          }
          // Julien: Update sister classes and analyses
          setSisterClassDisplay(); // Julien TODO : check that (already in createNorthPanel)
          fillAllAnalyses();       // Julien TODO : check that (already in createNorthPanel)
        }
      }
    });
  }

    // new (julien)    (based on the handling of myClassCombo)
    protected void createSisterPanel(JPanel panel, GridBagConstraints gbConstraints) {
        GridBagConstraints sisgbConstraints = (GridBagConstraints) gbConstraints.clone();
        sisgbConstraints.gridy=0;
        sisgbConstraints.gridx=1;
        panel.add(SeparatorFactory.createSeparator("Other classes to be modified", mySisterClassList), sisgbConstraints);
        mySisterClassList = new JBList();
        mySisterClassList.setCellRenderer(new ShortClassCellRenderer(mySisterClassList.getCellRenderer()));  // from (2)
        sisgbConstraints.gridy++;   // increment the target position before adding
        panel.add(mySisterClassList, sisgbConstraints);
        setSisterClassDisplay(); // cannot be done much earlier because myClassCombo must be initialized.
    }

    private void setSisterClassDisplay() {
        Collection<PsiClass> mySisterClasses = GenAnalysisUtils.findDirectSubClassesInDirectory(getSuperClass()); // getSuperClass can be invoked only after myClassCombo has been initialized.
        mySisterClassList.setListData(mySisterClasses.toArray());
    }


    protected PsiClass getPreselection() {
    PsiClass preselection = RefactoringHierarchyUtil.getNearestBaseClass(myClass, false);

    final String statKey = PULL_UP_STATISTICS_KEY + myClass.getQualifiedName();
    for (StatisticsInfo info : StatisticsManager.getInstance().getAllValues(statKey)) {
      final String superClassName = info.getValue();
      PsiClass superClass = null;
      for (PsiClass aClass : mySuperClasses) {
        if (Comparing.strEqual(superClassName, aClass.getQualifiedName())) {
          superClass = aClass;
          break;
        }
      }
      if (superClass != null && StatisticsManager.getInstance().getUseCount(info) > 0) {
        preselection = superClass;
        break;
      }
    }
    return preselection;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.MEMBERS_PULL_UP);
  }

  protected void doAction() {
    if (!myCallback.checkConflicts(this)) return;
    JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC = myJavaDocPanel.getPolicy();
    final PsiClass superClass = getSuperClass();
    String name = superClass.getQualifiedName();
    if (name != null) {
      StatisticsManager
        .getInstance().incUseCount(new StatisticsInfo(PULL_UP_STATISTICS_KEY + myClass.getQualifiedName(), name));
    }

    List<MemberInfo> infos = getSelectedMemberInfos();
    invokeRefactoring(new PullUpGenProcessor(myClass, superClass, infos.toArray(new MemberInfo[infos.size()]),
                                               new DocCommentPolicy(getJavaDocPolicy())));
    close(OK_EXIT_CODE);
  }



  // FIXME : doublon
  // moved in PullUpDialogBase in community commit on 14 Jul 2013
  // The center panel contains the member selection panel and the javadoc panel
  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());

      final CustomMemberSelectionTable customTable = createMemberSelectionTable(myMemberInfos);
      final CustomMemberSelectionPanel customMemberSelectionPanel =
            new CustomMemberSelectionPanel(RefactoringBundle.message("members.to.be.pulled.up"), customTable /*, RefactoringBundle.message("make.abstract") */);

    myMemberSelectionPanel = customMemberSelectionPanel; // Julien : use custom panel for abstract column
    myMemberInfoModel = new MyMemberInfoModel();
    myMemberInfoModel.memberInfoChanged(new MemberInfoChange<PsiMember, MemberInfo>(myMemberInfos));
    myMemberSelectionPanel.getTable().setMemberInfoModel(myMemberInfoModel);
    myMemberSelectionPanel.getTable().addMemberInfoChangeListener(myMemberInfoModel);
    panel.add(myMemberSelectionPanel, BorderLayout.CENTER);

    addCustomElementsToCentralPanel(panel);
    return panel;
  }



  protected CustomMemberSelectionTable getCustomTable(){ // FIXME

      return myMemberSelectionPanel.getTable();
  }

  @Override
  protected void addCustomElementsToCentralPanel(JPanel panel) { // TODO (J) : use that kind of method to add the sister panel

    myJavaDocPanel = new DocCommentPanel(RefactoringBundle.message("javadoc.for.abstracts"));
    myJavaDocPanel.setPolicy(JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC);
    boolean hasJavadoc = false;
    for (MemberInfo info : myMemberInfos) {
      final PsiMember member = info.getMember();
      if (myMemberInfoModel.isAbstractEnabled(info)) {
        info.setToAbstract(myMemberInfoModel.isAbstractWhenDisabled(info));
        if (!hasJavadoc &&
            member instanceof PsiDocCommentOwner &&
            ((PsiDocCommentOwner)member).getDocComment() != null) {
          hasJavadoc = true;
        }
      }
    }
    UIUtil.setEnabled(myJavaDocPanel, hasJavadoc, true);
    panel.add(myJavaDocPanel, BorderLayout.EAST);
  }

  @Override
  protected CustomMemberSelectionTable createMemberSelectionTable(List<MemberInfo> infos) { //J
   return new CustomMemberSelectionTable(infos, RefactoringBundle.message("make.abstract"));
  }

  @Override
  protected MemberInfoModel<PsiMember, MemberInfo> createMemberInfoModel() {
    return new MyMemberInfoModel();
  }

  private class MyMemberInfoModel extends UsesAndInterfacesDependencyMemberInfoModel<PsiMember, MemberInfo> {
    public MyMemberInfoModel() {
      super(myClass, getSuperClass(), false, myInterfaceContainmentVerifier);
    }


                                    // what is it supposed to indicate? The first box is checkable? the method is pullupable? is it redundant?
                                    // TODO: Replace with a read on the "can make abstract" checkbox?
                                    // TODO : Do we compute several times the same result (fill "can make abstract" ...)
    @Override
    public boolean isMemberEnabled(MemberInfo member) {
    /* rem Julien : indicates if a member can be pulled up to the selected superclass (not clear) */
      final PsiClass currentSuperClass = getSuperClass();
      if(currentSuperClass == null) return true;
      if (myMemberInfoStorage.getDuplicatedMemberInfos(currentSuperClass).contains(member)) return false;
      if (myMemberInfoStorage.getExtending(currentSuperClass).contains(member.getMember())) return false;  /* rem Julien cannot do a pull up if the method is already in the selected superclass */
      final boolean isInterface = currentSuperClass.isInterface();
      if (!isInterface) return true; /* rem Julien : if the selected superclass is a real class (not an interface, the above tests are sufficients, else (interface), continue with some tests */

      PsiElement element = member.getMember();
      if (element instanceof PsiClass && ((PsiClass) element).isInterface()) return true;
          // rem Julien : can pull up an interface in an interface

      if (element instanceof PsiField) {
        /* rem Julien : interfaces can contain static fields (must be final) */
        return ((PsiModifierListOwner) element).hasModifierProperty(PsiModifier.STATIC);
      }
      if (element instanceof PsiMethod) {
        /* rem Julien : don't pull up static methods in interfaces */
        final PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(currentSuperClass, myClass, PsiSubstitutor.EMPTY);
        final MethodSignature signature = ((PsiMethod) element).getSignature(superSubstitutor);
        final PsiMethod superClassMethod = MethodSignatureUtil.findMethodBySignature(currentSuperClass, signature, false);
        if (superClassMethod != null && !PsiUtil.isLanguageLevel8OrHigher(currentSuperClass)) return false;
        return !((PsiModifierListOwner) element).hasModifierProperty(PsiModifier.STATIC) || PsiUtil.isLanguageLevel8OrHigher(currentSuperClass);
      }
      return true; /* Rem Julien : can pull up instance method in interface */
    }

    @Override
    public boolean isAbstractEnabled(MemberInfo member) {
      PsiClass currentSuperClass = getSuperClass();
      if (currentSuperClass == null || !currentSuperClass.isInterface()) return true;
      if (PsiUtil.isLanguageLevel8OrHigher(currentSuperClass)) {
        return true;
      }
      return false;
    }

    @Override
    public boolean isAbstractWhenDisabled(MemberInfo member) {
      // rem Julien: if a method is pulled-up to an interface, it becomes abstract in the interface even if the 'abstract' chekbox is not checked (the checkbox is disabled).
      PsiClass currentSuperClass = getSuperClass();
      if(currentSuperClass == null) return false;
      if (currentSuperClass.isInterface()) {
        final PsiMember psiMember = member.getMember();
        if (psiMember instanceof PsiMethod) {
          return !psiMember.hasModifierProperty(PsiModifier.STATIC);
        }
      }
      return false;
    }

    @Override
    public int checkForProblems(@NotNull MemberInfo member) {
      if (member.isChecked()) return OK;
      PsiClass currentSuperClass = getSuperClass();

      if (currentSuperClass != null && currentSuperClass.isInterface()) {
        PsiMember element = member.getMember();
        if (element.hasModifierProperty(PsiModifier.STATIC)) {
          return super.checkForProblems(member);
        }
        return OK;
      }
      else {
        return super.checkForProblems(member);
      }
    }

    @Override
    public Boolean isFixedAbstract(MemberInfo member) {
      return Boolean.TRUE;

      /* rem Julien : according to MemberSelectionTable.isAbstractColumnEditable(...) (is it the correct reference?),
       to be non-editable the member must have the abstract modifier AND isFixedAbstract(...) must return TRUE
       or FALSE (not null).
       This expresses the fact that an abstract method cannot override a non abstract method. */
    }
  }

    void fillAllAnalyses() {
        CustomMemberSelectionTable table = getCustomTable();
        table.fillAllCanGenMembers(getSuperClass());
        table.fillAllDirectAbstractPullupFields(getSuperClass());
        table.fillAllCanMakeAbstractFields(); // J  (to be done in that order because of data dependancy)
  }


}
