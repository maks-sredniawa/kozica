import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.Profile;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmReader;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import com.onthegomap.planetiler.reader.osm.OsmSourceFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.LineString;

/** The Region schema of docs/phase-2.md § 3: its layer table is the filter. */
public class KozicaProfile implements Profile {

  private static final List<String> COLOURS = List.of("red", "blue", "green", "yellow", "black");
  private static final String NO_COLOUR = "other";
  private static final Map<String, String> COLOUR_ALIASES = Map.ofEntries(
      Map.entry("czerwony", "red"), Map.entry("#ff0000", "red"),
      Map.entry("niebieski", "blue"), Map.entry("#0000ff", "blue"),
      Map.entry("zielony", "green"), Map.entry("#00ff00", "green"),
      Map.entry("żółty", "yellow"), Map.entry("#ffff00", "yellow"),
      Map.entry("czarny", "black"), Map.entry("#000000", "black"));

  private static final Set<String> LAND_NATURAL =
      Set.of("wood", "scrub", "grassland", "heath", "scree", "bare_rock", "glacier");
  private static final Set<String> LAND_USE = Set.of("forest", "meadow", "residential");
  private static final Map<String, Integer> ROADS = Map.of(
      "motorway", 8, "trunk", 8, "primary", 9, "secondary", 10, "tertiary", 11,
      "unclassified", 12, "residential", 12, "track", 12, "service", 13);
  private static final Set<String> PATHS = Set.of("path", "footway", "steps", "bridleway");
  private static final Map<String, Integer> POIS = Map.of(
      "hut", 11, "peak", 11, "station", 11, "pass", 12, "shelter", 13,
      "viewpoint", 13, "parking", 13, "spring", 14, "stop", 14);
  private static final Map<String, Integer> PLACES = Map.of("town", 8, "village", 10, "hamlet", 12);

  private static final Set<String> REGIONAL = Set.of("rwn", "lwn");
  private static final Set<String> LONG_DISTANCE = Set.of("nwn", "iwn");
  private static final Set<String> ROLES =
      Set.of("forward", "backward", "alternative", "excursion", "approach", "branch");
  private static final Set<String> HAS_SECTIONS = Set.of("icn", "ncn");
  private static final Map<String, String> BIKE_KINDS = Map.of("bicycle", "touring", "mtb", "mtb");
  private static final int FORWARD = 1;
  private static final int BACKWARD = -1;
  private static final int TWO_WAY = 0;

  record Trail(long id, String colour, String symbol, String ref, long[] ways) implements OsmRelationInfo {}

  record Boundary(long id, String kind) implements OsmRelationInfo {}

  record Member(boolean way, long ref, String role) {}

  record Hiking(long id, String network, boolean marked, Trail marking, List<Member> members) {}

  record BikeRoute(long id, String kind, String colour, String marking, String network, String ref, boolean signed,
      long[] ways, List<Member> members) implements OsmRelationInfo {}

  private static final Comparator<Trail> TRAIL_ORDER =
      Comparator.comparingInt((Trail trail) -> colourRank(trail.colour())).thenComparingLong(Trail::id);
  private static final Comparator<BikeRoute> BIKE_ROUTE_ORDER =
      Comparator.comparing((BikeRoute route) -> !route.kind().equals("touring"))
          .thenComparingInt(route -> colourRank(route.colour()))
          .thenComparingLong(BikeRoute::id);

  private final Map<Long, long[]> wayEnds = new ConcurrentHashMap<>();
  private final Map<Long, Hiking> hiking = new ConcurrentHashMap<>();
  private final Map<Long, BikeRoute> bikeRoutes = new ConcurrentHashMap<>();
  private final Map<Long, Map<Long, Integer>> senses = new ConcurrentHashMap<>();
  private volatile Membership membership;
  private volatile BikeMembership bikeMembership;

  public static void main(String[] args) throws IOException {
    var arguments = Arguments.fromArgs(args);
    var profile = new KozicaProfile();
    var trailsPath = arguments.file("trails", "where to write every Trail's membership, for the network", null);
    Planetiler.create(arguments)
        .setProfile(profile)
        .addOsmSource("osm", arguments.inputFile("osm_path", "the Region's cut", Path.of("region.osm.pbf")))
        .overwriteOutput(Path.of("region.pmtiles"))
        .run();
    if (trailsPath != null) {
      var lines = new ArrayList<>(profile.membership().lines());
      lines.addAll(profile.bikeMembership().lines(profile.senses));
      Files.write(trailsPath, lines);
    }
  }

  @Override
  public void preprocessOsmWay(OsmElement.Way way) {
    var nodes = way.nodes();
    if (nodes.size() >= 2) {
      wayEnds.put(way.id(), new long[] {nodes.get(0), nodes.get(nodes.size() - 1)});
    }
  }

  @Override
  public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
    if (relation.hasTag("route", "bicycle", "mtb")) {
      if (notSigned(relation)) {
        return null;
      }
      var route = bikeRouteOf(relation);
      bikeRoutes.put(relation.id(), route);
      return List.of(route);
    }
    if (relation.hasTag("route", "hiking") && relation.hasTag("type", "route", "superroute")) {
      if (notSigned(relation)) {
        return null;
      }
      var trail = trailOf(relation);
      var members = membersOf(relation);
      var marked = !relation.getString("osmc:symbol", "").isBlank() || !relation.getString("colour", "").isBlank();
      hiking.put(relation.id(), new Hiking(relation.id(), relation.getString("network", ""), marked, trail, members));
      return relation.hasTag("type", "route") ? List.of(trail) : null;
    }
    var kind = boundaryKind(relation);
    return kind == null ? null : List.of(new Boundary(relation.id(), kind));
  }

  /** docs/phase-7a.md § 2: planned, or no longer kept up, so no paint to find on the ground. */
  private static boolean notSigned(OsmElement.Relation relation) {
    return relation.hasTag("state", "proposed", "abandoned");
  }

  private static List<Member> membersOf(OsmElement.Relation relation) {
    return relation.members().stream()
        .filter(member -> member.type() != OsmElement.Type.NODE)
        .map(member -> new Member(member.type() == OsmElement.Type.WAY, member.ref(),
            ROLES.contains(member.role()) ? member.role() : ""))
        .toList();
  }

  private static BikeRoute bikeRouteOf(OsmElement.Relation relation) {
    var written = relation.getString("colour", "").trim().toLowerCase(Locale.ROOT);
    var colour = colourOf(written);
    var ways = relation.members().stream()
        .filter(member -> member.type() == OsmElement.Type.WAY)
        .mapToLong(OsmElement.Relation.Member::ref)
        .toArray();
    return new BikeRoute(relation.id(), BIKE_KINDS.get(relation.getString("route")),
        colour == null ? NO_COLOUR : colour, colour == null ? written : colour, relation.getString("network", ""),
        relation.getString("ref"), relation.hasTag("signed_direction", "yes"), ways, membersOf(relation));
  }

  private BikeMembership bikeMembership() {
    var built = bikeMembership;
    if (built == null) {
      synchronized (this) {
        built = bikeMembership;
        if (built == null) {
          built = new BikeMembership(Map.copyOf(bikeRoutes));
          bikeMembership = built;
        }
      }
    }
    return built;
  }

  private Membership membership() {
    var built = membership;
    if (built == null) {
      synchronized (this) {
        built = membership;
        if (built == null) {
          built = new Membership(Map.copyOf(hiking));
          membership = built;
        }
      }
    }
    return built;
  }

  /**
   * Which relations are Trails, and which Trails each relation's ways belong to (docs/phase-6c.md § 2):
   * a regional parent takes the members marked as it is as its pieces, a Long-distance Trail takes its
   * members with a role, and its own ways belong to no Trail.
   */
  static final class Membership {
    private final Map<Long, Hiking> hiking;
    private final Map<Long, List<Long>> parents = new HashMap<>();
    private final Map<Long, Set<Long>> owners = new HashMap<>();
    private final Map<Long, Trail> markings = new HashMap<>();

    Membership(Map<Long, Hiking> hiking) {
      this.hiking = hiking;
      for (var relation : new TreeMap<>(hiking).values()) {
        for (var piece : piecesOf(relation)) {
          parents.computeIfAbsent(piece.id(), id -> new ArrayList<>()).add(relation.id());
        }
      }
      // Filled here, before the tiles are built, because processFeature reads them from many threads.
      for (var id : hiking.keySet()) {
        for (var trail : owners(id)) {
          markings.computeIfAbsent(trail, this::markingFor);
        }
      }
    }

    private boolean longDistance(Hiking relation) {
      return LONG_DISTANCE.contains(relation.network())
          && relation.members().stream().anyMatch(member -> !member.way() && member.role().isEmpty());
    }

    private List<Hiking> memberRelations(Hiking relation) {
      return relation.members().stream()
          .filter(member -> !member.way() && hiking.containsKey(member.ref()))
          .map(member -> hiking.get(member.ref()))
          .toList();
    }

    private List<Hiking> piecesOf(Hiking relation) {
      if (longDistance(relation)) {
        return List.of();
      }
      if (LONG_DISTANCE.contains(relation.network())) {
        var withRole = relation.members().stream()
            .filter(member -> !member.way() && !member.role().isEmpty())
            .map(Member::ref)
            .collect(Collectors.toSet());
        return memberRelations(relation).stream().filter(member -> withRole.contains(member.id())).toList();
      }
      if (!REGIONAL.contains(relation.network())) {
        return List.of();
      }
      var marking = markingOf(relation);
      return marking == null ? List.of()
          : memberRelations(relation).stream().filter(member -> sameMarking(member.marking(), marking)).toList();
    }

    private Trail markingOf(Hiking relation) {
      if (relation.marked()) {
        return relation.marking();
      }
      var shared = memberRelations(relation).stream()
          .map(Hiking::marking)
          .map(marking -> List.of(marking.colour(), Objects.toString(marking.symbol(), "")))
          .distinct()
          .toList();
      return shared.size() == 1 ? memberRelations(relation).get(0).marking() : null;
    }

    private static boolean sameMarking(Trail a, Trail b) {
      return a.colour().equals(b.colour()) && Objects.equals(a.symbol(), b.symbol());
    }

    private boolean isTrail(Hiking relation) {
      return !parents.containsKey(relation.id())
          && !longDistance(relation)
          && (relation.members().stream().anyMatch(Member::way) || !piecesOf(relation).isEmpty());
    }

    Set<Long> ownersOf(long id) {
      return owners.getOrDefault(id, Set.of());
    }

    private Set<Long> owners(long id) {
      var found = owners.get(id);
      if (found == null) {
        found = new TreeSet<>();
        owners.put(id, found);
        var relation = hiking.get(id);
        if (relation != null && isTrail(relation)) {
          found.add(id);
        }
        for (var parent : parents.getOrDefault(id, List.of())) {
          found.addAll(owners(parent));
        }
      }
      return found;
    }

    Trail trail(long id) {
      return markings.get(id);
    }

    private Trail markingFor(long id) {
      var relation = hiking.get(id);
      var marking = markingOf(relation);
      var own = relation.marking();
      return marking == null ? own : new Trail(id, marking.colour(), marking.symbol(), own.ref(), own.ways());
    }

    private void members(Hiking relation, String role, Set<Long> seen, Map<Long, String> out) {
      if (!seen.add(relation.id())) {
        return;
      }
      var pieces = piecesOf(relation).stream().map(Hiking::id).collect(Collectors.toSet());
      for (var member : relation.members()) {
        var taken = role.isEmpty() ? member.role() : role;
        if (member.way()) {
          out.putIfAbsent(member.ref(), taken.isEmpty() ? String.valueOf(member.ref()) : member.ref() + "/" + taken);
        } else if (pieces.contains(member.ref())) {
          members(hiking.get(member.ref()), taken, seen, out);
        }
      }
    }

    /**
     * {@code trail <id> <colour> <symbol> <way>[/<role>] ...}, a piece's ways at its place and in its order,
     * then {@code long <id> <section> ...}: build-network.py's input, sorted so two builds write the same bytes.
     */
    List<String> lines() {
      var out = new ArrayList<String>();
      for (var relation : new TreeMap<>(hiking).values()) {
        if (isTrail(relation)) {
          var trail = trail(relation.id());
          var ways = new LinkedHashMap<Long, String>();
          members(relation, "", new HashSet<>(), ways);
          out.add(String.join("\t", "trail", String.valueOf(relation.id()), trail.colour(),
              Objects.toString(trail.symbol(), ""), String.join(" ", ways.values())));
        }
      }
      for (var relation : new TreeMap<>(hiking).values()) {
        if (longDistance(relation)) {
          var sections = relation.members().stream()
              .filter(member -> !member.way() && member.role().isEmpty() && hiking.containsKey(member.ref()))
              .map(member -> String.valueOf(member.ref()))
              .distinct()
              .toList();
          out.add(String.join("\t", "long", String.valueOf(relation.id()), String.join(" ", sections)));
        }
      }
      return out;
    }
  }

  /**
   * Which relations are Bike routes, and which Bike routes each relation's ways belong to (docs/phase-7a.md § 2):
   * an {@code icn} or {@code ncn} parent, or one no member carries the marking of, has Sections; any other takes
   * the members marked as it is as its pieces; a parent's own ways are its own row.
   */
  static final class BikeMembership {
    private final Map<Long, BikeRoute> routes;
    private final Map<Long, List<Long>> parents = new HashMap<>();
    private final Map<Long, Set<Long>> owners = new HashMap<>();

    BikeMembership(Map<Long, BikeRoute> routes) {
      this.routes = routes;
      for (var route : new TreeMap<>(routes).values()) {
        for (var piece : piecesOf(route)) {
          parents.computeIfAbsent(piece.id(), id -> new ArrayList<>()).add(route.id());
        }
      }
      // Filled here, before the tiles are built, because processFeature reads them from many threads.
      for (var id : routes.keySet()) {
        owners(id);
      }
    }

    private List<BikeRoute> memberRelations(BikeRoute route) {
      return route.members().stream()
          .filter(member -> !member.way() && routes.containsKey(member.ref()))
          .map(member -> routes.get(member.ref()))
          .toList();
    }

    private List<BikeRoute> piecesOf(BikeRoute route) {
      if (HAS_SECTIONS.contains(route.network()) || route.marking().isEmpty()) {
        return List.of();
      }
      return memberRelations(route).stream().filter(member -> member.marking().equals(route.marking())).toList();
    }

    private boolean isBikeRoute(BikeRoute route) {
      return !parents.containsKey(route.id()) && (route.ways().length > 0 || !piecesOf(route).isEmpty());
    }

    private Set<Long> owners(long id) {
      var found = owners.get(id);
      if (found == null) {
        found = new TreeSet<>();
        owners.put(id, found);
        var route = routes.get(id);
        if (route != null && isBikeRoute(route)) {
          found.add(id);
        }
        for (var parent : parents.getOrDefault(id, List.of())) {
          found.addAll(owners(parent));
        }
      }
      return found;
    }

    Set<Long> ownersOf(long id) {
      return owners.getOrDefault(id, Set.of());
    }

    BikeRoute route(long id) {
      return routes.get(id);
    }

    private void members(BikeRoute route, String role, Set<Long> seen, Map<Long, String> out) {
      if (!seen.add(route.id())) {
        return;
      }
      var pieces = piecesOf(route).stream().map(BikeRoute::id).collect(Collectors.toSet());
      for (var member : route.members()) {
        var taken = role.isEmpty() ? member.role() : role;
        if (member.way()) {
          out.putIfAbsent(member.ref(), taken.isEmpty() ? String.valueOf(member.ref()) : member.ref() + "/" + taken);
        } else if (pieces.contains(member.ref())) {
          members(routes.get(member.ref()), taken, seen, out);
        }
      }
    }

    private List<String> sectionsOf(BikeRoute route) {
      return !piecesOf(route).isEmpty() ? List.of() : route.members().stream()
          .filter(member -> !member.way() && member.role().isEmpty() && routes.containsKey(member.ref()))
          .map(member -> String.valueOf(member.ref()))
          .distinct()
          .toList();
    }

    /**
     * {@code bike <id> <colour> <kind> <way>[/<role>][@f|@b] ...} with the sense each way is ridden in against its
     * nodes, then {@code sections <id> <section> ...}: build-network.py's input, sorted as {@link Membership#lines}.
     */
    List<String> lines(Map<Long, Map<Long, Integer>> senses) {
      var out = new ArrayList<String>();
      for (var route : new TreeMap<>(routes).values()) {
        if (isBikeRoute(route)) {
          var ways = new LinkedHashMap<Long, String>();
          members(route, "", new HashSet<>(), ways);
          var sensed = senses.getOrDefault(route.id(), Map.of());
          var listed = ways.entrySet().stream()
              .map(way -> way.getValue() + switch (sensed.getOrDefault(way.getKey(), TWO_WAY)) {
                case FORWARD -> "@f";
                case BACKWARD -> "@b";
                default -> "";
              })
              .toList();
          out.add(String.join("\t", "bike", String.valueOf(route.id()), route.colour(), route.kind(),
              String.join(" ", listed)));
        }
      }
      for (var route : new TreeMap<>(routes).values()) {
        var sections = sectionsOf(route);
        if (!sections.isEmpty()) {
          out.add(String.join("\t", "sections", String.valueOf(route.id()), String.join(" ", sections)));
        }
      }
      return out;
    }
  }

  private static Trail trailOf(OsmElement.Relation relation) {
    var osmcSymbol = relation.getString("osmc:symbol");
    var hasSymbol = osmcSymbol != null && !osmcSymbol.isBlank();
    var parts = hasSymbol ? osmcSymbol.split(":") : new String[0];
    var colour = hasSymbol
        ? colourOf(parts.length > 0 ? parts[0] : null)
        : colourOf(relation.getString("colour"));
    var symbol = parts.length > 2 && !parts[2].isBlank() ? parts[2].trim().toLowerCase(Locale.ROOT) : null;
    var ways = relation.members().stream()
        .filter(member -> member.type() == OsmElement.Type.WAY)
        .mapToLong(OsmElement.Relation.Member::ref)
        .toArray();
    return new Trail(relation.id(), colour == null ? NO_COLOUR : colour, symbol, relation.getString("ref"), ways);
  }

  private static String colourOf(String value) {
    if (value == null) {
      return null;
    }
    var colour = value.trim().toLowerCase(Locale.ROOT);
    return COLOURS.contains(colour) ? colour : COLOUR_ALIASES.get(colour);
  }

  private static int colourRank(String colour) {
    var rank = COLOURS.indexOf(colour);
    return rank < 0 ? COLOURS.size() : rank;
  }

  // admin_level=2 alone also matches other things, so it is read with boundary=administrative.
  private static String boundaryKind(OsmElement element) {
    if (element.hasTag("boundary", "national_park")
        || (element.hasTag("boundary", "protected_area") && element.hasTag("protect_class", "2"))) {
      return "national_park";
    }
    if (element.hasTag("boundary", "administrative") && element.hasTag("admin_level", "2")) {
      return "country";
    }
    return null;
  }

  @Override
  public void processFeature(SourceFeature feature, FeatureCollector features) {
    if (feature.canBePolygon()) {
      land(feature, features);
    }
    water(feature, features);
    if (feature.canBeLine()) {
      boundaries(feature, features);
      roadsAndPaths(feature, features);
      routes(feature, features);
    }
    poi(feature, features);
    place(feature, features);
  }

  private static void land(SourceFeature feature, FeatureCollector features) {
    var natural = feature.getString("natural", "");
    var landuse = feature.getString("landuse", "");
    var kind = LAND_NATURAL.contains(natural) ? natural : LAND_USE.contains(landuse) ? landuse : null;
    if (kind != null) {
      features.polygon("land").setMinZoom(8).setAttr("kind", kind);
    }
  }

  private static void water(SourceFeature feature, FeatureCollector features) {
    if (feature.canBePolygon() && feature.hasTag("natural", "water")) {
      features.polygon("water").setMinZoom(8).setAttr("kind", "water");
    }
    if (feature.canBeLine() && feature.hasTag("waterway", "river", "stream")) {
      var kind = feature.getString("waterway");
      features.line("water").setMinZoom(kind.equals("river") ? 8 : 12).setAttr("kind", kind);
    }
  }

  private static void boundaries(SourceFeature feature, FeatureCollector features) {
    var kinds = new TreeSet<String>();
    for (var member : feature.relationInfo(Boundary.class)) {
      kinds.add(member.relation().kind());
    }
    if (feature instanceof OsmSourceFeature<?> osm
        && osm.originalElement() instanceof OsmElement.Way way) {
      var own = boundaryKind(way);
      if (own != null) {
        kinds.add(own);
      }
    }
    for (var kind : kinds) {
      features.line("boundaries").setMinZoom(6).setAttr("kind", kind);
    }
  }

  private static void roadsAndPaths(SourceFeature feature, FeatureCollector features) {
    var highway = feature.getString("highway");
    if (highway == null) {
      return;
    }
    var road = highway.endsWith("_link") ? highway.substring(0, highway.length() - "_link".length()) : highway;
    if (ROADS.containsKey(road)) {
      features.line("roads").setMinZoom(ROADS.get(road)).setAttr("kind", road);
    } else if (PATHS.contains(highway)) {
      features.line("paths").setMinZoom(12).setAttr("kind", highway).setAttr("sac_scale", feature.getString("sac_scale"));
    }
  }

  /**
   * One feature per Trail and per Bike route per way, in one order over both, the way oriented by the first's
   * relation (docs/phase-2.md § 3, docs/phase-7a.md § 2).
   */
  private void routes(SourceFeature feature, FeatureCollector features) {
    var trailRelations = feature.relationInfo(Trail.class).stream()
        .map(OsmReader.RelationMember::relation)
        .distinct()
        .sorted(TRAIL_ORDER)
        .toList();
    var bikeRelations = feature.relationInfo(BikeRoute.class).stream()
        .sorted(Comparator.comparing(member -> member.relation(), BIKE_ROUTE_ORDER))
        .toList();
    if (trailRelations.isEmpty() && bikeRelations.isEmpty()) {
      return;
    }
    var membership = membership();
    var trails = trailRelations.stream()
        .flatMap(relation -> membership.ownersOf(relation.id()).stream())
        .distinct()
        .map(membership::trail)
        .sorted(TRAIL_ORDER)
        .toList();
    var bikeMembership = bikeMembership();
    var routeSenses = new HashMap<Long, Integer>();
    for (var member : bikeRelations) {
      var sense = sense(feature, member.relation(), member.role());
      for (var owner : bikeMembership.ownersOf(member.relation().id())) {
        routeSenses.putIfAbsent(owner, sense);
      }
    }
    var bikes = routeSenses.keySet().stream().map(bikeMembership::route).sorted(BIKE_ROUTE_ORDER).toList();
    if (trails.isEmpty() && bikes.isEmpty()) {
      return;
    }
    LineString line;
    try {
      line = (LineString) feature.line();
    } catch (GeometryException e) {
      e.log("route way " + feature.id());
      return;
    }
    var reference = trailRelations.isEmpty() ? bikeRelations.get(0).relation().ways() : trailRelations.get(0).ways();
    var turned = reversed(feature.id(), reference, line);
    if (turned) {
      line = line.reverse();
    }
    var count = trails.size() + bikes.size();
    for (var index = 0; index < trails.size(); index++) {
      var trail = trails.get(index);
      features.geometry("trails", line)
          .setMinZoom(10)
          .setAttr("trail", trail.id())
          .setAttr("colour", trail.colour())
          .setAttr("symbol", trail.symbol())
          .setAttr("index", index)
          .setAttr("count", trails.size())
          .setAttr("union_index", index)
          .setAttr("union_count", count)
          .setAttr("ref", trail.ref());
    }
    for (var index = 0; index < bikes.size(); index++) {
      var route = bikes.get(index);
      int sense = routeSenses.get(route.id());
      senses.computeIfAbsent(route.id(), id -> new ConcurrentHashMap<>()).put(feature.id(), sense);
      features.geometry("bike_routes", line)
          .setMinZoom(10)
          .setAttr("bike_route", route.id())
          .setAttr("kind", route.kind())
          .setAttr("colour", route.colour())
          .setAttr("network", route.network().isEmpty() ? null : route.network())
          .setAttr("ref", route.ref())
          .setAttr("index", trails.size() + index)
          .setAttr("count", count)
          .setAttr("dir", sense == TWO_WAY ? null : (sense == FORWARD) != turned ? "forward" : "backward");
    }
  }

  /** docs/phase-7a.md § 2: the sense a relation is ridden in on this way, against the way's nodes. */
  private int sense(SourceFeature feature, BikeRoute route, String role) {
    var bicycle = feature.getString("oneway:bicycle");
    var tagged = switch (bicycle != null ? bicycle : feature.getString("oneway", "")) {
      case "yes", "true", "1" -> FORWARD;
      case "-1" -> BACKWARD;
      default -> TWO_WAY;
    };
    if (tagged != TWO_WAY) {
      return tagged;
    }
    if (role.equals("forward")) {
      return FORWARD;
    }
    if (role.equals("backward")) {
      return BACKWARD;
    }
    if (!route.signed()) {
      return TWO_WAY;
    }
    var runs = runsWithOrder(feature.id(), route.ways());
    return runs == null || runs ? FORWARD : BACKWARD;
  }

  private boolean reversed(long way, long[] reference, LineString line) {
    var runs = runsWithOrder(way, reference);
    if (runs != null) {
      return !runs;
    }
    // World coordinates grow east in x and south in y: west first, or south first on a shared longitude.
    var start = line.getCoordinateN(0);
    var end = line.getCoordinateN(line.getNumPoints() - 1);
    return start.x != end.x ? start.x > end.x : start.y < end.y;
  }

  /** Whether the way's nodes run on from the member before it or into the one after, null when neither tells. */
  private Boolean runsWithOrder(long way, long[] order) {
    var ends = wayEnds.get(way);
    var at = indexOf(order, way);
    if (ends != null && at >= 0) {
      var previous = at > 0 ? wayEnds.get(order[at - 1]) : null;
      if (touches(previous, ends[0])) {
        return true;
      }
      if (touches(previous, ends[1])) {
        return false;
      }
      var next = at + 1 < order.length ? wayEnds.get(order[at + 1]) : null;
      if (touches(next, ends[1])) {
        return true;
      }
      if (touches(next, ends[0])) {
        return false;
      }
    }
    return null;
  }

  private static int indexOf(long[] ways, long way) {
    for (var i = 0; i < ways.length; i++) {
      if (ways[i] == way) {
        return i;
      }
    }
    return -1;
  }

  private static boolean touches(long[] ends, long node) {
    return ends != null && (ends[0] == node || ends[1] == node);
  }

  private static void poi(SourceFeature feature, FeatureCollector features) {
    var kind = poiKind(feature);
    if (kind == null) {
      return;
    }
    FeatureCollector.Feature point;
    if (feature.isPoint()) {
      point = features.point("pois");
    } else if (feature.canBePolygon()) {
      point = features.pointOnSurface("pois");
    } else {
      return;
    }
    point.setMinZoom(POIS.get(kind))
        .setAttr("kind", kind)
        .setAttr("name", feature.getString("name"))
        .setAttr("ele", feature.getString("ele"));
  }

  private static String poiKind(SourceFeature feature) {
    if (feature.hasTag("tourism", "alpine_hut")) {
      return "hut";
    }
    if (feature.hasTag("tourism", "wilderness_hut")
        || (feature.hasTag("amenity", "shelter") && !feature.hasTag("shelter_type", "public_transport"))) {
      return "shelter";
    }
    if (feature.hasTag("natural", "peak")) {
      return "peak";
    }
    if (feature.hasTag("natural", "saddle") || feature.hasTag("mountain_pass", "yes")) {
      return "pass";
    }
    if (feature.hasTag("natural", "spring")) {
      return "spring";
    }
    if (feature.hasTag("tourism", "viewpoint")) {
      return "viewpoint";
    }
    if (feature.hasTag("amenity", "parking")) {
      return "parking";
    }
    if (feature.hasTag("highway", "bus_stop")) {
      return "stop";
    }
    if (feature.hasTag("railway", "station", "halt")) {
      return "station";
    }
    return null;
  }

  private static void place(SourceFeature feature, FeatureCollector features) {
    var kind = feature.getString("place");
    if (kind == null || !PLACES.containsKey(kind)) {
      return;
    }
    var point = feature.isPoint() ? features.point("places")
        : feature.canBePolygon() ? features.pointOnSurface("places") : null;
    if (point != null) {
      point.setMinZoom(PLACES.get(kind)).setAttr("kind", kind).setAttr("name", feature.getString("name"));
    }
  }

  @Override
  public String name() {
    return "Kozica";
  }

  @Override
  public String attribution() {
    return OSM_ATTRIBUTION;
  }
}
