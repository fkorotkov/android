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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.run.ApkProviderUtil;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class HotSwapTask implements LaunchTask {
  private final AndroidFacet myFacet;

  public HotSwapTask(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Hotswapping changes";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.DEPLOY_HOTSWAP;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    printer.stdout("Hotswapping changes...");
    InstantRunManager.pushChanges(device, myFacet);

    String pkgName;
    try {
      pkgName = ApkProviderUtil.computePackageName(myFacet);
    }
    catch (ApkProvisionException e) {
      launchStatus.terminateLaunch("Unable to obtain application id: " + e);
      return false;
    }

    DeployApkTask.cacheInstallationData(device, myFacet, pkgName);
    return true;
  }
}