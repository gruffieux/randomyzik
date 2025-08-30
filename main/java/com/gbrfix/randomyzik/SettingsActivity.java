package com.gbrfix.randomyzik;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        final View settingsView = findViewById(R.id.settingsView);
        ViewCompat.setOnApplyWindowInsetsListener(settingsView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemGestures());
            settingsView.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private void loadCatalogs(SharedPreferences prefs, ListPreference catalogsPref) {
            catalogsPref.setValue(prefs.getString("amp_catalog", "0"));
            catalogsPref.setEnabled(false);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());
            executor.execute(() -> {
                Map<String, String> cats;
                try {
                    AmpSession ampSession = AmpSession.getInstance(getContext());
                    ampSession.connect();
                    cats = ampSession.catalogs();
                } catch (Exception e) {
                    cats = null;
                }
                final Map<String, String> catalogs = cats;
                if (catalogs != null) {
                    handler.post(() -> {
                        CharSequence[] entries = catalogs.keySet().toArray(new String[0]);
                        CharSequence[] values = catalogs.values().toArray(new String[0]);
                        catalogsPref.setEntries(entries);
                        catalogsPref.setEntryValues(values);
                        catalogsPref.setDefaultValue(values[0]);
                        catalogsPref.setEnabled(true);
                    });
                }
            });
        }

        private void stopPlay() {
            Intent intent = new Intent(getActivity(), MediaPlaybackService.class);
            intent.setAction("stop");
            getActivity().startService(intent);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            final SwitchPreferenceCompat ampSwitcher = findPreference("amp");
            final EditTextPreference serverPref = findPreference("amp_server");
            final SwitchPreferenceCompat apiKeySwicher = findPreference("amp_api");
            final EditTextPreference apiKeyPref = findPreference("amp_api_key");
            final EditTextPreference userPref = findPreference("amp_user");
            final EditTextPreference pwdPref = findPreference("amp_pwd");
            final ListPreference catalogsPref = findPreference("amp_catalog");
            final SwitchPreferenceCompat modeSwitcher = findPreference("amp_streaming");
            apiKeyPref.setVisible(apiKeySwicher.isChecked());
            userPref.setVisible(!apiKeySwicher.isChecked());
            pwdPref.setVisible(!apiKeySwicher.isChecked());

            apiKeySwicher.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    boolean value = (boolean) newValue;
                    apiKeyPref.setVisible(value);
                    userPref.setVisible(!value);
                    pwdPref.setVisible(!value);
                    stopPlay();
                    return true;
                }
            });

            serverPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    catalogsPref.setValue("0");
                    stopPlay();
                    return true;
                }
            });

            pwdPref.setSummaryProvider(new Preference.SummaryProvider() {
                @Nullable
                @Override
                public CharSequence provideSummary(@NonNull Preference preference) {
                    String pwd = prefs.getString("amp_pwd", "");
                    StringBuilder sb = new StringBuilder();
                    for (int s = 0; s < pwd.length(); s++) {
                        sb.append("*");
                    }
                    return sb.toString();
                }
            });

            userPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    stopPlay();
                    return true;
                }
            });

            apiKeyPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    stopPlay();
                    return true;
                }
            });

            pwdPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    stopPlay();
                    return true;
                }
            });

            pwdPref.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
                @Override
                public void onBindEditText(@NonNull EditText editText) {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
            });

            ampSwitcher.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    if ((boolean)newValue) {
                        loadCatalogs(prefs, catalogsPref);
                    }
                    stopPlay();
                    return true;
                }
            });

            catalogsPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    stopPlay();
                    return true;
                }
            });

            modeSwitcher.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    stopPlay();
                    return true;
                }
            });

            // Chargement de la liste des catalogues par le serveur
            if (prefs.getBoolean("amp", false)) {
                loadCatalogs(prefs, catalogsPref);
            }
        }
    }
}