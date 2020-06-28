package com.bloomcyclecare.cmcc.ui.cloud;

import android.app.Application;
import android.content.Context;

import com.bloomcyclecare.cmcc.backup.drive.DriveServiceHelper;
import com.bloomcyclecare.cmcc.backup.drive.WorkerManager;
import com.bloomcyclecare.cmcc.utils.GoogleAuthHelper;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.api.services.drive.model.File;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;
import org.joda.time.ReadableInstant;
import org.joda.time.Instant;

import java.util.List;
import java.util.Optional;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveDataReactiveStreams;
import androidx.lifecycle.LiveData;
import androidx.work.OneTimeWorkRequest;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;
import timber.log.Timber;

public class CloudPublishViewModel extends AndroidViewModel {

  private final CompositeDisposable mDisposables = new CompositeDisposable();
  private final Subject<Optional<GoogleSignInAccount>> mAccountSubject = BehaviorSubject.create();
  private final Subject<Boolean> mPublishEnabledSubject = BehaviorSubject.create();
  private final Subject<ViewState> mViewStateSubject = BehaviorSubject.create();
  private final Subject<Optional<WorkerManager.ItemStats>> mStatsSubject = BehaviorSubject.createDefault(Optional.empty());

  private final Context mContext;
  private final GoogleSignInClient mSigninClient;
  private final WorkerManager mWorkerManager;

  public CloudPublishViewModel(@NonNull Application application) {
    super(application);
    mContext = application.getApplicationContext();
    mWorkerManager = WorkerManager.fromApp(application);
    mSigninClient = GoogleAuthHelper.getClient(mContext);

    Observable.merge(Observable.combineLatest(
        mAccountSubject.distinctUntilChanged(),
        mPublishEnabledSubject.distinctUntilChanged(),
        mStatsSubject.distinctUntilChanged(),
        (account, publishEnabled, stats) -> {
          if (account.isPresent() && publishEnabled) {
            maybeConnectWorkSteam();
            DriveServiceHelper driveServiceHelper =
                DriveServiceHelper.forAccount(account.get(), application);
            Optional<Integer> itemsOutstanding = stats.map(itemStats -> itemStats.numEncueuedRequests() - itemStats.numCompletedRequests());
            Optional<ReadableInstant> lastEncueueTime = stats.map(itemStats -> new Instant(itemStats.lastEncueueTime()));
            Optional<ReadableInstant> lastCompletedTime = stats.map(itemStats -> new Instant(stats.get().lastCompletedTime()));
            return driveServiceHelper
                .getOrCreateFolder(DriveServiceHelper.FOLDER_NAME_MY_CHARTS)
                .map(myChartsFolder -> ViewState.create(
                    account, Optional.of(getDriveLink(myChartsFolder)), true,
                    itemsOutstanding, lastEncueueTime, lastCompletedTime))
                .toObservable();
          }
          if (!disconnectWorkStream()) {
            Timber.v("No work stream to disconnect");
          } else {
            Timber.v("Work stream disconnected");
          }
          return Single.just(ViewState.create(account, Optional.empty(), false, Optional.empty(), Optional.empty(), Optional.empty())).toObservable();
        })).subscribe(mViewStateSubject);

    checkAccount();
  }

  private void maybeConnectWorkSteam() {
    Optional<Observable<WorkerManager.ItemStats>> statsStream =
        mWorkerManager.getUpdateStream(WorkerManager.Item.PUBLISH);
    if (statsStream.isPresent()) {
      Timber.v("Work stream already active");
      return;
    }
    Timber.d("Connecting work stream");
    statsStream = mWorkerManager.register(WorkerManager.Item.PUBLISH, Observable.create(emitter -> {
    }));
    if (statsStream.isPresent()) {
      Timber.d("Work stream connected");
      statsStream.get().map(Optional::of).subscribe(mStatsSubject);
      return;
    }
    Timber.w("Failed to connect publish stream!");
  }

  private boolean disconnectWorkStream() {
    return mWorkerManager.cancel(WorkerManager.Item.PUBLISH);
  }

  public LiveData<ViewState> viewState() {
    return LiveDataReactiveStreams.fromPublisher(mViewStateSubject.toFlowable(BackpressureStrategy.BUFFER));
  }

  public Observer<Boolean> publishEnabledObserver() {
    return mPublishEnabledSubject;
  }

  public void signOut() {
    mSigninClient.signOut().addOnSuccessListener(aVoid -> checkAccount());
  }

  public void checkAccount() {
    Timber.d("Checking for account");
    GoogleAuthHelper.googleAccount(mContext)
        .map(Optional::of)
        .switchIfEmpty(Single.just(Optional.empty()))
        .subscribe(account -> mAccountSubject.onNext(account));
  }

  @AutoValue
  public static abstract class ViewState {

    public abstract Optional<GoogleSignInAccount> account();
    public abstract Optional<String> myChartsLink();
    public abstract Boolean publishEnabled();
    public abstract Optional<Integer> itemsOutstanding();
    public abstract Optional<ReadableInstant> lastEncueueTimeMs();
    public abstract Optional<ReadableInstant> lastSuccessTimeMs();

    public static ViewState create(Optional<GoogleSignInAccount> account, Optional<String> myChartsLink, Boolean publishEnabled, Optional<Integer> itemsOutstanding, Optional<ReadableInstant> lastEncueueTimeMs, Optional<ReadableInstant> lastSuccessTimeMs) {
      return new AutoValue_CloudPublishViewModel_ViewState(account, myChartsLink, publishEnabled, itemsOutstanding, lastEncueueTimeMs, lastSuccessTimeMs);
    }
  }

  private static String getDriveLink(@NonNull File file) {
    return String.format("https://drive.google.com/drive/folders/%s", file.getId());
  }
}