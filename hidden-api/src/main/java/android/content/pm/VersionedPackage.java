package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;

public class VersionedPackage implements Parcelable {
    public static final Creator<VersionedPackage> CREATOR = new Creator<VersionedPackage>() {
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
