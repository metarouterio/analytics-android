package com.segment.analytics.internal;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import com.segment.analytics.ValueMap;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static com.segment.analytics.Analytics.OnIntegrationReadyListener;
import static com.segment.analytics.internal.Utils.THREAD_PREFIX;
import static com.segment.analytics.internal.Utils.buffer;
import static com.segment.analytics.internal.Utils.createDirectory;
import static com.segment.analytics.internal.Utils.isConnected;
import static com.segment.analytics.internal.Utils.isNullOrEmpty;
import static com.segment.analytics.internal.Utils.panic;
import static java.lang.System.currentTimeMillis;

/**
 * The class that forwards operations from the client to integrations, including Segment. It
 * maintains it's own in-memory queue to account for the latency between receiving the first event,
 * fetching settings from the server and enabling the integrations. Once it enables all
 * integrations
 * it replays any events in the queue. This will only affect the first app install, subsequent
 * launches will be use the cached settings on disk.
 */
public class IntegrationManager {
  private static final String PROJECT_SETTINGS_CACHE_KEY_PREFIX = "project-settings-";
  private static final String MANAGER_THREAD_NAME = THREAD_PREFIX + "IntegrationManager";
  private static final long SETTINGS_REFRESH_INTERVAL = 1000 * 60 * 60 * 24; // 24 hours
  private static final long SETTINGS_RETRY_INTERVAL = 1000 * 60; // 1 minute

  final Context context;
  final Client client;
  final HandlerThread networkingThread;
  final Handler networkingHandler;
  final Handler integrationManagerHandler;
  final Stats stats;
  final boolean debuggingEnabled;
  final ValueMap.Cache<ProjectSettings> projectSettingsCache;
  final Logger logger;
  final List<AbstractIntegration> integrations = new CopyOnWriteArrayList<>();
  public final Map<String, Boolean> bundledIntegrations = new ConcurrentHashMap<>();
  private final Cartographer cartographer;
  Queue<IntegrationOperation> operationQueue;
  boolean initialized;
  OnIntegrationReadyListener listener;

  public static synchronized IntegrationManager create(Context context, Cartographer cartographer,
      Client client, Logger logger, Stats stats, String tag, boolean debuggingEnabled) {
    ValueMap.Cache<ProjectSettings> projectSettingsCache =
        new ValueMap.Cache<>(context, cartographer, PROJECT_SETTINGS_CACHE_KEY_PREFIX + tag,
            ProjectSettings.class);
    return new IntegrationManager(context, client, cartographer, logger, stats,
        projectSettingsCache, debuggingEnabled);
  }

  IntegrationManager(Context context, Client client, Cartographer cartographer, Logger logger,
      Stats stats, ValueMap.Cache<ProjectSettings> projectSettingsCache, boolean debuggingEnabled) {
    this.context = context;
    this.client = client;
    this.cartographer = cartographer;
    this.logger = logger;
    this.stats = stats;
    this.projectSettingsCache = projectSettingsCache;
    this.debuggingEnabled = debuggingEnabled;

    networkingThread = new HandlerThread(MANAGER_THREAD_NAME, THREAD_PRIORITY_BACKGROUND);
    networkingThread.start();
    networkingHandler = new NetworkingHandler(networkingThread.getLooper(), this);
    integrationManagerHandler = new IntegrationHandler(Looper.getMainLooper(), this);

    loadBundledIntegration("com.segment.analytics.AmplitudeIntegration");
    loadBundledIntegration("com.segment.analytics.AppsFlyerIntegration");
    loadBundledIntegration("com.segment.analytics.BugsnagIntegration");
    loadBundledIntegration("com.segment.analytics.CountlyIntegration");
    loadBundledIntegration("com.segment.analytics.CrittercismIntegration");
    loadBundledIntegration("com.segment.analytics.FlurryIntegration");
    loadBundledIntegration("com.segment.analytics.GoogleAnalyticsIntegration");
    loadBundledIntegration("com.segment.analytics.KahunaIntegration");
    loadBundledIntegration("com.segment.analytics.LeanplumIntegration");
    loadBundledIntegration("com.segment.analytics.LocalyticsIntegration");
    loadBundledIntegration("com.segment.analytics.MixpanelIntegration");
    loadBundledIntegration("com.segment.analytics.QuantcastIntegration");
    loadBundledIntegration("com.segment.analytics.TapstreamIntegration");

    if (!projectSettingsCache.isSet()) {
      dispatchFetch();
    } else {
      ProjectSettings projectSettings = projectSettingsCache.get();
      if (projectSettings == null) {
        dispatchFetch();
      } else {
        dispatchInitialize(projectSettings);
        if (projectSettings.timestamp() + SETTINGS_REFRESH_INTERVAL < currentTimeMillis()) {
          dispatchFetch();
        }
      }
    }
  }

  private void loadBundledIntegration(String className) {
    try {
      Class clz = Class.forName(className);
      AbstractIntegration integration = (AbstractIntegration) clz.newInstance();
      integrations.add(integration);
      bundledIntegrations.put(integration.key(), false);
    } catch (InstantiationException e) {
      logger.print(e, "Skipped integration %s as it could not be instantiated.", className);
    } catch (ClassNotFoundException e) {
      logger.print("Skipped integration %s as it was not bundled.", className);
    } catch (IllegalAccessException e) {
      logger.print(e, "Skipped integration %s as it could not be accessed.", className);
    }
  }

  void dispatchFetch() {
    networkingHandler.sendMessage(networkingHandler //
        .obtainMessage(NetworkingHandler.REQUEST_FETCH_SETTINGS));
  }

  void performFetch() {
    try {
      if (isConnected(context)) {
        Client.Response response = client.fetchSettings();
        ProjectSettings projectSettings;
        try {
          projectSettings = ProjectSettings.create(cartographer.fromJson(buffer(response.is)),
              currentTimeMillis());
          projectSettingsCache.set(projectSettings);
        } catch (IOException e) {
          retryFetch();
          return;
        } finally {
          response.close();
        }

        File downloadedJarsDirectory = getJarDownloadDirectory(context);
        try {
          createDirectory(downloadedJarsDirectory);
        } catch (IOException e) {
          logger.print(e, "Unable to download integrations into " + downloadedJarsDirectory);
          return;
        }

        Set<String> bundledIntegrationsKeys = bundledIntegrations.keySet();
        HashSet<String> skippedIntegrations = new HashSet<>(bundledIntegrationsKeys.size() + 2);
        skippedIntegrations.addAll(bundledIntegrationsKeys);
        skippedIntegrations.add(ProjectSettings.SEGMENT_KEY);
        skippedIntegrations.add(ProjectSettings.TIMESTAMP_KEY);

        for (String key : projectSettings.keySet()) {
          if (skippedIntegrations.contains(key)) continue;

          String fileName = getFileNameForIntegration(key);
          String url = "https://dl.dropboxusercontent.com/u/11371156/integrations/" + fileName;
          client.downloadFile(url, new File(downloadedJarsDirectory, fileName));
        }

        if (!initialized) dispatchInitialize(projectSettings);
      } else {
        retryFetch();
      }
    } catch (IOException e) {
      retryFetch();
    }
  }

  void retryFetch() {
    networkingHandler.sendMessageDelayed(networkingHandler //
        .obtainMessage(NetworkingHandler.REQUEST_FETCH_SETTINGS), SETTINGS_RETRY_INTERVAL);
  }

  void dispatchInitialize(ProjectSettings projectSettings) {
    integrationManagerHandler.sendMessageAtFrontOfQueue(integrationManagerHandler //
        .obtainMessage(IntegrationHandler.REQUEST_INITIALIZE, projectSettings));
  }

  static String getSanitizedKeyForIntegration(String integrationKey) {
    return integrationKey.replace(' ', '-').toLowerCase();
  }

  static String getFileNameForIntegration(String integrationKey) {
    return getSanitizedKeyForIntegration(integrationKey)
        + "-3.0.0-SNAPSHOT-jar-with-dependencies.jar";
  }

  static File getJarDownloadDirectory(Context context) {
    return context.getDir("jars", Context.MODE_PRIVATE);
  }

  void performInitialize(ProjectSettings projectSettings) {
    if (initialized) return;

    LinkedHashMap<String, Boolean> bundledIntegrationsCopy =
        new LinkedHashMap<>(bundledIntegrations);

    // Iterate over all the bundled integrations
    Iterator<AbstractIntegration> iterator = integrations.iterator();
    while (iterator.hasNext()) {
      AbstractIntegration integration = iterator.next();
      String key = integration.key();
      if (projectSettings.containsKey(key)) {
        ValueMap settings = projectSettings.getValueMap(key);
        try {
          logger.debug(Logger.OWNER_INTEGRATION_MANAGER, Logger.VERB_INITIALIZE, key,
              "settings: %s", settings);
          integration.initialize(context, settings, debuggingEnabled);
          if (listener != null) {
            listener.onIntegrationReady(key, integration.getUnderlyingInstance());
            listener = null; // clear the reference
          }
        } catch (IllegalStateException e) {
          iterator.remove();
          bundledIntegrations.remove(key);
          logger.print(e, "Did not initialize integration %s as it needed more permissions.", key);
        }
      } else {
        iterator.remove();
        logger.print("Did not initialize integration %s as it was disabled.", key);
      }
    }

    File downloadedJarsDirectory = getJarDownloadDirectory(context);
    try {
      createDirectory(downloadedJarsDirectory);
    } catch (IOException e) {
      logger.print(e, "Unable to load downloaded integrations");
      return;
    }
    File optimizedDexDirectory = context.getDir("optimized-dex", Context.MODE_PRIVATE);
    try {
      createDirectory(optimizedDexDirectory);
    } catch (IOException e) {
      logger.print(e, "Unable to dex downloaded integrations");
      return;
    }
    ClassLoader defaultClassLoader = context.getClassLoader();

    Set<String> bundledIntegrationsKeys = bundledIntegrationsCopy.keySet();
    HashSet<String> skippedIntegrations = new HashSet<>(bundledIntegrationsKeys.size() + 2);
    skippedIntegrations.addAll(bundledIntegrationsKeys);
    skippedIntegrations.add(ProjectSettings.SEGMENT_KEY);
    skippedIntegrations.add(ProjectSettings.TIMESTAMP_KEY);

    for (String key : projectSettings.keySet()) {
      if (skippedIntegrations.contains(key)) continue;

      File jarFile = new File(downloadedJarsDirectory, getFileNameForIntegration(key));
      try {
        DexClassLoader dexClassLoader =
            new DexClassLoader(jarFile.getAbsolutePath(), optimizedDexDirectory.getAbsolutePath(),
                null, defaultClassLoader);
        String className =
            "com.segment.analytics.internal.integrations." + key.replace(" ", "") + "Integration";
        Class integrationClass = dexClassLoader.loadClass(className);
        AbstractIntegration integration = (AbstractIntegration) integrationClass.newInstance();
        ValueMap settings = projectSettings.getValueMap(key);
        try {
          logger.debug(Logger.OWNER_INTEGRATION_MANAGER, Logger.VERB_INITIALIZE, key,
              "settings: %s", settings);
          integration.initialize(context, settings, debuggingEnabled);
          integrations.add(integration);
          if (listener != null) {
            listener.onIntegrationReady(key, integration.getUnderlyingInstance());
            listener = null; // clear the reference
          }
          bundledIntegrations.put(key, true);
        } catch (IllegalStateException e) {
          iterator.remove();
          bundledIntegrations.remove(key);
          logger.print(e, "Did not initialize integration %s as it needed more permissions.", key);
        }
      } catch (Exception e) {
        logger.print(e, "Could not initialize " + key);
      }
    }

    if (!isNullOrEmpty(operationQueue)) {
      Iterator<IntegrationOperation> operationIterator = operationQueue.iterator();
      while (operationIterator.hasNext()) {
        IntegrationOperation operation = operationIterator.next();
        run(operation);
        operationIterator.remove();
      }
    }
    operationQueue = null;
    initialized = true;
  }

  public void dispatchFlush() {
    dispatchOperation(new FlushOperation());
  }

  public void dispatchOperation(IntegrationOperation operation) {
    integrationManagerHandler.sendMessage(
        integrationManagerHandler.obtainMessage(IntegrationHandler.REQUEST_DISPATCH_OPERATION,
            operation));
  }

  void performOperation(IntegrationOperation operation) {
    if (initialized) {
      run(operation);
    } else {
      if (operationQueue == null) {
        operationQueue = new ArrayDeque<>();
      }
      logger.debug(Logger.OWNER_INTEGRATION_MANAGER, Logger.VERB_ENQUEUE, operation.id(), null);
      operationQueue.add(operation);
    }
  }

  /** Runs the given operation on all Bundled integrations. */
  private void run(IntegrationOperation operation) {
    for (int i = 0; i < integrations.size(); i++) {
      AbstractIntegration integration = integrations.get(i);
      long startTime = currentTimeMillis();
      operation.run(integration);
      long endTime = currentTimeMillis();
      long duration = endTime - startTime;
      logger.debug(integration.key(), Logger.VERB_DISPATCH, operation.id(), "duration: %s",
          duration);
      stats.dispatchIntegrationOperation(integration.key(), duration);
    }
  }

  public void dispatchRegisterIntegrationInitializedListener(OnIntegrationReadyListener listener) {
    integrationManagerHandler.sendMessage(
        integrationManagerHandler.obtainMessage(IntegrationHandler.REQUEST_REGISTER_LISTENER,
            listener));
  }

  void performRegisterIntegrationInitializedListener(OnIntegrationReadyListener listener) {
    if (initialized && listener != null) {
      // Integrations are already ready, notify the listener right away
      for (AbstractIntegration abstractIntegration : integrations) {
        listener.onIntegrationReady(abstractIntegration.key(),
            abstractIntegration.getUnderlyingInstance());
      }
    } else {
      this.listener = listener;
    }
  }

  public void shutdown() {
    Utils.quitThread(networkingThread);
    if (operationQueue != null) {
      operationQueue.clear();
      operationQueue = null;
    }
  }

  public interface IntegrationOperation {
    void run(AbstractIntegration integration);

    String id();
  }

  public static class ActivityLifecyclePayload implements IntegrationOperation {
    final Type type;
    final Bundle bundle;
    final Activity activity;
    final String id;

    public ActivityLifecyclePayload(Type type, Activity activity, Bundle bundle) {
      this.type = type;
      this.bundle = bundle;
      this.id = UUID.randomUUID().toString();
      // Ideally we would store a weak reference, but it doesn't work for stop/destroy events
      this.activity = activity;
    }

    Activity getActivity() {
      return activity;
    }

    @Override public void run(AbstractIntegration integration) {
      switch (type) {
        case CREATED:
          integration.onActivityCreated(activity, bundle);
          break;
        case STARTED:
          integration.onActivityStarted(activity);
          break;
        case RESUMED:
          integration.onActivityResumed(activity);
          break;
        case PAUSED:
          integration.onActivityPaused(activity);
          break;
        case STOPPED:
          integration.onActivityStopped(activity);
          break;
        case SAVE_INSTANCE:
          integration.onActivitySaveInstanceState(activity, bundle);
          break;
        case DESTROYED:
          integration.onActivityDestroyed(activity);
          break;
        default:
          panic("Unknown lifecycle event type: " + type);
      }
    }

    @Override public String id() {
      return id;
    }

    @Override public String toString() {
      return "ActivityLifecycle{" + type + '}';
    }

    public enum Type {
      CREATED, STARTED, RESUMED, PAUSED, STOPPED, SAVE_INSTANCE, DESTROYED
    }
  }

  static class FlushOperation implements IntegrationOperation {
    final String id;

    FlushOperation() {
      this.id = UUID.randomUUID().toString();
    }

    @Override public void run(AbstractIntegration integration) {
      integration.flush();
    }

    @Override public String id() {
      return id;
    }

    @Override public String toString() {
      return "Flush";
    }
  }

  private static class NetworkingHandler extends Handler {
    static final int REQUEST_FETCH_SETTINGS = 1;
    private final IntegrationManager integrationManager;

    NetworkingHandler(Looper looper, IntegrationManager integrationManager) {
      super(looper);
      this.integrationManager = integrationManager;
    }

    @Override public void handleMessage(final Message msg) {
      switch (msg.what) {
        case REQUEST_FETCH_SETTINGS:
          integrationManager.performFetch();
          break;
        default:
          panic("Unhandled dispatcher message: " + msg.what);
      }
    }
  }

  private static class IntegrationHandler extends Handler {
    static final int REQUEST_INITIALIZE = 1;
    static final int REQUEST_DISPATCH_OPERATION = 2;
    static final int REQUEST_REGISTER_LISTENER = 3;

    private final IntegrationManager integrationManager;

    IntegrationHandler(Looper looper, IntegrationManager integrationManager) {
      super(looper);
      this.integrationManager = integrationManager;
    }

    @Override public void handleMessage(final Message msg) {
      switch (msg.what) {
        case REQUEST_INITIALIZE:
          integrationManager.performInitialize((ProjectSettings) msg.obj);
          break;
        case REQUEST_DISPATCH_OPERATION:
          integrationManager.performOperation((IntegrationOperation) msg.obj);
          break;
        case REQUEST_REGISTER_LISTENER:
          integrationManager.performRegisterIntegrationInitializedListener(
              (OnIntegrationReadyListener) msg.obj);
          break;
        default:
          panic("Unhandled dispatcher message: " + msg.what);
      }
    }
  }
}
