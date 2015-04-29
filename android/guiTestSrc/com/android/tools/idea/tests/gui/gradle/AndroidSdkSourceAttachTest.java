/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.gradle;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorNotificationPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.GuiTests.SHORT_TIMEOUT;
import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static com.intellij.openapi.util.io.FileUtil.rename;
import static com.intellij.openapi.util.io.FileUtilRt.delete;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;
import static org.fest.swing.timing.Pause.pause;
import static org.jetbrains.android.sdk.AndroidSdkUtils.clearLocalPkgInfo;
import static org.jetbrains.android.sdk.AndroidSdkUtils.findSuitableAndroidSdk;
import static org.jetbrains.android.sdk.AndroidSdkUtils.updateSdkSourceRoot;
import static org.junit.Assert.assertNotNull;

@BelongsToTestGroups({PROJECT_SUPPORT})
public class AndroidSdkSourceAttachTest extends GuiTestCase {
  private static final String ACTIVITY_CLASS_FILE_PATH = "android/app/Activity.class";
  private static final String ACTIVITY_JAVA_FILE_PATH = "android/app/Activity.java";

  // Sdk used for the simpleApplication project.
  private Sdk mySdk = findSuitableAndroidSdk("android-21");

  private File mySdkSourcePath;
  private File mySdkSourceTmpPath;

  @Before
  public void skipSourceGenerationOnSync() {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  @Before
  public void restoreAndroidSdkSource() throws IOException {
    mySdkSourcePath = new File(mySdk.getHomePath() + "/sources/android-21");
    mySdkSourceTmpPath = new File(mySdk.getHomePath() + "/sources.tmp/android-21"); // it can't be in 'sources' folder

    if (!mySdkSourcePath.isDirectory() && mySdkSourceTmpPath.isDirectory()) {
      rename(mySdkSourceTmpPath, mySdkSourcePath);
    }
  }

  @Test
  @IdeGuiTest
  public void testDownloadSdkSource() throws IOException {
    if (mySdkSourcePath.isDirectory()) {
      delete(mySdkSourceTmpPath);
      rename(mySdkSourcePath, mySdkSourceTmpPath);
    }
    updateSdkSourceRoot(mySdk);

    IdeFrameFixture projectFrame = importSimpleApplication();
    final EditorFixture editor = projectFrame.getEditor();

    final VirtualFile classFile = findActivityClassFile();
    editor.open(classFile, EditorFixture.Tab.EDITOR);

    acceptLegalNoticeIfNeeded();

    // Download the source.
    findNotificationPanel(projectFrame).performAction("Download");

    DialogFixture downloadDialog = findDialog(withTitle("SDK Quickfix Installation"))
      .withTimeout(SHORT_TIMEOUT.duration()).using(myRobot);

    final JButtonFixture finish = downloadDialog.button(withText("Finish"));

    // Wait until installation is finished. By then the "Finish" button will be enabled.
    pause(new Condition("Android source is installed") {
      @Override
      public boolean test() {
        return finish.isEnabled();
      }
    });
    finish.click();

    pause(new Condition("Source file is opened") {
      @Override
      public boolean test() {
        return !classFile.equals(editor.getCurrentFile());
      }
    }, SHORT_TIMEOUT);

    VirtualFile sourceFile = editor.getCurrentFile();
    assertNotNull(sourceFile);
    assertThat(sourceFile.getPath()).endsWith(ACTIVITY_JAVA_FILE_PATH);
  }

  @Test
  @IdeGuiTest
  public void testRefreshSdkSource() throws IOException {
    if (!mySdkSourcePath.isDirectory()) {
      // Skip test if Sdk source is not installed.
      System.out.println("Android Sdk Source for '" + mySdk.getName() + "' must be installed before running 'testRefreshSdkSource'");
      return;
    }

    clearLocalPkgInfo(mySdk);
    SdkModificator sdkModificator = mySdk.getSdkModificator();
    sdkModificator.removeRoots(OrderRootType.SOURCES);
    sdkModificator.commitChanges();

    IdeFrameFixture projectFrame = importSimpleApplication();
    final EditorFixture editor = projectFrame.getEditor();

    final VirtualFile classFile = findActivityClassFile();
    editor.open(classFile, EditorFixture.Tab.EDITOR);

    acceptLegalNoticeIfNeeded();

    // Refresh the source.
    findNotificationPanel(projectFrame).performAction("Refresh (if already downloaded)");

    pause(new Condition("Source file is opened") {
      @Override
      public boolean test() {
        return !classFile.equals(editor.getCurrentFile());
      }
    }, SHORT_TIMEOUT);

    VirtualFile sourceFile = editor.getCurrentFile();
    assertNotNull(sourceFile);
    assertThat(sourceFile.getPath()).endsWith(ACTIVITY_JAVA_FILE_PATH);
  }

  private void acceptLegalNoticeIfNeeded() {
    if(!PropertiesComponent.getInstance().isTrueValue("decompiler.legal.notice.accepted")) {
      DialogFixture acceptTermDialog = findDialog(withTitle("JetBrains Decompiler"))
        .withTimeout(SHORT_TIMEOUT.duration()).using(myRobot);

      acceptTermDialog.button(withText("Accept")).click();
    }
  }

  @NotNull
  private EditorNotificationPanelFixture findNotificationPanel(@NotNull IdeFrameFixture projectFrame) {
    return projectFrame.requireEditorNotification(
      "Sources for '" + mySdk.getName() + "' not found.");
  }

  @NotNull
  private VirtualFile findActivityClassFile() {
    VirtualFile jarRoot = null;
    for (VirtualFile f : mySdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
      if (f.getUrl().startsWith(SdkConstants.EXT_JAR)) {
        jarRoot = f;
      }
    }
    assertNotNull(jarRoot);
    VirtualFile classFile = jarRoot.findFileByRelativePath(ACTIVITY_CLASS_FILE_PATH);
    assertNotNull(classFile);
    return classFile;
  }
}
