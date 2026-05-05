package android.content.pm;

import android.content.IntentSender;
import android.os.Parcel;

import java.io.File;
import java.io.OutputStream;

/**
 * Hidden API stub for PackageInstaller
 */
public class PackageInstaller {
    
    public static class SessionParams {
        public static final int MODE_INVALID = 0;
        public static final int MODE_DEFAULT = 1;
        public static final int MODE_INHERIT_EXISTING = 2;
        public static final int MODE_FULL_INSTALL = 3;
        
        public int installFlags;
        
        public SessionParams(int mode) {
        }
    }
    
    public class Session {
        public Session(int sessionId) {
        }
        
        public int openWrite(String name, long offsetBytes, long lengthBytes) {
            throw new UnsupportedOperationException("Stub");
        }
        
        public void write(int handle, long offsetBytes, long lengthBytes, File file) {
            throw new UnsupportedOperationException("Stub");
        }
        
        public void fsync(OutputStream os) {
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
