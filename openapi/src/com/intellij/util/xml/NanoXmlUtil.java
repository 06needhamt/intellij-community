/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.intellij.util.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.CharSequenceReader;
import net.n3.nanoxml.*;
import org.jetbrains.annotations.NonNls;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.util.Properties;
import java.util.Stack;

/**
 * @author mike
 */
public class NanoXmlUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.NanoXmlUtil");

  private NanoXmlUtil() {
  }

  public static void parseFile(PsiFile psiFile, final IXMLBuilder builder) {
    final Document document = FileDocumentManager.getInstance().getDocument(psiFile.getVirtualFile());
    assert document != null;
    final CharSequenceReader documentReader = new CharSequenceReader(document.getCharsSequence());

    parse(documentReader, builder);
  }

  public static void parse(final Reader reader, final IXMLBuilder builder) {
    try {
      final IXMLParser parser = XMLParserFactory.createDefaultXMLParser();
      IXMLReader r = new MyXMLReader(reader);
      parser.setReader(r);
      parser.setBuilder(builder);
      parser.setValidator(new EmptyValidator());
      parser.setResolver(new EmptyEntityResolver());
      try {
        parser.parse();
      }
      catch (XMLException e) {
        if (e.getException() instanceof ParserStoppedException) return;
        LOG.error(e);
      }
      finally {
        reader.close();
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public static String createLocation(@NonNls String ...tagNames) {
    StringBuffer result = new StringBuffer();
    for (String tagName : tagNames) {
      result.append(".");
      result.append(tagName);
    }

    return result.toString();
  }

  public static class BaseXmlBuilder implements IXMLBuilder {
    private Stack<String> myLocation = new Stack<String>();

    public void startBuilding(String systemID, int lineNr) throws Exception {
      myLocation.push("");
    }

    public void newProcessingInstruction(String target, Reader reader) throws Exception {
    }

    public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
      myLocation.push(myLocation.peek() + "." + name);
    }

    public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
    }

    public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
    }

    public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
      myLocation.pop();
    }

    public void addPCData(Reader reader, String systemID, int lineNr) throws Exception {
    }

    protected String readText(final Reader reader) throws IOException {
      return new String(StreamUtil.readTextAndConvertSeparators(reader));
    }

    public Object getResult() throws Exception {
      return null;
    }

    protected String getLocation() {
      return myLocation.peek();
    }

    protected void stop() {
      throw new ParserStoppedException();
    }
  }

  private static class EmptyValidator extends NonValidator {
    private IXMLEntityResolver myParameterEntityResolver;

    public void setParameterEntityResolver(IXMLEntityResolver resolver) {
      myParameterEntityResolver = resolver;
    }

    public IXMLEntityResolver getParameterEntityResolver() {
      return myParameterEntityResolver;
    }

    public void parseDTD(String publicID, IXMLReader reader, IXMLEntityResolver entityResolver, boolean external) throws Exception {
      if (!external) {
        super.parseDTD(publicID, reader, entityResolver, external);
      }
      else {
        int origLevel = reader.getStreamLevel();

        while (true) {
          char ch = reader.read();

          if (reader.getStreamLevel() < origLevel) {
            reader.unread(ch);
            return; // end external DTD
          }
        }
      }
    }

    public void elementStarted(String name, String systemId, int lineNr) {
    }

    public void elementEnded(String name, String systemId, int lineNr) {
    }

    public void attributeAdded(String key, String value, String systemId, int lineNr) {
    }

    public void elementAttributesProcessed(String name, Properties extraAttributes, String systemId, int lineNr) {
    }

    public void PCDataAdded(String systemId, int lineNr)  {
    }
  }

  private static class EmptyEntityResolver implements IXMLEntityResolver {
    public void addInternalEntity(String name, String value) {
    }

    public void addExternalEntity(String name, String publicID, String systemID) {
    }

    public Reader getEntity(IXMLReader xmlReader, String name) throws XMLParseException {
      return new StringReader("");
    }

    public boolean isExternalEntity(String name) {
      return false;
    }
  }

  private static class MyXMLReader extends StdXMLReader {
    public MyXMLReader(final Reader documentReader) {
      super(documentReader);
    }


    public Reader openStream(String publicID, String systemID) throws MalformedURLException, FileNotFoundException, IOException {
      return new StringReader(" ");
    }
  }

  private static class ParserStoppedException extends RuntimeException {
  }
}
