package app.kozica.data

/** The one switch the map, search, planning and recording all follow (`CONTEXT.md`). */
enum class Mode {
    HIKING,
    CYCLING,
    ;

    val other: Mode get() = if (this == HIKING) CYCLING else HIKING
}

/** The chips beside the switch in Cycling; `kind` is the tiles' own value, null for every Bike route. */
enum class BikeRouteFilter(
    val kind: String?,
) {
    ALL(null),
    TOURING("touring"),
    MTB("mtb"),
}
