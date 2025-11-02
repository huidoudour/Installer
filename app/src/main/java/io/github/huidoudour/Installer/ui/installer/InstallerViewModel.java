package io.github.huidoudour.Installer.ui.installer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class InstallerViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public InstallerViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("安装器页面");
    }

    public LiveData<String> getText() {
        return mText;
    }
}