package com.roamingroths.cmcc;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.google.common.collect.ImmutableSet;
import com.google.firebase.database.FirebaseDatabase;
import com.roamingroths.cmcc.data.WellnessEntryProvider;
import com.roamingroths.cmcc.logic.WellnessEntry;
import com.roamingroths.cmcc.utils.MultiSelectPrefAdapter;

/**
 * Created by parkeroth on 9/11/17.
 */

public class WellnessEntryFragment extends EntryFragment<WellnessEntry> {

  private RecyclerView mRecyclerView;
  private MultiSelectPrefAdapter mAdapter;
  private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener;

  public WellnessEntryFragment() {
    super(R.layout.fragment_wellness_entry);
  }

  @Override
  WellnessEntryProvider createEntryProvider(FirebaseDatabase db) {
    return WellnessEntryProvider.forDb(db);
  }

  @Override
  void duringCreateView(View view, Bundle args, Bundle savedInstanceState) {
    String[] values = getActivity().getResources().getStringArray(R.array.pref_wellness_option_values);
    String[] keys = getActivity().getResources().getStringArray(R.array.pref_wellness_option_keys);
    mAdapter = new MultiSelectPrefAdapter(
        getContext(),
        R.layout.wellness_list_item,
        R.id.tv_wellness_item,
        R.id.switch_wellness_item, values, keys, savedInstanceState);

    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
    mAdapter.updateActiveItems(
        preferences.getStringSet("pref_key_wellness_options", ImmutableSet.<String>of()));
    mPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
      @Override
      public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.v("WellnessEntryFragment", "onSharedPreferenceChanged: " + key);
        if (key.equals("pref_key_wellness_options")) {
          mAdapter.updateActiveItems(
              sharedPreferences.getStringSet("pref_key_wellness_options", ImmutableSet.<String>of()));
        }
      }
    };
    preferences.registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);

    mRecyclerView = (RecyclerView) view.findViewById(R.id.recyclerview_wellness_entry);
    mRecyclerView.setLayoutManager(
        new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
    mRecyclerView.setHasFixedSize(false);
    mRecyclerView.setAdapter(mAdapter);
  }

  @Override
  public void setUserVisibleHint(boolean isVisibleToUser) {
    Log.v("WellnessEntryFragment", "Hint visible: " + isVisibleToUser);
    if (isVisibleToUser) {
      hideKeyboard();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.v("WellnessEntryFragment", "onCreateView");
    hideKeyboard();
  }

  @Override
  public void onPause() {
    super.onPause();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    mAdapter.fillBundle(outState);
  }

  private void hideKeyboard() {
    Context context = getContext();
    if (context != null) {
      InputMethodManager imm =
          (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
      imm.hideSoftInputFromWindow(getView().getRootView().getWindowToken(), 0);
    }
  }

  @Override
  WellnessEntry getEntryFromUi() {
    return new WellnessEntry(getEntryDate(), mAdapter.getActiveEntries(), getCycle().keys.wellnessKey);
  }

  @Override
  void updateUiWithEntry(WellnessEntry entry) {
    mAdapter.updateValues(entry.wellnessItems);
  }
}
