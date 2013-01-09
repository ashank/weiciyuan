package org.qii.weiciyuan.othercomponent.sendweiboservice;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import org.qii.weiciyuan.R;
import org.qii.weiciyuan.bean.GeoBean;
import org.qii.weiciyuan.dao.send.StatusNewMsgDao;
import org.qii.weiciyuan.support.database.DraftDBManager;
import org.qii.weiciyuan.support.database.draftbean.StatusDraftBean;
import org.qii.weiciyuan.support.error.WeiboException;
import org.qii.weiciyuan.support.file.FileUploaderHttpHelper;
import org.qii.weiciyuan.support.imagetool.ImageTool;
import org.qii.weiciyuan.support.lib.MyAsyncTask;
import org.qii.weiciyuan.ui.preference.DraftActivity;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * User: qii
 * Date: 12-8-21
 */
public class SendWeiboService extends Service {
    private String accountId;
    private String token;
    private String picPath;
    private String content;
    private GeoBean geoBean;

    private StatusDraftBean statusDraftBean;

    private Map<WeiboSendTask, Boolean> tasksResult = new HashMap<WeiboSendTask, Boolean>();
    private Map<WeiboSendTask, Integer> tasksNotifications = new HashMap<WeiboSendTask, Integer>();

    private Handler handler = new Handler();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        token = intent.getStringExtra("token");
        accountId = intent.getStringExtra("accountId");
        picPath = intent.getStringExtra("picPath");
        content = intent.getStringExtra("content");
        geoBean = (GeoBean) intent.getSerializableExtra("geo");

        statusDraftBean = (StatusDraftBean) intent.getSerializableExtra("draft");

        WeiboSendTask task = new WeiboSendTask();
        task.executeOnExecutor(MyAsyncTask.THREAD_POOL_EXECUTOR);

        tasksResult.put(task, false);

        return START_REDELIVER_INTENT;

    }


    private class WeiboSendTask extends MyAsyncTask<Void, Long, Void> {

        Notification notification;
        WeiboException e;
        long size;
        BroadcastReceiver receiver;
        PendingIntent pendingIntent;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Notification.Builder builder = new Notification.Builder(SendWeiboService.this)
                    .setTicker(getString(R.string.sending))
                    .setContentTitle(getString(R.string.sending))
                    .setContentText(content)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.upload_white);

            if (!TextUtils.isEmpty(picPath)) {
                builder.setProgress(0, 100, false);
            } else {
                builder.setProgress(0, 100, true);
            }

            int notificationId = new Random().nextInt(Integer.MAX_VALUE);

            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {

                receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        WeiboSendTask.this.cancel(true);
                    }
                };

                IntentFilter intentFilter = new IntentFilter("org.qii.weiciyuan.SendWeiboService.stop." + String.valueOf(notificationId));

                registerReceiver(receiver, intentFilter);

                Intent broadcastIntent = new Intent("org.qii.weiciyuan.SendWeiboService.stop." + String.valueOf(notificationId));

                pendingIntent = PendingIntent.getBroadcast(SendWeiboService.this, 1, broadcastIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                builder.addAction(R.drawable.send_failed, getString(R.string.cancel), pendingIntent);
                notification = builder.build();
            } else {
                notification = builder.getNotification();
            }
            NotificationManager notificationManager = (NotificationManager) getApplicationContext()
                    .getSystemService(NOTIFICATION_SERVICE);
            notificationManager.notify(notificationId, notification);

            tasksNotifications.put(WeiboSendTask.this, notificationId);

        }

        private boolean sendPic(String uploadPicPath) throws WeiboException {
            return new StatusNewMsgDao(token).setPic(uploadPicPath).setGeoBean(geoBean).sendNewMsg(content, new FileUploaderHttpHelper.ProgressListener() {

                @Override
                public void transferred(long data) {

                    publishProgress((long) (data * 0.95));
                }

                @Override
                public void completed() {
                    publishProgress(size);
                }
            });
        }

        private boolean sendText() throws WeiboException {
            return new StatusNewMsgDao(token).setGeoBean(geoBean).sendNewMsg(content, null);
        }

        @Override
        protected Void doInBackground(Void... params) {
            boolean result = false;

            try {
                if (!TextUtils.isEmpty(picPath)) {
                    String uploadPicPath = ImageTool.compressPic(SendWeiboService.this, picPath);
                    size = new File(uploadPicPath).length();
                    result = sendPic(uploadPicPath);
                } else {
                    result = sendText();
                }
            } catch (WeiboException e) {
                this.e = e;
                cancel(true);

            }
            if (!result)
                cancel(true);
            return null;
        }


        private double lastStatus = -1d;

        @Override
        protected void onProgressUpdate(Long... values) {

            if (values.length > 0) {

                long data = values[0];

                double r = data / (double) size;

                if (Math.abs(r - lastStatus) < 0.01d) {
                    return;
                }

                lastStatus = r;

                Notification.Builder builder = new Notification.Builder(SendWeiboService.this)
                        .setTicker(getString(R.string.send_photo))
                        .setContentTitle(getString(R.string.background_sending))
                        .setNumber((int) (r * 100))
                        .setContentText(content)
                        .setProgress((int) size, (int) data, false)
                        .setOnlyAlertOnce(true)
                        .setOngoing(true)
                        .setSmallIcon(R.drawable.upload_white);

                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    builder.addAction(R.drawable.send_failed, getString(R.string.cancel), pendingIntent);
                    notification = builder.build();
                } else {
                    notification = builder.getNotification();
                }

                NotificationManager notificationManager = (NotificationManager) getApplicationContext()
                        .getSystemService(NOTIFICATION_SERVICE);
                notificationManager.notify(tasksNotifications.get(WeiboSendTask.this), notification);

            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if (statusDraftBean != null)
                DraftDBManager.getInstance().remove(statusDraftBean.getId());
            showSuccessfulNotification(WeiboSendTask.this);

            if (receiver != null) {
                unregisterReceiver(receiver);
            }
        }

        @Override
        protected void onCancelled(Void aVoid) {
            super.onCancelled(aVoid);
            if (statusDraftBean != null) {
                DraftDBManager.getInstance().remove(statusDraftBean.getId());
                DraftDBManager.getInstance().insertStatus(content, geoBean, picPath, accountId);
            } else {
                DraftDBManager.getInstance().insertStatus(content, geoBean, picPath, accountId);
            }

            showFailedNotification(WeiboSendTask.this);

            if (receiver != null) {
                unregisterReceiver(receiver);
            }
        }

    }

    private void stopServiceIfTasksAreEnd(WeiboSendTask currentTask) {

        tasksResult.put(currentTask, true);

        boolean isAllTaskEnd = true;
        Set<WeiboSendTask> taskSet = tasksResult.keySet();
        for (WeiboSendTask task : taskSet) {
            if (!tasksResult.get(task)) {
                isAllTaskEnd = false;
                break;
            }
        }
        if (isAllTaskEnd) {
            stopForeground(true);
            stopSelf();
        }
    }

    private void showSuccessfulNotification(final WeiboSendTask task) {
        Notification.Builder builder = new Notification.Builder(SendWeiboService.this)
                .setTicker(getString(R.string.send_successfully))
                .setContentTitle(getString(R.string.send_successfully))
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.send_successfully)
                .setOngoing(false);
        Notification notification = builder.getNotification();
        final NotificationManager notificationManager = (NotificationManager) getApplicationContext()
                .getSystemService(NOTIFICATION_SERVICE);
        final int id = tasksNotifications.get(task);
        notificationManager.notify(id, notification);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                notificationManager.cancel(id);
                stopServiceIfTasksAreEnd(task);
            }
        }, 3000);
    }

    private void showFailedNotification(final WeiboSendTask task) {
        Notification.Builder builder = new Notification.Builder(SendWeiboService.this)
                .setTicker(getString(R.string.send_failed_and_save_to_draft))
                .setContentTitle(getString(R.string.send_failed))
                .setContentText(getString(R.string.click_to_open_draft))
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.send_failed)
                .setOngoing(false);

        Intent notifyIntent = new Intent(SendWeiboService.this, DraftActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setContentIntent(pendingIntent);

        Notification notification = builder.getNotification();
        final NotificationManager notificationManager = (NotificationManager) getApplicationContext()
                .getSystemService(NOTIFICATION_SERVICE);
        final int id = tasksNotifications.get(task);
        notificationManager.notify(id, notification);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopServiceIfTasksAreEnd(task);
            }
        }, 3000);
    }
}
