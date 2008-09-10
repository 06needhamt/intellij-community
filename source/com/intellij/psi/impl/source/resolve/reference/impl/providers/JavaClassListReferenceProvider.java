package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 05.06.2003
 * Time: 20:27:59
 * To change this template use Options | File Templates.
 */
public class JavaClassListReferenceProvider extends JavaClassReferenceProvider {
  @NonNls private static final Pattern PATTERN = Pattern.compile("([A-Za-z]\\w*\\s*([\\.\\$]\\s*[A-Za-z]\\w*\\s*)+)");

  public JavaClassListReferenceProvider(final Project project) {
    super(project);
    setOption(ADVANCED_RESOLVE, Boolean.TRUE);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, int offsetInPosition){
    final Set<String> knownTopLevelPackages = new HashSet<String>();
    final List<PsiElement> defaultPackages = getDefaultPackages();
    for (final PsiElement pack : defaultPackages) {
      if (pack instanceof PsiPackage) {
        knownTopLevelPackages.add(((PsiPackage)pack).getName());
      }
    }
    final List<PsiReference> results = new ArrayList<PsiReference>();

    final Matcher matcher = PATTERN.matcher(str);

    while(matcher.find()){
      final String identifier = matcher.group().trim();
      final int pos = identifier.indexOf('.');
      if(pos >= 0 && knownTopLevelPackages.contains(identifier.substring(0, pos))){
        results.addAll(Arrays.asList(new JavaClassReferenceSet(identifier, position, offsetInPosition + matcher.start(), false, this){
          public boolean isSoft(){
            return true;
          }
        }.getAllReferences()));
      }
    }
    return results.toArray(new PsiReference[results.size()]);
  }
}
