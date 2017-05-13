package com.example.user.camera;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

public class SettingActivity extends PreferenceActivity {

    //EDIT: In appcompat-v7 22.1.0 Google added the AppCompatDelegate abstract class
    // as a delegate you can use to extend AppCompat's support to any activity.

    public static final String PREF_CAR_IP = "com.example.user.camera.SettingActivity.IP";
    public static final String PREF_CAR_CAMPORT = "com.example.user.camera.SettingActivity.CAMPORT";
    public static final String PREF_CAR_CTLPORT = "com.example.user.camera.SettingActivity.CTLPORT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.pref_setting);
        bindPreferenceSummaryToValue(findPreference(PREF_CAR_IP));
        bindPreferenceSummaryToValue(findPreference(PREF_CAR_CAMPORT));
        bindPreferenceSummaryToValue(findPreference(PREF_CAR_CTLPORT));
    }

    private static final String TAG = "SettingActivity";
    private static Preference.OnPreferenceChangeListener
        sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {

            String stringValue = newValue.toString();
            preference.setSummary(stringValue);
            Log.d(TAG, "onPreferenceChange: "+stringValue);
            return true;
        }
    };
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }
}
