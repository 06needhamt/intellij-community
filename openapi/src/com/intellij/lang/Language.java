package com.intellij.lang;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.newCodeFormatting.FormattingModelBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The base class for all programming language support implementations. Specific language implementations should inherit from this class
 * and its register instance wrapped with {@link com.intellij.openapi.fileTypes.LanguageFileType} instance through
 * <code>FileTypeManager.getInstance().registerFileType</code>
 * There should be exactly one instance of each Language. It usually created when creating LanguageFileType and can be retreived later
 * with {@link #findInstance(Class)}.
 */
public abstract class Language {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.Language");

  private static SurroundDescriptor[] EMPTY_SURROUND_DESCRIPTORS_ARRAY = new SurroundDescriptor[0];
  private static Map<Class<? extends Language>, Language> ourRegisteredLanguages = new HashMap<Class<? extends Language>, Language>();
  private String myID;
  private String[] myMimeTypes;
  public static final Language ANY = new Language("", "") {};
  private static final EmptyFindUsagesProvider EMPTY_FIND_USAGES_PROVIDER = new EmptyFindUsagesProvider();

  protected Language(String id) {
    this(id, "");
  }

  protected Language(final String ID, final String... mimeTypes) {
    myID = ID;
    myMimeTypes = mimeTypes;
    Class<? extends Language> langClass = getClass();

    if (ourRegisteredLanguages.containsKey(langClass)) {
      LOG.error("Language '" + langClass.getName() + "' is already registered");
      return;
    }
    ourRegisteredLanguages.put(langClass, this);

    try {
      final Field langField = StdLanguages.class.getDeclaredField(ID);
      LOG.assertTrue(Modifier.isStatic(langField.getModifiers()));
      LOG.assertTrue(Modifier.isPublic(langField.getModifiers()));
      langField.set(null, this);
    }
    catch (NoSuchFieldException e) {
      // Do nothing. Not a standard file type
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
  }

  /**
   * @return collection of all languages registered so far.
   */
  public static Collection<Language> getRegisteredLanguages() {
    return Collections.unmodifiableCollection(ourRegisteredLanguages.values());
  }

  /**
   * @param klass <code>java.lang.Class</code> of the particular language. Serves key purpose.
   * @return instance of the <code>klass</code> language registered if any.
   */
  public static <T extends Language> T findInstance(Class<T> klass) {
    return (T)ourRegisteredLanguages.get(klass);
  }

  /**
   * Override this method to provide syntax highlighting (coloring) capabilities for your language implementation.
   * By syntax highlighting we mean highlighting of keywords, comments, braces etc. where lexing the file content is enough
   * to identify proper highlighting attributes.
   *
   * Default implementation doesn't highlight anything.
   * @param project might be necessary to gather various project settings from.
   * @return <code>SyntaxHighligher</code> interface implementation for this particular language.
   */
  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(Project project) {
    return new PlainSyntaxHighlighter();
  }

  /**
   * Override this method to provide code formatter (aka pretty print, aka code buitifier) for your language implementation.
   * Note that formatter implementation is necessary to make smart enter and smart end functions to work properly.
   *
   * @return <code>FormatterModelBuilder</code> interface implementation for this particular language or <code>null</code>
   * if no formatting capabilities provided.
   */
  @Nullable
  public FormattingModelBuilder getFormattingModelBuilder(){
    return null;
  }

  /**
   * Override this method to provide parser implementation.
   * Parsed tree (AST) and program structure interface (PSI) based on AST is necessary for most of IDEA smart functions like
   * in-editor error highlighting, advanced syntax highlighting, error-checking, intention actions, inspections, folding,
   * finding usages, refactoring, file structure view etc.
   *
   * @return <code>ParserDefinition</code> interface implementation for this particular language or <code>null</code>
   * if no parsing capabilities provided.
   */
  @Nullable
  public ParserDefinition getParserDefinition() {
    return null;
  }

  /**
   * Override this method to provide code folding capabilities when editing files of this language.
   * Please note {@link #getParserDefinition()} should return parser implementation for folding building to work properly.
   *
   * @return <code>FoldingBuilder</code> interface implementation for this particular language or <code>null</code>
   * if no folding capabilities provided.
   */
  @Nullable
  public FoldingBuilder getFoldingBuilder() {
    return null;
  }

  /**
   * Override this method to provide paired brace matching and highlighting ability this language editors.
   * For this functionality to work properly own {@link SyntaxHighlighter} implementation is necessary.
   *
   * @return <code>PairedBraceMatcher</code> interface implementation for this particular language or <code>null</code>
   * if no brace matching capabilities provided.
   */
  @Nullable
  public PairedBraceMatcher getPairedBraceMatcher() {
    return null;
  }

  /**
   * Override this method to provide comment-by-block and/or comment-by-line actions implementations for your language
   * For this functionality to work properly {@link ParserDefinition} implementation is necessary.
   *
   * @return <code>Commenter</code> interface implementation for this particular language or <code>null</code>
   * if no auto-commenting capabilities provided.
   */
  @Nullable
  public Commenter getCommenter() {
    return null;
  }

  /**
   * Word completion feature related method. It supposed to return token types of the places where word completion should be enabled.
   * Default implementation delegates to parser definition and returns comment tokens so words completion is enabled in comments
   * if parser definition is implemented.
   * @return set of token types where word completion should be enabled.
   */
  @NotNull
  public TokenSet getReadableTextContainerElements(){
    final ParserDefinition parserDefinition = getParserDefinition();
    if(parserDefinition != null) return parserDefinition.getCommentTokens();
    return TokenSet.EMPTY;
  }

  /**
   * Override this method to provide on-the-fly error highlighting with quickfixes as well as parse tree based syntax annotations
   * like highlighting instance variables in java.
   * For this functionality to work properly {@link ParserDefinition} implementation is necessary.
   * Note that syntax errors flagged by parser in ParserDefinition are highlighted automatically.
   * Annotator is run against changed parts of the parse tree incrementally.
   *
   * @return <code>Annotator</code> interface implementation for this particular language or <code>null</code>
   * if no error and syntax highlighting capabilities provided.
   */
  @Nullable
  public Annotator getAnnotator() {
    return null;
  }

  /**
   * Same as {@link #getAnnotator()} but is being run once against whole file. It's most proper to use when integrating external
   * validation tools like xerces schema validator for XML.
   * @return external annotator for a whole file.
   * Since this annotating is expensive due to nonincrementality, it is run last
   */
  @Nullable
  public ExternalAnnotator getExternalAnnotator() {
    return null;
  }

  /**
   * Override this method to provide find usages capability for the elements of your language
   * For this functionality to work properly {@link ParserDefinition} implementation is necessary.
   *
   * Default implementation returns mock find usages provider uncapable to search anything.
   * @return <code>FindUsagesProvider</code> interface implementation for this particular language.
   */
  @NotNull
  public FindUsagesProvider getFindUsagesProvider() {
    return EMPTY_FIND_USAGES_PROVIDER;
  }

  /**
   * Override this method to provide structure view and file structure popup content for the files of your language.
   *
   * @return <code>StructureViewBuilder</code> interface implementation for this particular language or <code>null</code>
   * if no file structure implementation.
   */
  @Nullable
  public StructureViewBuilder getStructureViewBuilder(PsiElement psiElement) {
    return null;
  }

  /**
   * Override this method to provide common refactorings implementation for the elements of your language.
   * For the time being only safe delete refactoring implemented.
   * Note that rename refactoring will be automatically enabled with <code>FindUsagesProvider</code> and <code>ParserDefinition</code>.
   *
   * @return <code>RefactoringSupportProvider</code> interface implementation for this particular language or <code>null</code>
   * if no safe delete refactoring implementation is necessary.
   */
  @Nullable
  public RefactoringSupportProvider getRefactoringSupportProvider() {
    return null;
  }

  /**
   * Override this method to provide 'surround with...' feature implementation for editors of the files in your language.
   *
   * Default implementation returns empty array of SurroundDescriptor implementations thus disabling the feature.
   * @return <code>SurroundDescriptor</code> interface implementations for this particular language.
   */
  @NotNull
  public SurroundDescriptor[] getSurroundDescriptors() {
    return EMPTY_SURROUND_DESCRIPTORS_ARRAY;
  }

  public String toString() {
    return "Language: " + myID;
  }

  public String[] getMimeTypes(){
    return myMimeTypes;
  }

  public String getID() {
    return myID;
  }
}
