package org.jetbrains.jet.plugin.compiler;

import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.ex.CompilerPathsEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.JetDeclaration;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.java.AnalyzerFacade;
import org.jetbrains.jet.plugin.JetFileType;

import java.util.Collection;
import java.util.Set;

/**
 * @author abreslav
 */
public class WholeProjectAnalyzerFacade {
    public static final Function<JetFile, Collection<JetDeclaration>> WHOLE_PROJECT_DECLARATION_PROVIDER = new Function<JetFile, Collection<JetDeclaration>>() {
        @Override
        public Collection<JetDeclaration> fun(JetFile file) {
            final Project project = file.getProject();
            final Set<JetDeclaration> namespaces = Sets.newLinkedHashSet();
            ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
            if (rootManager != null && !ApplicationManager.getApplication().isUnitTestMode()) {
                VirtualFile[] contentRoots = rootManager.getContentRoots();

                CompilerPathsEx.visitFiles(contentRoots, new CompilerPathsEx.FileVisitor() {
                    @Override
                    protected void acceptFile(VirtualFile file, String fileRoot, String filePath) {
                        final FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
                        if (fileType != JetFileType.INSTANCE) return;
                        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                        if (psiFile instanceof JetFile) {
                            namespaces.add(((JetFile) psiFile).getRootNamespace());
                        }
                    }
                });
            }
            else {
                namespaces.add(file.getRootNamespace());
            }
            return namespaces;
            }
    };

    @NotNull
    public static BindingContext analyzeProjectWithCacheOnAFile(@NotNull JetFile file) {
        return AnalyzerFacade.analyzeFileWithCache(file, WHOLE_PROJECT_DECLARATION_PROVIDER);
    }

}
