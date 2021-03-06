package com.adriantache.manasia_events;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.adriantache.manasia_events.custom_class.Event;
import com.adriantache.manasia_events.databinding.ActivityEventDetailBinding;
import com.adriantache.manasia_events.db.DBUtils;
import com.adriantache.manasia_events.util.Utils;
import com.google.android.material.snackbar.Snackbar;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Objects;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static com.adriantache.manasia_events.notification.NotifyUtils.scheduleNotifications;
import static com.adriantache.manasia_events.util.CommonStrings.DB_EVENT_ID_TAG;
import static com.adriantache.manasia_events.util.CommonStrings.ERROR_VALUE;
import static com.adriantache.manasia_events.util.CommonStrings.FIRST_LAUNCH_SETTING;
import static com.adriantache.manasia_events.util.CommonStrings.NOTIFY_SETTING;
import static com.adriantache.manasia_events.util.CommonStrings.SOURCE_EVENT_ACTIVITY;
import static com.adriantache.manasia_events.util.Utils.getDip;

public class EventDetail extends AppCompatActivity {
    private static final String TAG = "EventDetail";
    private Event event = null;
    private long dbEventId = ERROR_VALUE;
    private boolean reverseAnimation = false;
    private boolean noImage = false;
    private ActivityEventDetailBinding binding;

    //todo [BUG] find NOTIFY_SETTINGS problem
    //todo set notifyOnAllEvents and settings reading to default to TRUE

    @Override
    public void onBackPressed() {
        backToMainActivity();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_event_detail);

        //get the event for which to display details, or fail
        Intent intent = getIntent();
        dbEventId = Objects.requireNonNull(intent.getExtras()).getLong(DB_EVENT_ID_TAG);
        if (dbEventId != ERROR_VALUE)
            event = DBUtils.getEventFromDatabase(this, dbEventId);
        else {
            Toast.makeText(this, "Error getting event ID.", Toast.LENGTH_SHORT).show();
            finish();
        }

        //populate the activity with event details, or fail
        if (event != null) {
            populateDetails();
        } else {
            Toast.makeText(this, "Error getting event " + dbEventId + " from database.",
                    Toast.LENGTH_SHORT).show();
            finish();
        }

        binding.back.setOnClickListener(v -> backToMainActivity());

        binding.call.setOnClickListener(v -> {
            Intent phoneIntent = new Intent(Intent.ACTION_DIAL);
            phoneIntent.setData(Uri.parse("tel:004 0736 760 063"));
            if (phoneIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(phoneIntent);
            }
        });

        binding.map.setOnClickListener(v -> {
            String geoLocation = "geo:0,0?q=Manasia Hub, Stelea Spătarul, nr.13, 030211 Bucharest, Romania";
            Intent locationIntent = new Intent(Intent.ACTION_VIEW);
            locationIntent.setData(Uri.parse(geoLocation));
            if (locationIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(locationIntent);
            }
        });

        binding.location.setOnClickListener(v -> {
            String geoLocation = "geo:0,0?q=Manasia Hub, Stelea Spătarul, nr.13, 030211 Bucharest, Romania";
            Intent locationIntent = new Intent(Intent.ACTION_VIEW);
            locationIntent.setData(Uri.parse(geoLocation));
            if (locationIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(locationIntent);
            }
        });

        //inform MainActivity that this isn't first launch
        //todo replace with startActivityForResult
        SharedPreferences sharedPref = getDefaultSharedPreferences(getApplicationContext());
        sharedPref.edit().putBoolean(FIRST_LAUNCH_SETTING, false).apply();

        //Hide title bar on image click if we have an image
        if (!noImage) {
            binding.thumbnail.setOnClickListener(v -> {
                int duration = 300;
                float start = reverseAnimation ? 0f : 1f;
                float end = reverseAnimation ? 1f : 0f;

                AlphaAnimation animation = new AlphaAnimation(start, end);
                animation.setDuration(duration);
                animation.setFillAfter(true);

                binding.titleBar.startAnimation(animation);
                binding.notifyStatus.startAnimation(animation);
                reverseAnimation();
            });
        }
    }

    private void reverseAnimation() {
        reverseAnimation = !reverseAnimation;
    }

    private void populateDetails() {
        //populate fields with details
        if (!TextUtils.isEmpty(event.getPhotoUrl())) {
            Picasso.get().load(event.getPhotoUrl()).centerCrop().fit().into(binding.thumbnail);
            binding.thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            binding.thumbnail.setBackgroundResource(R.color.colorAccent);
        } else {
            noImage = true;
            binding.thumbnail.setImageResource(R.drawable.manasia_logo);
            binding.thumbnail.setScaleType(ImageView.ScaleType.CENTER);
            binding.thumbnail.setBackgroundResource(R.color.blue_grey100);
            binding.thumbnail.setPadding(0, 0, 0, getDip(this, 100));
        }
        binding.day.setText(Utils.extractDayOrMonth(event.getDate(), true));
        binding.month.setText(Utils.extractDayOrMonth(event.getDate(), false));
        binding.title.setText(event.getTitle());

        //Add tags under description
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(event.getDescription());
        stringBuilder.append("\n\nTags: ");
        ArrayList<String> tags = event.getEventTags();
        for (String tag : tags) {
            stringBuilder.append("[").append(tag).append("] ");
        }
        binding.description.setText(stringBuilder);

        //set on click listener and details for the notify button
        setNotifyDetails();
    }

    private void setNotifyDetails() {
        //if event is in the past, set some defaults and no onClickListener
        if (Utils.compareDateToToday(event.getDate()) < 0) {
            //hide the notification indicator up top
            binding.notifyStatus.setVisibility(View.INVISIBLE);

            //and gray out the SwitchIconView
            binding.notifyIcon.setIconEnabled(false);
            binding.notifyIcon.setEnabled(false);

            return;
        }

        //read notify setting to determine if notifyOnAllEvents is true
        SharedPreferences sharedPref = getDefaultSharedPreferences(getApplicationContext());
        boolean notifyOnAllEvents = sharedPref.getBoolean(NOTIFY_SETTING, true);

        //set notify status appearance
        if (notifyOnAllEvents || event.getNotify() == 1)
            binding.notifyStatus.setImageResource(R.drawable.alarm_accent);
        else
            binding.notifyStatus.setImageResource(R.drawable.alarm);

        //set notify button appearance and onClickListener
        if (notifyOnAllEvents) {
            binding.notifyIcon.setIconEnabled(true);
            binding.notifyLabel.setText(getString(R.string.notifying));

            binding.notify.setOnClickListener(v -> showSnackbar(false));
        } else {
            binding.notify.setOnClickListener(v -> {
                if (event.getNotify() == 1) {
                    binding.notifyIcon.setIconEnabled(false);
                    binding.notifyLabel.setText(getString(R.string.notify));

                    event.setNotify(0);
                    updateDatabase();

                    Toast.makeText(getApplicationContext(), "Disabled notification.",
                            Toast.LENGTH_SHORT).show();
                } else {
                    binding.notifyIcon.setIconEnabled(true);
                    binding.notifyLabel.setText(getString(R.string.notifying));

                    event.setNotify(1);
                    updateDatabase();

                    //since we set the notify flag, use the Snackbar to prompt the user to always get notified
                    showSnackbar(true);
                }

                scheduleNotifications(getApplicationContext(), false);
            });
        }
    }

    //show a snackbar inviting the user to activate notification for all events
    public void showSnackbar(boolean promptAlwaysNotify) {
        if (promptAlwaysNotify) {
            Snackbar snackbar = Snackbar.make(binding.constraintLayout,
                    "You will be notified on the day of the event.\n" +
                            "Would you like to be notified for all events?",
                    Snackbar.LENGTH_LONG);

            snackbar.setAction("Activate", v -> {
                SharedPreferences sharedPref = getDefaultSharedPreferences(getApplicationContext());
                //todo remove this is there are no problems
                if (!sharedPref.edit().putBoolean(NOTIFY_SETTING, true).commit())
                    Toast.makeText(this, "FATAL ERROR SAVING NOTIFY ALL SETTING!", Toast.LENGTH_LONG).show();

                //since we're activating the setting to always be notified, go ahead and schedule notifications
                scheduleNotifications(getApplicationContext(), true);

                Toast.makeText(this, "We will notify you for all future events.",
                        Toast.LENGTH_SHORT).show();

                setNotifyDetails();
            });

            //center snackbar text
            View view = snackbar.getView();
            TextView textView = view.findViewById(R.id.snackbar_text);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);

            snackbar.show();
        } else {
            Snackbar snackbar = Snackbar.make(binding.constraintLayout,
                    "You are already being notified for all events.\n" +
                            "Do you want to change this option?",
                    Snackbar.LENGTH_LONG);

            snackbar.setAction("Settings", v -> {
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                settingsIntent.putExtra("activity", SOURCE_EVENT_ACTIVITY);
                settingsIntent.putExtra(DB_EVENT_ID_TAG, dbEventId);
                startActivity(settingsIntent);
            });

            //center snackbar text
            View view = snackbar.getView();
            TextView textView = view.findViewById(R.id.snackbar_text);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);

            snackbar.show();
        }
    }

    //method to update event in the local database
    private void updateDatabase() {
        if (DBUtils.updateEventToDatabase(this, event) == ERROR_VALUE)
            Log.d(TAG, "updateDatabase: Error writing event to database.");
    }

    //method that handles clicking the back button to create an artificial back stack to MainActivity
    private void backToMainActivity() {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(intent);
    }
}

//todo [IDEA] create intent to open calendar to schedule event ?
//todo [IDEA] create intent to open FB event page ?
