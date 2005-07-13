package com.intellij.formatting;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import java.util.*;

class InitialInfoBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.InitialInfoBuilder");

  private WhiteSpace myCurrentWhiteSpace;
  private final FormattingDocumentModel myModel;
  private TextRange myAffectedRange;
  private final Map<Block, AbstractBlockWrapper> myResult = new LinkedHashMap<Block, AbstractBlockWrapper>();
  private LeafBlockWrapper myPreviousBlock;
  private LeafBlockWrapper myFirstTokenBlock;
  private SpacingImpl myCurrentSpaceProperty;
  private final CodeStyleSettings.IndentOptions myOptions;

  private InitialInfoBuilder(final FormattingDocumentModel model,
                             final TextRange affectedRange,
                             final CodeStyleSettings.IndentOptions options) {
    myModel = model;
    myAffectedRange = affectedRange;
    myCurrentWhiteSpace = new WhiteSpace(0, true);
    myOptions = options;
  }

  public static final InitialInfoBuilder buildBlocks(Block root,
                                                     FormattingDocumentModel model,
                                                     final TextRange affectedRange,
                                                     final CodeStyleSettings.IndentOptions options) {
    final InitialInfoBuilder builder = new InitialInfoBuilder(model, affectedRange, options);
    final AbstractBlockWrapper wrapper = builder.buildFrom(root, 0, null, null, root.getTextRange());
    wrapper.setIndent((IndentImpl)Indent.getNoneIndent());
    return builder;
  }

  private AbstractBlockWrapper buildFrom(final Block rootBlock,
                                         final int index,
                                         final AbstractBlockWrapper parent,
                                         WrapImpl currentWrapParent,
                                         final TextRange textRange) {
    final WrapImpl wrap = ((WrapImpl)rootBlock.getWrap());
    if (wrap != null) {
      wrap.registerParent(currentWrapParent);
      currentWrapParent = wrap;
    }
    final int blockStartOffset = textRange.getStartOffset();

    if (parent != null) {
      if (textRange.getStartOffset() < parent.getTextRange().getStartOffset()) {
        LOG.assertTrue(false, FormatterImpl.getText(myModel));
      }
      if (textRange.getEndOffset() > parent.getTextRange().getEndOffset()) {
        LOG.assertTrue(false, FormatterImpl.getText(myModel));
      }
    }

    myCurrentWhiteSpace.append(blockStartOffset, myModel, myOptions);
    boolean isReadOnly = isReadOnly(textRange);

    if (isReadOnly) {
      return processSimpleBlock(rootBlock, parent, isReadOnly, textRange, index);
    }
    else {
      final List<Block> subBlocks = rootBlock.getSubBlocks();
      if (subBlocks.isEmpty()) {
        return processSimpleBlock(rootBlock, parent, isReadOnly, textRange, index);
      }
      else {
        return processCompositeBlock(rootBlock, parent, textRange, index, subBlocks, currentWrapParent);
      }

    }
  }

  private AbstractBlockWrapper processCompositeBlock(final Block rootBlock,
                                                     final AbstractBlockWrapper parent,
                                                     final TextRange textRange,
                                                     final int index,
                                                     final List<Block> subBlocks, final WrapImpl currentWrapParent) {
    final CompositeBlockWrapper info = new CompositeBlockWrapper(rootBlock, myCurrentWhiteSpace, parent, textRange);
    if (index == 0) {
      info.arrangeParentTextRange();
    }
    myResult.put(rootBlock, info);

    Block previous = null;
    List<AbstractBlockWrapper> list = new ArrayList<AbstractBlockWrapper>();
    for (int i = 0; i < subBlocks.size(); i++) {
      final Block block = subBlocks.get(i);
      if (previous != null) {
        myCurrentSpaceProperty = (SpacingImpl)rootBlock.getSpacing(previous, block);
      }
      final TextRange blockRange = block.getTextRange();
      final AbstractBlockWrapper wrapper = buildFrom(block, i, info, currentWrapParent, blockRange);
      list.add(wrapper);
      final IndentImpl indent = (IndentImpl)block.getIndent();
      wrapper.setIndent(indent);
      previous = block;
    }
    setDefaultIndents(list);
    return info;
  }

  private void setDefaultIndents(final List<AbstractBlockWrapper> list) {
    if (!list.isEmpty()) {
      for (Iterator<AbstractBlockWrapper> iterator = list.iterator(); iterator.hasNext();) {
        AbstractBlockWrapper wrapper = iterator.next();
        if (wrapper.getIndent() == null) {
          wrapper.setIndent((IndentImpl)Indent.getContinuationWithoutFirstIndent());
        }
      }
    }
  }

  private AbstractBlockWrapper processSimpleBlock(final Block rootBlock,
                                                  final AbstractBlockWrapper parent,
                                                  final boolean readOnly,
                                                  final TextRange textRange, final int index) {
    final LeafBlockWrapper info = new LeafBlockWrapper(rootBlock, parent, myCurrentWhiteSpace, myModel, myPreviousBlock, readOnly,
                                                       textRange);
    if (index == 0) {
      info.arrangeParentTextRange();
    }
    myResult.put(rootBlock, info);

    if (textRange.getLength() == 0) {
      LOG.assertTrue(false);
    }
    if (myPreviousBlock != null) {
      myPreviousBlock.setNextBlock(info);
    }
    if (myFirstTokenBlock == null) {
      myFirstTokenBlock = info;
    }
    if (currentWhiteSpaceIsRreadOnly()) {
      myCurrentWhiteSpace.setReadOnly(true);
    }
    if (myCurrentSpaceProperty != null) {
      myCurrentWhiteSpace.setIsSafe(myCurrentSpaceProperty.isSafe());
      myCurrentWhiteSpace.setKeepFirstColumn(myCurrentSpaceProperty.shouldKeepFirstColumn());
    }

    info.setSpaceProperty(myCurrentSpaceProperty);
    myCurrentWhiteSpace = new WhiteSpace(textRange.getEndOffset(), false);
    myPreviousBlock = info;
    return info;
  }

  private boolean currentWhiteSpaceIsRreadOnly() {
    if (myCurrentSpaceProperty != null && myCurrentSpaceProperty.isReadOnly()) {
      return true;
    }
    else {
      if (myAffectedRange == null) return false;
      final TextRange textRange = myCurrentWhiteSpace.getTextRange();

      if (textRange.getStartOffset() >= myAffectedRange.getEndOffset()) return true;
      if (textRange.getEndOffset() < myAffectedRange.getStartOffset()) return true;
      return false;
    }
  }

  private boolean isReadOnly(final TextRange textRange) {
    if (myAffectedRange == null) return false;
    if (textRange.getStartOffset() > myAffectedRange.getEndOffset()) return true;
    if (textRange.getEndOffset() < myAffectedRange.getStartOffset()) return true;
    return false;
  }

  public Map<Block, AbstractBlockWrapper> getBlockToInfoMap() {
    return myResult;
  }

  public LeafBlockWrapper getFirstTokenBlock() {
    return myFirstTokenBlock;
  }
}
