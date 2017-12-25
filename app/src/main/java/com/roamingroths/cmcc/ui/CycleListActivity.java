package com.roamingroths.cmcc.ui;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.roamingroths.cmcc.Preferences;
import com.roamingroths.cmcc.R;
import com.roamingroths.cmcc.data.ChartEntryList;
import com.roamingroths.cmcc.data.CycleAdapter;
import com.roamingroths.cmcc.logic.Cycle;
import com.roamingroths.cmcc.print.ChartPrinter;

import java.util.Comparator;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;

public class CycleListActivity extends BaseActivity {

  private static final boolean DEBUG = true;
  private static final String TAG = CycleListActivity.class.getSimpleName();

  private RecyclerView mRecyclerView;
  private CycleAdapter mCycleAdapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_cycle_list);
    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    //setSupportActionBar(toolbar);

    setTitle("Select cycles to print");

    mRecyclerView = (RecyclerView) findViewById(R.id.recyclerview_cycle_entry);
    boolean shouldReverseLayout = false;
    LinearLayoutManager layoutManager
        = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, shouldReverseLayout);
    mRecyclerView.setLayoutManager(layoutManager);
    mRecyclerView.setHasFixedSize(false);

    CycleAdapter adapter = CycleAdapter.fromBundle(this, savedInstanceState);
    if (adapter != null) {
      mRecyclerView.setAdapter(adapter);
    } else {
      getProvider().forCycle()
          .getAllCycles(getUser().getUid())
          .sorted(new Comparator<Cycle>() {
            @Override
            public int compare(Cycle o1, Cycle o2) {
              return o2.startDate.compareTo(o1.startDate);
            }
          })
          .flatMap(CycleAdapter.cycleToViewModel(getProvider().forChartEntry()))
          .toList()
          .subscribe(new Consumer<List<CycleAdapter.ViewModel>>() {
            @Override
            public void accept(List<CycleAdapter.ViewModel> viewModels) throws Exception {
              mRecyclerView.setAdapter(
                  CycleAdapter.fromViewModels(CycleListActivity.this, viewModels));
            }
          });
    }

    FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
    final Toast invalidSelectionToast =
        Toast.makeText(this, "Continuous selection required", Toast.LENGTH_LONG);
    fab.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (!getAdapter().hasValidSelection()) {
          invalidSelectionToast.show();
          return;
        }
        Observable<ChartEntryList> entryLists = getProvider().forCycleEntry().getChartEntryLists(
            getAdapter().getSelectedCycles(), Preferences.fromShared(CycleListActivity.this));
        ChartPrinter.create(CycleListActivity.this, entryLists).print().toCompletable().subscribe(new Action() {
          @Override
          public void run() throws Exception {
            //finish();
          }
        });
      }
    });
  }

  private CycleAdapter getAdapter() {
    return (CycleAdapter) mRecyclerView.getAdapter();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    getAdapter().fillBundle(outState);
  }

  @Override
  protected void onResume() {
    super.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_cycle_list, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();

    return super.onOptionsItemSelected(item);
  }
}
