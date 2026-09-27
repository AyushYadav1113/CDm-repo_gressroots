package com.grassroots.cdm.deployment.adapter.java.profile;

/**
 * Strategy for restarting or reloading a Java application upon certificate update.
 */
public enum RestartStrategy {
    /**
     * Managed via systemd service restart (e.g. systemctl restart myapp).
     */
    SYSTEMD_SERVICE,

    /**
     * Graceful reload via systemd or application hook without dropping connections.
     */
    GRACEFUL_RELOAD,

    /**
     * Executed via custom shell script (e.g. /opt/app/bin/restart.sh).
     */
    SCRIPT_RESTART,

    /**
     * Application dynamically detects keystore file modification (e.g. dynamic SSLContext or watcher).
     */
    HOT_RELOAD,

    /**
     * No automated restart is executed (manual operator control or external orchestration).
     */
    NO_RESTART;

    public static RestartStrategy fromString(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return SYSTEMD_SERVICE;
        }
        for (RestartStrategy rs : values()) {
            if (rs.name().equalsIgnoreCase(strategy.trim())) {
                return rs;
            }
        }
        return SYSTEMD_SERVICE;
    }
}
