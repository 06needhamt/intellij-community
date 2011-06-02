package org.jetbrains.jet.lang.annotations;

import com.google.common.collect.Lists;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Function;
import com.intellij.util.Icons;
import com.intellij.util.PsiNavigateUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.AnalyzingUtils;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.resolve.DescriptorRenderer;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author abreslav
 */
public class JetLineMarkerProvider implements LineMarkerProvider {

    public static final Icon OVERRIDING_FUNCTION = IconLoader.getIcon("/general/overridingMethod.png");

    @Override
    public LineMarkerInfo getLineMarkerInfo(PsiElement element) {
        JetFile file = PsiTreeUtil.getParentOfType(element, JetFile.class);

        if (file == null) return null;
        final BindingContext bindingContext = AnalyzingUtils.analyzeFileWithCache(file);

        if (element instanceof JetClass) {
            JetClass jetClass = (JetClass) element;
            return new LineMarkerInfo<JetClass>(jetClass, jetClass.getTextOffset(), Icons.CLASS_ICON, Pass.UPDATE_ALL,
                    new Function<JetClass, String>() {
                        @Override
                        public String fun(JetClass jetClass) {
                            ClassDescriptor classDescriptor = bindingContext.getClassDescriptor(jetClass);
                            if (classDescriptor == null) {
                                return "<it>Unresolved</it>";
                            }
                            return DescriptorRenderer.HTML.render(classDescriptor);
                        }
                    },
                    new GutterIconNavigationHandler<JetClass>() {
                        @Override
                        public void navigate(MouseEvent e, JetClass elt) {
                        }
                    });
        }

        if (element instanceof JetProperty) {
            JetProperty jetProperty = (JetProperty) element;
            final VariableDescriptor variableDescriptor = bindingContext.getVariableDescriptor(jetProperty);
            if (variableDescriptor instanceof PropertyDescriptor) {
                return new LineMarkerInfo<JetProperty>(jetProperty, jetProperty.getTextOffset(), Icons.PROPERTY_ICON, Pass.UPDATE_ALL,
                        new Function<JetProperty, String>() {
                            @Override
                            public String fun(JetProperty property) {
                                return DescriptorRenderer.HTML.render(variableDescriptor);
                            }
                        },
                        new GutterIconNavigationHandler<JetProperty>() {
                            @Override
                            public void navigate(MouseEvent e, JetProperty elt) {
                            }
                        });
            }
        }

        if (element instanceof JetFunction) {
            JetFunction jetFunction = (JetFunction) element;

            final FunctionDescriptor functionDescriptor = bindingContext.getFunctionDescriptor(jetFunction);
            if (functionDescriptor == null) return null;
            final Set<? extends FunctionDescriptor> overriddenFunctions = functionDescriptor.getOverriddenFunctions();
            Icon icon = isMember(functionDescriptor) ? (overriddenFunctions.isEmpty() ? Icons.METHOD_ICON : OVERRIDING_FUNCTION) : Icons.FUNCTION_ICON;
            return new LineMarkerInfo<JetFunction>(
                    jetFunction, jetFunction.getTextOffset(), icon, Pass.UPDATE_ALL,
                    new Function<JetFunction, String>() {
                        @Override
                        public String fun(JetFunction jetFunction) {
                            StringBuilder builder = new StringBuilder();
                            builder.append(DescriptorRenderer.HTML.render(functionDescriptor));
                            int overrideCount = overriddenFunctions.size();
                            if (overrideCount >= 1) {
                                builder.append(" overrides ").append(DescriptorRenderer.HTML.render(overriddenFunctions.iterator().next()));
                            }
                            if (overrideCount > 1) {
                                int count = overrideCount - 1;
                                builder.append(" and ").append(count).append(" other function");
                                if (count > 1) {
                                    builder.append("s");
                                }
                            }

                            return builder.toString();
                        }
                    },
                    new GutterIconNavigationHandler<JetFunction>() {
                        @Override
                        public void navigate(MouseEvent event, JetFunction elt) {
                            if (overriddenFunctions.isEmpty()) return;
                            final List<PsiElement> list = Lists.newArrayList();
                            for (FunctionDescriptor overriddenFunction : overriddenFunctions) {
                                PsiElement declarationPsiElement = bindingContext.getDeclarationPsiElement(overriddenFunction);
                                list.add(declarationPsiElement);
                            }
                            if (list.isEmpty()) {
                                String myEmptyText = "empty text";
                                final JComponent renderer = HintUtil.createErrorLabel(myEmptyText);
                                final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(renderer, renderer).createPopup();
                                if (event != null) {
                                    popup.show(new RelativePoint(event));
                                }
                                return;
                            }
                            if (list.size() == 1) {
                                PsiNavigateUtil.navigate(list.iterator().next());
                            }
                            else {
                                final JBPopup popup = NavigationUtil.getPsiElementPopup(PsiUtilBase.toPsiElementArray(list), new DefaultPsiElementCellRenderer() {
                                            @Override
                                            public String getElementText(PsiElement element) {
                                                if (element instanceof JetFunction) {
                                                    JetFunction function = (JetFunction) element;
                                                    return DescriptorRenderer.HTML.render(bindingContext.getFunctionDescriptor(function));
                                                }
                                                return super.getElementText(element);
                                            }
                                        }, DescriptorRenderer.HTML.render(functionDescriptor));
                                if (event != null) {
                                    popup.show(new RelativePoint(event));
                                }
                            }
                        }
                    }
            );
        }

        if (element instanceof JetNamespace) {
            JetNamespace jetNamespace = (JetNamespace) element;
            return new LineMarkerInfo<JetNamespace>(
                    jetNamespace, jetNamespace.getTextOffset(), Icons.PACKAGE_ICON, Pass.UPDATE_ALL,
                    new Function<JetNamespace, String>() {
                        @Override
                        public String fun(JetNamespace jetNamespace) {
                            NamespaceDescriptor namespaceDescriptor = bindingContext.getNamespaceDescriptor(jetNamespace);
                            return DescriptorRenderer.HTML.render(namespaceDescriptor);
                        }
                    },
                    new GutterIconNavigationHandler<JetNamespace>() {
                        @Override
                        public void navigate(MouseEvent e, JetNamespace elt) {
                        }
                    }
            );
        }

        if (element instanceof JetObjectDeclaration) {
            JetObjectDeclaration jetObjectDeclaration = (JetObjectDeclaration) element;
            return new LineMarkerInfo<JetObjectDeclaration>(
                    jetObjectDeclaration, jetObjectDeclaration.getTextOffset(), Icons.ANONYMOUS_CLASS_ICON, Pass.UPDATE_ALL,
                    new Function<JetObjectDeclaration, String>() {
                        @Override
                        public String fun(JetObjectDeclaration jetObjectDeclaration) {
                            ClassDescriptor classDescriptor = bindingContext.getClassDescriptor(jetObjectDeclaration);
                            return DescriptorRenderer.HTML.renderAsObject(classDescriptor);
                        }
                    },
                    new GutterIconNavigationHandler<JetObjectDeclaration>() {
                        @Override
                        public void navigate(MouseEvent e, JetObjectDeclaration elt) {
                        }
                    }
            );
        }

        return null;
    }

    private boolean isMember(@NotNull FunctionDescriptor functionDescriptor) {
        return functionDescriptor.getContainingDeclaration().getOriginal() instanceof ClassifierDescriptor;
    }

    @Override
    public void collectSlowLineMarkers(List<PsiElement> elements, Collection<LineMarkerInfo> result) {
    }
}
