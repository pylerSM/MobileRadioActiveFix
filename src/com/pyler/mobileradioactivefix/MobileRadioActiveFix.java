package com.pyler.mobileradioactivefix;

import java.util.ArrayList;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.SystemClock;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MobileRadioActiveFix implements IXposedHookLoadPackage {

	@Override
	public void handleLoadPackage(final LoadPackageParam lpparam)
			throws Throwable {
		if (!"android".equals(lpparam.packageName)) {
			return;
		}

		XposedHelpers.findAndHookMethod(
				"com.android.server.content.SyncManager$SyncHandler",
				lpparam.classLoader, "manageSyncAlarmLocked", long.class,
				long.class, new XC_MethodReplacement() {
					@Override
					protected Object replaceHookedMethod(
							XC_MethodReplacement.MethodHookParam param)
							throws Throwable {
						long nextPeriodicEventElapsedTime = (long) param.args[0];
						long nextPendingEventElapsedTime = (long) param.args[1];
						Object syncManager = XposedHelpers
								.getSurroundingThis(param.thisObject);

						// in each of these cases the sync loop will be kicked,
						// which will cause this method to be called again
						boolean mDataConnectionIsConnected = XposedHelpers
								.getBooleanField(syncManager,
										"mDataConnectionIsConnected");
						boolean mStorageIsLow = XposedHelpers.getBooleanField(
								syncManager, "mStorageIsLow");
						if (!mDataConnectionIsConnected) {
							return null;
						}
						if (mStorageIsLow) {
							return null;
						}

						// When the status bar notification should be raised
						Object mSyncHandler = XposedHelpers.getObjectField(
								syncManager, "mSyncHandler");
						Object mSyncNotificationInfo = XposedHelpers
								.getObjectField(mSyncHandler,
										"mSyncNotificationInfo");
						boolean isActive = XposedHelpers.getBooleanField(
								mSyncNotificationInfo, "isActive");
						Long startTime = (Long) XposedHelpers.getObjectField(
								mSyncNotificationInfo, "startTime");
						long SYNC_NOTIFICATION_DELAY = XposedHelpers
								.getLongField(syncManager,
										"SYNC_NOTIFICATION_DELAY");
						final long notificationTime = (!isActive && startTime != null) ? startTime
								+ SYNC_NOTIFICATION_DELAY
								: Long.MAX_VALUE;

						// When we should consider canceling an active sync
						long earliestTimeoutTime = Long.MAX_VALUE;
						String TAG = (String) XposedHelpers.getObjectField(
								syncManager, "TAG");
						@SuppressWarnings("unchecked")
						ArrayList<Object> mActiveSyncContexts = (ArrayList<Object>) XposedHelpers
								.getObjectField(syncManager,
										"mActiveSyncContexts");
						long MAX_TIME_PER_SYNC = XposedHelpers.getLongField(
								syncManager, "MAX_TIME_PER_SYNC");
						for (Object currentSyncContext : mActiveSyncContexts) {
							long mTimeoutStartTime = XposedHelpers
									.getLongField(currentSyncContext,
											"mTimeoutStartTime");
							final long currentSyncTimeoutTime = mTimeoutStartTime
									+ MAX_TIME_PER_SYNC;
							if (Log.isLoggable(TAG, Log.VERBOSE)) {
								Log.v(TAG,
										"manageSyncAlarm: active sync, mTimeoutStartTime + MAX is "
												+ currentSyncTimeoutTime);
							}
							if (earliestTimeoutTime > currentSyncTimeoutTime) {
								earliestTimeoutTime = currentSyncTimeoutTime;
							}
						}

						if (Log.isLoggable(TAG, Log.VERBOSE)) {
							Log.v(TAG, "manageSyncAlarm: notificationTime is "
									+ notificationTime);
						}

						if (Log.isLoggable(TAG, Log.VERBOSE)) {
							Log.v(TAG,
									"manageSyncAlarm: earliestTimeoutTime is "
											+ earliestTimeoutTime);
						}

						if (Log.isLoggable(TAG, Log.VERBOSE)) {
							Log.v(TAG,
									"manageSyncAlarm: nextPeriodicEventElapsedTime is "
											+ nextPeriodicEventElapsedTime);
						}
						if (Log.isLoggable(TAG, Log.VERBOSE)) {
							Log.v(TAG,
									"manageSyncAlarm: nextPendingEventElapsedTime is "
											+ nextPendingEventElapsedTime);
						}

						long alarmTime = Math.min(notificationTime,
								earliestTimeoutTime);
						alarmTime = Math.min(alarmTime,
								nextPeriodicEventElapsedTime);
						alarmTime = Math.min(alarmTime,
								nextPendingEventElapsedTime);

						// Bound the alarm time.
						long SYNC_ALARM_TIMEOUT_MIN = XposedHelpers
								.getLongField(syncManager,
										"SYNC_ALARM_TIMEOUT_MIN");
						long SYNC_ALARM_TIMEOUT_MAX = XposedHelpers
								.getLongField(syncManager,
										"SYNC_ALARM_TIMEOUT_MAX");
						final long now = SystemClock.elapsedRealtime();
						if (alarmTime < now + SYNC_ALARM_TIMEOUT_MIN) {
							if (Log.isLoggable(TAG, Log.VERBOSE)) {
								Log.v(TAG,
										"manageSyncAlarm: the alarmTime is too small, "
												+ alarmTime
												+ ", setting to "
												+ (now + SYNC_ALARM_TIMEOUT_MIN));
							}
							alarmTime = now + SYNC_ALARM_TIMEOUT_MIN;
						} else if (alarmTime > now + SYNC_ALARM_TIMEOUT_MAX) {
							if (Log.isLoggable(TAG, Log.VERBOSE)) {
								Log.v(TAG,
										"manageSyncAlarm: the alarmTime is too large, "
												+ alarmTime
												+ ", setting to "
												+ (now + SYNC_ALARM_TIMEOUT_MIN));
							}
							alarmTime = now + SYNC_ALARM_TIMEOUT_MAX;
						}

						// determine if we need to set or cancel the alarm
						Long mAlarmScheduleTime = (Long) XposedHelpers
								.getObjectField(param.thisObject,
										"mAlarmScheduleTime");
						boolean shouldSet = false;
						boolean shouldCancel = false;
						final boolean alarmIsActive = (mAlarmScheduleTime != null)
								&& (now < mAlarmScheduleTime);
						final boolean needAlarm = alarmTime != Long.MAX_VALUE;
						if (needAlarm) {
							// Need the alarm if
							// - it's currently not set
							// - if the alarm is set in the past.
							if (!alarmIsActive
									|| alarmTime != mAlarmScheduleTime) {
								shouldSet = true;
							}
						} else {
							shouldCancel = alarmIsActive;
						}

						// Set or cancel the alarm as directed.
						XposedHelpers.callMethod(syncManager,
								"ensureAlarmService");

						PendingIntent mSyncAlarmIntent = (PendingIntent) XposedHelpers
								.getObjectField(syncManager, "mSyncAlarmIntent");
						AlarmManager mAlarmService = (AlarmManager) XposedHelpers
								.getObjectField(syncManager, "mAlarmService");
						if (shouldSet) {
							if (Log.isLoggable(TAG, Log.VERBOSE)) {
								Log.v(TAG,
										"requesting that the alarm manager wake us up at elapsed time "
												+ alarmTime + ", now is " + now
												+ ", "
												+ ((alarmTime - now) / 1000)
												+ " secs from now");
							}
							XposedHelpers.setObjectField(param.thisObject,
									"mAlarmScheduleTime", alarmTime);
							mAlarmService.setExact(
									AlarmManager.ELAPSED_REALTIME_WAKEUP,
									alarmTime, mSyncAlarmIntent);
						} else if (shouldCancel) {
							XposedHelpers.setObjectField(param.thisObject,
									"mAlarmScheduleTime", null);
							mAlarmService.cancel(mSyncAlarmIntent);
						}

						return null;
					}
				});

		XposedHelpers.findAndHookMethod(
				"com.android.server.NetworkManagementService",
				lpparam.classLoader, "notifyInterfaceClassActivity", int.class,
				int.class, long.class, boolean.class, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam param)
							throws Throwable {
						param.args[3] = true;
					}
				});
	}

}
