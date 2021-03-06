package at.bestsolution.maven.osgi.targetplatform.lib.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.jar.JarFile;

import at.bestsolution.maven.osgi.targetplatform.lib.LoggingSupport;

/**
 * Accesses the target platform jar on the update site and provides the feature.xml entry as an input stream.
 * 
 *
 */
class JarAccessor {

    private static final String JAR_URL_SUFFIX = "!/";
    private static final String JAR_URL_PREFIX = "jar:";

    static InputStream readEntry(String jarUrl, String entryName) {

        try {
            URL url = new URL(convertToJarUrl(jarUrl));
            JarURLConnection jarConnection = (JarURLConnection) url.openConnection();
            JarFile jarFile = jarConnection.getJarFile();
            return jarFile.getInputStream(jarFile.getEntry(entryName));
        } catch (IOException e) {
            LoggingSupport.logErrorMessage(e.getMessage(), e);
        }
        return null;
    }

    private static String convertToJarUrl(String httpUrl) {
        return JAR_URL_PREFIX + httpUrl + JAR_URL_SUFFIX;
    }

}
