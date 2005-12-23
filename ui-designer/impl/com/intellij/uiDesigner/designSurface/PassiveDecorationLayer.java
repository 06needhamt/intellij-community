package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.util.IconLoader;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.RadRootContainer;
import com.intellij.uiDesigner.RadButtonGroup;
import com.intellij.uiDesigner.lw.IComponentUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

/**
 * Decoration layer is over COMPONENT_LAYER (layer where all components are located).
 * It contains all necessary decorators. Decorators are:
 * - special borders to show component bounds and cell bounds inside grids
 * - special component which marks selected rectangle
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
class PassiveDecorationLayer extends JComponent{
  @NotNull private final GuiEditor myEditor;
  private static Icon ourDragIcon;

  public PassiveDecorationLayer(@NotNull final GuiEditor editor) {
    myEditor = editor;
  }

  /**
   * Paints all necessary decoration for the specified <code>component</code>
   */
  protected final void paintPassiveDecoration(final RadComponent component, final Graphics g){
    // Paint component bounds and grid markers
    Painter.paintComponentDecoration(myEditor, component, g);

    final Set<RadButtonGroup> paintedGroups = new HashSet<RadButtonGroup>();
    final RadRootContainer rootContainer = myEditor.getRootContainer();

    // Paint selection and dragger
    IComponentUtil.iterate(
      component,
      new IComponentUtil.ComponentVisitor<RadComponent>() {
        public boolean visit(final RadComponent component) {
          final Point point = SwingUtilities.convertPoint(
            component.getDelegee(),
            0,
            0,
            rootContainer.getDelegee()
          );
          if (component.isSelected()) {
            RadButtonGroup group = rootContainer.findGroupForComponent(component);
            if (group != null && !paintedGroups.contains(group)) {
              paintedGroups.add(group);
              Painter.paintButtonGroupLines(rootContainer, group, g);
            }
          }
          g.translate(point.x, point.y);
          try{
            Painter.paintSelectionDecoration(component, g,myEditor.getGlassLayer().isFocusOwner());
            // Over selection we have to paint dragger
            if (component.hasDragger()){
              final Icon icon = getDragIcon();
              icon.paintIcon(PassiveDecorationLayer.this, g, - icon.getIconWidth(), - icon.getIconHeight());
            }
          }finally{
            g.translate(-point.x, -point.y);
          }
          return true;
        }
      }
    );
  }

  private static Icon getDragIcon() {
    if (ourDragIcon == null) {
      ourDragIcon = IconLoader.getIcon("/com/intellij/uiDesigner/icons/drag.png");
    }
    return ourDragIcon;
  }

  public void paint(final Graphics g){
    // Passive decoration
    final RadRootContainer root = myEditor.getRootContainer();
    for(int i = root.getComponentCount() - 1; i >= 0; i--){
      final RadComponent component = root.getComponent(i);
      paintPassiveDecoration(component, g);
    }

    // Paint active decorators
    paintChildren(g);
  }
}
