package app.kozica.ui.support

import app.kozica.BuildConfig

/**
 * Everything about Help and feedback that differs from one app to the next — **the one file in
 * `ui/support/` that `scripts/sync-support.py` never overwrites.** Every other file in this package,
 * and the `shared:support` block of each `strings.xml`, comes from android-starter.
 *
 * Each constant is compiled into every build and frozen there for as long as a sideloaded copy
 * survives, so the address and the site sit on a domain we own, whose routing can move without a
 * release. The Play listing's contact field carries the same address —
 * `docs/DOD.md`, *Before the first upload*.
 */
object SupportConfig {
    const val SUPPORT_ADDRESS = "kozica.support@maksforge.com"

    /**
     * The id Play knows this app by, as a literal rather than `BuildConfig.APPLICATION_ID`: debug
     * builds carry `applicationIdSuffix = ".debug"`, and Play has never heard of the suffixed id.
     */
    const val PLAY_PACKAGE = "io.github.srednimax.kozica"

    /** The mail subject's prefix — "`<MAIL_TAG> #bug`" — which a mail filter sorts on. Never translated. */
    const val MAIL_TAG = "Kozica"

    /** The app's page on maksforge.com; the privacy policy is `privacy-policy` beneath it. */
    const val SITE_URL = "https://maksforge.com/kozica/"

    /**
     * The ODbL §4.6 offer: every release attaches its Regions' archives to its GitHub release
     * (docs/phase-2b.md § 6), so this version's tag is where the derivative database is.
     */
    const val MAP_DATA_RELEASE = "https://github.com/maks-sredniawa/kozica/releases/tag/v"

    /** The relief build every release packs: `scripts/relief.pin`'s tag, which a test holds this to. */
    const val RELIEF_TAG = "relief-2026-11-01"

    private val ccBy =
        listOf(
            SpdxLicence(
                "CC-BY-4.0",
                "Creative Commons Attribution 4.0",
                "https://creativecommons.org/licenses/by/4.0/",
            ),
        )

    /**
     * What the build ships that Licensee never sees — a model or a data set fetched as a file — shown
     * on the Credits screen beside the dependencies. Here the map's data, its relief's three sources
     * (docs/phase-4.md § 3: GUGiK named, as Polish law asks; the other two under CC BY 4.0) and the
     * map's font. Each SPDX licence's text is bundled in `assets/licences/`.
     */
    val BUNDLED_CREDITS: List<LicensedArtifact> =
        listOf(
            LicensedArtifact(
                groupId = "org.openstreetmap",
                artifactId = "map-data",
                version = BuildConfig.VERSION_NAME,
                name = "OpenStreetMap",
                source = "© OpenStreetMap contributors · $MAP_DATA_RELEASE${BuildConfig.VERSION_NAME}",
                spdxLicenses =
                    listOf(
                        SpdxLicence(
                            "ODbL-1.0",
                            "Open Database License 1.0",
                            "https://opendatacommons.org/licenses/odbl/1-0/",
                        ),
                    ),
            ),
            LicensedArtifact(
                groupId = "sk.ugkk",
                artifactId = "dmr-5.0",
                version = RELIEF_TAG,
                name = "ÚGKK SR: DMR 5.0",
                source = "ÚGKK SR: DMR 5.0",
                spdxLicenses = ccBy,
            ),
            LicensedArtifact(
                groupId = "cz.cuzk",
                artifactId = "dmr-4g",
                version = RELIEF_TAG,
                name = "ČÚZK: DMR 4G",
                source = "ČÚZK: DMR 4G",
                spdxLicenses = ccBy,
            ),
            LicensedArtifact(
                groupId = "pl.gugik",
                artifactId = "nmt",
                version = RELIEF_TAG,
                name = "GUGiK: Numeryczny Model Terenu",
                source = "GUGiK",
                unknownLicenses =
                    listOf(
                        UnknownLicence(
                            "Numeryczny Model Terenu",
                            "https://www.geoportal.gov.pl/pl/dane/numeryczny-model-terenu-nmt/",
                        ),
                    ),
            ),
            LicensedArtifact(
                groupId = "com.google.fonts",
                artifactId = "noto-sans",
                version = "regular",
                name = "Noto Sans",
                source = "Noto Sans, the map's labels",
                spdxLicenses =
                    listOf(
                        SpdxLicence("OFL-1.1", "SIL Open Font License 1.1", "https://openfontlicense.org"),
                    ),
            ),
        )
}
