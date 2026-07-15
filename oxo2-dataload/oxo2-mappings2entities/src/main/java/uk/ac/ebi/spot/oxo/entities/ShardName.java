package uk.ac.ebi.spot.oxo.entities;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * A filesystem-safe, collision-free name for a prefix shard's JSON file.
 *
 * <p>A CURIE prefix is a Solr query key, not a filename. It may contain any character except a colon
 * and whitespace, so a malformed-IRI-as-CURIE such as {@code srao_/SRAO:0000006} — a colon with no
 * {@code "//"}, which {@code StringUtils.isCurie} therefore accepts as a CURIE — resolves to the
 * prefix {@code SRAO_/SRAO}. That is a perfectly valid Solr facet value, and it is exactly what the
 * mappings index already stores in {@code subject_prefix}/{@code object_prefix} (ADR-0031), so this
 * stage cannot refuse it. But its slashes make it an unsafe filename, and {@code mappings2entities}
 * names each shard's JSON after its prefix.
 *
 * <p>This maps a prefix to a safe filename <b>injectively</b>: two distinct prefixes never share a
 * name. That is a correctness requirement, not a nicety — two shards publishing to the same file
 * would clobber each other under {@code publishDir}, silently dropping one ontology's entities from
 * the typeahead. Safe prefixes — the overwhelming majority ({@code MONDO}, {@code DOID}, the
 * {@code __none__} sentinel) — are used verbatim, so shard files stay readable and the Nextflow
 * {@code -resume} cache stays stable. Anything else is hex-encoded under a reserved {@code enc-}
 * scheme; hex over the UTF-8 bytes is injective, and the verbatim branch excludes any prefix that
 * already looks like an encoded name, so the two namespaces are disjoint.
 */
final class ShardName {

    /** FilenameGuard's safe-filename alphabet. Keep in sync with {@code lib/FilenameGuard.groovy}. */
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]+");

    /**
     * The shape an encoded name takes: {@code enc-} followed by only lowercase hex. A prefix that
     * already matches this is encoded rather than used verbatim, so a real prefix can never collide
     * with the name minted for some other, unsafe prefix.
     */
    private static final Pattern ENCODED = Pattern.compile("enc-[0-9a-f]*");

    private ShardName() {
    }

    /** The safe, unique filename stem (no extension) for {@code prefix}'s shard. */
    static String of(String prefix) {
        boolean usableVerbatim = SAFE.matcher(prefix).matches()
                && !prefix.startsWith(".")
                && !prefix.startsWith("-")
                && !ENCODED.matcher(prefix).matches();
        return usableVerbatim ? prefix : encode(prefix);
    }

    private static String encode(String prefix) {
        StringBuilder encoded = new StringBuilder("enc-");
        for (byte octet : prefix.getBytes(StandardCharsets.UTF_8)) {
            encoded.append(Character.forDigit((octet >> 4) & 0xF, 16));
            encoded.append(Character.forDigit(octet & 0xF, 16));
        }
        return encoded.toString();
    }
}
