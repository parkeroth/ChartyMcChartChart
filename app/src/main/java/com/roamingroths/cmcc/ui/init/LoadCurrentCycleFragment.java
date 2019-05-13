package com.roamingroths.cmcc.ui.init;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.auth.FirebaseUser;
import com.roamingroths.cmcc.application.MyApplication;
import com.roamingroths.cmcc.data.entities.Cycle;
import com.roamingroths.cmcc.data.repos.CycleRepo;
import com.roamingroths.cmcc.ui.entry.list.ChartEntryListActivity;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;

import org.joda.time.LocalDate;

import java.util.Calendar;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Created by parkeroth on 10/8/17.
 */

public class LoadCurrentCycleFragment extends SplashFragment implements UserInitializationListener {

  private static boolean DEBUG = false;
  private static String TAG = LoadCurrentCycleFragment.class.getSimpleName();

  private CompositeDisposable mDisposables = new CompositeDisposable();
  private CycleRepo mCycleRepo;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mCycleRepo = new CycleRepo(MyApplication.cast(getActivity().getApplication()).db());
  }

  @Override
  public void onDestroy() {
    mDisposables.clear();
    super.onDestroy();
  }

  @Override
  public void onUserInitialized(final FirebaseUser user) {
    updateStatus("Loading your data");
    if (DEBUG) Log.v(TAG, "Getting current cycleToShow");
    mDisposables.add(mCycleRepo.getCurrentCycle()
        .switchIfEmpty(promptForStart()
            .flatMap(startDate -> {
              Cycle cycle = new Cycle("foo", LocalDate.now(), null);
              return mCycleRepo.insertOrUpdate(cycle).andThen(Single.just(cycle));
            }))
        .subscribeOn(Schedulers.computation())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(cycle -> {
          Intent intent = new Intent(getContext(), ChartEntryListActivity.class);
          startActivity(intent);
        }, throwable -> {
          Timber.e(throwable);
          showError("Could not find or create a cycle!");
        }));
  }

  private Single<LocalDate> promptForStart() {
    return Single.create(e -> {
      updateStatus("Prompting for start of first cycleToShow.");
      updateStatus("Creating first cycleToShow");
      DatePickerDialog datePickerDialog = DatePickerDialog.newInstance(
          (view, year, monthOfYear, dayOfMonth) -> e.onSuccess(new LocalDate(year, monthOfYear + 1, dayOfMonth)));
      datePickerDialog.setTitle("First day of current cycleToShow");
      datePickerDialog.setMaxDate(Calendar.getInstance());
      datePickerDialog.show(getActivity().getFragmentManager(), "datepickerdialog");
    });
  }
}
