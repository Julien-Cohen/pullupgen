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
 * Date: 18.06.2002
 * Time: 13:19:44
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */

package com.intellij.refactoring.ui;

import com.intellij.psi.PsiMember;
import com.intellij.refactoring.ui.AbstractMemberSelectionTable;

import com.intellij.refactoring.util.classMembers.MemberInfo;

import java.util.List;

public class CustomMemberSelectionPanel extends MemberSelectionPanelBase<PsiMember, MemberInfo,  AbstractMemberSelectionTable<PsiMember, MemberInfo>> /* CustomMemberSelectionTable> */ {
  //private final CustomMemberSelectionTable myTable; // TODO (J) : check that

  //MemberSelectionPanel p;   // j: for reference, can be deleted

  /**
   * @param title if title contains 'm' - it would look and feel as mnemonic
   * @param table
   */
  public CustomMemberSelectionPanel(String title, CustomMemberSelectionTable table) {

    super(title,table);
  }
  /*public CustomMemberSelectionPanel(String title, List<MemberInfo> infos, String columnHeader) {

    super(title, new CustomMemberSelectionTable(infos, columnHeader));
    //System.out.println("creation Custom Panel (debut)");
    //setLayout(new BorderLayout());


    //JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(table);
    //add(SeparatorFactory.createSeparator(title, table), BorderLayout.NORTH);
    //add(scrollPane, BorderLayout.CENTER);
    //System.out.println("creation Custom Panel (fin)");
  }*/


  protected CustomMemberSelectionTable createMemberSelectionTable(List<MemberInfo> memberInfo, String abstractColumnHeader) {
    return new CustomMemberSelectionTable(memberInfo, abstractColumnHeader);
  }

  @Override
  public CustomMemberSelectionTable getTable() {
      AbstractMemberSelectionTable<PsiMember, MemberInfo> table = super.getTable();
      if (table instanceof CustomMemberSelectionTable)
          return (CustomMemberSelectionTable) table;
      else throw new Error ("normal table found instead of CustomTable");
  }
}
