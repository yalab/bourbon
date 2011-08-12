package org.yalab.bourbon

import _root_.android.os.Bundle
import _root_.android.preference.PreferenceActivity

class SettingsActivity extends PreferenceActivity{
  override def onCreate(state: Bundle){
    super.onCreate(state)
    addPreferencesFromResource(R.layout.settings);
  }
}
