package com.roamingroths.cmcc.data;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.roamingroths.cmcc.Extras;
import com.roamingroths.cmcc.Preferences;
import com.roamingroths.cmcc.R;
import com.roamingroths.cmcc.logic.ChartEntry;
import com.roamingroths.cmcc.logic.Cycle;
import com.roamingroths.cmcc.logic.EntryContainer;
import com.roamingroths.cmcc.ui.entry.detail.EntryDetailActivity;
import com.roamingroths.cmcc.ui.entry.list.ChartEntryViewHolder;
import com.roamingroths.cmcc.utils.DateUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Completable;

/**
 * Created by parkeroth on 4/18/17.
 */

public class ChartEntryAdapter extends RecyclerView.Adapter<ChartEntryViewHolder.Impl> {

  private final Context mContext;
  private final OnClickHandler mClickHandler;
  private final EntryContainerListener mListener;
  private final AtomicBoolean mEntryListenerAttached;
  private final DatabaseReference mEntriesDbRef;
  private final Preferences mPreferences;
  private EntryContainerList mContainerList;

  public ChartEntryAdapter(
      Context context,
      Cycle cycle,
      OnClickHandler clickHandler,
      FirebaseDatabase db,
      CycleProvider cycleProvider) {
    mEntriesDbRef = db.getReference("entries").child(cycle.id).child("chart");
    mEntriesDbRef.keepSynced(true);
    mEntryListenerAttached = new AtomicBoolean(false);
    mContext = context;
    mClickHandler = clickHandler;
    mPreferences = Preferences.fromShared(mContext);
    mContainerList = EntryContainerList.builder(cycle, mPreferences).withAdapter(this).build();
    mListener = new EntryContainerListener(context, mContainerList, cycleProvider.getProviderForClazz(ChartEntry.class));

    PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(
        new SharedPreferences.OnSharedPreferenceChangeListener() {
          @Override
          public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            mPreferences.update(sharedPreferences);
            notifyDataSetChanged();
          }
        }
    );
  }

  public boolean initFromIntent(Intent intent) {
    if (!intent.hasExtra(EntryContainer.class.getName())) {
      return false;
    }
    List<EntryContainer> containers = intent.getParcelableArrayListExtra(EntryContainer.class.getName());
    for (EntryContainer container : containers) {
      mContainerList.addEntry(container);
    }
    return true;
  }

  public void initialize(List<EntryContainer> containers) {
    for (EntryContainer container : containers) {
      mContainerList.addEntry(container);
    }
    notifyDataSetChanged();
  }

  public Completable initialize(CycleProvider cycleProvider) {
    return mContainerList.initialize(cycleProvider);
  }

  public void updateContainer(EntryContainer container) {
    mContainerList.changeEntry(container);
  }

  public synchronized void attachListener() {
    if (mEntryListenerAttached.compareAndSet(false, true)) {
      //mEntriesDbRef.addChildEventListener(mListener);
    } else {
      Log.w("ChartEntryAdapter", "Already attached!");
    }
  }

  public synchronized void detachListener() {
    if (mEntryListenerAttached.compareAndSet(true, false)) {
      //mEntriesDbRef.removeEventListener(mListener);
    } else {
      Log.w("ChartEntryAdapter", "Not attached!");
    }
  }

  public Cycle getCycle() {
    return mContainerList.mCycle;
  }

  @Override
  public ChartEntryViewHolder.Impl onCreateViewHolder(ViewGroup parent, int viewType) {
    int layoutIdForListItem = R.layout.observation_list_item;
    LayoutInflater inflater = LayoutInflater.from(mContext);
    boolean shouldAttachToParentImmediately = false;

    View view = inflater.inflate(layoutIdForListItem, parent, shouldAttachToParentImmediately);
    return new ChartEntryViewHolder.Impl(view, mContainerList, mClickHandler);
  }

  /**
   * OnBindViewHolder is called by the RecyclerView to display the data at the specified
   * position. In this method, we update the contents of the ViewHolder to display the weather
   * details for this particular position, using the "position" argument that is conveniently
   * passed into us.
   *
   * @param holder   The ViewHolder which should be updated to represent the
   *                 contents of the item at the given position in the data set.
   * @param position The position of the item within the adapter's data set.
   */
  @Override
  public void onBindViewHolder(ChartEntryViewHolder.Impl holder, int position) {
    mContainerList.bindViewHolder(holder, position, mContext);
  }

  @Override
  public int getItemCount() {
    return mContainerList.size();
  }

  public interface OnClickHandler {
    void onClick(EntryContainer container, int index);
  }

  public Intent getIntentForModification(EntryContainer container, int index) {
    Intent intent = new Intent(mContext, EntryDetailActivity.class);
    intent.putExtra(Extras.ENTRY_DATE_STR, DateUtil.toWireStr(container.entryDate));
    intent.putExtra(Extras.EXPECT_UNUSUAL_BLEEDING, mContainerList.expectUnusualBleeding(index));
    intent.putExtra(Cycle.class.getName(), mContainerList.mCycle);
    intent.putExtra(EntryContainer.class.getName(), container);
    return intent;
  }
}
