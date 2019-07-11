/*******************************************************************************
 * Copyright (c) 2017 BestSolution.at and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tom Schindl - initial API and implementation
 *******************************************************************************/
package at.bestsolution.maven.osgi.exec;

import at.bestsolution.maven.osgi.support.AppClasspathLauncher;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Manifest;

@Mojo(name="exec-osgi-java", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class MVNJavaOSGiLaunch extends MVNBaseOSGiLaunchPlugin {


	@SuppressWarnings("unused")
	@Parameter(property = "exec.args")
	private String commandlineArgs;

	private AppClasspathLauncher launcher;

	public void execute() throws MojoExecutionException {
        if (!configPath.isEmpty()) {
			File configFile = new File(configPath);
			if (!configFile.exists()) {
				throw new MojoExecutionException("Given path to config file does not exists: " + configPath);

			} else {
				System.setProperty(AppClasspathLauncher.SYSPROP_CONFIG_FILE_PATH, configFile.getAbsolutePath());
			}
        }

        launcher = new AppClasspathLauncher(splitCommandLineArgs(commandlineArgs));
        launcher.execute(this::findAllBundlesFromProject);
    }

    private List<String> splitCommandLineArgs(String commandlineArgs) {
        if (commandlineArgs == null || commandlineArgs.isEmpty()) {
            return new ArrayList<>();
        }

        return Arrays.asList(commandlineArgs.split(" "));
    }


	@Override
	protected Integer getStartLevel(Manifest m) {
		return launcher.getStartLevel(m);
	}
}
