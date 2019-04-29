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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

/**
 * TODO
 */
public class AppClasspathLauncher {

    private static final String TMP_CONFIG_DIR_NAME = "eclipse.app.launcher";
    private static final String EQUINOX_LAUNCHER_MAIN_CLASS = "org.eclipse.equinox.launcher.Main";

    private Set<Bundle> bundles;
    private final static Logger logger = LoggerFactory.getLogger(AppClasspathLauncher.class);

    private OsgiBundleInfo osgiVerifier;
    private ConfigIniGenerator configIniGenerator;
    private BundleInfoGenerator bundleInfoGenerator;

    // external parameters: needs to be supprted

    protected Map<String, Integer> startLevels = new HashMap<>();
    protected List<String> osgiRuntimeArguments;
    protected Properties vmProperties;
    private List<String> commandLineArgs;


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



    public AppClasspathLauncher(List<String> commandLineArgs) {


        this.commandLineArgs = commandLineArgs;

        osgiRuntimeArguments = Arrays.asList(
                "-console",
                "8999",
                "-consoleLog",
                "-product",
                "com.zeiss.forum.viewer.app.product",
                "-clearPersistedState",
                "-clean");

        startLevels = new HashMap<>();
        startLevels.put("org.eclipse.core.runtime", 0);
        startLevels.put("org.eclipse.equinox.common", 2);
        startLevels.put("org.eclipse.equinox.ds", 2);
        startLevels.put("org.eclipse.equinox.event", 2);
        startLevels.put(Constants.SIMPLECONFIGURATOR_BUNDLE_NAME, 1);
        startLevels.put(Constants.OSGI_FRAMEWORK_BUNDLE_NAME, -1);
        startLevels.put("org.apache.servicemix.bundles.spring-beans", 3);
        startLevels.put("org.apache.servicemix.bundles.spring-context", 3);
        startLevels.put("org.apache.servicemix.bundles.spring-core", 3);
        startLevels.put("org.eclipse.gemini.blueprint.extender", 3);
//						<!-- TODO: this is still ugly. Need a better way to mark the bundle
        startLevels.put("com.zeiss.forum.viewer.core.service-impl", 4);
        startLevels.put("com.zeiss.forum.viewer.core.di", 4);
        startLevels.put("com.sun.tools.tools", 4);

        vmProperties = new Properties();
        vmProperties.put("org.osgi.framework.bundle.parent", "ext");





        bundles = findAllBundlesInClasspath();

        Path configPath = Paths.get(System.getProperty("java.io.tmpdir")).resolve(TMP_CONFIG_DIR_NAME).resolve("configuration");

        configIniGenerator = new ConfigIniGenerator(configPath);
        bundleInfoGenerator = new BundleInfoGenerator(configPath);

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
}
