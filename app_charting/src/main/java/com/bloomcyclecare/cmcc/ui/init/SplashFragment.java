package com.bloomcyclecare.cmcc.ui.init;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bloomcyclecare.cmcc.R;

import androidx.fragment.app.Fragment;
import timber.log.Timber;

public class SplashFragment extends Fragment {

  private static String TAG = SplashFragment.class.getSimpleName();

  private ProgressBar mProgressBar;
  private TextView mErrorView;
  private TextView mStatusView;

  public SplashFragment() {
    // Required empty public constructor
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (getArguments() != null) {
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_splash, container, false);

    mProgressBar = view.findViewById(R.id.splash_progress);
    mErrorView = view.findViewById(R.id.splash_error_tv);
    mStatusView = view.findViewById(R.id.splash_status_tv);

    return view;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
  }

  @Override
  public void onDetach() {
    super.onDetach();
  }

  public void showProgress(final String message) {
    Activity activity = getActivity();
    if (activity == null) {
      return;
    }
    activity.runOnUiThread(() -> {
      mProgressBar.setVisibility(View.VISIBLE);
      updateStatus(message);
      mStatusView.setVisibility(View.VISIBLE);
      mErrorView.setText("");
      mErrorView.setVisibility(View.INVISIBLE);
      Timber.i(message);
    });
  }

  public void updateStatus(final String status) {
    Activity activity = getActivity();
    if (activity == null) {
      return;
    }
    Timber.i("Updating status: %s", status);
    requireActivity().runOnUiThread(() -> mStatusView.setText(status));
  }

  public void showError(final String errorText) {
    Activity activity = getActivity();
    if (activity == null) {
      return;
    }
    activity.runOnUiThread(() -> {
      mProgressBar.setVisibility(View.INVISIBLE);
      mStatusView.setVisibility(View.INVISIBLE);
      mErrorView.setText(errorText);
      mErrorView.setVisibility(View.VISIBLE);
    });
  }

}
