// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.applicationinsights.smoketestextension;

import com.microsoft.applicationinsights.alerting.alert.AlertBreach;
import com.microsoft.applicationinsights.diagnostics.DiagnosisResult;
import com.microsoft.applicationinsights.diagnostics.DiagnosticEngine;
import com.microsoft.applicationinsights.diagnostics.DiagnosticEngineFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

public class MockDiagnosticEngineFactory implements DiagnosticEngineFactory {

  @Override
  public DiagnosticEngine create(ScheduledExecutorService executorService, int pid, String cgPath) {
    return new DiagnosticEngine() {

      @Override
      public void init(int thisPid) {
        System.setProperty("DIAGNOSTIC_CALLED", "true");
      }

      @Override
      public void startGatheringDiagnosticData(int i) {

      }

      @Override
      public void emitAlertBreaches(AlertBreach alertBreach) {

      }

      @Override
      public Future<DiagnosisResult<?>> performDiagnosis(AlertBreach alertBreach, int i) {
        return CompletableFuture.completedFuture(null);
      }

      @Override
      public void notifyNewProcess(Process process) {

      }

      @Override
      public void stop() {

      }
    };
  }
}
