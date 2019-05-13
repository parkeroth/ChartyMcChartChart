package com.roamingroths.cmcc.ui.entry.detail;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.common.collect.ImmutableSet;
import com.roamingroths.cmcc.data.entities.Cycle;
import com.roamingroths.cmcc.data.entities.Entry;
import com.roamingroths.cmcc.utils.DateUtil;

import org.joda.time.LocalDate;
import org.parceler.Parcels;

import java.util.Set;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;

/**
 * Created by parkeroth on 9/17/17.
 */

public abstract class EntryFragment<E extends Entry> extends Fragment {

  public enum Extras {
    CURRENT_CYCLE, EXISTING_ENTRY
  }

  public interface EntryListener {
    void onEntryUpdated(Entry entry, Class<? extends Entry> clazz);
  }

  private final Class<E> mClazz;
  private final String mTag;
  private final int layoutId;
  private EntryListener mUpdateListener;
  private EntryContext mEntryContext;

  protected boolean mUiActive = false;

  EntryFragment(Class<E> clazz, String tag, int layoutId) {
    this.mClazz = clazz;
    this.mTag = tag;
    this.layoutId = layoutId;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    Log.v(mTag, "onAttach (" + mClazz.getSimpleName() + "): Start");

    try {
      mUpdateListener = (EntryListener) context;
    } catch (ClassCastException cce) {
      throw new ClassCastException(context.toString() + " must implement EntryUpdateListener");
    }
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.v(mTag, "onCreate (" + mClazz.getSimpleName() + "): Start");

    mEntryContext = Parcels.unwrap(getArguments().getParcelable(EntryContext.class.getCanonicalName()));

    Log.v(mTag, "onCreate (" + mClazz.getSimpleName() + "): Finish");
  }

  @Override
  public final View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    Log.v(mTag, "onCreateView: (" + mClazz.getSimpleName() + "): Start");

    View view = inflater.inflate(layoutId, container, false);

    Log.v(mTag, "duringCreateView: (" + mClazz.getSimpleName() + "): Start");
    duringCreateView(view, getArguments(), savedInstanceState);
    Log.v(mTag, "duringCreateView: (" + mClazz.getSimpleName() + "): Finish");

    updateUiWithEntry(processExistingEntry(getExistingEntry()));

    mUiActive = true;

    Log.v(mTag, "onCreateView: (" + mClazz.getSimpleName() + "): Finish");
    return view;
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    try {
      mUpdateListener.onEntryUpdated(getEntryFromUi(), mClazz);
    } catch (Exception e) {
      // TODO: something better
      Log.w(mTag, "Could not notify EntryUpdateListener: " + e.getMessage());
    }
  }

  public Class<? extends E> getClazz() {
    return mClazz;
  }

  public abstract E getEntryFromUi() throws Exception;

  public Single<E> getEntryFromUiRx() {
    return Single.create(new SingleOnSubscribe<E>() {
      @Override
      public void subscribe(SingleEmitter<E> e) throws Exception {
        e.onSuccess(getEntryFromUi());
      }
    });
  }

  /**
   * Process an existing entry found in DB before storing it as a member.
   *
   * @param existingEntry
   * @return processed entry
   */
  E processExistingEntry(E existingEntry) {
    return existingEntry;
  }

  abstract void duringCreateView(View view, Bundle args, Bundle savedInstanceState);

  abstract void updateUiWithEntry(E entry);

  public Set<ValidationIssue> validateEntry(E entry) {
    return ImmutableSet.of();
  }

  LocalDate getEntryDate() {
    return mEntryContext.chartEntry.entryDate;
  }

  String getEntryDateStr() {
    return DateUtil.toWireStr(getEntryDate());
  }

  Cycle getCycle() {
    return mEntryContext.currentCycle;
  }

  EntryContext entryContext() {
    return mEntryContext;
  }

  E getExistingEntry() {
    return getExistingEntry(mEntryContext);
  }

  abstract E getExistingEntry(EntryContext entryContext);

  public static class ValidationIssue {
    public String title;
    public String message;

    public ValidationIssue(String title, String message) {
      this.title = title;
      this.message = message;
    }
  }
}
