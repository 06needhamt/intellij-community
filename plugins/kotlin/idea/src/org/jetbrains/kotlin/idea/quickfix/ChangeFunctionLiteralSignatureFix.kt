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

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.JetBundle
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.core.KotlinNameSuggester
import org.jetbrains.kotlin.idea.refactoring.changeSignature.*
import org.jetbrains.kotlin.psi.JetFile
import org.jetbrains.kotlin.psi.JetFunctionLiteral
import org.jetbrains.kotlin.types.JetType

class ChangeFunctionLiteralSignatureFix(
        functionLiteral: JetFunctionLiteral,
        functionDescriptor: FunctionDescriptor,
        private val parameterTypes: List<JetType>
) : ChangeFunctionSignatureFix(functionLiteral, functionDescriptor) {

    override fun getText() = JetBundle.message("change.function.literal.signature")

    override fun invoke(project: Project, editor: Editor?, file: JetFile) {
        runChangeSignature(
                project,
                functionDescriptor,
                object : JetChangeSignatureConfiguration {
                    override fun configure(originalDescriptor: JetMethodDescriptor): JetMethodDescriptor {
                        return originalDescriptor.modify { descriptor ->
                            val validator = CollectingNameValidator()
                            descriptor.clearNonReceiverParameters()
                            for (type in parameterTypes) {
                                val name = KotlinNameSuggester.suggestNamesByType(type, validator, "param").get(0)
                                descriptor.addParameter(JetParameterInfo(functionDescriptor, -1, name, type, null, null, JetValVar.None, null))
                            }
                        }
                    }

                    override fun performSilently(affectedFunctions: Collection<PsiElement>) = false
                    override fun forcePerformForSelectedFunctionOnly() = false
                },
                context,
                text)
    }
}
