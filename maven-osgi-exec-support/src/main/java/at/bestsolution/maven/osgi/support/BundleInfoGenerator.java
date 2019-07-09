/*******************************************************************************
 * Copyright (c) 2017 BestSolution.at and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Tom Schindl - initial Code
 *      Thomas Fahrmeyer - refactored to its own class
 *******************************************************************************/
package at.bestsolution.maven.osgi.support;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * Generates the bundle.info file referencing all bundles to be used.
 *
 * Startlevels are extracted from Manifest.mf
 */
class BundleInfoGenerator {

    private Path configTargetPath;

    /**
     *
     * @param configTargetPath pointing to an existing directory. This is a base directory for the directories created by
     *                         this class
     * @throws IllegalArgumentException for a path not pointing to a directory or to a resource does not exit.
     */
    public BundleInfoGenerator(Path configTargetPath) {
        Objects.nonNull(configTargetPath);
        if (!configTargetPath.toFile().exists() || !configTargetPath.toFile().isDirectory()) {
            throw new IllegalArgumentException("Path " + configTargetPath + " does not exists or is no directory");
        }

        this.configTargetPath = configTargetPath;
    }

    public Path generateBundlesInfo(Set<Bundle> bundles) {
        Path bundleInfoDir = configTargetPath.resolve(Constants.SIMPLECONFIGURATOR_BUNDLE_NAME);
        createConfigPathIfNecessary(bundleInfoDir);

        Path bundleInfo = configTargetPath.resolve(Constants.SIMPLECONFIGURATOR_BUNDLE_NAME).resolve("bundles.info");

        try (BufferedWriter writer = Files.newBufferedWriter(bundleInfo, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.append("#encoding=UTF-8");
            writer.append(Constants.LF);
            writer.append("#version=1");
            writer.append(Constants.LF);

            for (Bundle b : bundles) {
                if (Constants.OSGI_FRAMEWORK_BUNDLE_NAME.equals(b.symbolicName)) {
                    continue;
                }

                writer.append(b.symbolicName);
                writer.append("," + b.version);
                writer.append(",file:" + generateLocalPath(b, configTargetPath.resolve(".explode")).toString());
                writer.append("," + b.startLevel); // Start Level
                writer.append("," + b.autoStart); // Auto-Start
                writer.append(Constants.LF);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return bundleInfo;
    }

    private void createConfigPathIfNecessary(Path configPath) throws ConfigurationException {
        try {
            Files.createDirectories(configPath);

        } catch (IOException e) {
            throw new ConfigurationException("Path " + configPath + " could not be created.", e);
        }
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
}
