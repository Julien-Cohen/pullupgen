package com.intellij.refactoring.genUtils;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
/**
 * Copyright 2012, 2016 Université de Nantes
 * Contributor : Julien Cohen (Ascola team, Univ. Nantes)
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


public class MemberNotImplemented extends Exception {
    PsiClass c;
    PsiMember m;

    MemberNotImplemented(PsiMember _m, PsiClass _c) {
        m = _m;
        c = _c;
    }
}
