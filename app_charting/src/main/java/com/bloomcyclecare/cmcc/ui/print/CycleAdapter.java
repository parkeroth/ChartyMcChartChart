package com.bloomcyclecare.cmcc.ui.print;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import com.bloomcyclecare.cmcc.R;
import com.bloomcyclecare.cmcc.data.models.charting.Cycle;
import com.bloomcyclecare.cmcc.logic.print.PageRenderer;
import com.bloomcyclecare.cmcc.utils.DateUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import androidx.recyclerview.widget.RecyclerView;
import io.reactivex.annotations.Nullable;

/**
 * Created by parkeroth on 4/18/17.
 */

public class CycleAdapter extends RecyclerView.Adapter<CycleAdapter.CycleAdapterViewHolder> {

  private static final boolean DEBUG = true;
  private static final String TAG = CycleAdapter.class.getSimpleName();

  private final Context mContext;
  private final ImmutableList<CycleWithEntries> mData;
  private final Set<Integer> mSelectedIndexes;
  private ImmutableSet<Integer> mBreakAfterIndexs;

  private enum BundleKey {
    SELECTED_INDEXES, VIEW_MODELS
  }

  public static CycleAdapter create(Context context, List<CycleWithEntries> cyclesWithEntries) {
    // Initialize selected items with most recent cycles
    SortedSet<Integer> selectedIndexes = new TreeSet<>();
    int rowsLeftBeforeNextBreak = PageRenderer.numRowsPerPage();
    for (int i=0; i < cyclesWithEntries.size(); i++) {
      CycleWithEntries cycleWithEntries = cyclesWithEntries.get(i);
      rowsLeftBeforeNextBreak -= PageRenderer.numRows(cycleWithEntries.entries.size());
      if (rowsLeftBeforeNextBreak >= 0) {
        selectedIndexes.add(i);
      }
    }
    return new CycleAdapter(context, cyclesWithEntries, selectedIndexes);
  }

  private CycleAdapter(Context context, List<CycleWithEntries> cyclesWithEntries, SortedSet<Integer> selectedIndexes) {
    if (DEBUG) Log.v(TAG, "Create CycleAdapter");
    mContext = context;
    mData = ImmutableList.copyOf(cyclesWithEntries);
    mSelectedIndexes = selectedIndexes;
    updatePageBreaks();
  }

  public boolean hasValidSelection() {
    boolean selectionStarted = false;
    boolean selectionFinished = false;
    for (int i=0; i < mData.size(); i++) {
      if (mSelectedIndexes.contains(i)) {
        if (!selectionStarted) {
          selectionStarted = true;
        }
        if (selectionFinished) {
          return false;
        }
      } else {
        if (selectionStarted && !selectionFinished) {
          selectionFinished = true;
        }
      }
    }
    return true;
  }

  public List<Cycle> getSelectedCycles() {
    List<Cycle> cycles = new ArrayList<>();
    for (Integer index : mSelectedIndexes) {
      cycles.add(mData.get(index).cycle);
    }
    return cycles;
  }

  /**
   * This gets called when each new ViewHolder is created. This happens when the RecyclerView
   * is laid out. Enough ViewHolders will be created to fill the screen and allow for scrolling.
   *
   * @param parent   The ViewGroup that these ViewHolders are contained within.
   * @param viewType If your RecyclerView has more than one type of item (which ours doesn't) you
   *                 can use this viewType integer to provide a different layout. See
   *                 {@link RecyclerView.Adapter#getItemViewType(int)}
   *                 for more details.
   * @return A new EntryAdapterViewHolder that holds the View for each list item
   */
  @Override
  public CycleAdapterViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    int layoutIdForListItem = R.layout.list_item_cycle_select;
    LayoutInflater inflater = LayoutInflater.from(mContext);
    boolean shouldAttachToParentImmediately = false;

    View view = inflater.inflate(layoutIdForListItem, parent, shouldAttachToParentImmediately);
    return new CycleAdapterViewHolder(view);
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
  public void onBindViewHolder(CycleAdapterViewHolder holder, int position) {
    CycleWithEntries cycleWithEntries = mData.get(position);
    holder.mCycleDataTextView.setText("Starting: " + DateUtil.toPrintUiStr(cycleWithEntries.cycle.startDate));
    holder.mSelectBox.setChecked(mSelectedIndexes.contains(position));
    if (mBreakAfterIndexs.contains(position + 1)) {
      holder.showPageSeparator();
    } else {
      holder.hidePageSeparator();
    }
  }

  private void updateCheckStates(int position, boolean val) {
    if (val) {
      mSelectedIndexes.add(position);
    } else {
      mSelectedIndexes.remove(position);
    }
    updatePageBreaks();
  }

  private void updatePageBreaks() {
    int firstIndex = 0;
    for (int i=0; i < mData.size(); i++) {
      if (mSelectedIndexes.contains(i) && firstIndex < 0) {
        firstIndex = i;
        break;
      }
    }
    ImmutableSet.Builder<Integer> breakBuilder = ImmutableSet.builder();
    int rowsLeftBeforeNextBreak = PageRenderer.numRowsPerPage();
    for (int i=firstIndex; i < mData.size(); i++) {
      rowsLeftBeforeNextBreak -= PageRenderer.numRows(mData.get(i).entries.size());
      if (rowsLeftBeforeNextBreak < 0) {
        breakBuilder.add(i);
        rowsLeftBeforeNextBreak = PageRenderer.numRowsPerPage();
      }
    }
    mBreakAfterIndexs = breakBuilder.build();
    notifyDataSetChanged();
  }

  @Override
  public int getItemCount() {
    return mData.size();
  }

  public class CycleAdapterViewHolder extends RecyclerView.ViewHolder {
    public final TextView mCycleDataTextView;
    public final CheckBox mSelectBox;
    public final View mCycleSeparator;
    public final View mPageSeparator;

    public CycleAdapterViewHolder(View itemView) {
      super(itemView);
      mCycleDataTextView = itemView.findViewById(R.id.tv_cycle_data);
      mSelectBox = itemView.findViewById(R.id.checkbox_select);
      mSelectBox.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          updateCheckStates(getAdapterPosition(), mSelectBox.isChecked());
        }
      });
      mCycleSeparator = itemView.findViewById(R.id.cycle_separator);
      mPageSeparator = itemView.findViewById(R.id.page_separator);
      itemView.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          // Toggle checkbox
          mSelectBox.setChecked(!mSelectBox.isChecked());
          updateCheckStates(getAdapterPosition(), mSelectBox.isChecked());
        }
      });
    }

    public void showPageSeparator() {
      mPageSeparator.setVisibility(View.VISIBLE);
      mCycleSeparator.setVisibility(View.GONE);
    }

    public void hidePageSeparator() {
      mPageSeparator.setVisibility(View.GONE);
      mCycleSeparator.setVisibility(View.VISIBLE);
    }
  }

  public static class ViewModel implements Parcelable {
    public final Cycle mCycle;
    public final int mNumEntries;

    public ViewModel(Cycle mCycle, int mNumEntries) {
      this.mCycle = mCycle;
      this.mNumEntries = mNumEntries;
    }

    protected ViewModel(Parcel in) {
      mCycle = in.readParcelable(Cycle.class.getClassLoader());
      mNumEntries = in.readInt();
    }

    public static final Creator<ViewModel> CREATOR = new Creator<ViewModel>() {
      @Override
      public ViewModel createFromParcel(Parcel in) {
        return new ViewModel(in);
      }

      @Override
      public ViewModel[] newArray(int size) {
        return new ViewModel[size];
      }
    };

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeParcelable(Parcels.wrap(mCycle), flags);
      dest.writeInt(mNumEntries);
    }
  }
}
