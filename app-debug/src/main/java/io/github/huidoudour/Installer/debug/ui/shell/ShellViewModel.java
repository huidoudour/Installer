package io.github.huidoudour.Installer.debug.ui.shell;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class ShellViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public ShellViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("Shell 页面");
    }

    public LiveData<String> getText() {
        return mText;
    }
}
