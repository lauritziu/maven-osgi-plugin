/*******************************************************************************
 * Copyright (c) 2019 Thomas Fahrmeyer.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Thomas Fahrmeyer - initial API and implementation
 *******************************************************************************/
package at.bestsolution.maven.osgi.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

/**
 * Should be used as main class for starting the Eclipse application from an IDE while developing.
 * <p/>
 * It generates bundle.info and config.ini file, launches the Equinox OSGI framework. The bundles are
 * found by inspecting the app classpath. Each artifact (jar or path) identified as bundle will be considered.
 * It works in the same way as the {@code maven-osgi-exec} plugin's, but not Maven runtime needs to be started first.
 * <p/>
 * Parameter configuration:
 *
 * TODO
 */
public class AppClasspathLauncher {

    /** system property to specify the path to a config yaml file */
    public static final String SYSPROP_CONFIG_FILE_PATH = "launcher.config.path";

    private static final String LAUNCHER_ARGS_PREFIX = "-launcher.";

    private static final String LAUNCHER_PRODUCT_ID_PARAM = LAUNCHER_ARGS_PREFIX + "product.id";

    private static final String TMP_CONFIG_DIR_NAME = "eclipse.app.launcher";
    private static final String EQUINOX_LAUNCHER_MAIN_CLASS = "org.eclipse.equinox.launcher.Main";
    public static final String OSGI_PRODUCT_PARAM = "-product";

    private Set<Bundle> bundles;
    private final static Logger logger = LoggerFactory.getLogger(AppClasspathLauncher.class);

    private OsgiBundleInfo osgiVerifier;
    private ConfigIniGenerator configIniGenerator;
    private BundleInfoGenerator bundleInfoGenerator;

    private Configuration configuration;

    // external parameters: needs to be supprted

    protected Map<String, Integer> startLevels = new HashMap<>();
    protected List<String> osgiRuntimeArguments;
    protected Properties vmProperties;
    private List<String> commandLineArgs;
    private Optional<Path> ymlConfigPath;


    /**
     * TODO: following parameters are need to be processed:
     * - startLevels
     * - program arguments
     * - vm properties
     *
     * @param args
     */
    public static void main(String[] args) {

        AppClasspathLauncher launcher = new AppClasspathLauncher(Arrays.asList(args));

        launcher.execute();
    }



    public AppClasspathLauncher() {
        this(new ArrayList());
    }

    public AppClasspathLauncher(List<String> commandLineArgs) {
        defineConfigPath();
        readConfiguration();

        List<String> applicationCommandLineArgs = filterLauncherArgs(commandLineArgs);
        configuration.setCommandLineArgs(applicationCommandLineArgs);

        Optional<String> customizedProductId = findProductIdFromCommandline(commandLineArgs);
        customizedProductId.ifPresent(configuration::addProductId);

        bundles = findAllBundlesInClasspath();

        Path configPath = Paths.get(System.getProperty("java.io.tmpdir")).resolve(TMP_CONFIG_DIR_NAME).resolve("configuration");

        configIniGenerator = new ConfigIniGenerator(configPath);
        bundleInfoGenerator = new BundleInfoGenerator(configPath);

    }


    public Configuration getConfiguration() {
        return configuration;
    }


    @SuppressWarnings("Duplicates")
    public void execute() {

        Path bundleInfoPath = bundleInfoGenerator.generateBundlesInfo(bundles);
        Path ini = configIniGenerator.generateConfigIni(bundles, bundleInfoPath);

        Optional<URL> launcherJar = bundles.stream()
                .filter(bundle -> bundle.symbolicName.contains("org.eclipse.equinox.launcher"))
                .findFirst()
                .map(bundle -> {
                    try {
                        return bundle.path.toUri().toURL();
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                });

        URLClassLoader l = new URLClassLoader( new URL[] { launcherJar.get() } );

        List<String> cmd = new ArrayList<>();
        cmd.add("-configuration");
        cmd.add("file:" + ini.toString());
        cmd.addAll(osgiRuntimeArguments);

        appendCommandLineArgumentsTo(cmd);

        System.getProperties().putAll(vmProperties);

        Thread t = new Thread() {
            public void run() {
                try {
                    Class<?> cl = getContextClassLoader().loadClass(EQUINOX_LAUNCHER_MAIN_CLASS);
                    Method m = cl.getDeclaredMethod("main", String[].class);
                    m.invoke(null, new Object[] {cmd.toArray(new String[0])});

                } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    logger.error("Can not invoke main-method for " + EQUINOX_LAUNCHER_MAIN_CLASS, e);
                }
            }
        };
        t.setContextClassLoader(l);
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            logger.error("Error on waiting for Equinox launcher to finish.", e);
        }
    }

    //----------------------------------------
    // private methods
    //----------------------------------------

    private Optional<String> findProductIdFromCommandline(List<String> commandLineArgs) {

        int index = commandLineArgs.indexOf(LAUNCHER_PRODUCT_ID_PARAM);
        if (index != -1) {
            return Optional.of(commandLineArgs.get(index + 1));
        }

        return Optional.empty();
    }

    private List<String> filterLauncherArgs(List<String> commandLineArgs) {
        return commandLineArgs.stream().filter(arg -> !arg.startsWith(LAUNCHER_ARGS_PREFIX)).collect(Collectors.toList());
    }


    /**
     * Determines the path to the config yaml path. A custom path can be defined by system property {@link #SYSPROP_CONFIG_FILE_PATH}.
     * If that is not set a default config is used.
     *
     * @throws ConfigurationException if path to config file via system property {@link #SYSPROP_CONFIG_FILE_PATH} does not exist.
     */
    private void defineConfigPath() {
        String pathProp = System.getProperty(SYSPROP_CONFIG_FILE_PATH);

        if (pathProp == null) {
            ymlConfigPath = Optional.empty();

        } else {
            Path configPath = Paths.get(pathProp);
            if (!configPath.toFile().exists()) {
                throw new ConfigurationException("Path to config path specified via system property '" + SYSPROP_CONFIG_FILE_PATH + "' does not exists.");

            } else {
                ymlConfigPath = Optional.of(configPath);
            }
        }

    }

    private Configuration readConfiguration() {
        Yaml yaml = new Yaml(new Constructor(Configuration.class));

        FileReader defaultConfigReader = useDefaultConfig();

        Object config = null;

        try (Reader fileReader = ymlConfigPath.map(this::useCustomConfigReader).orElse(defaultConfigReader);) {

            config = yaml.load(fileReader);

        } catch (IOException e) {
            throw new ConfigurationException("Some problems reading the configuration has occured.", e);
        }

        configuration = (Configuration) config;

        return configuration;
    }

    private FileReader useCustomConfigReader(Path path) {

        FileReader reader = null;

        try {
            //noinspection IOResourceOpenedButNotSafelyClosed
            reader = new FileReader(path.toFile());

        } catch (FileNotFoundException e) {
            logger.error("Path to custom config does not exist: " + path);
        }

        return reader;
    }

    private FileReader useDefaultConfig() {
        return null;
    }

    private void appendCommandLineArgumentsTo(List<String> cmds)  {
        if (commandLineArgs != null) {
            cmds.addAll(commandLineArgs);
        }
    }

    private Set<Bundle> findAllBundlesInClasspath() {
        return Arrays.asList(getClasspath()).stream()
                .map(this::mapToBundle)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    private OsgiBundleInfo getOsgiVerifier() {
        if (osgiVerifier == null) {
            osgiVerifier = new OsgiBundleInfo();
        }
        return osgiVerifier;
    }


    private Optional<Bundle> mapToBundle(URL classpathEntry) {
        URI uri;

        try {
            uri = classpathEntry.toURI();
            Path pathToArtifact = Paths.get(uri);

            return getOsgiVerifier().getManifest(pathToArtifact)
                    .filter(osgiVerifier::isBundle)
                    .map(m -> new Bundle(m, pathToArtifact, getStartLevel(m)));

        } catch (URISyntaxException e) {
            logger.error("Classpath entry " + classpathEntry + " can not be transformed to URI", e);

        }

        return Optional.empty();
    }


    @SuppressWarnings("Duplicates")
    private Integer getStartLevel(Manifest m) {
        String name = OsgiBundleInfo.bundleName(m);
        if (startLevels != null) {
            return startLevels.get(name);
        } else {
            // default startlevels
            switch (name) {
                case "org.eclipse.core.runtime":
                    return 4;
                case "org.eclipse.equinox.common":
                    return 2;
                case "org.eclipse.equinox.ds":
                    return 2;
                case "org.eclipse.equinox.event":
                    return 2;
                case Constants.SIMPLECONFIGURATOR_BUNDLE_NAME:
                    return 1;
                case Constants.OSGI_FRAMEWORK_BUNDLE_NAME:
                    return -1;
                default:
                    return null;
            }
        }
    }

    private URL[] getClasspath() {
        ClassLoader classLoader = this.getClass().getClassLoader();

        if (!(classLoader instanceof URLClassLoader)) {
            throw new IllegalArgumentException("Only classloader instances of type " + URLClassLoader.class + " are supported");
        }

        URL[] urls = ((URLClassLoader) classLoader).getURLs();

        // TODO: support filtering

        return urls;
    }

    //----------------------------------------
    // Configuration class filled with yaml configuration
    //----------------------------------------
    public static class Configuration {

        private String osgiCommandLineArgs;
        private Map<String, Integer> startLevels;
        private Map<String, String> vmProperties;
        private List<String> commandLineArgs;

        /**
         * Adds a osgiCommand line argument {@code -product} with the given product id that is to be started.
         *
         * @throws ConfigurationException is thrown if argument {@link #OSGI_PRODUCT_PARAM} is already defined in osgi
         * command line args.
         */
        public void addProductId(String productId) {
            if (osgiCommandLineArgs.contains(OSGI_PRODUCT_PARAM)) {
                throw new ConfigurationException("Try to add a OSGI product id, but there is already one defined in parameters " + osgiCommandLineArgs);
            }

            osgiCommandLineArgs += " -product " + productId;
        }

        public void setOsgiRuntimeArguments(String args) {
            osgiCommandLineArgs = args;
        }

        public List<String> getOsgiRuntimeArgumentsAsList() {
            return Arrays.asList(osgiCommandLineArgs.split(" "));
        }

        public void setStartLevels(Map<String, Integer> startLevels) {
            this.startLevels = startLevels;
        }

        public Map<String, String> getVmProperties() {
            return vmProperties;
        }

        public void setVmProperties(Map<String, String> vmProperties) {
            this.vmProperties = vmProperties;
        }

        public Map<String, Integer> getStartLevels() {
            return startLevels;
        }


        public List<String> getCommandLineArgs() {
            return commandLineArgs;
        }

        public void setCommandLineArgs(List<String> commandLineArgs) {
            this.commandLineArgs = commandLineArgs;
        }
    }

    /**
     * Thrown if some problems reading the configuration from yaml file has occured.
     */
    private static class ConfigurationException extends RuntimeException {
        public ConfigurationException(String message) {
            super(message);
        }

        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
