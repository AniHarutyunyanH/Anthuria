package com.inania.Anthuria;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SecondFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_second, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button buttonSecond = view.findViewById(R.id.button_second);

        // Safety: prevent crash if ID is missing in XML
        if (buttonSecond == null) {
            // In real app you might log this or show debug toast
            // For now just return early
            return;
        }

        buttonSecond.setText("Start Designing");

        buttonSecond.setOnClickListener(v -> {
            // Get context safely (Fragment context preferred over Activity)
            Context context = getContext();
            if (context == null) {
                return; // Activity already gone → skip
            }

            // Mark onboarding as completed
            SharedPreferences pref = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            pref.edit()
                    .putBoolean("isFirstRun", false)
                    .apply();  // or .commit() if you need synchronous write

            // Go to Register
            Intent intent = new Intent(context, RegisterActivity.class);
            startActivity(intent);

            // Close the parent WelcomeActivity safely
            if (getActivity() != null) {
                getActivity().finish();
            }
        });
    }
}