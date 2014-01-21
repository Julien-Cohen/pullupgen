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
package com.intellij.refactoring.memberPullUp;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.*;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.util.classMembers.MemberInfo;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * Created by Max Medvedev on 10/4/13
 */
public interface PullUpGenHelper<T extends MemberInfoBase<? extends PsiMember>> extends PullUpHelper<T> {
  /* MIS EN COMMENTAIRE POUr VOIR SI LE INSTANCE SURCHARGE NE SUFFIRAIT PAS.
  LanguageExtension<PullUpGenHelperFactory> INSTANCE = new LanguageExtension<PullUpGenHelperFactory>("com.intellij.refactoring.pullUpHelperFactory"); // extension point, see plugin.xml
   */

 /* void encodeContextInfo(T info);

  void move(T info, PsiSubstitutor substitutor);

  void postProcessMember(PsiMember member);

  void setCorrectVisibility(T info);

  void moveFieldInitializations(LinkedHashSet<PsiField> movedFields);

  void updateUsage(PsiElement element);*/

 /* @Deprecated
  void initSisterClasses(MemberInfo info) throws GenAnalysisUtils.MemberNotImplemented, GenAnalysisUtils.AmbiguousOverloading; // (J)
*/

  Collection<PsiClass> getSisterClasses(MemberInfo[] infos) throws GenAnalysisUtils.MemberNotImplemented, GenAnalysisUtils.AmbiguousOverloading; // (J)
/*
  @Deprecated
  Collection<PsiClass> computeSisterClasses(MemberInfo info) throws GenAnalysisUtils.MemberNotImplemented, GenAnalysisUtils.AmbiguousOverloading; // (J)
  */
}
