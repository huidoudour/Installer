package io.github.huidoudour.Installer.demo;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hidden API Demo - 展示 hidden-api 模块中所有接口的文档信息
 */
public class MainActivity extends AppCompatActivity {

    private int colorPrimary;
    private int colorSecondary;
    private int colorTextSecondary;
    private int colorCodeBg;
    private int colorAidl;
    private int colorInterface;
    private int colorClass;
    private int colorCardBg;
    private int colorDivider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize colors
        colorPrimary = ContextCompat.getColor(this, android.R.color.holo_purple);
        colorSecondary = ContextCompat.getColor(this, android.R.color.holo_blue_dark);
        colorTextSecondary = ContextCompat.getColor(this, android.R.color.darker_gray);
        colorCodeBg = ContextCompat.getColor(this, android.R.color.white);
        colorAidl = ContextCompat.getColor(this, R.color.chip_aidl);
        colorInterface = ContextCompat.getColor(this, R.color.chip_interface);
        colorClass = ContextCompat.getColor(this, R.color.chip_class);
        colorCardBg = ContextCompat.getColor(this, R.color.card_bg);
        colorDivider = ContextCompat.getColor(this, R.color.divider);

        LinearLayout container = findViewById(R.id.container);
        buildApiCards(container);
    }

    private void buildApiCards(LinearLayout container) {
        List<ApiEntry> apis = new ArrayList<>();

        apis.add(new ApiEntry(
                "IIntentReceiver",
                "android.content",
                ApiType.INTERFACE,
                null,
                null,
                null,
                "Intent 接收回调接口，用于接收异步广播结果。",
                Arrays.asList("performReceive(Intent, int resultCode, String data,\n  Bundle extras, boolean ordered, boolean sticky, int sendingUser)"),
                new ArrayList<>(),
                new ArrayList<>()
        ));

        apis.add(new ApiEntry(
                "IIntentSender",
                "android.content",
                ApiType.INTERFACE,
                null,
                null,
                null,
                "Intent 发送者接口，用于拦截安装回调。核心隐藏 API，Dhizuku 安装流程依赖此接口。",
                Arrays.asList("send(int code, Intent intent, String resolvedType,\n  IBinder whitelistToken, IIntentReceiver finishedReceiver,\n  int flags, Bundle options)"),
                new ArrayList<>(),
                new ArrayList<>()
        ));

        apis.add(new ApiEntry(
                "IPackageInstaller",
                "android.content.pm",
                ApiType.AIDL,
                null,
                null,
                null,
                "包安装器 AIDL 接口，提供安装/卸载会话管理。通过 IPackageManager.getPackageInstaller() 获取。",
                Arrays.asList(
                        "uninstall(VersionedPackage, String callerPackageName,\n  int flags, IntentSender statusReceiver, int userId)",
                        "abandonSession(int sessionId)"
                ),
                Arrays.asList("Stub (Binder)"),
                new ArrayList<>()
        ));

        apis.add(new ApiEntry(
                "IPackageInstallerSession",
                "android.content.pm",
                ApiType.AIDL,
                null,
                null,
                null,
                "安装会话 AIDL 接口，代表一个活跃的安装操作。",
                new ArrayList<>(),
                Arrays.asList("Stub (Binder)"),
                new ArrayList<>()
        ));

        apis.add(new ApiEntry(
                "IPackageManager",
                "android.content.pm",
                ApiType.AIDL,
                null,
                null,
                null,
                "包管理器 AIDL 接口，Android 包管理核心服务。通过 ServiceManager 获取 IBinder 后转为接口。",
                Arrays.asList("getPackageInstaller() -> IPackageInstaller"),
                Arrays.asList("Stub (Binder)"),
                new ArrayList<>()
        ));

        apis.add(new ApiEntry(
                "PackageInstaller",
                "android.content.pm",
                ApiType.CLASS,
                null,
                null,
                null,
                "包安装器 Java 封装类，提供 Session 管理的高层 API。",
                Arrays.asList(
                        "createSession(SessionParams) -> int",
                        "openSession(int sessionId) -> Session"
                ),
                new ArrayList<>(),
                Arrays.asList(
                        "SessionParams - 安装会话参数",
                        "Session - 安装会话操作"
                )
        ));

        apis.add(new ApiEntry(
                "PackageInstaller.Session",
                "android.content.pm",
                ApiType.CLASS,
                null,
                null,
                null,
                "安装会话，负责写入 APK 数据并提交安装。",
                Arrays.asList(
                        "openWrite(String name, long offset, long length) -> int",
                        "write(int handle, long offset, long length, File file)",
                        "fsync(OutputStream os)",
                        "commit(IntentSender statusReceiver)",
                        "close()"
                ),
                new ArrayList<>(),
                new ArrayList<>()
        ));

        apis.add(new ApiEntry(
                "VersionedPackage",
                "android.content.pm",
                ApiType.CLASS,
                null,
                Arrays.asList("Parcelable"),
                null,
                "版本化包名，携带包名和版本代码。用于精确指定卸载目标。",
                Arrays.asList(
                        "describeContents() -> int",
                        "writeToParcel(Parcel dest, int flags)"
                ),
                new ArrayList<>(),
                Arrays.asList(
                        "packageName (String)",
                        "versionCode (long)",
                        "CREATOR (Creator<VersionedPackage>)"
                )
        ));

        apis.add(new ApiEntry(
                "ServiceManager",
                "android.os",
                ApiType.CLASS,
                null,
                null,
                "final",
                "系统服务管理器，用于获取系统服务的 IBinder。隐藏 API 的入口。",
                Arrays.asList("getService(String name) -> IBinder"),
                new ArrayList<>(),
                new ArrayList<>()
        ));

        apis.add(new ApiEntry(
                "UserHandle",
                "android.os",
                ApiType.CLASS,
                null,
                null,
                null,
                "用户句柄，用于标识 Android 多用户环境中的用户 ID。",
                Arrays.asList("myUserId() -> int"),
                new ArrayList<>(),
                new ArrayList<>()
        ));

        for (int i = 0; i < apis.size(); i++) {
            ApiEntry api = apis.get(i);
            container.addView(createApiCard(api));
            if (i < apis.size() - 1) {
                container.addView(createDivider(8));
            }
        }
    }

    private View createApiCard(ApiEntry api) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(colorCardBg);
        card.setPadding(dp(16), dp(12), dp(16), dp(16));
        card.setElevation(2f);

        // Header row: type chip + name
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        int chipColor;
        switch (api.type) {
            case AIDL:
                chipColor = colorAidl;
                break;
            case INTERFACE:
                chipColor = colorInterface;
                break;
            case CLASS:
                chipColor = colorClass;
                break;
            default:
                chipColor = colorClass;
        }
        header.addView(createChip(api.type.getLabel(), chipColor));

        TextView space = new TextView(this);
        space.setText("  ");
        header.addView(space);

        TextView nameView = new TextView(this);
        nameView.setText(api.name);
        nameView.setTextColor(colorPrimary);
        nameView.setTextSize(18f);
        nameView.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(nameView);

        card.addView(header);

        // Package path
        card.addView(createLabelValue("Package", api.pkg, dp(4)));

        // Modifiers
        if (api.modifiers != null && !api.modifiers.isEmpty()) {
            card.addView(createLabelValue("Modifiers", api.modifiers, dp(2)));
        }

        // Extends
        if (api.extends_ != null && !api.extends_.isEmpty()) {
            card.addView(createLabelValue("Extends", api.extends_, dp(2)));
        }

        // Implements
        if (api.implements_ != null && !api.implements_.isEmpty()) {
            StringBuilder implStr = new StringBuilder();
            for (int i = 0; i < api.implements_.size(); i++) {
                if (i > 0) implStr.append(", ");
                implStr.append(api.implements_.get(i));
            }
            card.addView(createLabelValue("Implements", implStr.toString(), dp(2)));
        }

        // Description
        card.addView(createDivider(4));
        TextView descView = new TextView(this);
        descView.setText(api.description);
        descView.setTextColor(colorTextSecondary);
        descView.setTextSize(13f);
        card.addView(descView);
        card.addView(createDivider(4));

        // Inner classes
        if (api.innerClasses != null && !api.innerClasses.isEmpty()) {
            card.addView(createSectionTitle("Inner Classes"));
            for (String ic : api.innerClasses) {
                card.addView(createCodeLine("  " + ic));
            }
            card.addView(createDivider(4));
        }

        // Methods
        if (api.methods != null && !api.methods.isEmpty()) {
            card.addView(createSectionTitle("Methods"));
            for (String m : api.methods) {
                card.addView(createCodeLine(m));
            }
            card.addView(createDivider(4));
        }

        // Fields
        if (api.fields != null && !api.fields.isEmpty()) {
            card.addView(createSectionTitle("Fields"));
            for (String f : api.fields) {
                card.addView(createCodeLine("  " + f));
            }
        }

        return card;
    }

    private TextView createChip(String text, int bgColor) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(Color.WHITE);
        chip.setTextSize(11f);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setBackgroundColor(bgColor);
        chip.setPadding(dp(10), dp(3), dp(10), dp(3));
        chip.setGravity(Gravity.CENTER);
        return chip;
    }

    private LinearLayout createLabelValue(String label, String value, int topMargin) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(0, topMargin, 0, 0);

        TextView labelView = new TextView(this);
        labelView.setText(label + ": ");
        labelView.setTextSize(13f);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        layout.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(13f);
        valueView.setTextColor(colorSecondary);
        layout.addView(valueView);

        return layout;
    }

    private TextView createSectionTitle(String title) {
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(14f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setPadding(0, dp(2), 0, dp(2));
        return titleView;
    }

    private TextView createCodeLine(String code) {
        TextView codeView = new TextView(this);
        codeView.setText(code);
        codeView.setTextSize(12f);
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setBackgroundColor(colorCodeBg);
        codeView.setPadding(dp(10), dp(4), dp(10), dp(4));
        return codeView;
    }

    private View createDivider(int heightDp) {
        View divider = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)
        );
        divider.setLayoutParams(params);
        return divider;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    enum ApiType {
        AIDL("AIDL"),
        INTERFACE("Interface"),
        CLASS("Class");

        private final String label;

        ApiType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    static class ApiEntry {
        final String name;
        final String pkg;
        final ApiType type;
        final String extends_;
        final List<String> implements_;
        final String modifiers;
        final String description;
        final List<String> methods;
        final List<String> fields;
        final List<String> innerClasses;

        ApiEntry(String name, String pkg, ApiType type, String extends_,
                 List<String> implements_, String modifiers, String description,
                 List<String> methods, List<String> innerClasses, List<String> fields) {
            this.name = name;
            this.pkg = pkg;
            this.type = type;
            this.extends_ = extends_;
            this.implements_ = implements_ != null ? implements_ : new ArrayList<>();
            this.modifiers = modifiers;
            this.description = description;
            this.methods = methods != null ? methods : new ArrayList<>();
            this.innerClasses = innerClasses != null ? innerClasses : new ArrayList<>();
            this.fields = fields != null ? fields : new ArrayList<>();
        }
    }
}
