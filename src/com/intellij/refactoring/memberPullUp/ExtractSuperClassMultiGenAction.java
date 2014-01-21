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

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.ExtractSuperActionBase;
import org.jetbrains.annotations.NotNull;


public class ExtractSuperClassMultiGenAction extends ExtractSuperActionBase {

    public ExtractSuperClassMultiGenAction() {
    setInjectedContext(true);
  }

  @Override
  protected RefactoringActionHandler getRefactoringHandler(@NotNull RefactoringSupportProvider supportProvider) {
      ExtractSuperclassMultiHandler h = new ExtractSuperclassMultiHandler();
      h.myUseGenericUnification = true ;   // this asks for generic unification: methods with the same name but different types can be considered to override a same method (with a generic type).
      return h;
  }

}
