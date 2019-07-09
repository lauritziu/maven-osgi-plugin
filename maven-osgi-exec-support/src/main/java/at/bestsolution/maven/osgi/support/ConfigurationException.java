package at.bestsolution.maven.osgi.support;

/**
 * Thrown if some problems reading the configuration from yaml file has occured.
 */
public class ConfigurationException extends RuntimeException {
    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
