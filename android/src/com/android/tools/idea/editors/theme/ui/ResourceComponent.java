/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.editors.theme.ui;

import com.android.tools.idea.editors.theme.ThemeEditorConstants;
import com.android.tools.swing.ui.ClickableLabel;
import com.android.tools.swing.ui.SwatchComponent;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Component for displaying a color or a drawable resource with attribute name, type and value text.
 */
public class ResourceComponent extends JPanel {

  /**
   * Maximum number of swatch icons to be displayed by default. See {@link SwatchComponent} constructor for more details.
   */
  private static final short MAX_SWATCH_ICONS = 3;
  public static final String NAME_LABEL = "Name Label";

  private final SwatchComponent mySwatchComponent = new SwatchComponent(MAX_SWATCH_ICONS);
  private final ClickableLabel myNameLabel = new ClickableLabel();
  protected final ClickableLabel myWarningLabel = new ClickableLabel();

  private final VariantsComboBox myVariantCombo = new VariantsComboBox();

  public ResourceComponent() {
    super(new BorderLayout(0, ThemeEditorConstants.ATTRIBUTE_ROW_GAP));
    setBorder(BorderFactory.createEmptyBorder(ThemeEditorConstants.ATTRIBUTE_MARGIN / 2, 0, ThemeEditorConstants.ATTRIBUTE_MARGIN / 2, 0));

    myWarningLabel.setIcon(AllIcons.General.BalloonWarning);
    myWarningLabel.setVisible(false);

    myNameLabel.setName(NAME_LABEL);
    myNameLabel.setForeground(ThemeEditorConstants.RESOURCE_ITEM_COLOR);
    myNameLabel.setFont(myNameLabel.getFont().deriveFont(Font.BOLD));

    Box topRowPanel = new Box(BoxLayout.LINE_AXIS);
    topRowPanel.add(myNameLabel);
    topRowPanel.add(myWarningLabel);

    topRowPanel.add(Box.createHorizontalGlue());
    topRowPanel.add(myVariantCombo);
    add(topRowPanel, BorderLayout.CENTER);

    mySwatchComponent.setBackground(JBColor.WHITE);
    mySwatchComponent.setForeground(null);
    add(mySwatchComponent, BorderLayout.SOUTH);
  }

  @Override
  public Dimension getPreferredSize() {
    if (!isPreferredSizeSet()) {
      int firstRowHeight = Math.max(getFontMetrics(getFont()).getHeight(), myVariantCombo.getPreferredSize().height);
      int secondRowHeight = mySwatchComponent.getPreferredSize().height;

      return new Dimension(0, ThemeEditorConstants.ATTRIBUTE_MARGIN + ThemeEditorConstants.ATTRIBUTE_ROW_GAP + firstRowHeight + secondRowHeight);
    }

    return super.getPreferredSize();
  }

  public void setSwatchIcons(@NotNull List<SwatchComponent.SwatchIcon> icons) {
    mySwatchComponent.setSwatchIcons(icons);
  }

  public void setNameText(@NotNull String name) {
    myNameLabel.setText(name);
  }

  public void setWarning(@NotNull String warning){
    myWarningLabel.setToolTipText(warning);
  }

  public void setWarningVisible(boolean isVisible){
    myWarningLabel.setVisible(isVisible);
  }

  public void setVariantsModel(@Nullable ComboBoxModel comboBoxModel) {
    myVariantCombo.setModel(comboBoxModel != null ? comboBoxModel : new DefaultComboBoxModel());
  }

  public void addVariantItemListener(@NotNull ItemListener itemListener) {
    myVariantCombo.addItemListener(itemListener);
  }

  public void addVariantPopupClosingListener(@NotNull VariantsComboBox.PopupClosingListener listener) {
    myVariantCombo.addPopupClosingListener(listener);
  }

  public void setValueText(@NotNull String value) {
    mySwatchComponent.setText(value);
  }

  @NotNull
  public String getValueText() {
    return mySwatchComponent.getText();
  }

  @Override
  public void setFont(final Font font) {
    super.setFont(font);
    // We need a null check here as this can be called from the setUI that's called from the constructor.
    if (mySwatchComponent != null) {
      mySwatchComponent.setFont(font);
    }
  }

  @Override
  public void setComponentPopupMenu(JPopupMenu popup) {
    super.setComponentPopupMenu(popup);
    myNameLabel.setComponentPopupMenu(popup);
    mySwatchComponent.setComponentPopupMenu(popup);
  }

  public void addActionListener(final ActionListener listener) {
    myNameLabel.addActionListener(listener);
    myWarningLabel.addActionListener(listener);
    mySwatchComponent.addActionListener(listener);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        if (!contains(e.getPoint()) || !SwingUtilities.isLeftMouseButton(e)) {
          return;
        }
        listener.actionPerformed(new ActionEvent(e.getSource(), ActionEvent.ACTION_PERFORMED, null));
      }
    });
  }

  public void setVariantComboVisible(boolean isVisible) {
    myVariantCombo.setVisible(isVisible);
  }

  /**
   * Returns a {@link ValidationInfo} for the {@link SwatchComponent}.
   */
  @NotNull
  public ValidationInfo createSwatchValidationInfo(@NotNull String errorText) {
    return new ValidationInfo(errorText, mySwatchComponent);
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    if (myWarningLabel.isVisible()) {
      validate();
      if (SwingUtilities.getLocalBounds(myWarningLabel)
        .contains(SwingUtilities.convertMouseEvent(this, event, myWarningLabel).getPoint())) {
        return myWarningLabel.getToolTipText();
      }
    }
    return super.getToolTipText(event);
  }
}