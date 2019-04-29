/*****************************************************************************
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Set;

import static at.bestsolution.maven.osgi.support.Constants.LF;

class ConfigIniGenerator {

    private final static Logger logger = LoggerFactory.getLogger(ConfigIniGenerator.class);
    private Path configIniTargetPath;

    public ConfigIniGenerator(Path configIniTargetPath) {
        this.configIniTargetPath = configIniTargetPath;
    }

    public Path generateConfigIni(Set<Bundle> bundles, Path bundlesInfoPath) {

        Optional<Bundle> simpleConfigurator = bundles.stream()
                .filter(b -> "org.eclipse.equinox.simpleconfigurator".equals(b.symbolicName)).findFirst();

        Optional<Bundle> equinox = bundles.stream().filter(b -> Constants.OSGI_FRAMEWORK_BUNDLE_NAME.equals(b.symbolicName))
                .findFirst();

        try {
            Files.createDirectories(configIniTargetPath);
        } catch (IOException e1) {
            logger.error("Can not create directories for " + configIniTargetPath);
        }

        if (simpleConfigurator.isPresent()) {
            Path configIni = configIniTargetPath.resolve("config.ini");
            try (BufferedWriter writer = Files.newBufferedWriter(configIni, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

                writer.append("osgi.bundles=" + toReferenceURL(simpleConfigurator.get()));
                writer.append(LF);
                writer.append("osgi.bundles.defaultStartLevel=4");
                writer.append(LF);
                writer.append("osgi.install.area=" + configIniTargetPath.getParent().resolve("install").toUri().toString());
                writer.append(LF);
                writer.append("osgi.framework=" + equinox.get().path.toUri().toString());
                writer.append(LF);
                writer.append("eclipse.p2.data.area=@config.dir/.p2");
                writer.append(LF);
                writer.append("org.eclipse.equinox.simpleconfigurator.configUrl="
                        + bundlesInfoPath.toAbsolutePath().toUri().toString());
                writer.append(LF);
                writer.append("osgi.configuration.cascaded=false");
                writer.append(LF);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException("Only '" + Constants.SIMPLECONFIGURATOR_BUNDLE_NAME + "' is supported");
        }

        return configIniTargetPath;
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
}
