package kabuki

/** Desktop: resolved from the `os.name` system property at runtime. */
public actual fun detectOs(): Os {
    val name = System.getProperty("os.name")?.lowercase().orEmpty()
    return when {
        name.contains("win") -> Os.Windows
        name.contains("mac") || name.contains("darwin") -> Os.MacOs
        else -> Os.Linux
    }
}

/** Desktop default: a laptop-sized window, see [Profiles.Desktop.Default]. */
public actual fun defaultTestProfile(): TestProfile {
    return Profiles.Desktop.Default
}
