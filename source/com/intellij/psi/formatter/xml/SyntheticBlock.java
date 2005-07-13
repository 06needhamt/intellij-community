package com.intellij.psi.formatter.xml;

import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SyntheticBlock extends AbstractSyntheticBlock implements Block{
  private final List<Block> mySubBlocks;
  private final Indent myChildIndent;

  SyntheticBlock(final List<Block> subBlocks, final Block parent, final Indent indent, XmlFormattingPolicy policy, final Indent childIndent) {
    super(subBlocks, parent, policy, indent);
    mySubBlocks = subBlocks;
    myChildIndent = childIndent;
  }

  @NotNull
  public TextRange getTextRange() {
    return calculateTextRange(mySubBlocks);
  }

  @NotNull
  public List<Block> getSubBlocks() {
    return mySubBlocks;
  }

  public Spacing getSpacing(Block child1, Block child2) {
    if (child1 instanceof ReadOnlyBlock || child2 instanceof ReadOnlyBlock) {
      return Spacing.getReadOnlySpacing();
    }
    if (!(child1 instanceof AbstractXmlBlock) || !(child2 instanceof AbstractXmlBlock)) {
      return null;
    }
    final ASTNode node1 = ((AbstractBlock)child1).getNode();
    final ASTNode node2 = ((AbstractBlock)child2).getNode();

    final IElementType type1 = node1.getElementType();
    final IElementType type2 = node2.getElementType();

    boolean firstIsText = isTextFragment(node1);
    boolean secondIsText = isTextFragment(node2);

    boolean firstIsTag = node1.getPsi() instanceof XmlTag;
    boolean secondIsTag = node2.getPsi() instanceof XmlTag;

    if (isSpaceInText(firstIsTag, secondIsTag, firstIsText, secondIsText) && keepWhiteSpaces()) {
        return Spacing.getReadOnlySpacing();
    }

    if (type1 == ElementType.XML_NAME && type2 == ElementType.XML_EMPTY_ELEMENT_END && myXmlFormattingPolicy.addSpaceIntoEmptyTag()) {
      return Spacing.createSpacing(1, 1, 0,
                                   myXmlFormattingPolicy.getShouldKeepLineBreaks(),
                                   myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (isXmlTagName(type1, type2)){
      final int spaces = shouldAddSpaceAroundTagName(node1, node2) ? 1 : 0;
      return Spacing.createSpacing(spaces, spaces, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (type2 == ElementType.XML_ATTRIBUTE) {
      return Spacing.createSpacing(1, 1, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (((AbstractXmlBlock)child1).isTextElement() && ((AbstractXmlBlock)child2).isTextElement()) {
      return Spacing.createSafeSpacing(myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    }

    if ((firstIsText || firstIsTag) && secondIsTag) {
      //<tag/>text <tag/></tag>
      if (((AbstractXmlBlock)child2).insertLineBreakBeforeTag()) {
        return Spacing.createSpacing(0, Integer.MAX_VALUE, 2, myXmlFormattingPolicy.getShouldKeepLineBreaks(),
                                     myXmlFormattingPolicy.getKeepBlankLines());
      } else if (((AbstractXmlBlock)child2).removeLineBreakBeforeTag()) {
        return Spacing.createSpacing(0, Integer.MAX_VALUE, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(),
                                     myXmlFormattingPolicy.getKeepBlankLines());
      }
    }


    if (firstIsTag && secondIsText) {     //<tag/>-text
      if (((AbstractXmlBlock)child1).isTextElement()) {
        return Spacing.createSafeSpacing(myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
      } else {
        return Spacing.createSpacing(0, 0, 0, true, myXmlFormattingPolicy.getKeepBlankLines());
      }
    }

    if ( firstIsText && secondIsTag) {     //text-<tag/>
      if (((AbstractXmlBlock)child2).isTextElement()) {
        return Spacing.createSafeSpacing(true, myXmlFormattingPolicy.getKeepBlankLines());
      } else {
        return Spacing.createSpacing(0, 0, 0, true, myXmlFormattingPolicy.getKeepBlankLines());
      }
    }

    if (firstIsTag && secondIsTag) {//<tag/><tag/>
      return Spacing.createSpacing(0, Integer.MAX_VALUE, 0, true,
                                   myXmlFormattingPolicy.getKeepBlankLines());
    }

    return Spacing.createSpacing(0, Integer.MAX_VALUE, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
  }

  private boolean shouldAddSpaceAroundTagName(final ASTNode node1, final ASTNode node2) {
    if (node1.getElementType() == ElementType.XML_START_TAG_START && node1.textContains('%')) return true;
    if (node2.getElementType() == ElementType.XML_EMPTY_ELEMENT_END && node2.textContains('%')) return true;
    return myXmlFormattingPolicy.getShouldAddSpaceAroundTagName();
  }

  private boolean isSpaceInText(final boolean firstIsTag,
                                final boolean secondIsTag,
                                final boolean firstIsText,
                                final boolean secondIsText) {
    return
      (firstIsText && secondIsText)
      || (firstIsTag && secondIsTag)
      || (firstIsTag && secondIsText)
      || (firstIsText && secondIsTag);
  }

  private boolean keepWhiteSpaces() {
    return (myXmlFormattingPolicy.keepWhiteSpacesInsideTag( getTag()) || myXmlFormattingPolicy.getShouldKeepWhiteSpaces());
  }

  private boolean isTextFragment(final ASTNode node1) {
    return node1.getTreeParent().getElementType() == ElementType.XML_TEXT || node1.getElementType() == ElementType.XML_DATA_CHARACTERS;
  }

  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(myChildIndent, null);
  }

  public boolean isIncomplete() {
    return getSubBlocks().get(getSubBlocks().size() - 1).isIncomplete();
  }

}
