import { useEffect, useState } from "react";

/**
 * The value, but only after it has stopped changing for `delayMs`.
 *
 * Typeahead fires a request per keystroke without this. 250ms is the typeahead default: long enough
 * that a fluent typist produces one request per word rather than per letter, short enough that the
 * dropdown still feels attached to the keyboard. Deliberately shorter than the 400ms the result
 * table's column filters use — those gate a refetch of the whole table, a far more expensive thing
 * to do early.
 */
export function useDebouncedValue<T>(value: T, delayMs = 250): T {
    const [debounced, setDebounced] = useState(value);

    useEffect(() => {
        const timer = setTimeout(() => setDebounced(value), delayMs);
        return () => clearTimeout(timer);
    }, [value, delayMs]);

    return debounced;
}
