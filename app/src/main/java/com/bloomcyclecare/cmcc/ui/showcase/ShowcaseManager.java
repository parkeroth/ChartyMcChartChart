package com.bloomcyclecare.cmcc.ui.showcase;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.Pair;
import android.view.View;

import com.google.common.collect.Queues;

import java.util.Deque;

import androidx.annotation.Nullable;
import timber.log.Timber;
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence;
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView;
import uk.co.deanwild.materialshowcaseview.ShowcaseConfig;

public class ShowcaseManager {

  private static final int DELAY_MS = 200;

  public enum SequenceID {
    INCORRECT_STICKER_SELECTION,
    ENTRY_DETAIL_PAGE
  }

  public enum ShowcaseID {
    FIRST_INCORRECT_STICKER(
        "Incorrect sticker selections are noted by a strike. Touch the sticker to reopen the selection dialog for more information."),
    INCORRECT_STICKER_REASON(
        "This section explains why the previous selection was incorrect."),
    INCORRECT_STICKER_HINT(
        "Touch here for a hint to determine the correct sticker for this observation."),
    ENTRY_DETAIL_INPUT_OBSERVATION(
        "Touch here to input your observation using the vaginal discharge recording system (VDRS).", true),
    ENTRY_DETAIL_EXPLAIN_DESCRIPTION(
        "Here is the summary of your description \n" +
            "Use to verify your description input.", true),
    ENTRY_DETAIL_EXPLAIN_PEAK_DAY(
        "Use this switch to indicate if the day was a peak day. \n" +
            "You can return to this entry on previous days to accurately mark a peak day."),
    ENTRY_DETAIL_EXPLAIN_INTERCOURSE(
        "Use this switch to record an act of intercourse "),
    ENTRY_DETAIL_EXPLAIN_FIRST_DAY_NEW_CYCLE(
        "Use this switch to indicate the beginning of a new cycle. \n" +
        "This will end the current cycle and create the next."),
    ENTRY_DETAIL_EXPLAIN_NOTE(
        "Use this space to record any special comments for the day that you want to remember. This is a good way to record things you want to discuss with your practitioner at your next follow up appointment.", true),
    ENTRY_DETAIL_EXPLAIN_EXTRA_TOGGLES("Please note that this screen will adapt and change as your instructions change."),
    ENTRY_DETAIL_EXPLAIN_MENU("Look here for additional observational entry options"),
    ENTRY_DETAIL_EXPLAIN_SAVE("Touch here to save your description. You can always return to make any changes.")
      ;

    final String content;
    final boolean useRectangle;

    ShowcaseID(String content) {
      this(content, false);
    }

    ShowcaseID(String content, boolean useRectangle) {
      this.content = content;
      this.useRectangle = useRectangle;
    }
  }

  public void showShowcase(ShowcaseID showcaseID, View target) {
    Activity activity = getActivity(target);
    if (activity == null) {
      Timber.w("Failed to find activity for view: %d", target.getId());
      return;
    }
    new MaterialShowcaseView.Builder(activity)
        .setTarget(target)
        .setDismissText("GOT IT")
        .setContentText(showcaseID.content)
        .setDelay(DELAY_MS) // optional but starting animations immediately in onCreate can make them choppy
        .singleUse(showcaseID.name())
        .show();
  }

  public static SequenceBuilder sequenceBuilder(SequenceID id) {
    return new SequenceBuilder(id);
  }

  public static class SequenceBuilder {

    private final SequenceID mSequenceID;
    private final Deque<Pair<ShowcaseID, View>> mQueue = Queues.newArrayDeque();

    private SequenceBuilder(SequenceID id) {
      mSequenceID = id;
    }

    public SequenceBuilder addShowcase(ShowcaseID showcaseID, View view) {
      mQueue.push(Pair.create(showcaseID, view));
      return this;
    }

    public void build() {
      if (mQueue.isEmpty()) {
        Timber.d("No showcases in sequence");
        return;
      }

      View firstView = mQueue.peek().second;
      Activity activity = getActivity(firstView);
      if (activity == null) {
        Timber.w("Failed to find activity for view: %d", firstView.getId());
      }

      MaterialShowcaseSequence sequence = new MaterialShowcaseSequence(activity, mSequenceID.name());
      while (!mQueue.isEmpty()) {
        Pair<ShowcaseID, View> p = mQueue.pollLast();
        MaterialShowcaseView.Builder builder = new MaterialShowcaseView.Builder(activity);
        if (p.first.useRectangle) {
          builder = builder.withRectangleShape();
        }
        sequence.addSequenceItem(builder
          .setTarget(p.second)
          .setDismissText("GOT IT")
          .setContentText(p.first.content)
          .setDelay(DELAY_MS) // optional but starting animations immediately in onCreate can make them choppy
          .singleUse(p.first.name())
          .build());
      }

      ShowcaseConfig config = new ShowcaseConfig();
      config.setDelay(500);
      sequence.setConfig(config);

      sequence.start();
    }
  }

  @Nullable
  private static Activity getActivity(View view) {
    Context context = view.getContext();
    while (context instanceof ContextWrapper) {
      if (context instanceof Activity) {
        return (Activity)context;
      }
      context = ((ContextWrapper)context).getBaseContext();
    }
    return null;
  }
}
