package com.jetbrains.python;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayUtil;
import com.jetbrains.python.psi.*;
import static com.jetbrains.python.psi.PyCallExpression.PyMarkedFunction;
import com.jetbrains.python.psi.impl.ParamHelper;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author yole
 */
public class PyParameterInfoHandler implements ParameterInfoHandler<PyArgumentList, PyArgumentList.AnalysisResult> {
  
  public boolean couldShowInLookup() {
    return true;
  }
  public Object[] getParametersForLookup(final LookupElement item, final ParameterInfoContext context) {
    return new Object[0];  // we don't
  }

  public Object[] getParametersForDocumentation(final PyArgumentList.AnalysisResult p, final ParameterInfoContext context) {
    return new Object[0];  // we don't
  }

  public PyArgumentList findElementForParameterInfo(final CreateParameterInfoContext context) {
    PyArgumentList arglist = findArgumentList(context);
    if (arglist != null) {
      PyArgumentList.AnalysisResult result = arglist.analyzeCall();
      if (result != null && result.getMarkedFunction() != null) {
        context.setItemsToShow(new Object[] { result });
        return arglist;
      }
    }
    return null;
  }

  private static PyArgumentList findArgumentList(final ParameterInfoContext context) {
    return ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), PyArgumentList.class);
  }

  public void showParameterInfo(@NotNull final PyArgumentList element, final CreateParameterInfoContext context) {
    context.showHint(element, element.getTextOffset(), this);
  }

  public PyArgumentList findElementForUpdatingParameterInfo(final UpdateParameterInfoContext context) {
    return findArgumentList(context);
  }

  /**
   <b>Note: instead of parameter index, we directly store parameter's offset for later use.</b><br/>
   We cannot store an index since we cannot determine what is an argument until we actually map arguments to parameters.
   This is because a tuple in arguments may be a whole argument or map to a tuple parameter.
  */
  public void updateParameterInfo(@NotNull final PyArgumentList arglist, final UpdateParameterInfoContext context) {
    if (context.getParameterOwner() != arglist) {
      context.removeHint();
      return;
    }
    // align offset to nearest expression; context may point to a space, etc.
    List<PyExpression> flat_args = PyUtil.flattenedParens(arglist.getArguments());
    int alleged_cursor_offset = context.getOffset(); // this is already shifted backwards to skip spaces
    PsiFile file = context.getFile();
    CharSequence chars = file.getViewProvider().getContents();
    int offset = -1;
    for (PyExpression arg : flat_args) {
      TextRange range = arg.getTextRange();
      // widen the range to include all whitespace around the arg
      int left = CharArrayUtil.shiftBackward(chars, range.getStartOffset()-1, " \t\r\n");
      int right = CharArrayUtil.shiftForwardCarefully(chars, range.getEndOffset(), " \t\r\n");
      if (left <= alleged_cursor_offset && right >= alleged_cursor_offset) {
        offset = range.getStartOffset();
        break;
      }
    }
    context.setCurrentParameter(offset);
  }

  public String getParameterCloseChars() {
    return ",()"; // lpar may mean a nested tuple param, so it's included
  }

  public boolean tracksParameterIndex() {
    return true;
  }

  public void updateUI(final PyArgumentList.AnalysisResult result, final ParameterInfoUIContext context) {
    if (result == null) return;
    PyMarkedFunction marked = result.getMarkedFunction();
    assert marked != null : "findElementForParameterInfo() did it wrong!";
    final PyFunction py_function = marked.getFunction();
    if (py_function == null) return; // resolution failed

    PyParameter[] raw_params = py_function.getParameterList().getParameters();
    final List<PyNamedParameter> n_param_list = new ArrayList<PyNamedParameter>(raw_params.length);
    final List<String> hint_texts = new ArrayList<String>(raw_params.length);

    // param -> hint index. indexes are not contiguous, because some hints are parentheses.
    final Map<PyNamedParameter, Integer> param_indexes = new HashMap<PyNamedParameter, Integer>();
    // formatting of hints: hint index -> flags. this includes flags for parens.
    final Map<Integer, EnumSet<ParameterInfoUIContextEx.Flag>> hint_flags = new HashMap<Integer, EnumSet<ParameterInfoUIContextEx.Flag>>();

    // build the textual picture and the list of named parameters
    ParamHelper.walkDownParamArray(
      py_function.getParameterList().getParameters(),
      new ParamHelper.ParamWalker() {
        public void enterTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          hint_flags.put(hint_texts.size(), EnumSet.noneOf(ParameterInfoUIContextEx.Flag.class));
          hint_texts.add("(");
        }

        public void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          hint_flags.put(hint_texts.size(), EnumSet.noneOf(ParameterInfoUIContextEx.Flag.class));
          if (last) hint_texts.add(")");
          else hint_texts.add("),");
        }

        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          n_param_list.add(param);
          StringBuilder strb = new StringBuilder();
          strb.append(param.getRepr(true));
          if (! last) strb.append(",");
          int hint_index = hint_texts.size();
          param_indexes.put(param, hint_index);
          hint_flags.put(hint_index, EnumSet.noneOf(ParameterInfoUIContextEx.Flag.class));
          hint_texts.add(strb.toString());
        }
      }
    );


    final PyArgumentList arglist = result.getArgumentList();

    final int current_param_offset = context.getCurrentParameterIndex(); // in Python mode, we get an offset here, not an index!

    // gray out enough first parameters as implicit
    for (int i=0; i < marked.getImplicitOffset(); i += 1) {
      hint_flags.get(param_indexes.get(n_param_list.get(i))).add(ParameterInfoUIContextEx.Flag.DISABLE); // show but mark as absent
    }

    // highlight current param(s)
    for (PyExpression arg : PyUtil.flattenedParens(arglist.getArguments())) {
      if (arg.getTextRange().contains(current_param_offset)) {
        PyNamedParameter param = result.getPlainMappedParams().get(arg);
        if (param != null) {
          final Integer param_index = param_indexes.get(param);
          if  (param_index < hint_flags.size()) {
            hint_flags.get(param_index).add(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
          }
        }
        else if (arg == result.getTupleArg()) {
          // mark all params that map to *arg
          for (PyNamedParameter tpar : result.getTupleMappedParams()) {
            final Integer param_index = param_indexes.get(tpar);
            if (param_index != null && param_index < hint_flags.size()) {
              hint_flags.get(param_index).add(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
            }
          }
        }
        else if (arg == result.getKwdArg()) {
          // mark all n_params that map to **arg
          for (PyNamedParameter tpar : result.getKwdMappedParams()) {
            final Integer param_index = param_indexes.get(tpar);
            if (param_index != null && param_index < hint_flags.size()) {
              hint_flags.get(param_index).add(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
            }
          }
        }
        else {
          // maybe it's mapped to a nested tuple?
          List<PyNamedParameter> nparams = result.getNestedMappedParams().get(arg);
          if (nparams != null) {
            for (PyNamedParameter tpar : nparams) {
              final Integer param_index = param_indexes.get(tpar);
              if (param_index != null && param_index < hint_flags.size()) {
                hint_flags.get(param_index).add(ParameterInfoUIContextEx.Flag.HIGHLIGHT);
              }
            }
          }
        }
      }
      // else: stay unhilited
    }

    final String NO_PARAMS_MSG = "<No parameters>";
    String[] hints = ArrayUtil.toStringArray(hint_texts);
    if (context instanceof ParameterInfoUIContextEx) {
      final ParameterInfoUIContextEx pic = (ParameterInfoUIContextEx)context;
      EnumSet<ParameterInfoUIContextEx.Flag>[] flags = new EnumSet[hint_flags.size()];
      for (int i = 0; i < flags.length; i += 1) flags[i] = hint_flags.get(i);
      if (hints.length < 1) {
        hints = new String[]{NO_PARAMS_MSG};
        flags = new EnumSet[]{EnumSet.of(ParameterInfoUIContextEx.Flag.DISABLE)};
      }
      pic.setupUIComponentPresentation(hints, flags, context.getDefaultParameterColor());
    }
    else { // fallback, no hilite
      StringBuilder signatureBuilder = new StringBuilder();
      if (hints.length > 1) {
        for (String s : hints) signatureBuilder.append(s);
      }
      else signatureBuilder.append(NO_PARAMS_MSG);
      context.setupUIComponentPresentation(
        signatureBuilder.toString(), -1, 0, false, false, false, context.getDefaultParameterColor()
      );
    }
  }
}
