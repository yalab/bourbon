package org.yalab.bourbon

import android.os.Bundle
import android.preference.PreferenceActivity

class SettingsActivity extends PreferenceActivity{
  override def onCreate(state: Bundle){
    super.onCreate(state)
    addPreferencesFromResource(R.layout.settings);
  }
}
