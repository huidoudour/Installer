package android.content.pm;

import android.content.IntentSender;

import java.io.File;
import java.io.OutputStream;

/**
 * Hidden API stub for PackageInstaller
 */
public class PackageInstaller {
    
    // 公共常量 — 值必须与 AOSP 完全一致
    public static final String EXTRA_STATUS = "android.content.pm.extra.STATUS";
    public static final String EXTRA_STATUS_MESSAGE = "android.content.pm.extra.STATUS_MESSAGE";
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_PENDING_USER_ACTION = -1;
    public static final int STATUS_FAILURE = 1;
    
    public static class SessionParams {
        public static final int MODE_INVALID = -1;
        public static final int MODE_FULL_INSTALL = 1;
        public static final int MODE_INHERIT_EXISTING = 2;
        
        public int installFlags;
        
        public SessionParams(int mode) {
        }

        public void setSize(long sizeBytes) {
            throw new UnsupportedOperationException("Stub");
        }

        public void setAppPackageName(String packageName) {
            throw new UnsupportedOperationException("Stub");
        }

        public void setInstallerPackageName(String installerPackageName) {
            throw new UnsupportedOperationException("Stub");
        }

        public void setInstallReason(int installReason) {
            throw new UnsupportedOperationException("Stub");
        }

        public void setOriginatingUid(int uid) {
            throw new UnsupportedOperationException("Stub");
        }

        public void setOriginatingUri(android.net.Uri uri) {
            throw new UnsupportedOperationException("Stub");
        }

        public void setReferrerUri(android.net.Uri referrerUri) {
            throw new UnsupportedOperationException("Stub");
        }
    }
    
    public class Session {
        public Session(int sessionId) {
        }
        
        public java.io.OutputStream openWrite(String name, long offsetBytes, long lengthBytes) {
            throw new UnsupportedOperationException("Stub");
        }
        
        public void write(int handle, long offsetBytes, long lengthBytes, File file) {
            throw new UnsupportedOperationException("Stub");
        }
        
        public void fsync(java.io.OutputStream os) {
            throw new UnsupportedOperationException("Stub");
        }
        
        public void commit(IntentSender statusReceiver) {
            throw new UnsupportedOperationException("Stub");
        }
        
        public void close() {
            throw new UnsupportedOperationException("Stub");
        }
    }
    
    public int createSession(SessionParams params) {
        throw new UnsupportedOperationException("Stub");
    }
    
    public Session openSession(int sessionId) {
        throw new UnsupportedOperationException("Stub");
    }
}
