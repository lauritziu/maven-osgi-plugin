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

import java.nio.file.Path;
import java.util.jar.Manifest;

public class Bundle {
    public final String symbolicName;
    public final String version;
    public final Integer startLevel;
    public final Path path;
    public final boolean dirShape;
    public final boolean autoStart;

    public Bundle(Manifest m, Path path, Integer startLevel) {
        this(OsgiBundleInfo.bundleName(m), m.getMainAttributes().getValue("Bundle-Version"), startLevel, path, startLevel != null, "dir".equals(m.getMainAttributes().getValue("Eclipse-BundleShape")));
    }

    public Bundle(String symbolicName, String version, Integer startLevel, Path path, boolean autoStart, boolean dirShape) {
        this.symbolicName = symbolicName;
        this.version = version;
        this.startLevel = startLevel == null ? 4 : startLevel;
        this.path = path;
        this.autoStart = autoStart;
        this.dirShape = dirShape;
    }
}
