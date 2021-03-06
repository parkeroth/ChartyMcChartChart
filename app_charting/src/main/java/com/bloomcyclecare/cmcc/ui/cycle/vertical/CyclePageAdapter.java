package com.bloomcyclecare.cmcc.ui.cycle.vertical;

import android.os.Bundle;
import android.view.ViewGroup;

import com.bloomcyclecare.cmcc.ViewMode;
import com.bloomcyclecare.cmcc.data.models.charting.Cycle;
import com.bloomcyclecare.cmcc.utils.SmartFragmentStatePagerAdapter;
import com.google.common.collect.Iterables;

import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import io.reactivex.annotations.Nullable;

/**
 * Created by parkeroth on 11/16/17.
 */

class CyclePageAdapter extends SmartFragmentStatePagerAdapter<EntryListFragment> {

  private final List<Cycle> mCycles = new ArrayList<>();
  private ViewMode viewMode;

  CyclePageAdapter(FragmentManager fragmentManager) {
    super(fragmentManager);
  }

  public void update(List<Cycle> cycles, ViewMode viewMode) {
    this.viewMode = viewMode;
    if (!Iterables.elementsEqual(mCycles, cycles)) {
      mCycles.clear();
      mCycles.addAll(cycles);
      notifyDataSetChanged();
    }
    // This should be done inside the if statement but doing so caused an issue when resuming the
    // fragment so this is basically a hack...
  }

  @Override
  public int getItemPosition(@NonNull Object object) {
    // DO NOT REMOVE...
    // https://stackoverflow.com/questions/7263291/viewpager-pageradapter-not-updating-the-view
    return POSITION_NONE;
  }

  // Returns total number of pages
  @Override
  public int getCount() {
    return mCycles.size();
  }

  // Returns the fragment to display for that page
  @Override
  public EntryListFragment getItem(int position) {
    Cycle cycle = mCycles.get(position);

    Bundle args = new Bundle();
    args.putParcelable(EntryListFragment.Extras.CURRENT_CYCLE.name(), Parcels.wrap(cycle));
    args.putBoolean(EntryListFragment.Extras.IS_LAST_CYCLE.name(), position == mCycles.size() - 1);
    args.putInt(EntryListFragment.Extras.VIEW_MODE.name(), viewMode.ordinal());

    EntryListFragment fragment = new EntryListFragment();
    fragment.setArguments(args);

    maybeUpdateFragments(fragment, position);

    return fragment;
  }

  void onPageActive(int position) {
    EntryListFragment f = getRegisteredFragment(position);
    if (f != null) {
      f.onScrollStateUpdate(f.getScrollState());
    }
  }

  private void maybeUpdateFragments(@Nullable EntryListFragment fragment, int position) {
    if (fragment != null) {
      EntryListFragment leftNeighbor = getRegisteredFragment(position - 1);
      if (leftNeighbor != null) {
        fragment.setNeighbor(leftNeighbor, EntryListFragment.Neighbor.LEFT);
        leftNeighbor.setNeighbor(fragment, EntryListFragment.Neighbor.RIGHT);
      }
      EntryListFragment rightNeighbor = getRegisteredFragment(position + 1);
      if (rightNeighbor != null) {
        fragment.setNeighbor(rightNeighbor, EntryListFragment.Neighbor.RIGHT);
        rightNeighbor.setNeighbor(fragment, EntryListFragment.Neighbor.LEFT);
      }
    }
  }

  @Override
  @NonNull
  public Object instantiateItem(ViewGroup container, int position) {
    EntryListFragment f = (EntryListFragment) super.instantiateItem(container, position);
    maybeUpdateFragments(f, position);
    return f;
  }

  // Returns the page title for the top indicator
  @Override
  public CharSequence getPageTitle(int position) {
    return "Tab Title";
  }
}
