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
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 17.06.2002
 * Time: 16:35:43
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
//package com.intellij.refactoring.ui;
package com.intellij.refactoring.ui;

import com.intellij.psi.*;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.genUtils.Comparison;
import com.intellij.refactoring.genUtils.GenAnalysisUtils;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.RowIcon;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
public class CustomMemberSelectionTable extends MemberSelectionTable {


  protected final Boolean[] directAbstractPullupCheckBoxes;
  protected final Boolean[] canGenerifyCheckBoxes ;


  protected static final int CAN_MAKE_ABSTRACT_COLUMN = 3; // added Julien
  protected static String canMakeAbstractColumnHeader = "can make abstract";
  protected final ThreeValue[] canMakeAbstractCheckBoxes ;

  enum ThreeValue {
      YesGenerics {public String toString(){return "Yes (with generics)";}},
      YesPlain    {public String toString(){return "Yes (without generics)";}} , No}

  public CustomMemberSelectionTable(final List<MemberInfo> memberInfos, String abstractColumnHeader) {
    this(memberInfos, null, abstractColumnHeader);
  }

  public CustomMemberSelectionTable(final List<MemberInfo> memberInfos, MemberInfoModel<PsiMember, MemberInfo> memberInfoModel, String abstractColumnHeader) {
    super(memberInfos, memberInfoModel, abstractColumnHeader);
    final int size = memberInfos.size();
    directAbstractPullupCheckBoxes = new Boolean[size];        // j
    canGenerifyCheckBoxes = new Boolean[size];                 // j
    canMakeAbstractCheckBoxes = new ThreeValue[size];          // j
    GenTableModel t = new GenTableModel (this); //julien
    setModel(t);                                // julien    (this is problematic)

    //begin copy from AbstractMemberSelectionTable

    TableColumnModel model = getColumnModel();
    model.getColumn(DISPLAY_NAME_COLUMN).setCellRenderer(new CustomTableRenderer(this));
      { // to be done again (why? because the first time this code is invoked, it acts on the model affected by the first setModel() invocation)
        final int checkBoxWidth = new JCheckBox().getPreferredSize().width;
        model.getColumn(CHECKED_COLUMN).setMaxWidth(checkBoxWidth);
        model.getColumn(CHECKED_COLUMN).setMinWidth(checkBoxWidth);

      }
    if (myAbstractEnabled) { // FIXME to be done again (why?)
      int width = (int)(1.3 * getFontMetrics(getFont()).charsWidth(myAbstractColumnHeader.toCharArray(), 0, myAbstractColumnHeader.length()));
      model.getColumn(ABSTRACT_COLUMN).setMaxWidth(width);
      model.getColumn(ABSTRACT_COLUMN).setPreferredWidth(width);
    }

    //end copy
    int canMakeAbstractColumnWidth = (int)(1.3 * getFontMetrics(getFont()).charsWidth(canMakeAbstractColumnHeader.toCharArray(), 0, canMakeAbstractColumnHeader.length()));
    getColumnModel().getColumn(CAN_MAKE_ABSTRACT_COLUMN).setMaxWidth(canMakeAbstractColumnWidth);
    getColumnModel().getColumn(CAN_MAKE_ABSTRACT_COLUMN).setPreferredWidth(canMakeAbstractColumnWidth);



  }





  /* --- Compute and Fill properties --- */
  /* Need to check the boxes (arrays) and the memberInfo */

  void fillCanGenMember(MemberInfo member, PsiClass sup){

      if (GenAnalysisUtils.computeCanGenMember(member, sup)) {
              setCanGenerifyColumnValue(member, true);
      }
      else  {
              setCanGenerifyColumnValue(member, false);
              member.setChecked(false); //TODO : desactivate the checkbox
              member.setToAbstract(false);
      }
  }


  void fillDirectAbstractPullupField(MemberInfo member, PsiClass sup){
        final boolean result = computeCanDirectAbstractPullupMember(sup, member);
        setDirectAbstractPullupColumnValue(member, result);
  }


  // See also GenAnalysisUtils.hasMember
  private static boolean computeCanDirectAbstractPullupMember(PsiClass sup, MemberInfo mem) {
        PsiMember m = mem.getMember();


        // *) Methods
        if (m instanceof PsiMethod){
            return GenAnalysisUtils.checkSubClassesHaveSameMethod((PsiMethod) m, sup) ;
        }

        // *) Fields
        else if (m instanceof PsiField) { return false ; } // fIXME

        // *) Implements interface
        else  if (m instanceof PsiClass && Comparison.memberClassComesFromImplements(mem)) {

            final PsiClassType[] referencedTypes = mem.getSourceReferenceList().getReferencedTypes();
            assert(referencedTypes.length == 1) ;

            return GenAnalysisUtils.checkSubClassesImplementInterface(referencedTypes[0], sup) ;
        }

        // *) Other cases.
        else throw new IncorrectOperationException("this type of member not handled yet : " + m);

  }


  void fillCanMakeAbstractField(MemberInfo member) {
      //TODO warning : check the dependencies wetween the various informations.
      ThreeValue v = ThreeValue.No ;
      if (getDirectAbstractPullupColumnValue(member)) v = ThreeValue.YesPlain    ;
      else if  (getCanGenerifyColumnValue(member))    v = ThreeValue.YesGenerics ;
      setCanMakeAbstractColumnValue(member, v);
  }



  public void fillAllCanGenMembers(PsiClass sup){
      for (MemberInfo m : myMemberInfos) fillCanGenMember(m, sup);
  }

  public void fillAllDirectAbstractPullupFields(PsiClass sup){
      for (MemberInfo m : myMemberInfos) fillDirectAbstractPullupField(m, sup);
  }

  public void fillAllCanMakeAbstractFields(){
      for (MemberInfo m : myMemberInfos) fillCanMakeAbstractField(m);
  }



  //new
  int getRowForMember(MemberInfo memberInfo){
      int rowIndex = -1 ;
      int i = 0 ;
      int m = myMemberInfos.size();
      while (i<m && rowIndex == -1){
          if (myMemberInfos.get(i).equals(memberInfo)) rowIndex = i ;
          i++ ;
      }
      if (rowIndex == -1) throw new InternalError("member not found in table");
      return rowIndex ;
  }






  /* --- Getters for boxes --- */

  protected Boolean getDirectAbstractPullupColumnValue(MemberInfo memberInfo) {
     // rem : the generify flag is not inside memberInfo, but in the generifyCheckBoxes array.
     return directAbstractPullupCheckBoxes[getRowForMember(memberInfo)];
  }

  protected Boolean getCanGenerifyColumnValue(MemberInfo memberInfo) {
     // rem : the generify flag is not inside memberInfo, but in the generifyCheckBoxes array.
     return canGenerifyCheckBoxes[getRowForMember(memberInfo)];
  }

  protected ThreeValue getCanMakeAbstractColumnValue(MemberInfo memberInfo) {
     return canMakeAbstractCheckBoxes[getRowForMember(memberInfo)];
  }







  /* --- Setters for boxes --- */
  protected void setCanGenerifyColumnValue(MemberInfo memberInfo, boolean b) {
     canGenerifyCheckBoxes[getRowForMember(memberInfo)] = b ;
  }

  protected void setDirectAbstractPullupColumnValue(MemberInfo memberInfo, boolean b) {
     directAbstractPullupCheckBoxes[getRowForMember(memberInfo)] = b ;
  }


  /* this fills the "can make abstract" checkboxes, and also the "to abstract" checkboxes (indirectly) */
  protected void setCanMakeAbstractColumnValue(MemberInfo memberInfo, ThreeValue b) {
     canMakeAbstractCheckBoxes[getRowForMember(memberInfo)] = b ;
     if (b==ThreeValue.No)  memberInfo.setToAbstract(false);
        else   memberInfo.setToAbstract(true);
  }







  /* --- Test editability of columns --- */

    @Override
  protected boolean isAbstractColumnEditable(int rowIndex) {
        return false;
  }


  // Added Julien
  //private static class GenTableModel   extends AbstractTableModel {
  private static class GenTableModel   extends MyTableModel {
        //private final AbstractMemberSelectionTable<PsiMember, MemberInfo> myTable; // J
        private final CustomMemberSelectionTable myTableCopy ;


        //public MyTableModel(AbstractMemberSelectionTable<PsiMember, MemberInfo> table) {
        public GenTableModel(CustomMemberSelectionTable table) {
            super(table);      //j
            myTableCopy = table;
        }

        public int getColumnCount() {
            if (myTableCopy.myAbstractEnabled) {
                return (4);         //julien
            }
            else {                              // TODO : simplify that
                return (3); //julien
            }
        }


        public Class getColumnClass(int columnIndex) {
            if (columnIndex == CHECKED_COLUMN
                    || columnIndex == ABSTRACT_COLUMN

                    ) {     //julien
                return Boolean.class;
            }
            if (columnIndex == CAN_MAKE_ABSTRACT_COLUMN)   return ThreeValue.class ;
            return super.getColumnClass(columnIndex);
        }

        public Object getValueAt(int rowIndex, int columnIndex) {
            final MemberInfo memberInfo = myTableCopy.myMemberInfos.get(rowIndex);
            switch (columnIndex) {
                case CHECKED_COLUMN:
                    if (myTableCopy.myMemberInfoModel.isMemberEnabled(memberInfo)) {
                        return memberInfo.isChecked() ? Boolean.TRUE : Boolean.FALSE;
                    }
                    else {
                        return myTableCopy.myMemberInfoModel.isCheckedWhenDisabled(memberInfo);
                    }
                case ABSTRACT_COLUMN:
                {
                    return myTableCopy.getAbstractColumnValue(memberInfo);
                }
                case DISPLAY_NAME_COLUMN:
                    return memberInfo.getDisplayName();

                case CAN_MAKE_ABSTRACT_COLUMN:                                         //julien
                    return myTableCopy.getCanMakeAbstractColumnValue (memberInfo);
                default:
                    throw new RuntimeException("Incorrect column index");
            }
        }

        public String getColumnName(int column) {
            switch (column) {
                case CHECKED_COLUMN:
                    return " ";
                case ABSTRACT_COLUMN:
                    return myTableCopy.myAbstractColumnHeader;
                case DISPLAY_NAME_COLUMN:
                    return DISPLAY_NAME_COLUMN_HEADER;

                case CAN_MAKE_ABSTRACT_COLUMN:                //julien
                    return canMakeAbstractColumnHeader;
                default:
                    throw new RuntimeException("Incorrect column index: " + column);
            }
        }

        public boolean isCellEditable(int rowIndex, int columnIndex) {
            switch (columnIndex) {
                case CHECKED_COLUMN:
                    final MemberInfo m = myTableCopy.myMemberInfos.get(rowIndex);
                    return (myTableCopy.myMemberInfoModel.isMemberEnabled(m))
                            && myTableCopy.getCanMakeAbstractColumnValue(m) != ThreeValue.No; // todo: does that work?
                case ABSTRACT_COLUMN:
                    return myTableCopy.isAbstractColumnEditable(rowIndex);

                case CAN_MAKE_ABSTRACT_COLUMN:
                    return false;
            }
            return false;
        }


        public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
            if (columnIndex == CHECKED_COLUMN) {
                myTableCopy.myMemberInfos.get(rowIndex).setChecked(((Boolean)aValue).booleanValue());
            }
            else if (columnIndex == ABSTRACT_COLUMN) {
                myTableCopy.myMemberInfos.get(rowIndex).setToAbstract(((Boolean)aValue).booleanValue());
            }
            // TODO : need to add a case for "can make abstract" columns which is not editable?


            Collection<MemberInfo> changed = Collections.singletonList(myTableCopy.myMemberInfos.get(rowIndex));
            myTableCopy.fireMemberInfoChange(changed);
            fireTableDataChanged();
//      fireTableRowsUpdated(rowIndex, rowIndex);
        }
    }  //end of inner class


    //copy (I would prefer to use the one in AbstractMemberSelectionTable but it is private)
   private static class CustomTableRenderer extends ColoredTableCellRenderer {
    private final CustomMemberSelectionTable myTable;

    public CustomTableRenderer(CustomMemberSelectionTable table) {
      myTable = table;
    }

    public void customizeCellRenderer(JTable table, final Object value,
                                      boolean isSelected, boolean hasFocus, final int row, final int column) {

      final int modelColumn = myTable.convertColumnIndexToModel(column);
      final MemberInfo memberInfo = myTable.myMemberInfos.get(row);
      setToolTipText(myTable.myMemberInfoModel.getTooltipText(memberInfo));
      switch (modelColumn) {
        case DISPLAY_NAME_COLUMN:
          {
            Icon memberIcon = myTable.getMemberIcon(memberInfo, 0);
            Icon overrideIcon = myTable.getOverrideIcon(memberInfo);

            RowIcon icon = new RowIcon(3);
            icon.setIcon(memberIcon, MEMBER_ICON_POSITION);
            myTable.setVisibilityIcon(memberInfo, icon);
            icon.setIcon(overrideIcon, OVERRIDE_ICON_POSITION);
            setIcon(icon);
            break;
          }
        default:
          {
            setIcon(null);
          }
      }
      setIconOpaque(false);
      setOpaque(false);
      final boolean cellEditable = myTable.myMemberInfoModel.isMemberEnabled(memberInfo);
      setEnabled(cellEditable);

      if (value == null) return;
      final int problem = myTable.myMemberInfoModel.checkForProblems(memberInfo);
      Color c = null;
      if (problem == MemberInfoModel.ERROR) {
        c = JBColor.RED;
      }
      else if (problem == MemberInfoModel.WARNING && !isSelected) {
        c = JBColor.BLUE;
      }
      append((String)value, new SimpleTextAttributes(Font.PLAIN, c));
    }

  }


}
