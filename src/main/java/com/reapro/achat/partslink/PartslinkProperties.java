package com.reapro.achat.partslink;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "partslink")
public class PartslinkProperties {

    private boolean enabled = false;
    private String baseUrl = "https://www.partslink24.com";
    private String loginUrl = "https://www.partslink24.com/partslink24/user/login.do";
    private String username = "";
    private String password = "";
    private String companyId = "";
    private String viewerBaseUrl = "http://localhost:7900/?autoconnect=1&resize=scale";
    private String remoteWebDriverUrl = "http://localhost:4444/wd/hub";
    private long sessionTimeoutMinutes = 60;
    private boolean autoReloginEnabled = true;
    private long viewerTokenTtlMinutes = 15;
    private String imageStoragePath = "uploads/partslink";
    private long lockAcquireTimeoutSeconds = 20;
    private long startupTimeoutSeconds = 120;
    private long seleniumReadinessTimeoutSeconds = 15;

    /**
     * Pool de sessions navigateur isolées (refonte multi-utilisateurs).
     * Remplace l'ancien WebDriver global unique.
     */
    private final Pool pool = new Pool();

    public Pool getPool() {
        return pool;
    }

    public static class Pool {
        /** true = utilise le pool isolé (cible). false = compat/diagnostic uniquement. */
        private boolean enabled = true;
        /** Nombre maximum de sessions navigateur simultanées (= limite licence). */
        private int maxSessions = 5;
        /** Délai max d'attente d'un slot libre avant de signaler "occupé". */
        private long acquireTimeoutSeconds = 60;
        /** Durée d'inactivité après laquelle un slot idle est recyclé. */
        private long idleTimeoutMinutes = 30;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getMaxSessions() { return maxSessions; }
        public void setMaxSessions(int maxSessions) { this.maxSessions = maxSessions; }

        public long getAcquireTimeoutSeconds() { return acquireTimeoutSeconds; }
        public void setAcquireTimeoutSeconds(long acquireTimeoutSeconds) { this.acquireTimeoutSeconds = acquireTimeoutSeconds; }

        public long getIdleTimeoutMinutes() { return idleTimeoutMinutes; }
        public void setIdleTimeoutMinutes(long idleTimeoutMinutes) { this.idleTimeoutMinutes = idleTimeoutMinutes; }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getViewerBaseUrl() {
        return viewerBaseUrl;
    }

    public void setViewerBaseUrl(String viewerBaseUrl) {
        this.viewerBaseUrl = viewerBaseUrl;
    }

    public String getRemoteWebDriverUrl() {
        return remoteWebDriverUrl;
    }

    public void setRemoteWebDriverUrl(String remoteWebDriverUrl) {
        this.remoteWebDriverUrl = remoteWebDriverUrl;
    }

    public long getSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }

    public void setSessionTimeoutMinutes(long sessionTimeoutMinutes) {
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
    }

    public boolean isAutoReloginEnabled() {
        return autoReloginEnabled;
    }

    public void setAutoReloginEnabled(boolean autoReloginEnabled) {
        this.autoReloginEnabled = autoReloginEnabled;
    }

    public long getViewerTokenTtlMinutes() {
        return viewerTokenTtlMinutes;
    }

    public void setViewerTokenTtlMinutes(long viewerTokenTtlMinutes) {
        this.viewerTokenTtlMinutes = viewerTokenTtlMinutes;
    }

    public String getImageStoragePath() {
        return imageStoragePath;
    }

    public void setImageStoragePath(String imageStoragePath) {
        this.imageStoragePath = imageStoragePath;
    }

    public long getLockAcquireTimeoutSeconds() {
        return lockAcquireTimeoutSeconds;
    }

    public void setLockAcquireTimeoutSeconds(long lockAcquireTimeoutSeconds) {
        this.lockAcquireTimeoutSeconds = lockAcquireTimeoutSeconds;
    }

    public long getStartupTimeoutSeconds() {
        return startupTimeoutSeconds;
    }

    public void setStartupTimeoutSeconds(long startupTimeoutSeconds) {
        this.startupTimeoutSeconds = startupTimeoutSeconds;
    }

    public long getSeleniumReadinessTimeoutSeconds() {
        return seleniumReadinessTimeoutSeconds;
    }

    public void setSeleniumReadinessTimeoutSeconds(long seleniumReadinessTimeoutSeconds) {
        this.seleniumReadinessTimeoutSeconds = seleniumReadinessTimeoutSeconds;
    }
}
