/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.impl;

import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.xslt.XsltConfig;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.impl.XsltLanguage;
import org.intellij.lang.xpath.xslt.refactoring.XsltRefactoringSupport;
import org.intellij.lang.xpath.xslt.validation.XsltAnnotator;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.lang.*;
import com.intellij.navigation.ChooseByNameRegistry;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameInputValidator;
import com.intellij.refactoring.rename.RenameInputValidatorRegistry;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.net.URL;

class XsltConfigImpl extends XsltConfig implements JDOMExternalizable, ApplicationComponent {
    private static final Logger LOG = Logger.getInstance(XsltConfigImpl.class.getName());

    private static final String XSLT_SCHEMA_LOCATION = "resources/xslt-schema.xsd";

    public boolean ENABLED = true;
    public boolean REGISTER_SCHEMA = true;
    public boolean SHOW_LINKED_FILES = true;

    public void readExternal(Element element) throws InvalidDataException {
        DefaultJDOMExternalizer.readExternal(this, element);
    }

    public void writeExternal(Element element) throws WriteExternalException {
        DefaultJDOMExternalizer.writeExternal(this, element);
    }

    @SuppressWarnings({ "StringEquality" })
    public void initComponent() {
        if (ENABLED) {
            ChooseByNameRegistry.getInstance().contributeToSymbols(new XsltChooseByNameContributor());

            final Language xmlLang = StdFileTypes.XML.getLanguage();
            final Language xpathLang = XPathFileType.XPATH.getLanguage();

            final XsltAnnotator annotator = new XsltAnnotator();
            LanguageAnnotators.INSTANCE.addExplicitExtension(xpathLang, annotator);
//            xmlLang.injectAnnotator(annotator);

            final XsltDocumentationProvider provider = new XsltDocumentationProvider();
            LanguageDocumentation.INSTANCE.addExplicitExtension(xmlLang, provider);
            LanguageDocumentation.INSTANCE.addExplicitExtension(xpathLang, provider);

            RenameInputValidatorRegistry.getInstance().registerInputValidator(psiElement().withLanguage(XsltLanguage.INSTANCE), new RenameInputValidator() {
                public boolean isInputValid(String newName, PsiElement element, ProcessingContext context) {
                    return LanguageNamesValidation.INSTANCE.forLanguage(XPathFileType.XPATH.getLanguage()).isIdentifier(newName, element.getProject());
                }
            });
//            intentionManager.addAction(new DeleteUnusedParameterFix());
//            intentionManager.addAction(new DeleteUnusedVariableFix());

            final XsltFormattingModelBuilder builder = new XsltFormattingModelBuilder(LanguageFormatting.INSTANCE.forLanguage(xmlLang));
            LanguageFormatting.INSTANCE.addExplicitExtension(xmlLang, builder);

            final ExternalResourceManagerEx erm = ExternalResourceManagerEx.getInstanceEx();
            erm.addIgnoredResource(XsltSupport.PLUGIN_EXTENSIONS_NS);

            if (REGISTER_SCHEMA) {
                final String resourceLocation = erm.getResourceLocation(XsltSupport.XSLT_NS);
                final Class<?> clazz = XsltConfig.class;
                final URL resource = clazz.getResource(XSLT_SCHEMA_LOCATION);
                LOG.info("Adding resource for '" + XsltSupport.XSLT_NS + "': " + resource);
                if (resourceLocation != XsltSupport.XSLT_NS && !resourceLocation.equals(resource.toExternalForm())) {
                    LOG.info("Warning: Resource for '" + XsltSupport.XSLT_NS + "' is already registered to: " + resourceLocation);
                } else {
                    erm.addStdResource(XsltSupport.XSLT_NS, XSLT_SCHEMA_LOCATION, clazz);
                }
            }
        }
    }

    public void disposeComponent() {
    }

    @NotNull
    @NonNls
    public String getComponentName() {
        return "XSLT-Support.Configuration";
    }

    public boolean isEnabled() {
        return ENABLED;
    }

    public boolean isRegisterSchema() {
        return REGISTER_SCHEMA;
    }

    public boolean isShowLinkedFiles() {
        return SHOW_LINKED_FILES;
    }

    public UI createConfigUI() {
        return new UIImpl(this);
    }

    public static class UIImpl extends JPanel implements UI {
        private final JCheckBox myXsltSupport;
        private final JCheckBox myRegisterSchema;
        private final JCheckBox myShowLinkedFiles;

        private final XsltConfigImpl myConfig;

        public UIImpl(XsltConfigImpl config) {
            myConfig = config;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            myXsltSupport = new JCheckBox("Enabled");
            myXsltSupport.setMnemonic('E');
            myXsltSupport.setToolTipText("Uncheck to disable built-in support for XSLT documents. Requires Project-reloading to take effect.");
            myXsltSupport.setSelected(myConfig.ENABLED);
            myXsltSupport.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    final boolean enabled = myXsltSupport.isSelected();
                    myRegisterSchema.setEnabled(enabled);
                    myShowLinkedFiles.setEnabled(enabled);
                }
            });
            myRegisterSchema = new JCheckBox("Register XSLT Schema");
            myRegisterSchema.setMnemonic('R');
            myRegisterSchema.setToolTipText("Registers the bundled XML Schema with the XSLT namespace. Requires to restart IDEA to take effect.");
            myRegisterSchema.setSelected(myConfig.REGISTER_SCHEMA);
            myRegisterSchema.setEnabled(myConfig.ENABLED);

            myShowLinkedFiles = new JCheckBox("Show Associated Files in Project View");
            myShowLinkedFiles.setMnemonic('A');
            myShowLinkedFiles.setSelected(myConfig.SHOW_LINKED_FILES);
            myRegisterSchema.setEnabled(myConfig.ENABLED);

            add(myXsltSupport);
            add(myRegisterSchema);
            add(myShowLinkedFiles);

            final JPanel jPanel = new JPanel(new BorderLayout());
            jPanel.add(Box.createVerticalGlue(), BorderLayout.CENTER);

            final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            jPanel.add(panel, BorderLayout.SOUTH);
            jPanel.setAlignmentX(0);
            add(jPanel);
        }

        @Nls
        public String getDisplayName() {
            return "XSLT";
        }

        @Nullable
        public Icon getIcon() {
            return null;
        }

        @Nullable
        @NonNls
        public String getHelpTopic() {
            return null;
        }

        public void disposeUIResources() {
        }

        public JComponent createComponent() {
            return this;
        }

        public boolean isModified() {
            return myConfig.ENABLED != myXsltSupport.isSelected() ||
                    myConfig.REGISTER_SCHEMA != myRegisterSchema.isSelected() ||
                    myConfig.SHOW_LINKED_FILES != myShowLinkedFiles.isSelected();
        }

        public void apply() {
            boolean oldValue = myConfig.SHOW_LINKED_FILES;

            myConfig.ENABLED = myXsltSupport.isSelected();
            myConfig.REGISTER_SCHEMA = myRegisterSchema.isSelected();
            myConfig.SHOW_LINKED_FILES = myShowLinkedFiles.isSelected();

            // TODO: make this a ConfigListener
            if (oldValue != myConfig.SHOW_LINKED_FILES) {
                final Project[] projects = ProjectManager.getInstance().getOpenProjects();
                for (Project project : projects) {
                    ProjectView.getInstance(project).refresh();
                }
            }

            XsltRefactoringSupport.getInstance().setEnabled(myConfig.ENABLED);
        }

        public void reset() {
            myXsltSupport.setSelected(myConfig.ENABLED);
            myRegisterSchema.setSelected(myConfig.REGISTER_SCHEMA);
            myShowLinkedFiles.setSelected(myConfig.SHOW_LINKED_FILES);
        }
    }
}