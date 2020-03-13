// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.eventLog.uploader.EventLogUploadException.EventLogUploadErrorType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EventLogSystemLogger {
  private static final String GROUP = "event.log";

  public static void logWhitelistLoad(@NotNull String recorderId, @Nullable String version) {
    final FeatureUsageData data = new FeatureUsageData().addVersionByString(version);
    logEvent(recorderId, "whitelist.loaded", data);
  }

  public static void logWhitelistUpdated(@NotNull String recorderId, @Nullable String version) {
    final FeatureUsageData data = new FeatureUsageData().addVersionByString(version);
    logEvent(recorderId, "whitelist.updated", data);
  }

  public static void logFilesSend(@NotNull String recorderId, int total, int succeed, int failed) {
    final FeatureUsageData data = new FeatureUsageData().
      addData("total", total).
      addData("send", succeed + failed).
      addData("failed", failed);
    logEvent(recorderId, "logs.send", data);
  }

  public static void logCreatingExternalSendCommand(@NotNull String recorderId) {
    logEvent(recorderId, "external.send.command.creation.started");
  }

  public static void logFinishedCreatingExternalSendCommand(@NotNull String recorderId, @Nullable EventLogUploadErrorType errorType) {
    boolean succeed = errorType == null;
    FeatureUsageData data = new FeatureUsageData().addData("succeed", succeed);
    if (!succeed) {
      data.addData("error", errorType.name());
    }
    logEvent(recorderId, "external.send.command.creation.finished", data);
  }

  private static void logEvent(@NotNull String recorderId, @NotNull String eventId, @NotNull FeatureUsageData data) {
    final StatisticsEventLoggerProvider provider = StatisticsEventLoggerKt.getEventLogProvider(recorderId);
    provider.getLogger().log(new EventLogGroup(GROUP, provider.getVersion()), eventId, data.build(), false);
  }

  private static void logEvent(@NotNull String recorderId, @NotNull String eventId) {
    final StatisticsEventLoggerProvider provider = StatisticsEventLoggerKt.getEventLogProvider(recorderId);
    provider.getLogger().log(new EventLogGroup(GROUP, provider.getVersion()), eventId, false);
  }
}
