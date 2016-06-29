/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.ex

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.configurationStore.PROJECT_CONFIG_DIR
import com.intellij.configurationStore.StoreAwareProjectManager
import com.intellij.configurationStore.loadAndUseProject
import com.intellij.configurationStore.saveStore
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.project.ProjectManager
import com.intellij.profile.codeInspection.ProjectInspectionProfileManagerImpl
import com.intellij.testFramework.Assertions.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.delete
import com.intellij.util.readText
import com.intellij.util.write
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths

internal class ProjectInspectionManagerTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  val tempDirManager = TemporaryDirectory()

  private val ruleChain = RuleChain(tempDirManager)
  @Rule fun getChain() = ruleChain

  @Test fun `component`() {
    loadAndUseProject(tempDirManager, {
      it.path
    }) { project ->
      val projectInspectionProfileManager = ProjectInspectionProfileManagerImpl.getInstanceImpl(project)

      assertThat(projectInspectionProfileManager.state).isEmpty()

      projectInspectionProfileManager.currentProfile

      assertThat(projectInspectionProfileManager.state).isEmpty()

      // cause to use app profile
      projectInspectionProfileManager.setRootProfile(null)
      val doNotUseProjectProfileState = """
      <state>
        <settings>
          <option name="USE_PROJECT_PROFILE" value="false" />
          <version value="1.0" />
        </settings>
      </state>""".trimIndent()
      assertThat(projectInspectionProfileManager.state).isEqualTo(doNotUseProjectProfileState)

      val inspectionDir = Paths.get(project.stateStore.stateStorageManager.expandMacros(PROJECT_CONFIG_DIR), "inspectionProfiles")
      val file = inspectionDir.resolve("profiles_settings.xml")
      project.saveStore()
      assertThat(file).exists()
      val doNotUseProjectProfileData = """
      <component name="InspectionProjectProfileManager">
        <settings>
          <option name="USE_PROJECT_PROFILE" value="false" />
          <version value="1.0" />
        </settings>
      </component>""".trimIndent()
      assertThat(file.readText()).isEqualTo(doNotUseProjectProfileData)

      // test load
      file.delete()

      project.baseDir.refresh(false, true)
      (ProjectManager.getInstance() as StoreAwareProjectManager).flushChangedAlarm()
      assertThat(projectInspectionProfileManager.state).isEmpty()

      file.write(doNotUseProjectProfileData)
      project.baseDir.refresh(false, true)
      (ProjectManager.getInstance() as StoreAwareProjectManager).flushChangedAlarm()
      assertThat(projectInspectionProfileManager.state).isEqualTo(doNotUseProjectProfileState)
    }
  }

  @Test fun `profiles`() {
    loadAndUseProject(tempDirManager, {
      it.path
    }) { project ->
      val projectInspectionProfileManager = ProjectInspectionProfileManagerImpl.getInstanceImpl(project)

      assertThat(projectInspectionProfileManager.state).isEmpty()

      // cause to use app profile
      InspectionProfileImpl.initAndDo {
        val currentProfile = projectInspectionProfileManager.currentProfile
        assertThat(currentProfile.isProjectLevel).isTrue()
        currentProfile.disableTool("Convert2Diamond", project)
      }

      project.saveStore()

      val inspectionDir = Paths.get(project.stateStore.stateStorageManager.expandMacros(PROJECT_CONFIG_DIR), "inspectionProfiles")
      val file = inspectionDir.resolve("profiles_settings.xml")

      assertThat(file).doesNotExist()
      val profileFile = inspectionDir.resolve("Project_Default.xml")
      assertThat(profileFile.readText()).isEqualTo("""
      <component name="InspectionProjectProfileManager">
        <profile version="1.0">
          <option name="myName" value="Project Default" />
          <inspection_tool class="Convert2Diamond" enabled="false" level="WARNING" enabled_by_default="false" />
        </profile>
      </component>""".trimIndent())

      profileFile.write("""
      <component name="InspectionProjectProfileManager">
        <profile version="1.0">
          <option name="myName" value="Project Default" />
          <inspection_tool class="Convert2Diamond" enabled="false" level="ERROR" enabled_by_default="false" />
        </profile>
      </component>""".trimIndent())

      project.baseDir.refresh(false, true)
      (ProjectManager.getInstance() as StoreAwareProjectManager).flushChangedAlarm()
      InspectionProfileImpl.initAndDo {
        assertThat(projectInspectionProfileManager.currentProfile.getToolDefaultState("Convert2Diamond", project).level).isEqualTo(HighlightDisplayLevel.ERROR)
      }
    }
  }
}