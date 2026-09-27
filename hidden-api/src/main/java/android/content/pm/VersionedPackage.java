package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;

// 隐藏 API 桩，app 模块通过 compileOnly 引用（含反射），IDE 误报未使用
@SuppressWarnings("unused")
public class VersionedPackage implements Parcelable {
    public static final Creator<VersionedPackage> CREATOR = new Creator<>() {
        @Override
        public VersionedPackage createFromParcel(Parcel source) {
            return new VersionedPackage(source);
        }

        @Override
        public VersionedPackage[] newArray(int size) {
            return new VersionedPackage[size];
        }
    };

    public String packageName;
    public long versionCode;

    public VersionedPackage(String packageName, long versionCode) {
        this.packageName = packageName;
        this.versionCode = versionCode;
    }

    public VersionedPackage(Parcel in) {
        this.packageName = in.readString();
        this.versionCode = in.readLong();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(packageName);
        dest.writeLong(versionCode);
    }
}
