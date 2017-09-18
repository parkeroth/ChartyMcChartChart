package com.roamingroths.cmcc.data;

import android.support.annotation.Nullable;
import android.util.Log;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.roamingroths.cmcc.crypto.CryptoUtil;
import com.roamingroths.cmcc.logic.ChartEntry;
import com.roamingroths.cmcc.logic.Cycle;
import com.roamingroths.cmcc.utils.Callbacks;
import com.roamingroths.cmcc.utils.Callbacks.Callback;
import com.roamingroths.cmcc.utils.DateUtil;
import com.roamingroths.cmcc.utils.FirebaseUtil;
import com.roamingroths.cmcc.utils.Listeners;

import org.joda.time.LocalDate;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by parkeroth on 9/2/17.
 */
public class CycleProvider {

  // TODO: Remove FirebaseAuth stuff

  private final FirebaseDatabase db;
  private final CycleKeyProvider cycleKeyProvider;
  private final ChartEntryProvider chartEntryProvider;

  public static CycleProvider forDb(FirebaseDatabase db) {
    return new CycleProvider(db, CycleKeyProvider.forDb(db), ChartEntryProvider.forDb(db));
  }

  private CycleProvider(
      FirebaseDatabase db, CycleKeyProvider cycleKeyProvider, ChartEntryProvider chartEntryProvider) {
    this.db = db;
    this.cycleKeyProvider = cycleKeyProvider;
    this.chartEntryProvider = chartEntryProvider;
  }

  public CycleKeyProvider getCycleKeyProvider() {
    return cycleKeyProvider;
  }

  public ChartEntryProvider getChartEntryProvider() {
    return chartEntryProvider;
  }

  public void attachListener(ChildEventListener listener, String userId) {
    DatabaseReference ref = reference(userId);
    ref.addChildEventListener(listener);
    ref.keepSynced(true);
  }

  public void detachListener(ChildEventListener listener, String userId) {
    reference(userId).removeEventListener(listener);
  }

  public void putCycle(final String userId, final Cycle cycle, final Callback<Cycle> callback) {
    // Store key
    Map<String, Object> updates = new HashMap<>();
    updates.put("previous-cycle-id", cycle.previousCycleId);
    updates.put("next-cycle-id", cycle.nextCycleId);
    updates.put("start-date", cycle.startDateStr);
    updates.put("end-date", DateUtil.toWireStr(cycle.endDate));
    reference(userId, cycle.id).updateChildren(updates, Listeners.completionListener(callback, new Runnable() {
      @Override
      public void run() {
        cycleKeyProvider.forCycle(cycle.id).putChartKeys(cycle.keys, userId, new Callbacks.ErrorForwardingCallback<Void>(callback) {
          @Override
          public void acceptData(Void data) {
            logV("Storing keys for cycle: " + cycle.id);
            callback.acceptData(cycle);
          }
        });
      }
    }));
  }

  public void createCycle(
      final String userId,
      @Nullable Cycle previousCycle,
      @Nullable Cycle nextCycle,
      final LocalDate startDate,
      final @Nullable LocalDate endDate,
      final Callback<Cycle> callback) {
    DatabaseReference cycleRef = reference(userId).push();
    logV("Creating new cycle: " + cycleRef.getKey());
    final String cycleId = cycleRef.getKey();
    Cycle.Keys keys = new Cycle.Keys(
        CryptoUtil.createSecretKey(), CryptoUtil.createSecretKey(), CryptoUtil.createSecretKey());
    final Cycle cycle = new Cycle(
        cycleId,
        (previousCycle == null) ? null : previousCycle.id,
        (nextCycle == null) ? null : nextCycle.id,
        startDate,
        endDate,
        keys);
    putCycle(userId, cycle, callback);
  }

  private void getCycles(
      final String userId, final Predicate<DataSnapshot> fetchPredicate, boolean criticalRead, final Callback<Collection<Cycle>> callback) {
    logV("Fetching cycles");
    ValueEventListener listener = new Listeners.SimpleValueEventListener(callback) {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        logV("Received " + dataSnapshot.getChildrenCount() + " cycles");
        if (dataSnapshot.getChildrenCount() == 0) {
          callback.acceptData(ImmutableSet.<Cycle>of());
        }
        final Set<DataSnapshot> snapshots = new HashSet<>();
        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
          if (fetchPredicate.apply(snapshot)) {
            snapshots.add(snapshot);
          }
        }
        final Map<String, Cycle> cycles = Maps.newConcurrentMap();
        for (final DataSnapshot snapshot : snapshots) {
          final String cycleId = snapshot.getKey();
          cycleKeyProvider.forCycle(cycleId).getChartKeys(userId, new Callbacks.ErrorForwardingCallback<Cycle.Keys>(callback) {
            @Override
            public void acceptData(Cycle.Keys keys) {
              Cycle cycle = Cycle.fromSnapshot(snapshot, keys);
              cycles.put(cycle.id, cycle);
              if (cycles.size() == snapshots.size()) {
                callback.acceptData(cycles.values());
              }
            }

            @Override
            public void handleNotFound() {
              Log.w("CycleProvider", "Could not find key for user " + userId + " cycle " + cycleId);
              callback.handleNotFound();
            }
          });
        }
      }
    };
    DatabaseReference ref = reference(userId);
    if (criticalRead) {
      FirebaseUtil.criticalRead(ref, callback, listener);
    } else {
      ref.addListenerForSingleValueEvent(listener);
    }
  }

  public void getAllCycles(String userId, Callback<Collection<Cycle>> callback) {
    getCycles(userId, Predicates.<DataSnapshot>alwaysTrue(), false, callback);
  }

  public void getCurrentCycle(final String userId, final Callback<Cycle> callback) {
    logV("Fetching user's cycles");
    Predicate<DataSnapshot> fetchPredicate = new Predicate<DataSnapshot>() {
      @Override
      public boolean apply(DataSnapshot snapshot) {
        return !snapshot.hasChild("end-date");
      }
    };
    getCycles(userId, fetchPredicate, true, new Callbacks.ErrorForwardingCallback<Collection<Cycle>>(callback) {
      @Override
      public void acceptData(Collection<Cycle> data) {
        if (data.size() == 0) {
          callback.handleNotFound();
          return;
        }
        if (data.size() == 1) {
          logV("Could not find current cycle");
          callback.acceptData(data.iterator().next());
          return;
        }
        callback.handleError(DatabaseError.fromException(new IllegalStateException()));
      }
    });
  }

  private void dropCycle(final String cycleId, final String userId, final Runnable onFinish) {
    logV("Dropping entries for cycle: " + cycleId);
    db.getReference("entries").child(cycleId).removeValue(new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
        logV("Dropping keys for cycle: " + cycleId);
        db.getReference("keys").child(cycleId).removeValue(new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
            logV("Dropping cycle: " + cycleId);
            reference(userId, cycleId).removeValue();
            onFinish.run();
          }
        });
      }
    });
  }

  public void dropCycles(final Callback<Void> doneCallback) {
    logV("Dropping cycles");
    final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    ValueEventListener listener = new ValueEventListener() {
      @Override
      public void onCancelled(DatabaseError databaseError) {
        databaseError.toException().printStackTrace();
      }
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        final AtomicLong cyclesToDrop = new AtomicLong(dataSnapshot.getChildrenCount());
        for (DataSnapshot cycleSnapshot : dataSnapshot.getChildren()) {
          Runnable onRemove = new Runnable() {
            @Override
            public void run() {
              if (cyclesToDrop.decrementAndGet() == 0) {
                doneCallback.acceptData(null);
              }
            }
          };
          dropCycle(cycleSnapshot.getKey(), user.getUid(), onRemove);
        }
      }
    };
    reference(user.getUid()).addListenerForSingleValueEvent(listener);
  }

  public void getCycle(
      final String userId, final @Nullable String cycleId, final Callback<Cycle> callback) {
    if (Strings.isNullOrEmpty(cycleId)) {
      callback.acceptData(null);
      return;
    }
    cycleKeyProvider.forCycle(cycleId).getChartKeys(userId, new Callbacks.ErrorForwardingCallback<Cycle.Keys>(callback) {
      @Override
      public void acceptData(final Cycle.Keys keys) {
        reference(userId, cycleId).addListenerForSingleValueEvent(new Listeners.SimpleValueEventListener(callback) {
          @Override
          public void onDataChange(DataSnapshot dataSnapshot) {
            callback.acceptData(Cycle.fromSnapshot(dataSnapshot, keys));
          }
        });
      }
    });
  }

  public void combineCycles(
      final Cycle currentCycle,
      final String userId,
      final Callback<Cycle> mergedCycleCallback) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(currentCycle.previousCycleId));
    getCycle(
        userId,
        currentCycle.previousCycleId,
        Callbacks.singleUse(new Callbacks.ErrorForwardingCallback<Cycle>(mergedCycleCallback) {
          @Override
          public void acceptData(final Cycle previousCycle) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("next-cycle-id", currentCycle.nextCycleId);
            updates.put("end-date", DateUtil.toWireStr(currentCycle.endDate));
            reference(userId, previousCycle.id).updateChildren(updates, Listeners.completionListener(this));
            if (currentCycle.nextCycleId != null) {
              reference(userId, currentCycle.nextCycleId).child("previous-cycle-id").setValue(
                  previousCycle.id, Listeners.completionListener(this));
            }
            moveEntries(
                currentCycle,
                previousCycle,
                userId,
                Predicates.<LocalDate>alwaysTrue(),
                new Callbacks.ErrorForwardingCallback<Void>(mergedCycleCallback) {
                  @Override
                  public void acceptData(Void unused) {
                    mergedCycleCallback.acceptData(previousCycle);
                  }
                });
          }
        }));
  }

  public void splitCycle(
      final String userId,
      final Cycle currentCycle,
      final ChartEntry firstEntry,
      final Callback<Cycle> resultCallback) {
    logV("Splitting cycle: " + currentCycle.id);
    logV("Next cycle: " + currentCycle.nextCycleId);
    getCycle(
        userId,
        currentCycle.nextCycleId,
        Callbacks.singleUse(new Callbacks.ErrorForwardingCallback<Cycle>(resultCallback) {
          @Override
          public void acceptData(@Nullable final Cycle nextCycle) {
            createCycle(
                userId,
                currentCycle,
                nextCycle,
                firstEntry.date,
                currentCycle.endDate,
                Callbacks.singleUse(new Callbacks.ErrorForwardingCallback<Cycle>(this) {
                  @Override
                  public void acceptData(final Cycle newCycle) {
                    if (nextCycle != null) {
                      reference(userId, nextCycle.id).child("previous-cycle-id")
                          .setValue(newCycle.id, Listeners.completionListener(this));
                    }
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("next-cycle-id", newCycle.id);
                    updates.put("end-date", DateUtil.toWireStr(firstEntry.date.minusDays(1)));
                    reference(userId, currentCycle.id).updateChildren(
                        updates, Listeners.completionListener(this));
                    moveEntries(currentCycle, newCycle, userId, new Predicate<LocalDate>() {
                      @Override
                      public boolean apply(LocalDate entryDate) {
                        return entryDate.equals(firstEntry.date) || entryDate.isAfter(firstEntry.date);
                      }
                    }, Callbacks.singleUse(new Callbacks.ErrorForwardingCallback<Void>(this) {
                      @Override
                      public void acceptData(Void data) {
                        resultCallback.acceptData(newCycle);
                      }
                    }));
                  }
                }));
          }
        }));
  }

  private void moveEntries(
      final Cycle fromCycle,
      final Cycle toCycle,
      final String userId,
      final Predicate<LocalDate> datePredicate,
      final Callback<?> callback) {
    logV("Source cycle id: " + fromCycle.id);
    logV("Destination cycle id: " + toCycle.id);
    chartEntryProvider.getDecryptedEntries(fromCycle, new Callbacks.ErrorForwardingCallback<Map<LocalDate, ChartEntry>>(callback) {
      @Override
      public void acceptData(Map<LocalDate, ChartEntry> decryptedEntries) {
        Map<LocalDate, ChartEntry> entriesToMove = Maps.filterKeys(decryptedEntries, datePredicate);
        final AtomicLong outstandingRemovals = new AtomicLong(entriesToMove.size());
        final boolean shouldDropCycle = entriesToMove.size() == decryptedEntries.size();

        logV("Moving " + outstandingRemovals.get() + " entries");
        for (Map.Entry<LocalDate, ChartEntry> mapEntry : entriesToMove.entrySet()) {
          final LocalDate entryDate = mapEntry.getKey();
          final String dateStr = DateUtil.toWireStr(entryDate);
          ChartEntry decryptedEntry = mapEntry.getValue();
          decryptedEntry.swapKey(toCycle.keys.chartKey);

          final DatabaseReference.CompletionListener rmListener =
              Listeners.completionListener(callback, new Runnable() {
                @Override
                public void run() {
                  logV("Removed entry: " + dateStr);
                  if (outstandingRemovals.decrementAndGet() == 0) {
                    logV("Entry moves complete");
                    if (shouldDropCycle) {
                      logV("Dropping cycle: " + fromCycle.id);
                      // TODO: fix race
                      cycleKeyProvider.forCycle(fromCycle.id).dropKeys(callback);
                      reference(userId, fromCycle.id).removeValue(
                          Listeners.completionListener(callback));
                    }
                    callback.acceptData(null);
                  }
                }
              });
          final DatabaseReference.CompletionListener moveCompleteListener =
              Listeners.completionListener(callback, new Runnable() {
                @Override
                public void run() {
                  logV("Added entry: " + dateStr);
                  chartEntryProvider.deleteChartEntry(fromCycle.id, entryDate, rmListener);
                }
              });

          try {
            chartEntryProvider.putEntry(toCycle.id, decryptedEntry, moveCompleteListener);
          } catch (CryptoUtil.CryptoException ce) {
            handleError(DatabaseError.fromException(ce));
          }
        }
      }
    });
  }

  private DatabaseReference reference(String userId, String cycleId) {
    return reference(userId).child(cycleId);
  }

  private DatabaseReference reference(String userId) {
    return db.getReference("cycles").child(userId);
  }

  private void logV(String message) {
    Log.v("CycleProvider", message);
  }
}