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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Provides utility methods to verifies {@link Artifact}'s whether they are OSGI bundles or not.
 */
public final class OsgiBundleInfo {

    private static final Attributes.Name MANIFEST_SYMBOLIC_NAME = new Attributes.Name("Bundle-SymbolicName");
    private static final Logger logger = LoggerFactory.getLogger(OsgiBundleInfo.class);

    public static String bundleName(Manifest m) {
        String name = m.getMainAttributes().getValue("Bundle-SymbolicName");
        return name.split(";")[0];
    }

    public Optional<Manifest> getManifest(Path pathToArtifact) {

        Optional<Manifest> manifest;

        if (Files.isDirectory(pathToArtifact)) {
            Path mf = pathToArtifact.resolve("META-INF").resolve("MANIFEST.MF");
            if( ! Files.exists(mf) ) {
                return Optional.empty();
            }
            try (InputStream in = Files
                    .newInputStream(mf)) {
                return Optional.of(new Manifest(in));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } else {
            try (JarFile f = new JarFile(pathToArtifact.toFile())) {
                manifest = Optional.ofNullable(f.getManifest());

            } catch (IOException e) {
                logger.error("Path " + pathToArtifact + " points to an artifact no jar file can be created from.", e);
                manifest = Optional.empty();
            }
        }

        return manifest;
    }

    public boolean isBundle(Manifest m) {
        Objects.nonNull(m);
        return m.getMainAttributes().getValue(MANIFEST_SYMBOLIC_NAME) != null;
    }
}
