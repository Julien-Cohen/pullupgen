/*
 * Copyright 2000-2011 JetBrains s.r.o.
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


import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoChange;
import com.intellij.refactoring.ui.*;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.classMembers.InterfaceContainmentVerifier;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.refactoring.util.classMembers.UsesAndInterfacesDependencyMemberInfoModel;
import com.intellij.ui.components.JBList;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

//import static com.intellij.refactoring.ui.AbstractMemberSelectionTable.MyTableModel;

/**
 * @author dsl
 * Date: 18.06.2002
 */
public class PullUpGenDialog extends RefactoringDialog {
  private final Callback myCallback;
  private CustomMemberSelectionPanel myMemberSelectionPanel;    // rem : initialized by createCenterPanel()
  private MyMemberInfoModel myMemberInfoModel;
  private final PsiClass myClass;
  private final List<PsiClass> mySuperClasses;
  private final MemberInfoStorage myMemberInfoStorage;
  private List<MemberInfo> myMemberInfos;
  private DocCommentPanel myJavaDocPanel;
  private JComboBox myClassCombo;
  private static final String PULL_UP_STATISTICS_KEY = "pull.up##";

  //Julien : to display the list of sister classes.

  JList mySisterClassList;

//  static CustomMemberInfo convert (MemberInfo i){}
    
  public interface Callback {
    boolean checkConflicts(PullUpGenDialog dialog);
  }

  public PullUpGenDialog(Project project, PsiClass aClass, List<PsiClass> superClasses, MemberInfoStorage memberInfoStorage, Callback callback) {
    super(project, true);
    myClass = aClass;
    mySuperClasses = superClasses;
    myMemberInfoStorage = memberInfoStorage;
    myMemberInfos = myMemberInfoStorage.getClassMemberInfos(aClass);
    myCallback = callback;


    setTitle(JavaPullUpGenHandler.REFACTORING_NAME);

    init();
  }

  @Nullable
  public PsiClass getSuperClass() {
    if (myClassCombo != null) {
      return (PsiClass) myClassCombo.getSelectedItem();
    }
    else {
      return null;
    }
  }

  public int getJavaDocPolicy() {
    return myJavaDocPanel.getPolicy();
  }

  public MemberInfo[] getSelectedMemberInfos() {
    ArrayList<MemberInfo> list = new ArrayList<MemberInfo>(myMemberInfos.size());
    for (MemberInfo info : myMemberInfos) {
      if (info.isChecked() && myMemberInfoModel.isMemberEnabled(info)) {
        list.add(info);
      }
    }
    return list.toArray(new MemberInfo[list.size()]);
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.memberPullUp.PullUpGenDialog";
  }

  InterfaceContainmentVerifier getContainmentVerifier() {
    return myInterfaceContainmentVerifier;
  }

  // The north panel contains the selector for the target super class (but not the member selection panel).
  // I (Julien) add the sister classes panel.
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

    myClassCombo = new JComboBox(mySuperClasses.toArray());  // cop (1)
    myClassCombo.setRenderer(new ClassCellRenderer(myClassCombo.getRenderer())); //cop (2)
    classComboLabel.setText(RefactoringBundle.message("pull.up.members.to", UsageViewUtil.getLongName(myClass)));   // cop (3)
    classComboLabel.setLabelFor(myClassCombo);  // cop (4)
    final PsiClass superClassPreselection = getSuperClassPreselection();
    int indexToSelect = 0;
    if (superClassPreselection != null) {
      indexToSelect = mySuperClasses.indexOf(superClassPreselection);
    }
    myClassCombo.setSelectedIndex(indexToSelect);
    myClassCombo.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          updateMemberInfo();
          if (myMemberSelectionPanel != null) {
            myMemberInfoModel.setSuperClass(getSuperClass());
            myMemberSelectionPanel.getTable().setMemberInfos(myMemberInfos);
            myMemberSelectionPanel.getTable().fireExternalDataChange();
          }
          setSisterClassDisplay(); // Julien
        }
      }
    });
    gbConstraints.gridy++;
    panel.add(myClassCombo, gbConstraints);


    // new (julien)    (based on the handling of myClassCombo)
    GridBagConstraints sisgbConstraints = (GridBagConstraints) gbConstraints.clone();
    final JLabel sisclassListLabel = new JLabel();
    sisgbConstraints.gridy=0;
    sisgbConstraints.gridx=1;
    panel.add(sisclassListLabel, sisgbConstraints);
    mySisterClassList = new JBList();
    mySisterClassList.setCellRenderer(new ClassCellRenderer(mySisterClassList.getCellRenderer()));  // from (2)
    sisclassListLabel.setText("Other classes to be modified:");
    sisclassListLabel.setLabelFor(mySisterClassList);
    sisgbConstraints.gridy++;   // increment the target position before adding
    panel.add(mySisterClassList, sisgbConstraints);
    setSisterClassDisplay(); // cannot be done musch earlier because myClassCombo must be initialized.
    return panel;
  }

    private void setSisterClassDisplay() {
        Collection<PsiClass> mySisterClasses = GenAnalysisUtils.findDirectSubClasses(getSuperClass()); // getSuperClass can be invoked only after myClassCombo has been initialized.
        mySisterClassList.setListData(mySisterClasses.toArray());
    }

    private PsiClass getSuperClassPreselection() {
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

  private void updateMemberInfo() {
    final PsiClass targetClass = (PsiClass) myClassCombo.getSelectedItem();
    myMemberInfos = myMemberInfoStorage.getIntermediateMemberInfosList(targetClass);
    /*Set duplicate = myMemberInfoStorage.getDuplicatedMemberInfos(targetClass);
    for (Iterator iterator = duplicate.getSectionsIterator(); getSectionsIterator.hasNext();) {
      ((MemberInfo) iterator.next()).setChecked(false);
    }*/
  }

  protected void doAction() {
    if (!myCallback.checkConflicts(this)) return;
    JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC = myJavaDocPanel.getPolicy();
    StatisticsManager
            .getInstance().incUseCount(new StatisticsInfo(PULL_UP_STATISTICS_KEY + myClass.getQualifiedName(), getSuperClass().getQualifiedName()));
    
    invokeRefactoring(new PullUpGenHelper(myClass, getSuperClass(), getSelectedMemberInfos(),
                                               new DocCommentPolicy(getJavaDocPolicy())));
    close(OK_EXIT_CODE);
  }




  // The center panel contains the member selection panel and the javadoc panel
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    myMemberSelectionPanel = new CustomMemberSelectionPanel(RefactoringBundle.message("members.to.be.pulled.up"), myMemberInfos, RefactoringBundle.message("make.abstract")); /* Julien : use custom panel for abstract column */
    myMemberSelectionPanel = new CustomMemberSelectionPanel(RefactoringBundle.message("members.to.be.pulled.up"), myMemberInfos, RefactoringBundle.message("make.abstract")); /* Julien : use custom panel for abstract column */
    myMemberInfoModel = new MyMemberInfoModel();
    myMemberInfoModel.memberInfoChanged(new MemberInfoChange<PsiMember, MemberInfo>(myMemberInfos));
    myMemberSelectionPanel.getTable().setMemberInfoModel(myMemberInfoModel);
    myMemberSelectionPanel.getTable().addMemberInfoChangeListener(myMemberInfoModel);
    panel.add(myMemberSelectionPanel, BorderLayout.CENTER);

    myJavaDocPanel = new DocCommentPanel(RefactoringBundle.message("javadoc.for.abstracts"));
    myJavaDocPanel.setPolicy(JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC);
    panel.add(myJavaDocPanel, BorderLayout.EAST);
    return panel;
  }

  private final InterfaceContainmentVerifier myInterfaceContainmentVerifier =
    new InterfaceContainmentVerifier() {
      public boolean checkedInterfacesContain(PsiMethod psiMethod) {
        return PullUpGenHelper.checkedInterfacesContain(myMemberInfos, psiMethod);
      }
    };




  private class MyMemberInfoModel extends UsesAndInterfacesDependencyMemberInfoModel {
    /* all @Override annotations in this internal class added by Julien */


    public MyMemberInfoModel() {
      super(myClass, getSuperClass(), false, myInterfaceContainmentVerifier);
    }

    @Override                       // what is it supposed to indicate? The first box is checkable? the method is pullupable? is it redundant?
                                    // TODO: Replace with a read on the "can make abstract" checkbox?
                                    // TODO : Do we compute several times the same result (fill "can make abstract" ...)
    public boolean isMemberEnabled(MemberInfo member) {
    /* rem Julien : indicates if a member can be pulled up to the selected superclass (not clear) */
      PsiClass currentSuperClass = getSuperClass();
      if(currentSuperClass == null) return true;
      if (myMemberInfoStorage.getDuplicatedMemberInfos(currentSuperClass).contains(member)) return false;
      if (myMemberInfoStorage.getExtending(currentSuperClass).contains(member.getMember())) return false;  /* rem Julien cannot do a pull up if the method is already in the selected superclass */
      if (!currentSuperClass.isInterface()) return true; /* rem Julien : if the selected superclass is a real class (not an interface, the above tests are sufficients, else (interface), continue with some tests */

      PsiElement element = member.getMember();
      if (element instanceof PsiClass && ((PsiClass) element).isInterface()) {
          /* rem Julien : can pull up an interface in an interface */
          return true;
      }
      if (element instanceof PsiField) {
        /* rem Julien : interfaces can contain static fields (must be final) */
        return ((PsiModifierListOwner) element).hasModifierProperty(PsiModifier.STATIC);
      }
      if (element instanceof PsiMethod) {
        /* rem Julien : don't pull up static methods in interfaces */
        return !((PsiModifierListOwner) element).hasModifierProperty(PsiModifier.STATIC);
      }
      return true; /* Rem Julien : can pull up instance method in interface */
    }


    @Override
    public boolean isAbstractEnabled(MemberInfo member) {
      PsiClass currentSuperClass = getSuperClass();
      if (currentSuperClass == null || !currentSuperClass.isInterface()) return true;
      return false;
    }


    @Override
    public boolean isAbstractWhenDisabled(MemberInfo member) {
      // rem Julien: if a method is pulled-up to an interface, it becomes abstract in the interface even if the 'abstract' chekbox is not checked (the checkbox is disabled).
      PsiClass currentSuperClass = getSuperClass();
      if(currentSuperClass == null) return false;
      if (currentSuperClass.isInterface()) {
        if (member.getMember() instanceof PsiMethod) {
          return true;
        }
      }
      return false;
    }


    public int checkForProblems(@NotNull MemberInfo member) {
      if (member.isChecked()) return OK;
      PsiClass currentSuperClass = getSuperClass();

      if (currentSuperClass != null && currentSuperClass.isInterface()) {
        PsiElement element = member.getMember();
        if (element instanceof PsiModifierListOwner) {
          if (((PsiModifierListOwner) element).hasModifierProperty(PsiModifier.STATIC)) {
            return super.checkForProblems(member);
          }
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

  void fillAllCanGenFields(){
        myMemberSelectionPanel.getTable().fillAllCanGenMembers(getSuperClass());
  }
  void fillAllDirectAbstractPullupFields(){
        myMemberSelectionPanel.getTable().fillAllDirectAbstractPullupFields(getSuperClass());
  }
  void fillAllWillGenFields(){
        myMemberSelectionPanel.getTable().fillAllWillGenFields();
  }
  void fillAllCanMakeAbstractFields(){
        myMemberSelectionPanel.getTable().fillAllCanMakeAbstractFields();
  }


}
