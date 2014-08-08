/*
 * Copyright 2010-2014 JetBrains s.r.o.
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

package org.jetbrains.jet.plugin.references

import com.intellij.psi.PsiReference
import com.intellij.psi.PsiElement
import org.jetbrains.jet.asJava.unwrapped
import org.jetbrains.jet.plugin.project.AnalyzerFacadeWithCache
import org.jetbrains.jet.lang.resolve.BindingContext
import org.jetbrains.jet.lang.descriptors.ValueParameterDescriptor
import com.intellij.psi.PsiMethod
import org.jetbrains.jet.lang.psi.JetPropertyAccessor
import org.jetbrains.jet.lang.psi.psiUtil.getParentByType
import org.jetbrains.jet.lang.psi.JetProperty
import java.util.HashSet
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.jet.lang.psi.JetNamedDeclaration

// Navigation element of the resolved reference
// For property accessor return enclosing property
val PsiReference.unwrappedTargets: Set<PsiElement>
    get() {
        fun PsiElement.adjust(): PsiElement? {
            val target = unwrapped
            return if (target is JetPropertyAccessor) target.getParentByType(javaClass<JetProperty>()) else target
        }

        return when (this) {
            is JetMultiReference<*> -> multiResolve(false).map { it.getElement()?.adjust() }.filterNotNullTo(HashSet<PsiElement>())
            else -> ContainerUtil.createMaybeSingletonSet(resolve()?.adjust())
        }
    }

fun PsiReference.matchesTarget(target: PsiElement): Boolean {
    val unwrapped = target.unwrapped
    return when {
        unwrapped in unwrappedTargets ->
            true

        this is JetReference
                && unwrappedTargets.any { it is PsiMethod && it.isConstructor() && it.getContainingClass() == unwrapped } ->
            true

        else ->
            false
    }
}
