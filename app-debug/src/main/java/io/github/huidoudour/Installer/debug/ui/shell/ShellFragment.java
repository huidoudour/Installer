package io.github.huidoudour.Installer.debug.ui.shell;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import io.github.huidoudour.Installer.debug.databinding.FragmentShellBinding;

public class ShellFragment extends Fragment {

    private FragmentShellBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        ShellViewModel shellViewModel =
                new ViewModelProvider(this).get(ShellViewModel.class);

        binding = FragmentShellBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textShell;
        shellViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
