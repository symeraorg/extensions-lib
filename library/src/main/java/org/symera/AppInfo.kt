package org.symera

/**
 * Information about the installed Symera host app, not the extension.
 *
 * Extensions can use this for temporary compatibility checks, header values, logging, or
 * diagnostics. Runtime values are provided by the host application.
 */
object AppInfo {
    /**
     * Version code of the host application.
     *
     * This value can differ between forks, so extension behavior should not rely on exact values
     * unless handling a known host-specific compatibility issue.
     */
    fun getVersionCode(): Int = throw Exception("Stub!")

    /**
     * Version name of the host application.
     *
     * This value can differ between forks, so extension behavior should not rely on exact values
     * unless handling a known host-specific compatibility issue.
     */
    fun getVersionName(): String = throw Exception("Stub!")
}
