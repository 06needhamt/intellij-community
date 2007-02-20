package net.sf.cglib.proxy;

import net.sf.cglib.core.*;
import net.sf.cglib.asm.Type;

import java.util.List;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Gregory.Shrago
 */
public class BridgeMethodGenerator implements CallbackGenerator {
  public static final InvocationHandlerGenerator INSTANCE = new InvocationHandlerGenerator();

  private final Map<MethodInfo, MethodInfo> myCovariantInfoMap;

  public BridgeMethodGenerator(final Map<MethodInfo, MethodInfo> covariantInfoMap) {
    myCovariantInfoMap = covariantInfoMap;
  }

  public void generate(ClassEmitter ce, CallbackGenerator.Context context, List methods) {
    for (Iterator it = methods.iterator(); it.hasNext();) {
      MethodInfo method = (MethodInfo)it.next();
      final MethodInfo delegate = myCovariantInfoMap.get(method);

      CodeEmitter e = context.beginMethod(ce, method);
      Block handler = e.begin_block();
      e.load_this();
      e.invoke_virtual_this(delegate.getSignature());
      e.return_value();
      handler.end();
      e.end_method();
    }
  }

  public void generateStatic(CodeEmitter e, CallbackGenerator.Context context, List methods) {
  }

}
