package com.roamingroths.cmcc.data.backup;

import com.roamingroths.cmcc.application.MyApplication;
import com.roamingroths.cmcc.data.models.AppState;
import com.roamingroths.cmcc.data.repos.ChartEntryRepo;
import com.roamingroths.cmcc.data.repos.CycleRepo;
import com.roamingroths.cmcc.data.repos.InstructionsRepo;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;

public class AppStateExporter {

  private final CycleRepo mCycleRepo;
  private final ChartEntryRepo mEntryRepo;
  private final InstructionsRepo mInstructionsRepo;

  public static AppStateExporter forApp(MyApplication myApp) {
    return new AppStateExporter(myApp);
  }

  public AppStateExporter(MyApplication myApp) {
    mCycleRepo = new CycleRepo(myApp.db());
    mEntryRepo = new ChartEntryRepo(myApp.db());
    mInstructionsRepo = myApp.instructionsRepo();
  }

  public Single<AppState> export() {
    return Single.zip(
        mCycleRepo.getStream()
            .firstOrError()
            .flatMapObservable(Observable::fromIterable)
            .flatMapSingle(cycle -> mEntryRepo
                .getStream(Flowable.just(cycle))
                .firstOrError()
                .map(entries -> new AppState.CycleData(cycle, entries))
            )
            .toList(),
        mInstructionsRepo.getAll()
            .firstOrError(),
        (cycleDatas, instructions) -> new AppState(cycleDatas, null, instructions));
  }}
