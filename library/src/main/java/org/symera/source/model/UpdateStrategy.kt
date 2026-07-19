package org.symera.source.model

enum class UpdateStrategy {
    /** Let the host apply its normal update policy. */
    DEFAULT,

    /** The item is expected to change while it remains in the user's library. */
    PERIODIC,

    /** Suitable for completed movies or immutable entries. */
    ONLY_FETCH_ONCE,

    /** Update only after an explicit user action. */
    MANUAL,
}
