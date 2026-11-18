package uk.ac.ebi.spot.oxo.backend.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Warms the shared response cache even when Solr starts later than the backend. */
@Component
public class OntologyCatalogWarmup {
    private static final Logger logger = LoggerFactory.getLogger(OntologyCatalogWarmup.class);

    private final OntologyCatalog catalog;
    private final long retryDelayMillis;
    private Thread warmer;

    public OntologyCatalogWarmup(OntologyCatalog catalog,
            @Value("${oxo.ontology-cache.retry-delay-ms:30000}") long retryDelayMillis) {
        if (retryDelayMillis <= 0) throw new IllegalArgumentException("Retry delay must be positive");
        this.catalog = catalog;
        this.retryDelayMillis = retryDelayMillis;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void onApplicationReady() {
        if (warmer != null) return;
        warmer = new Thread(this::warmUntilReady, "ontology-catalog-warmup");
        warmer.setDaemon(true);
        warmer.start();
    }

    private void warmUntilReady() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                long started = System.nanoTime();
                catalog.allOntologies();
                if (catalog.isWarm()) {
                    logger.info("Warmed the ontology catalog in {} ms",
                            (System.nanoTime() - started) / 1_000_000);
                    return;
                }
                logger.warn("Ontology catalog is empty or incomplete; retrying in {} ms",
                        retryDelayMillis);
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
                logger.warn("Could not warm the ontology catalog; retrying in {} ms: {}",
                        retryDelayMillis, failure.toString());
            }
            try {
                Thread.sleep(retryDelayMillis);
            } catch (InterruptedException shutdown) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @PreDestroy
    public synchronized void stop() {
        if (warmer != null) warmer.interrupt();
    }
}
