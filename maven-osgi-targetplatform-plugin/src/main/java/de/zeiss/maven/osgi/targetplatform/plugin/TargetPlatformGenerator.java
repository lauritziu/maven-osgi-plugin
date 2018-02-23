package de.zeiss.maven.osgi.targetplatform.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;

import de.zeiss.maven.osgi.targetplatform.plugin.internal.DefaultParameterProvider;
import de.zeiss.maven.osgi.targetplatform.plugin.internal.MainApplication;

@Mojo(name = "provide-target-dependencies", defaultPhase = LifecyclePhase.VALIDATE, requiresDependencyResolution = ResolutionScope.COMPILE)
public class TargetPlatformGenerator extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(required = true, readonly = true)
    private String outputFile;

    @Parameter(required = true, readonly = true)
    private String additionalDependenciesFile;

    @Parameter(required = true, readonly = true)
    private String whitelistFile;

    @Parameter(required = true, readonly = true)
    private String featureFile;

    @Parameter(required = true, readonly = true)
    private String targetFeatureJarPrefix;

    @Parameter(defaultValue = "site.xml", required = false, readonly = true)
    private String efxclipseSite;

    @Parameter(required = true, readonly = true)
    private String efxclipseGenericRepositoryUrl;

    @Component
    private Logger logger;

    @Component
    private MavenSession session;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        List<Dependency> generatingDependencies = this.project.getDependencies();

        File outputFileO = MainApplication.run(new DefaultParameterProvider(project.getVersion(), project.getArtifactId(), project.getGroupId(), outputFile,
                additionalDependenciesFile, whitelistFile, featureFile, targetFeatureJarPrefix, efxclipseSite, efxclipseGenericRepositoryUrl), this.project);

        this.project.setPomFile(outputFileO);
        
        

        try {
            List<MavenProject> allProjects = session.getAllProjects();

            allProjects.stream()
                    .filter(p -> p.getArtifactId() != null && (p.getArtifactId().equals("sample.mvn.app") || p.getArtifactId().equals("sample.mvn.product")))
                    .forEach(p -> {
                        p.getProjectReferences().remove(project);
                        List<org.apache.maven.model.Dependency> newDependencies = new ArrayList<>();
                        newDependencies.addAll(this.project.getDependencies());
                        newDependencies.addAll(p.getDependencies());
                        newDependencies = newDependencies.stream()
                                .filter(np -> generatingDependencies.stream().filter(gp -> gp.getArtifactId().equals(np.getArtifactId())).count() == 0)
                                .collect(Collectors.toList());
                        newDependencies.removeAll(generatingDependencies);
                        p.setDependencies(newDependencies);
                    });
            
            
            List<MavenProject> newProje = new ArrayList<>();
            newProje.addAll(session.getProjects());
            newProje.remove(this.project);
            session.setProjects(newProje);
            session.setProjectDependencyGraph(null);
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }

    }

}