package com.vypeensoft.ftpserver;

import android.os.Handler;
import android.os.Looper;
import com.vypeensoft.ftpserver.Log;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletResult;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.ftplet.Authentication;
import org.apache.ftpserver.ftplet.AuthenticationFailedException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FileSystemFactory;
import org.apache.ftpserver.filesystem.nativefs.NativeFileSystemFactory;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.AnonymousAuthentication;
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FTPServerManager {
    private static final String TAG = "FTPServerManager";

    private static FTPServerManager sInstance;
    private FtpServer mFtpServer;
    private final AtomicInteger mConnectedClients = new AtomicInteger(0);
    private ClientCountListener mClientCountListener;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public interface ClientCountListener {
        void onClientCountChanged(int count);
    }

    private FTPServerManager() {}

    public static synchronized FTPServerManager getInstance() {
        if (sInstance == null) {
            sInstance = new FTPServerManager();
        }
        return sInstance;
    }

    public void setClientCountListener(ClientCountListener listener) {
        this.mClientCountListener = listener;
        if (listener != null) {
            listener.onClientCountChanged(mConnectedClients.get());
        }
    }

    public boolean isRunning() {
        return mFtpServer != null && !mFtpServer.isStopped();
    }

    public int getConnectedClientsCount() {
        return mConnectedClients.get();
    }

    public synchronized void startServer(SettingsManager.Settings settings) throws FtpException {
        if (isRunning()) {
            Log.d(TAG, "FTP Server is already running");
            return;
        }

        mConnectedClients.set(0);

        FtpServerFactory serverFactory = new FtpServerFactory();
        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(settings.port);

        // Passive Mode Configuration
        if (settings.passive_mode) {
            DataConnectionConfigurationFactory dataConnFactory = new DataConnectionConfigurationFactory();
            // Optional: configure passive address if required, defaults are fine
            listenerFactory.setDataConnectionConfiguration(dataConnFactory.createDataConnectionConfiguration());
        }

        serverFactory.addListener("default", listenerFactory.createListener());

        // Max connections
        ConnectionConfigFactory connConfigFactory = new ConnectionConfigFactory();
        connConfigFactory.setMaxLogins(settings.max_connections);
        connConfigFactory.setMaxAnonymousLogins(settings.anonymous_login ? settings.max_connections : 0);
        serverFactory.setConnectionConfig(connConfigFactory.createConnectionConfig());

        // Custom in-memory user manager
        serverFactory.setUserManager(new CustomUserManager(settings));

        // Custom FileSystemFactory to wrap NativeFileSystemFactory and prevent NIO transferTo crashes on Android
        serverFactory.setFileSystem(new FileSystemFactory() {
            private final NativeFileSystemFactory delegate = new NativeFileSystemFactory();

            @Override
            public FileSystemView createFileSystemView(User user) throws FtpException {
                final FileSystemView view = delegate.createFileSystemView(user);
                return new FileSystemView() {
                    @Override
                    public FtpFile getHomeDirectory() throws FtpException {
                        return wrapFile(view.getHomeDirectory());
                    }

                    @Override
                    public FtpFile getWorkingDirectory() throws FtpException {
                        return wrapFile(view.getWorkingDirectory());
                    }

                    @Override
                    public boolean changeWorkingDirectory(String dir) throws FtpException {
                        return view.changeWorkingDirectory(dir);
                    }

                    @Override
                    public FtpFile getFile(String file) throws FtpException {
                        return wrapFile(view.getFile(file));
                    }

                    @Override
                    public boolean isRandomAccessible() throws FtpException {
                        return view.isRandomAccessible();
                    }

                    @Override
                    public void dispose() {
                        view.dispose();
                    }
                };
            }
        });

        // Ftplet for tracking connections
        Map<String, org.apache.ftpserver.ftplet.Ftplet> ftplets = new HashMap<>();
        ftplets.put("tracker", new ConnectionTrackerFtplet());
        serverFactory.setFtplets(ftplets);

        mFtpServer = serverFactory.createServer();
        mFtpServer.start();
        Log.d(TAG, "FTP Server started successfully on port " + settings.port);
    }

    public synchronized void stopServer() {
        if (mFtpServer != null) {
            mFtpServer.stop();
            mFtpServer = null;
            mConnectedClients.set(0);
            notifyClientCountChanged(0);
            Log.d(TAG, "FTP Server stopped successfully");
        }
    }

    private void notifyClientCountChanged(final int count) {
        mHandler.post(() -> {
            if (mClientCountListener != null) {
                mClientCountListener.onClientCountChanged(count);
            }
        });
    }

    // Custom Ftplet to trace logins/connections
    private class ConnectionTrackerFtplet extends DefaultFtplet {
        @Override
        public FtpletResult onConnect(FtpSession session) throws FtpException, IOException {
            int current = mConnectedClients.incrementAndGet();
            Log.d(TAG, "Client connected. Active count: " + current);
            notifyClientCountChanged(current);
            return super.onConnect(session);
        }

        @Override
        public FtpletResult onDisconnect(FtpSession session) throws FtpException, IOException {
            int current = mConnectedClients.decrementAndGet();
            if (current < 0) {
                current = 0;
                mConnectedClients.set(0);
            }
            Log.d(TAG, "Client disconnected. Active count: " + current);
            notifyClientCountChanged(current);
            return super.onDisconnect(session);
        }
    }

    // Dynamic, clean UserManager implementing org.apache.ftpserver.ftplet.UserManager
    private static class CustomUserManager implements UserManager {
        private final SettingsManager.Settings mSettings;

        public CustomUserManager(SettingsManager.Settings settings) {
            this.mSettings = settings;
        }

        private String getCanonicalHomeDir() {
            String rootDir = mSettings.root_directory;
            try {
                rootDir = new java.io.File(rootDir).getCanonicalPath();
            } catch (IOException e) {
                Log.w(TAG, "Failed to resolve canonical path for root directory: " + e.getMessage());
            }
            return rootDir;
        }

        @Override
        public User getUserByName(String username) throws FtpException {
            if (mSettings.anonymous_login && "anonymous".equalsIgnoreCase(username)) {
                return createAnonymousUser();
            }
            if (mSettings.username.equals(username)) {
                return createNormalUser();
            }
            return null;
        }

        private User createAnonymousUser() {
            BaseUser user = new BaseUser();
            user.setName("anonymous");
            user.setHomeDirectory(getCanonicalHomeDir());
            List<Authority> authorities = new ArrayList<>();
            authorities.add(new org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission(mSettings.max_connections, mSettings.max_connections));
            if (!mSettings.read_only) {
                authorities.add(new WritePermission());
            }
            user.setAuthorities(authorities);
            return user;
        }

        private User createNormalUser() {
            BaseUser user = new BaseUser();
            user.setName(mSettings.username);
            user.setPassword(mSettings.password);
            user.setHomeDirectory(getCanonicalHomeDir());
            List<Authority> authorities = new ArrayList<>();
            authorities.add(new org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission(mSettings.max_connections, mSettings.max_connections));
            if (!mSettings.read_only) {
                authorities.add(new WritePermission());
            }
            user.setAuthorities(authorities);
            return user;
        }

        @Override
        public String[] getAllUserNames() {
            if (mSettings.anonymous_login) {
                return new String[]{mSettings.username, "anonymous"};
            }
            return new String[]{mSettings.username};
        }

        @Override
        public void delete(String username) {}

        @Override
        public void save(User user) {}

        @Override
        public boolean doesExist(String username) {
            if (mSettings.anonymous_login && "anonymous".equalsIgnoreCase(username)) {
                return true;
            }
            return mSettings.username.equals(username);
        }

        @Override
        public User authenticate(Authentication authentication) throws AuthenticationFailedException {
            if (authentication instanceof UsernamePasswordAuthentication) {
                UsernamePasswordAuthentication upAuth = (UsernamePasswordAuthentication) authentication;
                String user = upAuth.getUsername();
                String pass = upAuth.getPassword();
                if (mSettings.username.equals(user) && mSettings.password.equals(pass)) {
                    return createNormalUser();
                }
                throw new AuthenticationFailedException("Authentication failed: invalid username/password");
            } else if (authentication instanceof AnonymousAuthentication) {
                if (mSettings.anonymous_login) {
                    return createAnonymousUser();
                }
                throw new AuthenticationFailedException("Anonymous login is disabled");
            }
            throw new AuthenticationFailedException("Unsupported authentication method");
        }

        @Override
        public String getAdminName() {
            return mSettings.username;
        }

        @Override
        public boolean isAdmin(String username) {
            return mSettings.username.equals(username);
        }
    }

    private static FtpFile wrapFile(FtpFile file) {
        if (file == null) return null;
        if (file instanceof WrappedFtpFile) {
            return file;
        }
        return new WrappedFtpFile(file);
    }

    private static class WrappedFtpFile implements FtpFile {
        private final FtpFile file;

        public WrappedFtpFile(FtpFile file) {
            this.file = file;
        }

        public FtpFile getDelegate() {
            return file;
        }

        @Override
        public String getAbsolutePath() {
            return file.getAbsolutePath();
        }

        @Override
        public String getName() {
            return file.getName();
        }

        @Override
        public boolean isHidden() {
            return file.isHidden();
        }

        @Override
        public boolean isDirectory() {
            return file.isDirectory();
        }

        @Override
        public boolean isFile() {
            return file.isFile();
        }

        @Override
        public boolean doesExist() {
            return file.doesExist();
        }

        @Override
        public boolean isReadable() {
            return file.isReadable();
        }

        @Override
        public boolean isWritable() {
            return file.isWritable();
        }

        @Override
        public boolean isRemovable() {
            return file.isRemovable();
        }

        @Override
        public String getOwnerName() {
            return file.getOwnerName();
        }

        @Override
        public String getGroupName() {
            return file.getGroupName();
        }

        @Override
        public int getLinkCount() {
            return file.getLinkCount();
        }

        @Override
        public long getLastModified() {
            return file.getLastModified();
        }

        @Override
        public boolean setLastModified(long time) {
            return file.setLastModified(time);
        }

        @Override
        public long getSize() {
            return file.getSize();
        }

        @Override
        public Object getPhysicalFile() {
            return file.getPhysicalFile();
        }

        @Override
        public boolean mkdir() {
            return file.mkdir();
        }

        @Override
        public boolean delete() {
            return file.delete();
        }

        @Override
        public boolean move(FtpFile dest) {
            FtpFile rawDest = dest instanceof WrappedFtpFile ? ((WrappedFtpFile) dest).getDelegate() : dest;
            return file.move(rawDest);
        }

        @Override
        public List<FtpFile> listFiles() {
            List<? extends FtpFile> files = file.listFiles();
            if (files == null) return null;
            List<FtpFile> wrapped = new ArrayList<>();
            for (FtpFile f : files) {
                wrapped.add(wrapFile(f));
            }
            return wrapped;
        }

        @Override
        public java.io.InputStream createInputStream(long offset) throws IOException {
            java.io.InputStream is = file.createInputStream(offset);
            if (is == null) return null;
            // Wrap in BufferedInputStream to prevent native transferTo crashes on Android
            return new java.io.BufferedInputStream(is);
        }

        @Override
        public java.io.OutputStream createOutputStream(long offset) throws IOException {
            java.io.OutputStream os = file.createOutputStream(offset);
            if (os == null) return null;
            // Wrap in BufferedOutputStream to prevent native transferFrom crashes on Android
            return new java.io.BufferedOutputStream(os);
        }
    }
}
