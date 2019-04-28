package at.bestsolution.maven.osgi.exec;

import at.bestsolution.maven.osgi.support.OsgiBundleVerifier;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.apache.maven.shared.utils.cli.CommandLineUtils;
import org.codehaus.plexus.logging.console.ConsoleLogger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

public class AppClasspathLauncher {
    private static final String TMP_CONFIG_DIR_NAME = "eclipse.app.launcher";
    private static final String EQUINOX_LAUNCHER_MAIN_CLASS = "org.eclipse.equinox.launcher.Main";

    private static final String LF = System.getProperty("line.separator");
    private final ConsoleLogger logger;
    private OsgiBundleVerifier osgiVerifier;
    private Set<Bundle> bundles;

    // external parameters: needs to be supprted

    protected Map<String, Integer> startLevels = new HashMap<>();
    protected List<String> programArguments;
    protected Properties vmProperties;
//    @Parameter(property = "exec.args")
    private List<String> commandlineArgs;


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

        try {
            launcher.execute();

        } catch (CommandLineException e) {
            e.printStackTrace();
        }

    }



    public AppClasspathLauncher(List<String> commandLineArgs) {

        // TODO: Hack hard code know parameters, replace this
//<configuration>
//					<programArguments>
//						<programArgument>-console</programArgument>
//						<programArgument>8999</programArgument>
//						<programArgument>-consoleLog</programArgument>
//						<programArgument>-product</programArgument>
//						<programArgument>${app.product.id}</programArgument>
//						<programArgument>-clearPersistedState</programArgument>
//						<programArgument>-clean</programArgument>
//
//					</programArguments>
//					<vmProperties>
//						<property>
//							<name>org.osgi.framework.bundle.parent</name>
//							<value>ext</value>
//						</property>
//					</vmProperties>
//					<startLevels>
//						<org.eclipse.core.runtime>0</org.eclipse.core.runtime>
//						<org.eclipse.equinox.common>2</org.eclipse.equinox.common>
//						<org.eclipse.equinox.ds>2</org.eclipse.equinox.ds>
//						<org.eclipse.equinox.event>2</org.eclipse.equinox.event>
//						<org.eclipse.equinox.simpleconfigurator>1</org.eclipse.equinox.simpleconfigurator>
//						<org.eclipse.osgi>-1</org.eclipse.osgi>
//						<org.apache.servicemix.bundles.spring-beans>3</org.apache.servicemix.bundles.spring-beans>
//						<org.apache.servicemix.bundles.spring-context>3</org.apache.servicemix.bundles.spring-context>
//						<org.apache.servicemix.bundles.spring-core>3</org.apache.servicemix.bundles.spring-core>
//						<org.eclipse.gemini.blueprint.extender>3</org.eclipse.gemini.blueprint.extender>
//
//						<!-- TODO: this is still ugly. Need a better way to mark the bundle
//        as "to start" -->
//						<com.zeiss.forum.viewer.core.service-impl>4</com.zeiss.forum.viewer.core.service-impl>
//						<com.zeiss.forum.viewer.core.di>4</com.zeiss.forum.viewer.core.di>
//						<!--<com.sun.tools.tools>4</com.sun.tools.tools> -->
//					</startLevels>
//				</configuration>


        this.commandlineArgs = commandLineArgs;

        programArguments = Arrays.asList(
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
        startLevels.put("org.eclipse.equinox.simpleconfigurator", 1);
        startLevels.put("org.eclipse.osgi", -1);
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


        logger = new ConsoleLogger();

        bundles = findAllBundlesInClasspath();
        generateConfigIni();

    }


    @SuppressWarnings("Duplicates")
    public Path generateConfigIni() {


        Path configPath = Paths.get(System.getProperty("java.io.tmpdir")).resolve(TMP_CONFIG_DIR_NAME).resolve("configuration");

        Optional<Bundle> simpleConfigurator = bundles.stream()
                .filter(b -> "org.eclipse.equinox.simpleconfigurator".equals(b.symbolicName)).findFirst();

        Optional<Bundle> equinox = bundles.stream().filter(b -> "org.eclipse.osgi".equals(b.symbolicName))
                .findFirst();

        try {
            Files.createDirectories(configPath);
        } catch (IOException e1) {
            logger.error("Can not create directories for " + configPath);
        }

        if (simpleConfigurator.isPresent()) {
            Path configIni = configPath.resolve("config.ini");
            try (BufferedWriter writer = Files.newBufferedWriter(configIni, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                Path bundlesInfo = generateBundlesInfo(configPath, bundles);

                writer.append("osgi.bundles=" + toReferenceURL(simpleConfigurator.get()));
                writer.append(LF);
                writer.append("osgi.bundles.defaultStartLevel=4");
                writer.append(LF);
                writer.append("osgi.install.area=" + configPath.getParent().resolve("install").toUri().toString());
                writer.append(LF);
                writer.append("osgi.framework=" + equinox.get().path.toUri().toString());
                writer.append(LF);
                writer.append("eclipse.p2.data.area=@config.dir/.p2");
                writer.append(LF);
                writer.append("org.eclipse.equinox.simpleconfigurator.configUrl="
                        + bundlesInfo.toAbsolutePath().toUri().toString());
                writer.append(LF);
                writer.append("osgi.configuration.cascaded=false");
                writer.append(LF);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException("Only 'org.eclipse.equinox.simpleconfigurator' is supported");
        }

        return configPath;
    }

    @SuppressWarnings("Duplicates")
    public void execute() throws CommandLineException {
        Path ini = generateConfigIni();

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
        cmd.addAll(programArguments);

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

    private void appendCommandLineArgumentsTo(List<String> cmds) throws CommandLineException {
        if (commandlineArgs != null) {
            cmds.addAll(commandlineArgs);
        }
    }
    //----------------------------------------
    // private methods
    //----------------------------------------

    private Set<Bundle> findAllBundlesInClasspath() {
        return Arrays.asList(getClasspath()).stream()
                .map(this::mapToBundle)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    private OsgiBundleVerifier getOsgiVerifier() {
        if (osgiVerifier == null) {
            osgiVerifier = new OsgiBundleVerifier(logger);
        }
        return osgiVerifier;
    }

    @SuppressWarnings("Duplicates")
    private String toReferenceURL(Bundle element) throws IOException {
        StringBuilder w = new StringBuilder();
        w.append("reference\\:" + element.path.toUri().toString());

        if (element.startLevel != null) {
            w.append("@" + element.startLevel + "\\:start");
        } else {
            w.append("@start");
        }
        return w.toString();
    }



    @SuppressWarnings("Duplicates")
    private Path generateBundlesInfo(Path configurationDir, Set<Bundle> bundles) {
        Path bundleInfo = configurationDir.resolve("org.eclipse.equinox.simpleconfigurator").resolve("bundles.info");
        try {
            Files.createDirectories(bundleInfo.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(bundleInfo, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.append("#encoding=UTF-8");
            writer.append(LF);
            writer.append("#version=1");
            writer.append(LF);

            for (Bundle b : bundles) {
                if ("org.eclipse.osgi".equals(b.symbolicName)) {
                    continue;
                }

                writer.append(b.symbolicName);
                writer.append("," + b.version);
                writer.append(",file:" + generateLocalPath(b, configurationDir.resolve(".explode")).toString());
                writer.append("," + b.startLevel); // Start Level
                writer.append("," + b.autoStart); // Auto-Start
                writer.append(LF);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return bundleInfo;
    }

    @SuppressWarnings("Duplicates")
    private Path generateLocalPath(Bundle b, Path explodeDir) {
        if (b.dirShape && Files.isRegularFile(b.path)) {
            Path p = explodeDir.resolve(b.symbolicName + "_" + b.version);
            if (!Files.exists(p)) {
                try (ZipFile z = new ZipFile(b.path.toFile())) {
                    z.stream().forEach(e -> {
                        Path ep = p.resolve(e.getName());
                        if (e.isDirectory()) {
                            try {
                                Files.createDirectories(ep);
                            } catch (IOException e1) {
                                throw new RuntimeException(e1);
                            }
                        } else {
                            if (!Files.exists(ep.getParent())) {
                                try {
                                    Files.createDirectories(ep.getParent());
                                } catch (IOException e1) {
                                    throw new RuntimeException(e1);
                                }
                            }
                            try (OutputStream out = Files.newOutputStream(ep);
                                 InputStream in = z.getInputStream(e)) {
                                byte[] buf = new byte[1024];
                                int l;
                                while ((l = in.read(buf)) != -1) {
                                    out.write(buf, 0, l);
                                }
                            } catch (IOException e2) {
                                throw new RuntimeException(e2);
                            }
                        }
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return p;
        }
        return b.path.toAbsolutePath();
    }

    private Optional<Bundle> mapToBundle(URL classpathEntry) {
        URI uri;

        try {
            uri = classpathEntry.toURI();
            Path pathToArtifact = Paths.get(uri);

            return getOsgiVerifier().getManifest(pathToArtifact)
                    .filter(this::isBundle)
                    .map(m -> new Bundle(m, pathToArtifact));

        } catch (URISyntaxException e) {
            logger.error("Classpath entry " + classpathEntry + " can not be transformed to URI", e);

        }

        return Optional.empty();
    }

    private static String bundleName(Manifest m) {
        String name = m.getMainAttributes().getValue("Bundle-SymbolicName");
        return name.split(";")[0];
    }

    private boolean isBundle(Manifest m) {
        return m.getMainAttributes().getValue("Bundle-SymbolicName") != null;
    }


    @SuppressWarnings("Duplicates")
    private Integer getStartLevel(Manifest m) {
        String name = bundleName(m);
        if (startLevels != null) {
            return startLevels.get(name);
        } else {
            switch (name) {
                case "org.eclipse.core.runtime":
                    return 4;
                case "org.eclipse.equinox.common":
                    return 2;
                case "org.eclipse.equinox.ds":
                    return 2;
                case "org.eclipse.equinox.event":
                    return 2;
                case "org.eclipse.equinox.simpleconfigurator":
                    return 1;
                case "org.eclipse.osgi":
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
    public class Bundle {
        public final String symbolicName;
        public final String version;
        public final Integer startLevel;
        public final Path path;
        public final boolean dirShape;
        public final boolean autoStart;

        public Bundle(Manifest m, Path path) {
            this(bundleName(m), m.getMainAttributes().getValue("Bundle-Version"), getStartLevel(m), path, getStartLevel(m) != null, "dir".equals(m.getMainAttributes().getValue("Eclipse-BundleShape")));
        }

        @SuppressWarnings("Duplicates")
        public Bundle(String symbolicName, String version, Integer startLevel, Path path, boolean autoStart, boolean dirShape) {
            this.symbolicName = symbolicName;
            this.version = version;
            this.startLevel = startLevel == null ? 4 : startLevel;
            this.path = path;
            this.autoStart = autoStart;
            this.dirShape = dirShape;
        }
    }
}
