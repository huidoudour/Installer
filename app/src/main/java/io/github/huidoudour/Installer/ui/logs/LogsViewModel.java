package io.github.huidoudour.Installer.ui.logs;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class LogsViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public LogsViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("日志页面");
    }

    public LiveData<String> getText() {
        return mText;
    }
}
