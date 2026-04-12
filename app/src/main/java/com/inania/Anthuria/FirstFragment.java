package com.inania.Anthuria;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.inania.Anthuria.R;

public class FirstFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_first, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView textView = view.findViewById(R.id.textview_first);
        Button buttonFirst = view.findViewById(R.id.button_first);

        // Safety: prevent crash if views missing
        if (textView != null) {
            textView.setText("Welcome to Anthuria Design!");
        } else {
            // Optional: debug hint (remove in production)
            Toast.makeText(requireContext(), "TextView missing: @+id/textview_first", Toast.LENGTH_SHORT).show();
        }

        if (buttonFirst != null) {
            buttonFirst.setOnClickListener(v -> {
                try {
                    NavController navController = NavHostFragment.findNavController(this);
                    navController.navigate(R.id.action_FirstFragment_to_SecondFragment);
                } catch (Exception e) {
                    // This catches "destination unknown" or "not attached" errors
                    Toast.makeText(requireContext(),
                            "Navigation error: " + e.getClass().getSimpleName() + "\n" + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            });
        } else {
            Toast.makeText(requireContext(), "Button missing: @+id/button_first", Toast.LENGTH_SHORT).show();
        }
    }
}