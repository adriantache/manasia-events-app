package com.adriantache.manasia_events.notification;

import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import com.adriantache.manasia_events.custom_class.Event;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import static com.adriantache.manasia_events.MainActivity.DBEventIDTag;
import static com.adriantache.manasia_events.db.DBUtils.readDatabase;
import static com.adriantache.manasia_events.util.Utils.calculateDelay;
import static com.adriantache.manasia_events.util.Utils.compareDateToToday;

/**
 * Class that stores various functionality related to notifications and scheduling
 **/
public class NotifyUtils {
    //set a tag in order to be able to disable all work if needed
    private static final String workTag = "notificationWork";

    /**
     * Method to read all events from the database and set notifications for the ones that
     * the user selected to be notified for.
     * <p>
     * todo [IDEA] allow user to be notified for all events but opt out of some
     *
     * @param context application context for database operation and notification clearing
     * @param addAll  flag to determine if user will be notified for all events in the future
     */
    public static void scheduleNotifications(Context context, boolean addAll) {
        ArrayList<Event> events = (ArrayList<Event>) readDatabase(context);
        if (events == null || events.size() == 0) return;

        resetAllWork(context);

        for (Event event : events) {
            if (addAll && compareDateToToday(event.getDate()) > -1) {
                addNotification(event.getDatabaseID(), event.getDate());
                continue;
            }

            if (event.getNotify() == 1)
                addNotification(event.getDatabaseID(), event.getDate());
        }
    }

    private static void resetAllWork(Context context) {
        //cancel all pending work tasks
        WorkManager.getInstance().cancelAllWorkByTag(workTag);

        //clear all notifications to prevent duplicates
        //todo [IMPORTANT] figure out database ID increment problem, should negate the need for this code
        NotificationManager notificationManager = (NotificationManager) context.
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancelAll();
        }
    }

    private static void addNotification(int DBEventID, String eventDate) {
        //store DBEventID to pass it to the PendingIntent and open the appropriate event page on notification click
        Data inputData = new Data.Builder().putInt(DBEventIDTag, DBEventID).build();
        Log.i("REMOVE THIS", "addNotification: "+DBEventID);

        OneTimeWorkRequest notificationWork = new OneTimeWorkRequest.Builder(NotifyWorker.class)
               //todo  .setInitialDelay(calculateDelay(eventDate), TimeUnit.MILLISECONDS)
                .setInitialDelay(5, TimeUnit.SECONDS)
                .setInputData(inputData)
                .addTag(workTag)
                .build();

        WorkManager.getInstance().enqueue(notificationWork);
    }
}