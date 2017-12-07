package de.zeiss.maven.extension;

import java.io.File;
import java.util.stream.Stream;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Repository;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "TychoMavenLifecycleListener")
public class DemoLifeCycleParticipant  extends AbstractMavenLifecycleParticipant {

    @Requirement
    private Logger logger;

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        super.afterProjectsRead(session);

        Repository p2Repo = null;

        for (Repository repo : session.getCurrentProject().getRepositories()) {
            if ("p2".equals(repo.getLayout())) {
                p2Repo = repo;
                break;
            }
        }

        if (p2Repo != null) {
            if (!repositoryExists(p2Repo)) {
                createRepository(p2Repo);
            }

        }

    }
    private boolean repositoryExists(Repository p2Repo) {
        File file = new File(p2Repo.getUrl());
        return file.exists();
    }

    private void createRepository(Repository p2Repo) {
        logger.info("##### REPO does not exist and will be created");
    }

}