# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/lib/jvm/android-studio/sdk/tools/proguard/proguard-android.txt

# Keep model classes
-keep class com.filemanager.model.** { *; }
-keep class com.filemanager.manager.** { *; }

# Keep FileInfo and RemoteConnection
-keep class com.filemanager.model.FileInfo { *; }
-keep class com.filemanager.model.RemoteConnection { *; }
-keep class com.filemanager.model.ProtocolType { *; }
-keep class com.filemanager.model.ConnectionState { *; }

# Keep FileSystemManager interface and implementations
-keep interface com.filemanager.manager.FileSystemManager { *; }
-keep class com.filemanager.manager.FtpFileSystemManager { *; }
-keep class com.filemanager.manager.FtpsFileSystemManager { *; }
-keep class com.filemanager.manager.SftpFileSystemManager { *; }
-keep class com.filemanager.manager.SmbFileSystemManager { *; }
-keep class com.filemanager.manager.WebDavFileSystemManager { *; }
-keep class com.filemanager.manager.ConnectionManager { *; }

# Apache Commons Net
-keep class org.apache.commons.net.** { *; }
-keep interface org.apache.commons.net.** { *; }

# SSHJ
-keep class net.schmizz.** { *; }
-keep interface net.schmizz.** { *; }

# JCIFS
-keep class jcifs.** { *; }
-keep interface jcifs.** { *; }

# Keep HTTP components
-keep class org.apache.commons.httpclient.** { *; }
-keep interface org.apache.commons.httpclient.** { *; }

# Keep JAXB
-keep class javax.xml.** { *; }
-keep class org.w3c.dom.** { *; }
