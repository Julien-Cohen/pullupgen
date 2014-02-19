package com.intellij.refactoring.ui;

import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Created by cohen-j on 19/02/14.
 */
public class ShortClassCellRenderer extends ClassCellRenderer{

    public ShortClassCellRenderer(ListCellRenderer original) {
    super(original);

  }

  @Override
  public void customize(JList list, PsiClass aClass, int index, boolean selected, boolean hasFocus) {
    if (aClass != null) {
      setText(getClassText(aClass));

      int flags = Iconable.ICON_FLAG_VISIBILITY;
      flags |= Iconable.ICON_FLAG_READ_STATUS;
      Icon icon = aClass.getIcon(flags);
      if (icon != null) {
        setIcon(icon);
      }
    }
  }

  protected static String getClassText(@NotNull PsiClass aClass) {

    String name = aClass.getName();
    if (name != null) {
      return name;
    }
    else return RefactoringBundle.message("anonymous.class.text");
  }

}
