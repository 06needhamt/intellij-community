package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleCircularDependencyException;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Build;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.core.util.MavenId;

import java.io.File;
import java.text.MessageFormat;
import java.util.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenToIdeaConverter extends MavenProjectModel.MavenProjectVisitorPlain {
  @NonNls public static final String JAR_TYPE = "jar";
  @NonNls public static final String JAVADOC_CLASSIFIER = "javadoc";
  @NonNls public static final String SOURCES_CLASSIFIER = "sources";

  public static void convert(final MavenProjectModel projectModel,
                             final ModifiableModuleModel modifiableModel,
                             final MavenToIdeaMapping mapping,
                             final boolean markSynthetic,
                             final boolean createGroups) {

    projectModel.visit(new MavenToIdeaConverter(modifiableModel, mapping, markSynthetic, createGroups));

    for (Module module : mapping.getExistingModules()) {
      new RootModelAdapter(module).resolveModuleDependencies(mapping.getLibraryNameToModuleName());
    }

    try {
      modifiableModel.commit();
    }
    catch (ModuleCircularDependencyException ignore) {
    }
  }

  final private ModifiableModuleModel modifiableModuleModel;
  final private MavenToIdeaMapping mavenToIdeaMapping;
  final private boolean markSynthetic;
  final private boolean createGroups;

  final private Stack<String> groups = new Stack<String>();

  private MavenToIdeaConverter(ModifiableModuleModel model,
                               final MavenToIdeaMapping mavenToIdeaMapping,
                               final boolean markSynthetic,
                               final boolean createGroups) {
    this.markSynthetic = markSynthetic;
    this.mavenToIdeaMapping = mavenToIdeaMapping;
    this.modifiableModuleModel = model;
    this.createGroups = createGroups;
  }

  public void visit(MavenProjectModel.Node node) {
    Module module = mavenToIdeaMapping.getModule(node);
    if (module == null) {
      module = modifiableModuleModel.newModule(mavenToIdeaMapping.getModuleFilePath(node));
    }

    convertRootModel(module, node);

    SyntheticModuleUtil.setSynthetic(module, markSynthetic && !node.isLinked());

    if (createGroups && !node.mavenModules.isEmpty()) {
      groups.push(ProjectBundle.message("module.group.name", module.getName()));
    }

    modifiableModuleModel.setModuleGroupPath(module, groups.isEmpty() ? null : groups.toArray(new String[groups.size()]));
  }

  public void leave(MavenProjectModel.Node node) {
    if (createGroups && !node.mavenModules.isEmpty()) {
      groups.pop();
    }
  }

  void convertRootModel(Module module, MavenProjectModel.Node node) {
    RootModelAdapter rootModel = new RootModelAdapter(module);
    rootModel.resetRoots(node.getDirectory());

    // TODO: do this properly
    rootModel.createSrcDir(new File(node.getDirectory(), "target/generated-sources/modello").getPath(), false);

    createSourceRoots(rootModel, node.getMavenProject());
    createDependencies(rootModel, node.getMavenProject());
    rootModel.commit();
  }

  void createSourceRoots(RootModelAdapter rootModel, MavenProject mavenProject) {
    for (Object o : mavenProject.getCompileSourceRoots()) {
      rootModel.createSrcDir((String)o, false);
    }
    for (Object o : mavenProject.getTestCompileSourceRoots()) {
      rootModel.createSrcDir((String)o, true);
    }

    for (Object o : mavenProject.getResources()) {
      rootModel.createSrcDir(((Resource)o).getDirectory(), false);
    }
    for (Object o : mavenProject.getTestResources()) {
      rootModel.createSrcDir(((Resource)o).getDirectory(), true);
    }

    Build build = mavenProject.getBuild();
    rootModel.excludeRoot(build.getOutputDirectory());
    rootModel.excludeRoot(build.getTestOutputDirectory());
  }

  void createDependencies(RootModelAdapter rootModel, MavenProject mavenProject) {
    for (Artifact artifact : extractDependencies(mavenProject)) {
      MavenId id = new MavenId(artifact);
      final String moduleName = mavenToIdeaMapping.getModuleName(id);
      if (moduleName != null) {
        rootModel.createModuleDependency(moduleName);
      }
      else {
        rootModel.createModuleLibrary(mavenToIdeaMapping.getLibraryName(id), getUrl(artifact, null), getUrl(artifact, SOURCES_CLASSIFIER),
                                      getUrl(artifact, JAVADOC_CLASSIFIER));
      }
    }
  }

  private List<Artifact> extractDependencies(final MavenProject mavenProject) {
    Map<String, Artifact> projectIdToArtifact = new TreeMap<String, Artifact>();
    for (Object o : mavenProject.getArtifacts()) {
      Artifact newArtifact = (Artifact)o;
      if (newArtifact.getType().equalsIgnoreCase(JAR_TYPE)) {
        String projectId = newArtifact.getGroupId() + ":" + newArtifact.getArtifactId();
        Artifact oldArtifact = projectIdToArtifact.get(projectId);
        if (oldArtifact == null ||
            new DefaultArtifactVersion(oldArtifact.getVersion()).compareTo(new DefaultArtifactVersion(newArtifact.getVersion())) < 0) {
          projectIdToArtifact.put(projectId, newArtifact);
        }
      }
    }
    return new ArrayList<Artifact>(projectIdToArtifact.values());
  }

  private static String getUrl(final Artifact artifact, final String classifier) {
    String path = artifact.getFile().getPath();
    if (classifier != null) {
      path = MessageFormat.format("{0}-{1}.{2}", path.substring(0, path.lastIndexOf(".")), classifier, artifact.getType());
      if (!new File(path).exists()) {
        return null;
      }
    }
    return VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, path) + JarFileSystem.JAR_SEPARATOR;
  }
}
