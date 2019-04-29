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

abstract class Constants {

    public static final String LF = System.getProperty("line.separator");
    public static final String OSGI_FRAMEWORK_BUNDLE_NAME = "org.eclipse.osgi";
    public static final String SIMPLECONFIGURATOR_BUNDLE_NAME = "org.eclipse.equinox.simpleconfigurator";


    private Constants() {
    }


}
