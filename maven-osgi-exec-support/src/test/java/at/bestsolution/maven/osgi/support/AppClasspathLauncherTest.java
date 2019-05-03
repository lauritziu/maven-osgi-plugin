package at.bestsolution.maven.osgi.support;


import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class AppClasspathLauncherTest {

    private final static String defaultConfigFileName = "/test-config.yml";
    private final static String withoutProductConfigFileName = "/test-config-without-product.yml";

    @Test
    public void readDefaultConfiguration() {
        fail("not yet implemented");

    }

    @Test
    public void readSuppliedConfiguration() {
        Path path = Paths.get(getConfigUrl(defaultConfigFileName));
        System.setProperty(AppClasspathLauncher.SYSPROP_CONFIG_FILE_PATH, path.toString());

        AppClasspathLauncher launcher = new AppClasspathLauncher();

        AppClasspathLauncher.Configuration configuration = launcher.getConfiguration();

        assertNotNull(configuration, "configuration is null");
        assertEquals(7, configuration.getOsgiRuntimeArgumentsAsList().size());
        assertEquals("-clean", configuration.getOsgiRuntimeArgumentsAsList().get(6), "last element of osgi parameters is not correct");

        assertEquals(13, configuration.getStartLevels().size(), "count of expeected startlevels is not correct");
        assertEquals(-1, configuration.getStartLevels().get(Constants.OSGI_FRAMEWORK_BUNDLE_NAME), "startlevel of osgi runtime is not correct");

        assertEquals(1, configuration.getVmProperties().size(), "count of expected vm properties is not correct");
        assertEquals("ext", configuration.getVmProperties().get("org.osgi.framework.bundle.parent"), "vm parameter not correct");
    }

    @Test
    public void addProductId_toDefaultConfiguration() {
        Path path = Paths.get(getConfigUrl(withoutProductConfigFileName));
        System.setProperty(AppClasspathLauncher.SYSPROP_CONFIG_FILE_PATH, path.toString());

        AppClasspathLauncher launcher = new AppClasspathLauncher(Arrays.asList("-launcher.product.id", "xyz"));
        AppClasspathLauncher.Configuration configuration = launcher.getConfiguration();

        assertTrue(configuration.getOsgiRuntimeArgumentsAsList().contains("-product"), "no product available");
        assertTrue(configuration.getOsgiRuntimeArgumentsAsList().contains("xyz"), "no product id available");

    }

    @Test
    public void readConfiguration_noneExisting() {
        AppClasspathLauncher launcher = new AppClasspathLauncher();
    }

    private URI getConfigUrl(String configFileName) {
        try {
            return getClass().getResource(configFileName).toURI();

        } catch (URISyntaxException e) {
            fail("default config filename can not be translated to an URI", e);
        }

        return null;
    }
}