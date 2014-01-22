package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import de.plushnikov.intellij.plugin.action.BaseLombokAction;
import de.plushnikov.intellij.plugin.processor.clazz.GetterProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.SetterProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.CommonsLogProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Log4j2Processor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Log4jProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.LogProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.Slf4jProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.XSlf4jProcessor;
import de.plushnikov.intellij.plugin.processor.field.GetterFieldProcessor;
import de.plushnikov.intellij.plugin.processor.field.SetterFieldProcessor;
import org.jetbrains.annotations.NotNull;

public class DelombokEverythingAction extends BaseLombokAction {
  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    final BaseDelombokHandler delombokHandler = new BaseDelombokHandler(
        new CommonsLogProcessor(), new Log4jProcessor(), new Log4j2Processor(), new LogProcessor(), new Slf4jProcessor(), new XSlf4jProcessor(),
        new GetterProcessor(),
        new SetterProcessor(),
        new RequiredArgsConstructorProcessor(), new AllArgsConstructorProcessor(), new NoArgsConstructorProcessor());

    delombokHandler.addFieldProcessor(new SetterFieldProcessor(), new GetterFieldProcessor());

    return delombokHandler;
  }

}