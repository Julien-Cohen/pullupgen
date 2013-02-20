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


import com.intellij.psi.*;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.ui.MemberSelectionTable;
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
//public class CustomMemberSelectionTable extends AbstractMemberSelectionTable<PsiMember, MemberInfo> {

  protected static final int DIRECT_ABSTRACT_PULLUP_COLUMN = 3; // added Julien
  protected static String directAbstractPullupColumnHeader = "can simply make abstract without Gen";
  protected final Boolean[] directAbstractPullupCheckBoxes;

  protected static final int CAN_GENERIFY_COLUMN = 4; // added Julien
  protected static String canGenerifyColumnHeader = "can Gen";
  protected final Boolean[] canGenerifyCheckBoxes ;

  protected static final int WILL_GENERIFY_COLUMN = 5; // added Julien
  protected static String willGenerifyColumnHeader = "will generify";
  protected final Boolean[] willGenerifyCheckBoxes ;


  public CustomMemberSelectionTable(final List<MemberInfo> memberInfos, String abstractColumnHeader) {
    this(memberInfos, null, abstractColumnHeader);
  }

  public CustomMemberSelectionTable(final List<MemberInfo> memberInfos, MemberInfoModel<PsiMember, MemberInfo> memberInfoModel, String abstractColumnHeader) {
    super(memberInfos, memberInfoModel, abstractColumnHeader);
    directAbstractPullupCheckBoxes = new Boolean[memberInfos.size()];        // j
    canGenerifyCheckBoxes = new Boolean[memberInfos.size()];        // j
    willGenerifyCheckBoxes = new Boolean[memberInfos.size()];        // j
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
    if (myAbstractEnabled) { // to be done again (why?)
      int width = (int)(1.3 * getFontMetrics(getFont()).charsWidth(myAbstractColumnHeader.toCharArray(), 0, myAbstractColumnHeader.length()));
      model.getColumn(ABSTRACT_COLUMN).setMaxWidth(width);
      model.getColumn(ABSTRACT_COLUMN).setPreferredWidth(width);
    }
    //end copy

    //new
    int generifyColumnWidth = (int)(1.3 * getFontMetrics(getFont()).charsWidth(directAbstractPullupColumnHeader.toCharArray(), 0, directAbstractPullupColumnHeader.length()));
    getColumnModel().getColumn(DIRECT_ABSTRACT_PULLUP_COLUMN).setMaxWidth(generifyColumnWidth);
    getColumnModel().getColumn(DIRECT_ABSTRACT_PULLUP_COLUMN).setPreferredWidth(generifyColumnWidth);
    int canGenerifyColumnWidth = (int)(1.3 * getFontMetrics(getFont()).charsWidth(canGenerifyColumnHeader.toCharArray(), 0, canGenerifyColumnHeader.length()));
    getColumnModel().getColumn(CAN_GENERIFY_COLUMN).setMaxWidth(canGenerifyColumnWidth);
    getColumnModel().getColumn(CAN_GENERIFY_COLUMN).setPreferredWidth(canGenerifyColumnWidth);
    int willGenerifyColumnWidth = (int)(1.3 * getFontMetrics(getFont()).charsWidth(willGenerifyColumnHeader.toCharArray(), 0, willGenerifyColumnHeader.length()));
    getColumnModel().getColumn(WILL_GENERIFY_COLUMN).setMaxWidth(willGenerifyColumnWidth);
    getColumnModel().getColumn(WILL_GENERIFY_COLUMN).setPreferredWidth(willGenerifyColumnWidth);

    System.out.println("creation Custom Table (fin)");


  }

  void fillCanGenMember(MemberInfo member, PsiClass sup){
          PsiMember m = member.getMember();


          if(!(m instanceof PsiMethod)) {
                System.out.println("cannot generify fields and classes yet, setting cangen(" + m + ") to false.");
                setCanGenerifyColumnValue(member, false);
                member.setChecked(false); //TODO : desactivate the checkbox
                member.setToAbstract(false);
          }
          else{
            Collection <PsiClass> compatClasses = GenAnalysisUtils.canGenMethod(member, sup);  // TODO : use that collection

            System.out.println("for member " + m + " for superclass " + sup + " : " + compatClasses); // debug

            if (compatClasses != null) {
               setCanGenerifyColumnValue(member, true);
               member.setToAbstract(true); //Todo : move this line to a better place
            }
             else  {
               setCanGenerifyColumnValue(member, false);
               member.setChecked(false); //TODO : desactivate the checkbox
               member.setToAbstract(false); //Todo : move this line to a better place
            }
          }
  }


  void fillDirectAbstractPullupField(MemberInfo member, PsiClass s){
        final PsiMember m = member.getMember();
        if (m instanceof PsiMethod){
            PsiMethod method = (PsiMethod) m ; // cannot fail

          if (GenAnalysisUtils.checkSubClassesHaveSameMethod(method, s)) setDirectAbstractPullupColumnValue(member, true);
          else setDirectAbstractPullupColumnValue(member, false);

        }
        else setDirectAbstractPullupColumnValue(member, false);
  }

  // warning, need to initalize needGen and canGen fields before invoking that method
  void fillWillGenField(MemberInfo member) {
        boolean b = !getDirectAbstractPullupColumnValue(member) && getCanGenerifyColumnValue(member);
        setWillGenerifyColumnValue(member,b );
    }


  void fillCanGenMembers(PsiClass sup){
      for (MemberInfo m : myMemberInfos) {
             fillCanGenMember(m, sup);
      }
  }
  void fillDirectAbstractPullupFields(PsiClass s){
      for (MemberInfo m : myMemberInfos) fillDirectAbstractPullupField(m, s);
  }
  void fillWillGenFields(){
      for (MemberInfo m : myMemberInfos) fillWillGenField(m);
  }


  @Deprecated
  void setToAbstractAll(){
      for (MemberInfo m : myMemberInfos) m.setToAbstract(true);
  }


  //new
  int getRowForMember(MemberInfo memberInfo){
      int rowIndex = -1 ;
      int i = 0 ;
      int m = myMemberInfos.size();
      while (i<m && rowIndex == -1)

      { if (myMemberInfos.get(i).equals(memberInfo)) rowIndex = i ;
          i++ ;}
      if (rowIndex == -1) throw new InternalError("member not found in table");
      return rowIndex ;
  }

  //new
  protected Boolean getDirectAbstractPullupColumnValue(MemberInfo memberInfo) {
     // rem : the generify flag is not inside memberInfo, but in the generifyCheckBoxes array.
     return directAbstractPullupCheckBoxes[getRowForMember(memberInfo)];
  }

  protected Boolean getCanGenerifyColumnValue(MemberInfo memberInfo) {
     // rem : the generify flag is not inside memberInfo, but in the generifyCheckBoxes array.
     return canGenerifyCheckBoxes[getRowForMember(memberInfo)];
  }
  protected Boolean willGenerifyColumnValue(MemberInfo memberInfo) {

     return willGenerifyCheckBoxes[getRowForMember(memberInfo)]; //todo : getRowForMember called too often
  }
  //new
  protected void setCanGenerifyColumnValue(MemberInfo memberInfo, boolean b) {

     canGenerifyCheckBoxes[getRowForMember(memberInfo)] = b ;
  }
  protected void setDirectAbstractPullupColumnValue(MemberInfo memberInfo, boolean b) {

     directAbstractPullupCheckBoxes[getRowForMember(memberInfo)] = b ;
  }

    protected void setWillGenerifyColumnValue(MemberInfo memberInfo, boolean b) {

     willGenerifyCheckBoxes[getRowForMember(memberInfo)] = b ;
  }

  // new
  protected boolean isDirectAbstractPullupColumnEditable(int rowIndex) {
    return false ; // TODO : implement this
  }
  // new
  protected boolean isCanGenerifyColumnEditable(int rowIndex) {
    return false ; // TODO : implement this
  }


  protected boolean isWillGenerifyColumnEditable(int rowIndex) {
    return false ; // TODO : implement this
  }

  @Override    // new
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
            System.out.println("creation Custom MyTableModel (fin)");
        }

        public int getColumnCount() {
            System.out.println("get column count (MyTableModel)");
            if (myTableCopy.myAbstractEnabled) {
                return (6);         //julien
            }
            else {
                return (5); //julien
            }
        }

        /*
    public int getRowCount() {
      return myTable.myMemberInfos.size();
    }     */

        public Class getColumnClass(int columnIndex) {
            if (columnIndex == CHECKED_COLUMN || columnIndex == ABSTRACT_COLUMN || columnIndex == DIRECT_ABSTRACT_PULLUP_COLUMN || columnIndex == CAN_GENERIFY_COLUMN|| columnIndex == WILL_GENERIFY_COLUMN) {     //julien
                return Boolean.class;
            }
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

                case DIRECT_ABSTRACT_PULLUP_COLUMN:                                  //julien
                    return myTableCopy.getDirectAbstractPullupColumnValue(memberInfo); //julien
                case CAN_GENERIFY_COLUMN:                                  //julien
                    return myTableCopy.getCanGenerifyColumnValue(memberInfo); //julien
                case WILL_GENERIFY_COLUMN:                                  //julien
                    return myTableCopy.willGenerifyColumnValue(memberInfo); //julien
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
                case DIRECT_ABSTRACT_PULLUP_COLUMN:                   //julien
                    return directAbstractPullupColumnHeader;                   //julien
                case CAN_GENERIFY_COLUMN:                   //julien
                    return canGenerifyColumnHeader;                   //julien
                case WILL_GENERIFY_COLUMN:                   //julien
                    return willGenerifyColumnHeader;                   //julien
                default:
                    throw new RuntimeException("Incorrect column index");
            }
        }

        public boolean isCellEditable(int rowIndex, int columnIndex) {
            switch (columnIndex) {
                case CHECKED_COLUMN:
                    return myTableCopy.myMemberInfoModel.isMemberEnabled(myTableCopy.myMemberInfos.get(rowIndex));
                case ABSTRACT_COLUMN:
                    return myTableCopy.isAbstractColumnEditable(rowIndex);
                case DIRECT_ABSTRACT_PULLUP_COLUMN:
                    return myTableCopy.isDirectAbstractPullupColumnEditable(rowIndex);
                case CAN_GENERIFY_COLUMN:
                    return myTableCopy.isCanGenerifyColumnEditable(rowIndex);
                case WILL_GENERIFY_COLUMN:
                    return myTableCopy.isWillGenerifyColumnEditable(rowIndex);
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
            else if (columnIndex == DIRECT_ABSTRACT_PULLUP_COLUMN){
                myTableCopy.directAbstractPullupCheckBoxes[rowIndex] = ((Boolean)aValue).booleanValue();

            }
            else if (columnIndex == CAN_GENERIFY_COLUMN){
                myTableCopy.canGenerifyCheckBoxes[rowIndex] = ((Boolean)aValue).booleanValue();

            }
            else if (columnIndex == WILL_GENERIFY_COLUMN){
                myTableCopy.willGenerifyCheckBoxes[rowIndex] = ((Boolean)aValue).booleanValue();

            }

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
