package dev.fusion.widget;

import android.app.job.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.net.Uri;
import java.util.concurrent.*;

public final class RefreshJob extends JobService {
    private static final int CHANGES = 8401, PERIODIC = 8402;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<Integer, Task> tasks = new ConcurrentHashMap<>();
    private static final class Task { volatile boolean stopped; volatile Future<?> future; }
    static void schedule(Context c) {
        if (FusionWidget.widgetIds(c).length == 0) return;
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        try { if (js.getPendingJob(CHANGES) == null) scheduleChanges(c); } catch (RuntimeException ignored) {}
        try { if (js.getPendingJob(PERIODIC) == null) js.schedule(new JobInfo.Builder(PERIODIC, new ComponentName(c, RefreshJob.class)).setPeriodic(1800000).setPersisted(true).build()); } catch (RuntimeException ignored) {}
    }
    private static void scheduleChanges(Context c) {
        c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(CHANGES, new ComponentName(c, RefreshJob.class))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(Uri.parse(Protocol.URI + "/export"), JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(Uri.parse(Protocol.URI + "/device"), JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(1500).setTriggerContentMaxDelay(10000).build());
    }
    static void cancel(Context c) { JobScheduler j = c.getSystemService(JobScheduler.class); if (j != null) { j.cancel(CHANGES); j.cancel(PERIODIC); } }
    @Override public boolean onStartJob(JobParameters p) {
        Task task = new Task();
        tasks.put(p.getJobId(), task);
        task.future = executor.submit(() -> {
            try { FusionWidget.fetch(this); }
            catch (Exception e) { DeviceStore.error(this, e.getMessage()); }
            if (!task.stopped) {
                FusionWidget.updateAll(this);
                try {
                    if (p.getJobId() == CHANGES && FusionWidget.widgetIds(this).length > 0) scheduleChanges(this);
                } catch (RuntimeException ignored) {}
                jobFinished(p, false);
            }
            tasks.remove(p.getJobId(), task);
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters p) {
        Task task = tasks.remove(p.getJobId());
        if (task != null) { task.stopped = true; if (task.future != null) task.future.cancel(true); }
        return true;
    }
    @Override public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
