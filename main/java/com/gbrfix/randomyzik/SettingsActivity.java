package com.gbrfix.randomyzik;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

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
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            final SwitchPreferenceCompat apiKeySwicher = findPreference("amp_api");
            final EditTextPreference apiKeyPref = findPreference("amp_api_key");
            final EditTextPreference userPref = findPreference("amp_user");
            final EditTextPreference pwdPref = findPreference("amp_pwd");
            apiKeyPref.setVisible(apiKeySwicher.isChecked());
            userPref.setVisible(!apiKeySwicher.isChecked());
            pwdPref.setVisible(!apiKeySwicher.isChecked());
            apiKeySwicher.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    boolean value = (boolean)newValue;
                    apiKeyPref.setVisible(value);
                    userPref.setVisible(!value);
                    pwdPref.setVisible(!value);
                    return true;
                }
            });
            pwdPref.setSummaryProvider(new Preference.SummaryProvider() {
                @Nullable
                @Override
                public CharSequence provideSummary(@NonNull Preference preference) {
                    String pwd = PreferenceManager.getDefaultSharedPreferences(getContext()).getString("amp_pwd", "");
                    StringBuilder sb = new StringBuilder();
                    for (int s = 0; s < pwd.length(); s++) {
                        sb.append("*");
                    }
                    return sb.toString();
                }
            });
            pwdPref.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
                @Override
                public void onBindEditText(@NonNull EditText editText) {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
            });
        }
    }
}