// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.impl.actions.ShowErrorDescriptionAction;
import com.intellij.codeInsight.hint.LineTooltipRenderer;
import com.intellij.codeInsight.hint.TooltipLinkHandlerEP;
import com.intellij.codeInspection.ui.InspectionNodeInfo;
import consulo.codeEditor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import consulo.application.ui.awt.ColorUtil;
import consulo.ui.ex.JBColor;
import consulo.application.ui.awt.Html;
import consulo.application.ui.awt.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.List;

class DaemonTooltipRenderer extends LineTooltipRenderer {
  @NonNls
  protected static final String END_MARKER = "<!-- end marker -->";


  DaemonTooltipRenderer(final String text, Object[] comparable) {
    super(text, comparable);
  }

  DaemonTooltipRenderer(final String text, final int width, Object[] comparable) {
    super(text, width, comparable);
  }

  @Override
  protected void onHide(@Nonnull JComponent contentComponent) {
    ShowErrorDescriptionAction.rememberCurrentWidth(contentComponent.getWidth());
  }

  @Nonnull
  @Override
  protected String dressDescription(@Nonnull final Editor editor, @Nonnull String tooltipText, boolean expand) {
    if (!expand) {
      return super.dressDescription(editor, tooltipText, false);
    }

    final List<String> problems = getProblems(tooltipText);
    StringBuilder text = new StringBuilder();
    for (String problem : problems) {
      final String ref = getLinkRef(problem);
      if (ref != null) {
        String description = TooltipLinkHandlerEP.getDescription(ref, editor);
        if (description != null) {
          description = InspectionNodeInfo.stripUIRefsFromInspectionDescription(UIUtil.getHtmlBody(new Html(description).setKeepFont(true)));
          text.append(getHtmlForProblemWithLink(problem)).append(END_MARKER).append("<p>").append("<span style=\"color:").append(ColorUtil.toHex(getDescriptionTitleColor())).append("\">")
                  .append(TooltipLinkHandlerEP.getDescriptionTitle(ref, editor)).append(":</span>").append(description).append(UIUtil.BORDER_LINE);
        }
      }
      else {
        text.append(UIUtil.getHtmlBody(new Html(problem).setKeepFont(true))).append(UIUtil.BORDER_LINE);
      }
    }
    if (text.length() > 0) { //otherwise do not change anything
      return XmlStringUtil.wrapInHtml(StringUtil.trimEnd(text.toString(), UIUtil.BORDER_LINE));
    }
    return super.dressDescription(editor, tooltipText, true);
  }

  @Nonnull
  protected List<String> getProblems(@Nonnull String tooltipText) {
    return StringUtil.split(UIUtil.getHtmlBody(new Html(tooltipText).setKeepFont(true)), UIUtil.BORDER_LINE);
  }

  @Nonnull
  protected String getHtmlForProblemWithLink(@Nonnull String problem) {
    Html html = new Html(problem).setKeepFont(true);
    return UIUtil.getHtmlBody(html).replace(DaemonBundle.message("inspection.extended.description"), DaemonBundle.message("inspection.collapse.description"));
  }

  @Nullable
  protected static String getLinkRef(@NonNls String text) {
    final String linkWithRef = "<a href=\"";
    final int linkStartIdx = text.indexOf(linkWithRef);
    if (linkStartIdx >= 0) {
      final String ref = text.substring(linkStartIdx + linkWithRef.length());
      final int quoteIdx = ref.indexOf('"');
      if (quoteIdx > 0) {
        return ref.substring(0, quoteIdx);
      }
    }
    return null;
  }

  @Nonnull
  protected Color getDescriptionTitleColor() {
    return JBColor.namedColor("ToolTip.infoForeground", new JBColor(0x919191, 0x919191));
  }

  @Nonnull
  @Override
  public LineTooltipRenderer createRenderer(@Nullable String text, final int width) {
    return new DaemonTooltipRenderer(text, width, getEqualityObjects());
  }
}
