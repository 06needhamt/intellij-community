/*
 * Copyright 2010-2015 JetBrains s.r.o.
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
 */

package org.jetbrains.kotlin.console

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.console.highlight.KotlinReplOutputHighlighter
import org.jetbrains.kotlin.diagnostics.Severity
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilderFactory

data class SeverityDetails(val severity: Severity, val description: String, val range: TextRange)

public class KotlinReplOutputHandler(
        private val outputHighlighter: KotlinReplOutputHighlighter,
        process: Process,
        commandLine: String
) : OSProcessHandler(process, commandLine) {

    private val dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    override fun notifyTextAvailable(text: String, key: Key<*>?) {
        // skip "/usr/lib/jvm/java-8-oracle/bin/java -cp ..." intro
        if (!text.startsWith(ReplConstants.XML_PREFIX)) return super.notifyTextAvailable(text, key)

        val output = dBuilder.parse(strToSource(text))
        val root = output.firstChild as Element
        val outputType = root.getAttribute("type")
        val content = StringUtil.replace(root.textContent, ReplConstants.XML_REPLACEMENTS, ReplConstants.SOURCE_CHARS).trim()

        when (outputType) {
            "INITIAL_PROMPT"  -> super.notifyTextAvailable("$content\n", key)
            "USER_OUTPUT"     -> outputHighlighter.printUserOutput(content)
            "REPL_RESULT"     -> outputHighlighter.printResultWithGutterIcon("$content\n")
            "REPL_INCOMPLETE" -> outputHighlighter.changeIndicatorOnIncomplete()
            "ERROR"           -> {
                val compilerMessages = arrayListOf<SeverityDetails>()

                val report = dBuilder.parse(strToSource(content, Charsets.UTF_16))
                val entries = report.getElementsByTagName("reportEntry")
                for (i in 0..entries.length - 1) {
                    val reportEntry = entries.item(i) as Element

                    val severityLevel = reportEntry.getAttribute("severity").toSeverity()
                    val rangeStart = reportEntry.getAttribute("rangeStart").toInt()
                    val rangeEnd = reportEntry.getAttribute("rangeEnd").toInt()
                    val description = reportEntry.textContent

                    compilerMessages.add(SeverityDetails(severityLevel, description, TextRange(rangeStart, rangeEnd)))
                }

                outputHighlighter.highlightErrors(compilerMessages)
            }
            else -> super.notifyTextAvailable("UNEXPECTED TEXT: $content\n", key)
        }
    }

    private fun strToSource(s: String, encoding: Charset = Charsets.UTF_8) = InputSource(ByteArrayInputStream(s.toByteArray(encoding)))

    private fun String.toSeverity() = when (this) {
        "ERROR"   -> Severity.ERROR
        "WARNING" -> Severity.WARNING
        "INFO"    -> Severity.INFO
        else      -> throw IllegalArgumentException("Unsupported Severity: '$this'") // this case shouldn't occur
    }
}