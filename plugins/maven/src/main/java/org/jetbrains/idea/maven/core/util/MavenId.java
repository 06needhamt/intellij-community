package org.jetbrains.idea.maven.core.util;

import com.intellij.openapi.diagnostic.Logger;
import org.apache.maven.artifact.Artifact;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.text.ParseException;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenId implements Comparable<MavenId>{
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.core.util.MavenId");

  @NonNls public String groupId;
  @NonNls public String artifactId;
  @NonNls public String version;

  @SuppressWarnings({"UnusedDeclaration"})
  public MavenId() {
  }

  public MavenId(@NonNls final String groupId, @NonNls final String artifactId, @NonNls final String version) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;

    assertIsValid();
  }

  private void assertIsValid() {
    // todo catch for IDEADEV-21389 exception
    if (groupId != null && artifactId != null) return;
    throw new RuntimeException("Invalid artifact " + toString());
  }

  public MavenId(Artifact artifact) {
    this(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MavenId projectId = (MavenId)o;

    if (artifactId != null ? !artifactId.equals(projectId.artifactId) : projectId.artifactId != null) return false;
    if (groupId != null ? !groupId.equals(projectId.groupId) : projectId.groupId != null) return false;
    //noinspection RedundantIfStatement
    if (version != null ? !version.equals(projectId.version) : projectId.version != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (groupId != null ? groupId.hashCode() : 0);
    result = 31 * result + (artifactId != null ? artifactId.hashCode() : 0);
    result = 31 * result + (version != null ? version.hashCode() : 0);
    return result;
  }

  public String toString() {
    return version != null
           ? MessageFormat.format("{0}:{1}:{2}", groupId, artifactId, version)
           : MessageFormat.format("{0}:{1}", groupId, artifactId);
  }

  public static MavenId parse(final String text) throws ParseException {
    final int colon1 = text.indexOf(":");
    if (colon1 <= 0) {
      throw new ParseException (text, 0);
    }
    final String groupId = text.substring(0, colon1);
    final String artifactId;
    final String version;
    final int colon2 = text.indexOf(":", colon1 + 1);
    if (colon2 <= 0) {
      artifactId = text.substring(colon1 + 1);
      version = null;
    }
    else {
      artifactId = text.substring(colon1 + 1, colon2);
      version = text.substring(colon2 + 1);
      final int colon3 = text.indexOf(":", colon2 + 1);
      if (colon3 > 0) {
        throw new ParseException (text, colon3);
      }
    }
    return new MavenId(groupId, artifactId, version);
  }

  public boolean matches(@NotNull final MavenId that) {
    return groupId.equals(that.groupId) && artifactId.equals(that.artifactId) &&
           (version == null || that.version == null || version.equals(that.version));
  }

  public int compareTo(final MavenId that) {
    return toString().compareTo(that.toString());
  }
}
