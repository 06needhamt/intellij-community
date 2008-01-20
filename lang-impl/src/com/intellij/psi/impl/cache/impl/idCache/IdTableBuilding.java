package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.ide.highlighter.custom.impl.CustomFileType;
import com.intellij.ide.startup.FileContent;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.cacheBuilder.CacheBuilderRegistry;
import com.intellij.lang.cacheBuilder.SimpleWordsScanner;
import com.intellij.lang.cacheBuilder.WordOccurrence;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.lexer.FilterLexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.cache.impl.CacheManagerImpl;
import com.intellij.psi.impl.cache.impl.CacheUtil;
import com.intellij.psi.impl.cache.index.FileTypeIdIndexer;
import com.intellij.psi.impl.cache.index.IdIndexEntry;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.IndexDataConsumer;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IdTableBuilding {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.cache.impl.idCache.IdTableBuilding");

  private static final int FILE_SIZE_LIMIT = 10000000; // ignore files of size > 10Mb

  private IdTableBuilding() {
  }

  public static class Result {
    final Runnable runnable;
    final TIntIntHashMap wordsMap;
    final int[] todoCounts;

    private Result(Runnable runnable, TIntIntHashMap wordsMap, int[] todoCounts) {
      this.runnable = runnable;
      this.wordsMap = wordsMap;
      this.todoCounts = todoCounts;
    }
  }

  public interface IdCacheBuilder {
    void build(CharSequence chars,
               int length,
               TIntIntHashMap wordsTable,
               IndexPattern[] todoPatterns,
               int[] todoCounts,
               final PsiManager manager);
  }

  public interface ScanWordProcessor {
    void run (CharSequence chars, int start, int end, @Nullable char[] charArray);
  }

  static class TextIdCacheBuilder implements IdCacheBuilder {
    public void build(CharSequence chars,
                      int length,
                      TIntIntHashMap wordsTable,
                      IndexPattern[] todoPatterns,
                      int[] todoCounts,
                      final PsiManager manager) {
      scanWords(wordsTable, chars, 0, length, UsageSearchContext.IN_PLAIN_TEXT);

      if (todoCounts != null) {
        for (int index = 0; index < todoPatterns.length; index++) {
          Pattern pattern = todoPatterns[index].getPattern();
          if (pattern != null) {
            Matcher matcher = pattern.matcher(chars);
            while (matcher.find()) {
              if (matcher.start() != matcher.end()) {
                todoCounts[index]++;
              }
            }
          }
        }
      }
    }
  }

  public static class PlainTextIndexer extends FileTypeIdIndexer {
  
    public void map(final FileBasedIndex.FileContent inputData, final IndexDataConsumer<IdIndexEntry, Void> consumer) {
      final CharSequence chars = inputData.content;
      scanWords(new ScanWordProcessor(){
        public void run(final CharSequence chars11, final int start, final int end, char[] charsArray) {
          if (charsArray != null) {
            addOccurrence(consumer, charsArray, start, end, (int)UsageSearchContext.IN_PLAIN_TEXT);
          } else {
            addOccurrence(consumer, chars11, start, end, (int)UsageSearchContext.IN_PLAIN_TEXT);
          }
        }
      }, chars, 0, chars.length());

      /*
      if (todoCounts != null) {
        for (int index = 0; index < todoPatterns.length; index++) {
          Pattern pattern = todoPatterns[index].getPattern();
          if (pattern != null) {
            Matcher matcher = pattern.matcher(chars);
            while (matcher.find()) {
              if (matcher.start() != matcher.end()) {
                todoCounts[index]++;
              }
            }
          }
        }
      }
      */
    }

  }
  
  static class EmptyBuilder implements IdCacheBuilder {
    public void build(CharSequence chars,
                      int length,
                      TIntIntHashMap wordsTable,
                      IndexPattern[] todoPatterns,
                      int[] todoCounts,
                      final PsiManager manager) {
      // Do nothing. This class is used to skip certain files from building caches for them.
    }
  }

  private static final HashMap<FileType,IdCacheBuilder> cacheBuilders = new HashMap<FileType, IdCacheBuilder>();
  private static final HashMap<FileType,FileTypeIdIndexer> ourIdIndexers = new HashMap<FileType, FileTypeIdIndexer>();

  public static void registerCacheBuilder(FileType fileType,IdCacheBuilder idCacheBuilder) {
    cacheBuilders.put(fileType, idCacheBuilder);
  }

  public static void registerIdIndexer(FileType fileType,FileTypeIdIndexer indexer) {
    ourIdIndexers.put(fileType, indexer);
  }

  public static boolean isIdIndexerRegistered(FileType fileType) {
    return ourIdIndexers.containsKey(fileType);
  }
  
  static {
    //registerCacheBuilder(FileTypes.PLAIN_TEXT,new TextIdCacheBuilder());
    registerIdIndexer(FileTypes.PLAIN_TEXT,new PlainTextIndexer());

    //registerCacheBuilder(StdFileTypes.IDEA_MODULE, new EmptyBuilder());
    //registerCacheBuilder(StdFileTypes.IDEA_WORKSPACE, new EmptyBuilder());
    //registerCacheBuilder(StdFileTypes.IDEA_PROJECT, new EmptyBuilder());
  }

  @Nullable
  public static IdCacheBuilder getCacheBuilder(FileType fileType, final Project project, final VirtualFile virtualFile) {
    return null;
    /*
    final IdCacheBuilder idCacheBuilder = cacheBuilders.get(fileType);

    if (idCacheBuilder != null) return idCacheBuilder;

    final WordsScanner customWordsScanner = CacheBuilderRegistry.getInstance().getCacheBuilder(fileType);
    if (customWordsScanner != null) {
      return new WordsScannerIdCacheBuilderAdapter(customWordsScanner, null, null, virtualFile);
    }

    final SyntaxHighlighter highlighter = SyntaxHighlighter.PROVIDER.create(fileType, project, virtualFile);
    if (fileType instanceof LanguageFileType) {
      final Language lang = ((LanguageFileType)fileType).getLanguage();
      final FindUsagesProvider findUsagesProvider = LanguageFindUsages.INSTANCE.forLanguage(lang);
      WordsScanner scanner = findUsagesProvider == null ? null : findUsagesProvider.getWordsScanner();
      if (scanner == null) {
        scanner = new SimpleWordsScanner();
      }
      final ParserDefinition parserDef = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
      final TokenSet commentTokens = parserDef != null ? parserDef.getCommentTokens() : null;
      return new WordsScannerIdCacheBuilderAdapter(scanner, highlighter, commentTokens, virtualFile);
    }

    if (fileType instanceof CustomFileType) {
      final TokenSet commentTokens = TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT,
                                                     CustomHighlighterTokenType.MULTI_LINE_COMMENT);

      return new WordsScannerIdCacheBuilderAdapter(((CustomFileType)fileType).getWordsScanner(),
                                                   highlighter, commentTokens, virtualFile);
    }

    return null;
    */
  }

  private static class WordsScannerIdCacheBuilderAdapter implements IdCacheBuilder {
    private WordsScanner myScanner;
    @Nullable private final SyntaxHighlighter myHighlighter;
    @Nullable private final TokenSet myCommentTokens;
    private final VirtualFile myFile;

    public WordsScannerIdCacheBuilderAdapter(@NotNull final WordsScanner scanner,
                                             @Nullable final SyntaxHighlighter highlighter,
                                             @Nullable final TokenSet commentTokens,
                                             @NotNull final VirtualFile file) {
      myScanner = scanner;
      myHighlighter = highlighter;
      myCommentTokens = commentTokens;
      myFile = file;
    }

    public void build(final CharSequence chars,
                      int length,
                      final TIntIntHashMap wordsTable,
                      final IndexPattern[] todoPatterns,
                      final int[] todoCounts,
                      final PsiManager manager) {
      if (length == 0) return;

      final char[] charsArray = CharArrayUtil.fromSequenceWithoutCopying(chars);

      myScanner.processWords(chars, new Processor<WordOccurrence>() {
        public boolean process(final WordOccurrence t) {
          if(charsArray != null && t.getBaseText() == chars) {
            IdCacheUtil.addOccurrence(wordsTable, charsArray, t.getStart(),t.getEnd(),convertToMask(t.getKind()));
          } else {
            IdCacheUtil.addOccurrence(wordsTable, t.getBaseText(), t.getStart(),t.getEnd(),convertToMask(t.getKind()));
          }
          return true;
        }

        private int convertToMask(final WordOccurrence.Kind kind) {
          if (kind == null) return UsageSearchContext.ANY;
          if (kind == WordOccurrence.Kind.CODE) return UsageSearchContext.IN_CODE;
          if (kind == WordOccurrence.Kind.COMMENTS) return UsageSearchContext.IN_COMMENTS;
          if (kind == WordOccurrence.Kind.LITERALS) return UsageSearchContext.IN_STRINGS;
          if (kind == WordOccurrence.Kind.FOREIGN_LANGUAGE) return UsageSearchContext.IN_FOREIGN_LANGUAGES;
          return 0;
        }
      });

      if (myHighlighter != null && myCommentTokens != null && todoCounts != null) {
        final EditorHighlighter highlighter = HighlighterFactory.createHighlighter(manager.getProject(), myFile);
        highlighter.setText(chars);

        final HighlighterIterator iterator = highlighter.createIterator(0);
        while (!iterator.atEnd()) {
          final IElementType token = iterator.getTokenType();
          if (IdCacheUtil.isInComments(token) || myCommentTokens.contains(token)) {
            BaseFilterLexer.advanceTodoItemsCount(chars.subSequence(iterator.getStart(), iterator.getEnd()), todoCounts);
          }

          iterator.advance();
        }
      }
    }
  }

  @Nullable
  public static FileTypeIdIndexer getFileTypeIndexer(FileType fileType, final VirtualFile virtualFile) {
    final FileTypeIdIndexer idIndexer = ourIdIndexers.get(fileType);

    if (idIndexer != null) {
      return idIndexer;
    }

    final WordsScanner customWordsScanner = CacheBuilderRegistry.getInstance().getCacheBuilder(fileType);
    if (customWordsScanner != null) {
      return new WordsScannerFileTypeIdIndexerAdapter(customWordsScanner, null, null, virtualFile);
    }

    final SyntaxHighlighter highlighter = SyntaxHighlighter.PROVIDER.create(fileType, null, virtualFile);
    if (fileType instanceof LanguageFileType) {
      final Language lang = ((LanguageFileType)fileType).getLanguage();
      final FindUsagesProvider findUsagesProvider = LanguageFindUsages.INSTANCE.forLanguage(lang);
      WordsScanner scanner = findUsagesProvider == null ? null : findUsagesProvider.getWordsScanner();
      if (scanner == null) {
        scanner = new SimpleWordsScanner();
      }
      final ParserDefinition parserDef = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
      final TokenSet commentTokens = parserDef != null ? parserDef.getCommentTokens() : null;
      return new WordsScannerFileTypeIdIndexerAdapter(scanner, highlighter, commentTokens, virtualFile);
    }

    if (fileType instanceof CustomFileType) {
      final TokenSet commentTokens = TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT,
                                                     CustomHighlighterTokenType.MULTI_LINE_COMMENT);

      return new WordsScannerFileTypeIdIndexerAdapter(((CustomFileType)fileType).getWordsScanner(), highlighter, commentTokens, virtualFile);
    }

    return null;
  }

  private static class WordsScannerFileTypeIdIndexerAdapter extends FileTypeIdIndexer {
    private WordsScanner myScanner;
    @Nullable private final SyntaxHighlighter myHighlighter;
    @Nullable private final TokenSet myCommentTokens;
    private final VirtualFile myFile;

    public WordsScannerFileTypeIdIndexerAdapter(@NotNull final WordsScanner scanner,
                                             @Nullable final SyntaxHighlighter highlighter,
                                             @Nullable final TokenSet commentTokens,
                                             @NotNull final VirtualFile file) {
      myScanner = scanner;
      myHighlighter = highlighter;
      myCommentTokens = commentTokens;
      myFile = file;
    }

    public void map(final FileBasedIndex.FileContent inputData, final IndexDataConsumer<IdIndexEntry, Void> consumer) {
      final CharSequence chars = inputData.content;
      final char[] charsArray = CharArrayUtil.fromSequenceWithoutCopying(chars);

      myScanner.processWords(chars, new Processor<WordOccurrence>() {
        public boolean process(final WordOccurrence t) {
          if(charsArray != null && t.getBaseText() == chars) {
            addOccurrence(consumer, charsArray, t.getStart(),t.getEnd(),convertToMask(t.getKind()));
          } 
          else {
            addOccurrence(consumer, t.getBaseText(), t.getStart(),t.getEnd(),convertToMask(t.getKind()));
          }
          return true;
        }

        private int convertToMask(final WordOccurrence.Kind kind) {
          if (kind == null) return UsageSearchContext.ANY;
          if (kind == WordOccurrence.Kind.CODE) return UsageSearchContext.IN_CODE;
          if (kind == WordOccurrence.Kind.COMMENTS) return UsageSearchContext.IN_COMMENTS;
          if (kind == WordOccurrence.Kind.LITERALS) return UsageSearchContext.IN_STRINGS;
          if (kind == WordOccurrence.Kind.FOREIGN_LANGUAGE) return UsageSearchContext.IN_FOREIGN_LANGUAGES;
          return 0;
        }
      });
    }
  }

  @Nullable
  public static Result getBuildingRunnable(final PsiManagerImpl manager, FileContent fileContent, final boolean buildTodos) {
    if (LOG.isDebugEnabled()){
      LOG.debug(
        "enter: getBuildingRunnable(file='" + fileContent.getVirtualFile() + "' buildTodos='" + buildTodos + "' )"
      );
      //LOG.debug(new Throwable());
    }

    final VirtualFile virtualFile = fileContent.getVirtualFile();
    LOG.assertTrue(virtualFile.isValid());

    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (fileTypeManager.isFileIgnored(virtualFile.getName())) return null;
    final FileType fileType = fileTypeManager.getFileTypeByFile(virtualFile);
    if (fileType.isBinary()) return null;
    if (StdFileTypes.CLASS.equals(fileType)) return null;

    // Still we have to have certain limit since there might be virtually unlimited resources like xml, sql etc, which
    // once loaded will produce OutOfMemoryError
    if (fileType != StdFileTypes.JAVA && virtualFile.getLength() > FILE_SIZE_LIMIT) return null;

    final TIntIntHashMap wordsTable = new TIntIntHashMap();

    final int[] todoCounts;
    final IndexPattern[] todoPatterns = IdCacheUtil.getIndexPatterns();
    if (buildTodos && CacheManagerImpl.canContainTodoItems(fileContent.getVirtualFile())){
      int patternCount = todoPatterns.length;
      todoCounts = patternCount > 0 ? new int[patternCount] : null;
    }
    else{
      todoCounts = null;
    }

    final CharSequence text = CacheUtil.getContentText(fileContent);

    final IdCacheBuilder cacheBuilder = getCacheBuilder(fileType, manager.getProject(), virtualFile);

    if (cacheBuilder==null) return null;

    Runnable runnable = new Runnable() {
      public void run() {
        synchronized (PsiLock.LOCK) {
          cacheBuilder.build(text, text.length(), wordsTable, todoPatterns, todoCounts, manager);
        }
      }
    };

    return new Result(runnable, wordsTable, todoCounts);
  }

  public static final FilterLexer.Filter TOKEN_FILTER = new FilterLexer.Filter() {
    public boolean reject(IElementType type) {
      return !(type instanceof IJavaElementType) || StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET.contains(type);
    }
  };

  public static void scanWords(final ScanWordProcessor processor, final CharSequence chars, final int startOffset, final int endOffset) {
    scanWords(processor, chars, CharArrayUtil.fromSequenceWithoutCopying(chars), startOffset, endOffset, false);
  }

  public static void scanWords(final ScanWordProcessor processor, final CharSequence chars, final @Nullable char[] charArray, final int startOffset,
                               final int endOffset,
                               final boolean mayHaveEscapes) {
    int index = startOffset;
    final boolean hasArray = charArray != null;

    ScanWordsLoop:
      while(true){
        while(true){
          if (index >= endOffset) break ScanWordsLoop;
          final char c = hasArray ? charArray[index]:chars.charAt(index);

          if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || (Character.isJavaIdentifierStart(c) && c != '$')) break;
          index++;
          if (mayHaveEscapes && c == '\\') index++; //the next symbol is for escaping
        }
        int index1 = index;
        while(true){
          index++;
          if (index >= endOffset) break;
          final char c = hasArray ? charArray[index]:chars.charAt(index);
          if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) continue;
          if (!Character.isJavaIdentifierPart(c) || c == '$') break;
        }
        if (index - index1 > 100) continue; // Strange limit but we should have some!

        processor.run(chars, index1, index, charArray);
      }
  }

  public static void scanWords(final TIntIntHashMap table,
                               final CharSequence chars,
                               final int start,
                               final int end,
                               final int occurrenceMask) {
    scanWords(new ScanWordProcessor(){
      public void run(final CharSequence chars, final int start, final int end, char[] charsArray) {
        if (charsArray != null) {
          IdCacheUtil.addOccurrence(table, charsArray, start, end, occurrenceMask);
        } else {
          IdCacheUtil.addOccurrence(table, chars, start, end, occurrenceMask);
        }
      }
    }, chars, start, end);
  }
}
