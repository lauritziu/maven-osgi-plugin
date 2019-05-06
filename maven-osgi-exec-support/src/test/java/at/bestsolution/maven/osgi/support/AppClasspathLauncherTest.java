package at.bestsolution.maven.osgi.support;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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

    @AfterEach
    public void afterAll() {
        System.clearProperty(AppClasspathLauncher.SYSPROP_CONFIG_FILE_PATH);
    }

    @Test
    public void readDefaultConfiguration() {
        AppClasspathLauncher launcher = new AppClasspathLauncher();

        AppClasspathLauncher.Configuration configuration = launcher.getConfiguration();

        assertNotNull(configuration, "configuration is null");
        assertEquals(5, configuration.getOsgiRuntimeArgumentsAsList().size());
        assertEquals("-clean", configuration.getOsgiRuntimeArgumentsAsList().get(4), "last element of osgi parameters is not correct");

        assertEquals(6, configuration.getStartLevels().size(), "count of expeected startlevels is not correct");
        assertEquals(-1, configuration.getStartLevels().get(Constants.OSGI_FRAMEWORK_BUNDLE_NAME), "startlevel of osgi runtime is not correct");

        assertEquals(1, configuration.getVmProperties().size(), "count of expected vm properties is not correct");
        assertEquals("ext", configuration.getVmProperties().get("org.osgi.framework.bundle.parent"), "vm parameter not correct");

    }

    @Test
    public void readDefaultConfiguration_addProductId() {
        AppClasspathLauncher launcher = new AppClasspathLauncher(Arrays.asList(AppClasspathLauncher.LAUNCHER_PRODUCT_ID_PARAM , "xyz"));

        AppClasspathLauncher.Configuration configuration = launcher.getConfiguration();

        assertNotNull(configuration, "configuration is null");
        assertEquals(7, configuration.getOsgiRuntimeArgumentsAsList().size());
        assertEquals("-clean", configuration.getOsgiRuntimeArgumentsAsList().get(4), "last element of osgi parameters is not correct");

        assertEquals(6, configuration.getStartLevels().size(), "count of expeected startlevels is not correct");
        assertEquals(-1, configuration.getStartLevels().get(Constants.OSGI_FRAMEWORK_BUNDLE_NAME), "startlevel of osgi runtime is not correct");

        assertEquals(1, configuration.getVmProperties().size(), "count of expected vm properties is not correct");
        assertEquals("ext", configuration.getVmProperties().get("org.osgi.framework.bundle.parent"), "vm parameter not correct");

        assertTrue(launcher.getConfiguration().getOsgiRuntimeArgumentsAsList().contains("-product"));
        assertTrue(launcher.getConfiguration().getOsgiRuntimeArgumentsAsList().contains("xyz"));
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
    public void addProductId_toGivenConfiguration() {
        Path path = Paths.get(getConfigUrl(withoutProductConfigFileName));
        System.setProperty(AppClasspathLauncher.SYSPROP_CONFIG_FILE_PATH, path.toString());

        AppClasspathLauncher launcher = new AppClasspathLauncher(Arrays.asList("-launcher.product.id", "xyz"));
        AppClasspathLauncher.Configuration configuration = launcher.getConfiguration();

        assertTrue(configuration.getOsgiRuntimeArgumentsAsList().contains("-product"), "no product available");
        assertTrue(configuration.getOsgiRuntimeArgumentsAsList().contains("xyz"), "no product id available");

    }

    @Test
    public void addProductId_toGivenConfiguration_withExistingProductId() {
        Path path = Paths.get(getConfigUrl(defaultConfigFileName));
        System.setProperty(AppClasspathLauncher.SYSPROP_CONFIG_FILE_PATH, path.toString());

        Assertions.assertThrows(AppClasspathLauncher.ConfigurationException.class, () -> {
            new AppClasspathLauncher(Arrays.asList("-launcher.product.id", "xyz"));
        });

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