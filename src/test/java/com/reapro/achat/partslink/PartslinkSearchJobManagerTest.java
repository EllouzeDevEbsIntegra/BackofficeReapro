package com.reapro.achat.partslink;

import com.reapro.achat.entities.primary.PartslinkVehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests d'isolation utilisateur, de file d'attente et de cache-first du gestionnaire de jobs.
 * Aucun Selenium réel : le pool est mocké.
 */
class PartslinkSearchJobManagerTest {

    private static final String VIN_A = "WBA11111111111111";
    private static final String VIN_B = "WDD22222222222222";

    private PartslinkSessionPool pool;
    private PartslinkScraperService scraper;
    private PartslinkCacheService cache;
    private PartslinkLoginHelper loginHelper;
    private PartslinkProperties properties;

    @BeforeEach
    void setUp() {
        pool = mock(PartslinkSessionPool.class);
        scraper = mock(PartslinkScraperService.class);
        cache = mock(PartslinkCacheService.class);
        properties = new PartslinkProperties();
        properties.setUsername("admin");
        properties.setPassword("SECRET_PWD_123");
        properties.getPool().setMaxSessions(1); // executor mono-thread → file déterministe
        loginHelper = new PartslinkLoginHelper(properties); // réel : sanitize() fonctionne
    }

    private PartslinkSearchJobManager newManager() {
        return new PartslinkSearchJobManager(pool, scraper, cache, loginHelper, properties);
    }

    private PartslinkScraperService.ScrapedVehicleDetails details(String vin) {
        return new PartslinkScraperService.ScrapedVehicleDetails(
                vin, "Model", "2020", "Noir", "Cuir", "Auto", "MC", "M", "bmw", List.of());
    }

    private PartslinkSearchJobManager.JobProgress awaitTerminal(PartslinkSearchJobManager mgr, String jobId, String user) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            PartslinkSearchJobManager.JobProgress p = mgr.getJobForOwner(jobId, user).orElse(null);
            if (p != null && p.isCompleted()) {
                return p;
            }
            sleep(20);
        }
        fail("Job " + jobId + " non terminé dans le délai imparti");
        return null;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // --- Isolation utilisateur ---

    @Test
    void userCannotReadAnotherUsersJob() {
        when(cache.findVehicleByVin(any())).thenReturn(Optional.empty());
        doReturn(details(VIN_A)).when(pool).withLeasedDriver(any());
        PartslinkSearchJobManager mgr = newManager();

        String jobId = mgr.submitVinSearch(VIN_A, "bmw", "userA");

        assertThat(mgr.getJobForOwner(jobId, "userA")).isPresent();
        assertThat(mgr.getJobForOwner(jobId, "userB")).isEmpty(); // isolation stricte
    }

    @Test
    void twoJobsKeepSeparateContexts() {
        // cache hit pour finir vite, sans pool
        PartslinkVehicle vA = PartslinkVehicle.builder().vin(VIN_A).model("A").build();
        PartslinkVehicle vB = PartslinkVehicle.builder().vin(VIN_B).model("B").build();
        when(cache.findVehicleByVin(VIN_A)).thenReturn(Optional.of(vA));
        when(cache.findVehicleByVin(VIN_B)).thenReturn(Optional.of(vB));
        when(cache.findGroupsByVehicle(any())).thenReturn(List.of());
        PartslinkSearchJobManager mgr = newManager();

        String jobA = mgr.submitVinSearch(VIN_A, "bmw", "userA");
        String jobB = mgr.submitVinSearch(VIN_B, "mercedes", "userB");

        assertThat(mgr.getJobForOwner(jobA, "userA").orElseThrow().getVin()).isEqualTo(VIN_A);
        assertThat(mgr.getJobForOwner(jobB, "userB").orElseThrow().getVin()).isEqualTo(VIN_B);
        // aucun mélange de VIN entre utilisateurs
        assertThat(mgr.getJobForOwner(jobA, "userB")).isEmpty();
        assertThat(mgr.getJobForOwner(jobB, "userA")).isEmpty();
    }

    // --- Cache-first ---

    @Test
    void cacheHit_doesNotReserveBrowserSession() {
        PartslinkVehicle v = PartslinkVehicle.builder().vin(VIN_A).model("A").build();
        when(cache.findVehicleByVin(VIN_A)).thenReturn(Optional.of(v));
        when(cache.findGroupsByVehicle(v)).thenReturn(List.of());
        PartslinkSearchJobManager mgr = newManager();

        String jobId = mgr.submitVinSearch(VIN_A, "bmw", "userA");
        PartslinkSearchJobManager.JobProgress p = awaitTerminal(mgr, jobId, "userA");

        assertThat(p.getStatus()).isEqualTo(PartslinkSearchJobManager.COMPLETED);
        verify(pool, never()).withLeasedDriver(any()); // AUCUN slot réservé sur cache hit
    }

    @Test
    void cacheMiss_reservesBrowserSession() {
        when(cache.findVehicleByVin(any())).thenReturn(Optional.empty());
        doReturn(details(VIN_A)).when(pool).withLeasedDriver(any());
        PartslinkSearchJobManager mgr = newManager();

        String jobId = mgr.submitVinSearch(VIN_A, "bmw", "userA");
        PartslinkSearchJobManager.JobProgress p = awaitTerminal(mgr, jobId, "userA");

        assertThat(p.getStatus()).isEqualTo(PartslinkSearchJobManager.COMPLETED);
        verify(pool).withLeasedDriver(any());              // un slot réservé sur cache miss
        verify(cache).saveVehicleAndGroups(any());          // résultat persisté en cache
    }

    // --- Erreur login Partslink → ERROR propre, sans secret ---

    @Test
    void loginError_yieldsFailedStatus_withoutLeakingPassword() {
        when(cache.findVehicleByVin(any())).thenReturn(Optional.empty());
        doThrow(new PartslinkLoginException("login failed for user admin with password SECRET_PWD_123"))
                .when(pool).withLeasedDriver(any());
        PartslinkSearchJobManager mgr = newManager();

        String jobId = mgr.submitVinSearch(VIN_A, "bmw", "userA");
        PartslinkSearchJobManager.JobProgress p = awaitTerminal(mgr, jobId, "userA");

        assertThat(p.getStatus()).isEqualTo(PartslinkSearchJobManager.FAILED);
        assertThat(p.getError()).doesNotContain("SECRET_PWD_123"); // aucun password dans l'erreur
        assertThat(p.getError()).contains("****");
    }

    // --- File d'attente : 6e (ici 2e avec pool=1) reste QUEUED ---

    @Test
    void whenWorkerBusy_secondJobStaysQueued() throws InterruptedException {
        when(cache.findVehicleByVin(any())).thenReturn(Optional.empty());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return details(VIN_A);
        }).when(pool).withLeasedDriver(any());
        PartslinkSearchJobManager mgr = newManager();

        String job1 = mgr.submitVinSearch(VIN_A, "bmw", "userA");
        assertThat(started.await(3, TimeUnit.SECONDS)).isTrue(); // job1 occupe l'unique worker

        String job2 = mgr.submitVinSearch(VIN_B, "mercedes", "userA");
        sleep(150);

        PartslinkSearchJobManager.JobProgress p2 = mgr.getJobForOwner(job2, "userA").orElseThrow();
        assertThat(p2.getStatus()).isEqualTo(PartslinkSearchJobManager.QUEUED);
        assertThat(mgr.queuePosition(p2)).isGreaterThanOrEqualTo(1);

        release.countDown();
        awaitTerminal(mgr, job1, "userA");
        awaitTerminal(mgr, job2, "userA");
    }

    @Test
    void queuedJobCanBeCancelled_byOwnerOnly() throws InterruptedException {
        when(cache.findVehicleByVin(any())).thenReturn(Optional.empty());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return details(VIN_A);
        }).when(pool).withLeasedDriver(any());
        PartslinkSearchJobManager mgr = newManager();

        String job1 = mgr.submitVinSearch(VIN_A, "bmw", "userA");
        assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
        String job2 = mgr.submitVinSearch(VIN_B, "bmw", "userA");
        sleep(100);

        assertThat(mgr.cancelJob(job2, "userB")).isFalse(); // pas le propriétaire
        assertThat(mgr.cancelJob(job2, "userA")).isTrue();  // annulé par le propriétaire
        assertThat(mgr.getJobForOwner(job2, "userA").orElseThrow().getStatus())
                .isEqualTo(PartslinkSearchJobManager.CANCELLED);

        release.countDown();
        awaitTerminal(mgr, job1, "userA");
    }

    @Test
    void submitRequiresOwner() {
        PartslinkSearchJobManager mgr = newManager();
        try {
            mgr.submitVinSearch(VIN_A, "bmw", "  ");
            fail("doit refuser un owner vide");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
