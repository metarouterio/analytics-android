package com.segment.analytics.internal;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;
import com.segment.analytics.StatsSnapshot;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static com.segment.analytics.internal.Utils.panic;
import static com.segment.analytics.internal.Utils.quitThread;

public class Stats {
  private static final String STATS_THREAD_NAME = Utils.THREAD_PREFIX + "Stats";

  final HandlerThread statsThread;
  final StatsHandler handler;

  long flushCount;
  long flushEventCount;
  long integrationOperationCount;
  long integrationOperationDuration;
  Map<String, Long> integrationOperationDurationByIntegration = new HashMap<>();

  public Stats() {
    statsThread = new HandlerThread(STATS_THREAD_NAME, THREAD_PRIORITY_BACKGROUND);
    statsThread.start();
    handler = new StatsHandler(statsThread.getLooper(), this);
  }

  public void shutdown() {
    quitThread(statsThread);
  }

  void dispatchFlush(int eventCount) {
    handler.sendMessage(handler //
        .obtainMessage(StatsHandler.TRACK_FLUSH, eventCount, 0));
  }

  void performFlush(int eventCount) {
    flushCount++;
    flushEventCount += eventCount;
  }

  void dispatchIntegrationOperation(String key, long duration) {
    handler.sendMessage(handler //
        .obtainMessage(StatsHandler.TRACK_INTEGRATION_OPERATION, new Pair<>(key, duration)));
  }

  void performIntegrationOperation(Pair<String, Long> durationForIntegration) {
    integrationOperationCount++;
    integrationOperationDuration += durationForIntegration.second;
    Long duration = integrationOperationDurationByIntegration.get(durationForIntegration.first);
    if (duration == null) {
      integrationOperationDurationByIntegration.put(durationForIntegration.first,
          durationForIntegration.second);
    } else {
      integrationOperationDurationByIntegration.put(durationForIntegration.first,
          duration + durationForIntegration.second);
    }
  }

  public StatsSnapshot createSnapshot() {
    return new StatsSnapshot(System.currentTimeMillis(), flushCount, flushEventCount,
        integrationOperationCount, integrationOperationDuration,
        Collections.unmodifiableMap(integrationOperationDurationByIntegration));
  }

  private static class StatsHandler extends Handler {
    private static final int TRACK_FLUSH = 1;
    private static final int TRACK_INTEGRATION_OPERATION = 2;

    private final Stats stats;

    StatsHandler(Looper looper, Stats stats) {
      super(looper);
      this.stats = stats;
    }

    @Override public void handleMessage(final Message msg) {
      switch (msg.what) {
        case TRACK_FLUSH:
          stats.performFlush(msg.arg1);
          break;
        case TRACK_INTEGRATION_OPERATION:
          //noinspection unchecked
          stats.performIntegrationOperation((Pair<String, Long>) msg.obj);
          break;
        default:
          panic("Unhandled stats message: " + msg.what);
      }
    }
  }
}
