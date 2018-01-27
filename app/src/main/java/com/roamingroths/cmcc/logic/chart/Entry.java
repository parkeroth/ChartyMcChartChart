package com.roamingroths.cmcc.logic.chart;

import android.os.Parcelable;

import com.roamingroths.cmcc.crypto.Cipherable;
import com.roamingroths.cmcc.utils.DateUtil;

import org.joda.time.LocalDate;

import java.util.List;

import javax.crypto.SecretKey;

/**
 * Created by parkeroth on 9/20/17.
 */

public abstract class Entry implements Cipherable, Parcelable {

  private final LocalDate mEntryDate;
  private transient volatile SecretKey mKey;

  Entry(LocalDate entryDate) {
    mEntryDate = entryDate;
  }

  public LocalDate getDate() {
    return mEntryDate;
  }

  public String getDateStr() {
    return DateUtil.toWireStr(mEntryDate);
  }

  @Override
  public final SecretKey getKey() {
    return mKey;
  }

  @Override
  public boolean hasKey() {
    return mKey != null;
  }

  @Override
  public final void swapKey(SecretKey key) {
    mKey = key;
  }

  public abstract List<String> getSummaryLines();
}