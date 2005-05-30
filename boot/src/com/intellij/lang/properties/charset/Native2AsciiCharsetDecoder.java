package com.intellij.lang.properties.charset;

/**
 * @author Alexey
 */

import java.nio.*;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

class Native2AsciiCharsetDecoder extends CharsetDecoder {
  private static final char INVALID_CHAR = (char)-1;
  private final StringBuffer myOutBuffer = new StringBuffer();

  public Native2AsciiCharsetDecoder() {
    super(Native2AsciiCharset.INSTANCE, 1, 6);
  }

  protected void implReset() {
    super.implReset();
    myOutBuffer.setLength(0);
  }

  protected CoderResult implFlush(CharBuffer out) {
    return doFlush(out);
  }

  private CoderResult doFlush(final CharBuffer out) {
    if (myOutBuffer.length() != 0) {
      int remaining = out.remaining();
      int outlen = Math.min(remaining, myOutBuffer.length());
      out.put(myOutBuffer.toString().substring(0,outlen).toCharArray());
      myOutBuffer.delete(0, outlen);
      if (myOutBuffer.length() != 0) return CoderResult.OVERFLOW;
    }
    return CoderResult.UNDERFLOW;
  }

  protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
    byte[] buf = new byte[4];
    try {
      CoderResult coderResult = doFlush(out);
      if (coderResult == CoderResult.OVERFLOW) return CoderResult.OVERFLOW;

      while (in.position() < in.limit()) {
        in.mark();
        final byte b = in.get();
        if (b == '\\') {
          byte next = in.get();
          if (next == 'u') {
            buf[0] = in.get();
            buf[1] = in.get();
            buf[2] = in.get();
            buf[3] = in.get();
            char decoded = unicode(buf);
            if (decoded == INVALID_CHAR) {
              myOutBuffer.append("\\u");
              myOutBuffer.append((char)buf[0]);
              myOutBuffer.append((char)buf[1]);
              myOutBuffer.append((char)buf[2]);
              myOutBuffer.append((char)buf[3]);
            }
            else {
              myOutBuffer.append(decoded);
            }
          }
          else {
            myOutBuffer.append("\\");
            myOutBuffer.append((char)next);
          }
        }
        else {
          buf[0] = b;
          ByteBuffer byteBuffer = ByteBuffer.wrap(buf, 0, 1);
          CharBuffer charBuffer = Native2AsciiCharset.DEFAULT_CHARSET.decode(byteBuffer);
          myOutBuffer.append(charBuffer);
        }
      }
    }
    catch (BufferUnderflowException e) {
      in.reset();
    }
    return doFlush(out);
  }

  private static char unicode(byte[] ord) {
    int d1 = Character.digit((char)ord[0], 16);
    if (d1 == -1) return INVALID_CHAR;
    int d2 = Character.digit((char)ord[1], 16);
    if (d2 == -1) return INVALID_CHAR;
    int d3 = Character.digit((char)ord[2], 16);
    if (d3 == -1) return INVALID_CHAR;
    int d4 = Character.digit((char)ord[3], 16);
    if (d4 == -1) return INVALID_CHAR;
    int b1 = (d1 << 12) & 0xF000;
    int b2 = (d2 << 8) & 0x0F00;
    int b3 = (d3 << 4) & 0x00F0;
    int b4 = (d4 << 0) & 0x000F;
    int code = b1 | b2 | b3 | b4;
    if (Character.isWhitespace(code)) return INVALID_CHAR;
    return (char)code;
  }
}