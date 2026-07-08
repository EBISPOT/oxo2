/**
 * Split a typed, pasted or uploaded term list into terms, dropping blanks.
 *
 * Newlines and commas both separate — the entry box accepts either, and so does an uploaded .csv.
 * Terms are trimmed, because a trailing space in a CURIE is never meant.
 */
export function parseTerms(text: string): string[] {
    return text.split(/[\n\r,]+/).map((term) => term.trim()).filter((term) => term !== "");
}
