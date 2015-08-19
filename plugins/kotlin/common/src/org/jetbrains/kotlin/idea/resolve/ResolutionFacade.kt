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

package org.jetbrains.kotlin.idea.resolve

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.JetDeclaration
import org.jetbrains.kotlin.psi.JetElement
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

public interface ResolutionFacade {
    public val project: Project

    public fun analyze(element: JetElement, bodyResolveMode: BodyResolveMode = BodyResolveMode.FULL): BindingContext

    public fun analyzeFullyAndGetResult(elements: Collection<JetElement>): AnalysisResult

    public fun resolveToDescriptor(declaration: JetDeclaration): DeclarationDescriptor

    public fun findModuleDescriptor(element: JetElement): ModuleDescriptor

    public fun <T> getFrontendService(element: PsiElement, serviceClass: Class<T>): T

    public fun <T> getFrontendService(moduleDescriptor: ModuleDescriptor, serviceClass: Class<T>): T

    public fun <T> getIdeService(element: PsiElement, serviceClass: Class<T>): T

    public fun <T> getIdeService(moduleDescriptor: ModuleDescriptor, serviceClass: Class<T>): T
}

public inline fun <reified T> ResolutionFacade.frontendService(element: PsiElement): T
        = this.getFrontendService(element, javaClass<T>())

public inline fun <reified T> ResolutionFacade.frontendService(moduleDescriptor: ModuleDescriptor): T
        = this.getFrontendService(moduleDescriptor, javaClass<T>())

public inline fun <reified T> ResolutionFacade.ideService(element: PsiElement): T
        = this.getIdeService(element, javaClass<T>())

public inline fun <reified T> ResolutionFacade.ideService(moduleDescriptor: ModuleDescriptor): T
        = this.getIdeService(moduleDescriptor, javaClass<T>())