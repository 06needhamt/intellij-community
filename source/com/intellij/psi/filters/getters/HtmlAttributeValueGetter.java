package com.intellij.psi.filters.getters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 24.11.2003
 * Time: 14:17:59
 * To change this template use Options | File Templates.
 */
public class HtmlAttributeValueGetter extends XmlAttributeValueGetter {
  private boolean myCaseSensitive;

  public HtmlAttributeValueGetter(boolean _caseSensitive) {
    myCaseSensitive = _caseSensitive;
  }

  @Nullable
  @NonNls
  protected String[] addSpecificCompletions(final PsiElement context) {
    if (!(context instanceof XmlAttribute)) return null;

    XmlAttribute attribute = (XmlAttribute)context;
    @NonNls String name = attribute.getName();
    final XmlTag tag = attribute.getParent();

    @NonNls String tagName = tag != null ? tag.getName() : "";
    if (!myCaseSensitive) {
      name = name.toLowerCase();
      tagName = tagName.toLowerCase();
    }

    final String namespace = tag.getNamespace();
    if (XmlUtil.XHTML_URI.equals(namespace) || XmlUtil.HTML_URI.equals(namespace)) {

      if ("target".equals(name)) {
        return new String[]{"_blank", "_top", "_self", "_parent"};
      }
      else if ("enctype".equals(name)) {
        return new String[]{"multipart/form-data", "application/x-www-form-urlencoded"};
      }
      else if ("rel".equals(name) || "rev".equals(name)) {
        return new String[]{"alternate", "stylesheet", "start", "next", "prev", "contents", "index", "glossary", "copyright", "chapter",
          "section", "subsection", "appendix", "help", "bookmark", "script"};
      }
      else if ("media".equals(name)) {
        return new String[]{"screen", "tty", "tv", "projection", "handheld", "print", "all", "aural", "braille"};
      }
      else if ("language".equals(name)) {
        return new String[]{"JavaScript", "VBScript", "JScript", "JavaScript1.2", "JavaScript1.3", "JavaScript1.4", "JavaScript1.5"};
      }
      else if ("type".equals(name) && "link".equals(tagName)) {
        return new String[]{"text/css", "text/html", "text/plain", "text/xml"};
      }
      else if ("http-equiv".equals(name) && "meta".equals(tagName)) {
        return new String[]{"Accept", "Accept-Charset", "Accept-Encoding", "Accept-Language", "Accept-Ranges", "Age", "Allow",
          "Authorization", "Cache-Control", "Connection", "Content-Encoding", "Content-Language", "Content-Length", "Content-Location",
          "Content-MD5", "Content-Range", "Content-Type", "Date", "ETag", "Expect", "Expires", "From", "Host", "If-Match",
          "If-Modified-Since", "If-None-Match", "If-Range", "If-Unmodified-Since", "Last-Modified", "Location", "Max-Forwards", "Pragma",
          "Proxy-Authenticate", "Proxy-Authorization", "Range", "Referer", "Retry-After", "Server", "TE", "Trailer", "Transfer-Encoding",
          "Upgrade", "User-Agent", "Vary", "Via", "Warning", "WWW-Authenticate"};
      }
    }

    return null;
  }
}
