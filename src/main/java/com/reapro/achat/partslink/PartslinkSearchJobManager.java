package com.reapro.achat.partslink;

import com.reapro.achat.entities.primary.PartslinkVehicle;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * File de jobs de recherche VIN, refondue pour le multi-utilisateurs.
 *
 * <ul>
 *   <li><b>Isolation utilisateur</b> : chaque job porte son {@code ownerUserId} ;
 *       {@code jobId} seul ne suffit pas — la lecture vérifie l'appartenance.</li>
 *   <li><b>États</b> : {@code QUEUED → RUNNING → COMPLETED|FAILED|CANCELLED} (+ position en file).</li>
 *   <li><b>Cache-first</b> : un cache hit ne réserve aucune session navigateur.</li>
 *   <li><b>Pool</b> : un cache miss réserve un slot isolé via {@link PartslinkSessionPool}.</li>
 *   <li><b>Concurrence bornée</b> : l'executor a autant de threads que de slots du pool.</li>
 * </ul>
 *
 * <p>Valeurs de statut conservées telles quelles pour ne pas casser le frontend existant
 * ({@code COMPLETED}/{@code FAILED}). Mapping logique : {@code DONE↔COMPLETED}, {@code ERROR↔FAILED}.</p>
 *
 * <p>NB : les jobs sont en mémoire (clé = jobId, valeur scopée par owner). La persistance en
 * base ({@code partslink_job}) est documentée comme amélioration suivante ; l'isolation user est
 * déjà garantie ici sans migration DB.</p>
 */
@Service
@Slf4j
public class PartslinkSearchJobManager {

    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED"; // DONE
    public static final String FAILED = "FAILED";       // ERROR
    public static final String CANCELLED = "CANCELLED";

    private static final int MAX_BUSY_REQUEUE = 5;

    private final PartslinkSessionPool sessionPool;
    private final PartslinkScraperService scraperService;
    private final PartslinkCacheService cacheService;
    private final PartslinkLoginHelper loginHelper;
    private final ExecutorService executorService;
    private final Map<String, JobProgress> jobs = new ConcurrentHashMap<>();

    public PartslinkSearchJobManager(PartslinkSessionPool sessionPool,
                                     PartslinkScraperService scraperService,
                                     PartslinkCacheService cacheService,
                                     PartslinkLoginHelper loginHelper,
                                     PartslinkProperties properties) {
        this.sessionPool = sessionPool;
        this.scraperService = scraperService;
        this.cacheService = cacheService;
        this.loginHelper = loginHelper;
        int threads = Math.max(1, properties.getPool().getMaxSessions());
        this.executorService = Executors.newFixedThreadPool(threads);
    }

    @Data
    @Builder
    public static class JobProgress {
        private String jobId;
        private String ownerUserId;
        private String vin;
        private String brand;
        private String status;
        private String step;
        private boolean completed;
        private String error;
        private int busyRequeues;
        private PartslinkScraperService.ScrapedVehicleDetails vehicleDetails;
        private long createdAt;
        private Long startedAt;
        private Long finishedAt;
    }

    /** Soumet une recherche VIN pour l'utilisateur courant. Retourne le jobId. */
    public String submitVinSearch(String vin, String brand, String ownerUserId) {
        if (!StringUtils.hasText(ownerUserId)) {
            throw new IllegalArgumentException("Utilisateur courant requis pour soumettre un job Partslink.");
        }
        cleanupOldJobs();

        String jobId = "JOB-" + UUID.randomUUID();
        JobProgress progress = JobProgress.builder()
                .jobId(jobId)
                .ownerUserId(ownerUserId)
                .vin(vin)
                .brand(brand)
                .status(QUEUED)
                .step("En file d'attente...")
                .completed(false)
                .createdAt(System.currentTimeMillis())
                .build();
        jobs.put(jobId, progress);
        log.info("[JobManager] Job={} soumis (owner={}, VIN={}, brand={})", jobId, ownerUserId, vin, brand);

        executorService.submit(() -> runJob(progress));
        return jobId;
    }

    /**
     * Lecture scopée : retourne le job uniquement s'il appartient à l'utilisateur courant.
     * Vide si introuvable OU si appartient à un autre user (aucune fuite d'existence).
     */
    public Optional<JobProgress> getJobForOwner(String jobId, String ownerUserId) {
        JobProgress p = jobs.get(jobId);
        if (p == null || !StringUtils.hasText(ownerUserId) || !ownerUserId.equals(p.getOwnerUserId())) {
            return Optional.empty();
        }
        return Optional.of(p);
    }

    /** Annule un job en file (QUEUED). Un job déjà RUNNING n'est pas interrompu. */
    public boolean cancelJob(String jobId, String ownerUserId) {
        Optional<JobProgress> opt = getJobForOwner(jobId, ownerUserId);
        if (opt.isEmpty()) {
            return false;
        }
        JobProgress p = opt.get();
        synchronized (p) {
            if (QUEUED.equals(p.getStatus())) {
                p.setStatus(CANCELLED);
                p.setStep("Annulé par l'utilisateur.");
                p.setCompleted(true);
                p.setFinishedAt(System.currentTimeMillis());
                return true;
            }
        }
        return false;
    }

    /** Position dans la file (1 = prochain). 0 si le job n'est pas en file. */
    public int queuePosition(JobProgress job) {
        if (job == null || !QUEUED.equals(job.getStatus())) {
            return 0;
        }
        long before = jobs.values().stream()
                .filter(j -> QUEUED.equals(j.getStatus()))
                .filter(j -> j.getCreatedAt() < job.getCreatedAt())
                .count();
        return (int) before + 1;
    }

    private void runJob(JobProgress progress) {
        synchronized (progress) {
            if (CANCELLED.equals(progress.getStatus())) {
                return; // annulé avant démarrage
            }
            progress.setStatus(RUNNING);
            progress.setStartedAt(System.currentTimeMillis());
            progress.setStep("Démarrage de la recherche...");
        }
        log.info("[JobManager] Job={} démarré (owner={}, VIN={})",
                progress.getJobId(), progress.getOwnerUserId(), progress.getVin());

        try {
            // Cache-first : un hit ne réserve AUCUNE session navigateur.
            Optional<PartslinkVehicle> cached = cacheService.findVehicleByVin(progress.getVin());
            if (cached.isPresent()) {
                log.info("[JobManager] Job={} cache hit (aucun slot réservé).", progress.getJobId());
                progress.setVehicleDetails(fromCache(cached.get()));
                markCompleted(progress, "Chargé depuis le cache local.");
                return;
            }

            Consumer<String> stepListener = step -> {
                progress.setStep(step);
                log.info("[JobManager] Job={} | {}", progress.getJobId(), step);
            };

            progress.setStep("Réservation d'une session Partslink isolée...");
            PartslinkScraperService.ScrapedVehicleDetails vehicleDetails = sessionPool.withLeasedDriver(driver ->
                    scraperService.identifyVehicleAndGroups(driver, progress.getVin(), progress.getBrand(), stepListener));

            try {
                cacheService.saveVehicleAndGroups(vehicleDetails);
            } catch (Exception cacheEx) {
                log.error("[JobManager] Job={} échec de mise en cache: {}", progress.getJobId(), cacheEx.getMessage());
            }

            progress.setVehicleDetails(vehicleDetails);
            markCompleted(progress, "Recherche terminée avec succès.");
        } catch (PartslinkPoolBusyException busy) {
            // Tous les slots occupés : on remet le job en file plutôt que de le faire échouer.
            if (progress.getBusyRequeues() < MAX_BUSY_REQUEUE) {
                progress.setBusyRequeues(progress.getBusyRequeues() + 1);
                progress.setStatus(QUEUED);
                progress.setStep("Toutes les sessions sont occupées, en attente d'un slot...");
                progress.setStartedAt(null);
                log.info("[JobManager] Job={} remis en file (slots occupés, tentative {}).",
                        progress.getJobId(), progress.getBusyRequeues());
                executorService.submit(() -> runJob(progress));
            } else {
                markFailed(progress, "Sessions Partslink saturées, réessayez plus tard.");
            }
        } catch (Exception ex) {
            log.error("[JobManager] Job={} échec pour VIN={}", progress.getJobId(), progress.getVin(), ex);
            markFailed(progress, loginHelper.sanitize(
                    ex.getMessage() != null ? ex.getMessage() : "Une erreur inconnue est survenue."));
        }
    }

    private PartslinkScraperService.ScrapedVehicleDetails fromCache(PartslinkVehicle entity) {
        var groups = cacheService.findGroupsByVehicle(entity).stream()
                .map(g -> new PartslinkScraperService.ScrapedGroup(g.getCode(), g.getName()))
                .toList();
        return new PartslinkScraperService.ScrapedVehicleDetails(
                entity.getVin(), entity.getModelDesignation(), entity.getProductionDate(),
                entity.getColor(), entity.getUpholstery(), entity.getTransmission(),
                entity.getModelCode(), entity.getModel(), entity.getBrandCode(), groups);
    }

    private void markCompleted(JobProgress progress, String message) {
        progress.setStatus(COMPLETED);
        progress.setStep(message);
        progress.setCompleted(true);
        progress.setFinishedAt(System.currentTimeMillis());
    }

    private void markFailed(JobProgress progress, String message) {
        progress.setStatus(FAILED);
        progress.setError(message);
        progress.setStep("Une erreur est survenue.");
        progress.setCompleted(true);
        progress.setFinishedAt(System.currentTimeMillis());
    }

    private void cleanupOldJobs() {
        long oneHourAgo = System.currentTimeMillis() - 3600000;
        jobs.entrySet().removeIf(e -> e.getValue().isCompleted() && e.getValue().getCreatedAt() < oneHourAgo);
    }
}
