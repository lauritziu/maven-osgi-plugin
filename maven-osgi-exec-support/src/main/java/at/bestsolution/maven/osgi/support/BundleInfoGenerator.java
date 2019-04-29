package at.bestsolution.maven.osgi.support;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.zip.ZipFile;

class BundleInfoGenerator {

    private Path configTargetPath;

    public BundleInfoGenerator(Path configTargetPath) {

        this.configTargetPath = configTargetPath;
    }

    public Path generateBundlesInfo(Set<Bundle> bundles) {
        Path bundleInfo = configTargetPath.resolve("org.eclipse.equinox.simpleconfigurator").resolve("bundles.info");

        try {
            Files.createDirectories(bundleInfo.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(bundleInfo, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.append("#encoding=UTF-8");
            writer.append(Constants.LF);
            writer.append("#version=1");
            writer.append(Constants.LF);

            for (Bundle b : bundles) {
                if ("org.eclipse.osgi".equals(b.symbolicName)) {
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
