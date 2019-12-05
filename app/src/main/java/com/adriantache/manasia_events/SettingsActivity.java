package com.adriantache.manasia_events;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

import java.util.Calendar;

import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;
import static com.adriantache.manasia_events.util.CommonStrings.DB_EVENT_ID_TAG;
import static com.adriantache.manasia_events.util.CommonStrings.FIRST_LAUNCH_SETTING;
import static com.adriantache.manasia_events.util.CommonStrings.LAST_UPDATE_TIME_SETTING;
import static com.adriantache.manasia_events.util.CommonStrings.NOTIFY_SETTING;
import static com.adriantache.manasia_events.util.CommonStrings.SOURCE_ACTIVITY;
import static com.adriantache.manasia_events.util.CommonStrings.SOURCE_EVENT_ACTIVITY;
import static com.adriantache.manasia_events.util.CommonStrings.SOURCE_MAIN_ACTIVITY;

public class SettingsActivity extends AppCompatActivity {
    TextView devTools;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        final int activity = getIntent().getIntExtra(SOURCE_ACTIVITY, 0);
        final int DBEventID = getIntent().getExtras().getInt(DB_EVENT_ID_TAG);
        ImageView back = findViewById(R.id.back);
        if (activity == SOURCE_MAIN_ACTIVITY) {
            back.setOnClickListener(v -> {
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(intent);
            });
        } else if (activity == SOURCE_EVENT_ACTIVITY) {
            back.setOnClickListener(v -> {
                Intent intent = new Intent(getApplicationContext(), EventDetail.class);
                intent.putExtra(DB_EVENT_ID_TAG, DBEventID);
                startActivity(intent);
            });
        }

        Button notificationSettings = findViewById(R.id.notification_settings);
        notificationSettings.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        });

        //now that we can get here directly from MainActivity, we set the flag here as well
        //todo replace this with startActivityForResult as well
        SharedPreferences sharedPrefs = getDefaultSharedPreferences(getApplicationContext());

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean(FIRST_LAUNCH_SETTING, false);
        editor.apply();

        //this generates some debugging text useful for when logcat isn't available
        //todo eventually remove this, maybe (could be useful to help users with debug, but settings is user facing)
        devTools = findViewById(R.id.dev_tools);
        populateDevText();
        devTools.setOnClickListener(v -> populateDevText());
        //set hide on click for dev tools view
        TextView devToolsHider = findViewById(R.id.dev_tools_hide);
        devToolsHider.setOnClickListener(v -> devToolsHider.setVisibility(View.INVISIBLE));
    }

    private void populateDevText() {
        //time of last remote update
        SharedPreferences sharedPrefs = getDefaultSharedPreferences(getApplicationContext());

        long lastUpdateTime = sharedPrefs.getLong(LAST_UPDATE_TIME_SETTING, 0);
        Calendar calendar = Calendar.getInstance();
        long timeSinceLUT = (calendar.getTimeInMillis() - lastUpdateTime) / 1000 / 3600;
        //notify on every future event
        //whether this is the first launch of MainActivity to prevent open hours Toast when coming back
        String displayText = "Time since LUT: " + timeSinceLUT + " hours; NotifyAll: "
                + sharedPrefs.getBoolean(NOTIFY_SETTING, false) + "; FirstLaunch: " +
                sharedPrefs.getBoolean(FIRST_LAUNCH_SETTING, true);
        devTools.setTextColor(0xffD4E157);
        devTools.setBackgroundColor(0xff795548);
        devTools.setText(displayText);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        //in the future, remember to add this line to the styles.xml theme:
        //<item name="preferenceTheme">@style/PreferenceThemeOverlay</item>
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setStorageDefault();
            addPreferencesFromResource(R.xml.preferences);
        }
    }
}