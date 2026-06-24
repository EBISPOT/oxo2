package uk.ac.ebi.spot.oxo.inferences.nemo;

import uk.ac.ebi.spot.oxo.inferences.nemo.model.NemoInferences;

import java.util.Optional;

/**
 * Resolves an inferred conclusion to the {@link NemoInferences.NemoInference} that derived it.
 *
 * <p>This is the single seam that lets {@code NemoHelper} walk an inference chain without caring
 * whether the chains live in heap or on disk. The cross-set closure no longer fits in memory
 * (ADR-0018), so the production implementation is {@link OnDiskChainStore}; the per-node build
 * logic in {@code NemoHelper} is identical regardless of which backing is used.
 */
public interface InferenceLookup {

    /**
     * @return the inference whose {@code conclusion} equals {@code conclusion}, or empty if the
     *         conclusion is not a derived intermediate (e.g. it is an asserted leaf, resolved from
     *         Solr rather than from the chain store).
     */
    Optional<NemoInferences.NemoInference> find(String conclusion);
}
