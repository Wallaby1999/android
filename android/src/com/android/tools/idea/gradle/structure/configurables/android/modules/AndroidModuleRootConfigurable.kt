/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.structure.configurables.android.modules

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.ui.modules.ModulePanel
import com.android.tools.idea.gradle.structure.configurables.ui.properties.SimplePropertyEditor
import com.android.tools.idea.gradle.structure.model.android.AndroidModuleDescriptors
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModuleDefaultConfigDescriptors
import com.android.tools.idea.gradle.structure.model.meta.PropertiesUiModel
import com.android.tools.idea.gradle.structure.model.meta.uiProperty

class AndroidModuleRootConfigurable(context: PsContext, module: PsAndroidModule) : AbstractModuleConfigurable<ModulePanel>(context, module) {

  private val signingConfigsModel = createSigningConfigsModel(module)

  override fun getId() = "android.psd.modules." + displayName
  override fun createPanel(module: PsAndroidModule) =
      ModulePanel(context, module, signingConfigsModel)
}

fun androidModulePropertiesModel() =
    PropertiesUiModel(
        listOf(
            uiProperty(AndroidModuleDescriptors.compileSdkVersion, ::SimplePropertyEditor),
            uiProperty(AndroidModuleDescriptors.buildToolsVersion, ::SimplePropertyEditor),
            uiProperty(AndroidModuleDescriptors.sourceCompatibility, ::SimplePropertyEditor),
            uiProperty(AndroidModuleDescriptors.targetCompatibility, ::SimplePropertyEditor)))

fun defaultConfigPropertiesModel() =
    PropertiesUiModel(
        listOf(
            uiProperty(PsAndroidModuleDefaultConfigDescriptors.applicationId, ::SimplePropertyEditor),
            uiProperty(PsAndroidModuleDefaultConfigDescriptors.targetSdkVersion, ::SimplePropertyEditor),
            uiProperty(PsAndroidModuleDefaultConfigDescriptors.minSdkVersion, ::SimplePropertyEditor),
            uiProperty(PsAndroidModuleDefaultConfigDescriptors.maxSdkVersion, ::SimplePropertyEditor),
            uiProperty(PsAndroidModuleDefaultConfigDescriptors.multiDexEnabled, ::SimplePropertyEditor),
// TODO(b/70501607): Decide on PsProductFlavor.ProductFlavorDescriptors.testFunctionalTest,
// TODO(b/70501607): Decide on PsProductFlavor.ProductFlavorDescriptors.testHandleProfiling,
            uiProperty(PsAndroidModuleDefaultConfigDescriptors.testInstrumentationRunner, ::SimplePropertyEditor),
            uiProperty(PsAndroidModuleDefaultConfigDescriptors.testApplicationId, ::SimplePropertyEditor),
            uiProperty(PsAndroidModuleDefaultConfigDescriptors.versionCode, ::SimplePropertyEditor),
            uiProperty(PsAndroidModuleDefaultConfigDescriptors.versionName, ::SimplePropertyEditor)))

