package com.adriantache.manasia_events;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.adriantache.manasia_events.custom_class.Event;
import com.adriantache.manasia_events.util.Utils;
import com.github.zagum.switchicon.SwitchIconView;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;

public class EventDetail extends AppCompatActivity {
    private final int EVENT_DETAIL = 1;
    private final String OPENED_FROM_NOTIFICATION = "com.adriantache.manasia_events.openedFromNotification";
    @BindView(R.id.thumbnail)
    ImageView thumbnail;
    @BindView(R.id.category_image)
    ImageView category_image;
    @BindView(R.id.day)
    TextView day;
    @BindView(R.id.month)
    TextView month;
    @BindView(R.id.title)
    TextView title;
    @BindView(R.id.bookmark)
    ImageView bookmark;
    @BindView(R.id.bookmark_layout)
    LinearLayout bookmark_layout;
    @BindView(R.id.description)
    TextView description;
    @BindView(R.id.back)
    ImageView back;
    @BindView(R.id.call)
    TextView call;
    @BindView(R.id.map)
    TextView map;
    @BindView(R.id.notify_icon)
    SwitchIconView notify_icon;
    @BindView(R.id.notify)
    LinearLayout notify;
    boolean openedFromNotification;
    private Event event;
    private int arrayPosition = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);
        ButterKnife.bind(this);

        //get the event for which to display details
        Intent intent = getIntent();
        event = (Event) intent.getParcelableArrayListExtra("events").get(0);

        //set whether activity was opened from notification
        if (intent.hasExtra(OPENED_FROM_NOTIFICATION)) {
            openedFromNotification = intent.getExtras().getBoolean(OPENED_FROM_NOTIFICATION);
        }

        if (intent.hasExtra("arrayPosition")) {
            arrayPosition = intent.getExtras().getInt("arrayPosition");
        }

        //populate fields with details
        if (!TextUtils.isEmpty(event.getPhotoUrl()))
            Picasso.get().load(event.getPhotoUrl()).into(thumbnail);
        else
            thumbnail.setImageResource(R.drawable.manasia_logo);
        category_image.setImageResource(event.getCategory_image());
        day.setText(Utils.extractDate(event.getDate(), true));
        month.setText(Utils.extractDate(event.getDate(), false));
        title.setText(event.getTitle());
        description.setText(event.getDescription());
        if (event.getNotify())
            bookmark.setImageResource(R.drawable.alarm_accent);
        else
            bookmark.setImageResource(R.drawable.alarm);

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (openedFromNotification) {
                    ArrayList<Event> temp = new ArrayList<>();
                    temp.add(event);

                    Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                    intent.putParcelableArrayListExtra("events_result", temp);
                    intent.putExtra("arrayPosition", arrayPosition);
                    //setResult(RESULT_OK, intent);
                    startActivityForResult(intent, EVENT_DETAIL);
                } else {
                    setResult(); //todo rethink if necessary?
                    finish();
                }
            }
        });

        call.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:0736 760 063"));
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                }
            }
        });

        map.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String geoLocation = "geo:0,0?q=Manasia Hub, Stelea Spătarul, nr.13, 030211 Bucharest, Romania";
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(geoLocation));
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                }

            }
        });

        //set notify button state depending on notify state
        notify_icon.setIconEnabled(event.getNotify());

        //only add notification for events in the future (or today)
        if (Utils.compareDateToToday(event.getDate()) < 0) {
            notify_icon.setEnabled(false);

            //also hide the notification indicator up top
            bookmark_layout.setVisibility(View.INVISIBLE);
        } else
            notify.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //todo implement code to show notification

                    //todo implement actual notification code
                    //todo implement notifications in the main Event class, then run a method to reset and then set all notifications (might be inefficient)

                    if (event.getNotify()) {
                        notify_icon.setIconEnabled(false);
                        Toast.makeText(getApplicationContext(), "Disabled notification.", Toast.LENGTH_SHORT).show();
                        bookmark.setImageResource(R.drawable.alarm);
                        //todo implement a way to send back and store data to the Event object, otherwise this is kind of pointless
                        event.setNotify(false);

                        //set the event modifier in the main app since we can't access that ArrayList
                        setResult();
                    } else {
                        notify_icon.setIconEnabled(true);
                        Toast.makeText(getApplicationContext(), "We will notify you on the day of the event.", Toast.LENGTH_SHORT).show();
                        bookmark.setImageResource(R.drawable.alarm_accent);
                        event.setNotify(true);
                        showNotification(event);

                        //set the event modifier in the main app since we can't access that ArrayList
                        setResult();
                    }
                }
            });
    }

    //use this to pass the modified event back to the main app
    private void setResult() {
        ArrayList<Event> temp = new ArrayList<>();
        temp.add(event);

        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.putParcelableArrayListExtra("events_result", temp);
        setResult(RESULT_OK, intent);
    }

    //todo implement real notification system (probably with a service)
    //todo schedule notification https://stackoverflow.com/questions/36902667/how-to-schedule-notification-in-android
    //http://droidmentor.com/schedule-notifications-using-alarmmanager/
    //https://developer.android.com/topic/performance/scheduling
    private void showNotification(Event event) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            CharSequence name = "manasia_notification";
            String description = "manasia notification channel";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("MANASIA", name, importance);
            channel.setDescription(description);
            // Register the channel with the system
            NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        //todo rewrite this once we figure out data delivery
        //get latest event and Build notification
        String notificationTitle = "Manasia event: " + event.getTitle();
        String notificationText = event.getDate() + " at Stelea Spatarul 13, Bucuresti";

        //maybe make activity open latest even by default, but how do you send it to it?
        Intent intent = new Intent(getApplicationContext(), EventDetail.class);
        //put event in the notification to open event details directly
        ArrayList<Event> temp = new ArrayList<>();
        temp.add(event);
        intent.putParcelableArrayListExtra("events", temp);
        intent.putExtra(OPENED_FROM_NOTIFICATION, true);
        intent.putExtra("arrayPosition", arrayPosition);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 1, intent, FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext(), "MANASIA")
                .setSmallIcon(R.drawable.ic_manasia_small)
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(1, mBuilder.build());
    }
}

//todo create intent to open calendar to schedule event ?
//todo create intent to open FB event page ?
//todo setting to always notify on the day of the event