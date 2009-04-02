package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.idea.maven.dom.MavenArtifactCoordinatesVersionConverter;

public interface MavenDomArtifactCoordinates extends MavenDomShortMavenArtifactCoordinates {
  @Required
  @Convert(MavenArtifactCoordinatesVersionConverter.class)
  GenericDomValue<String> getVersion();
}
