# Definition of done — what is still open

The **live checklist**. Keep it short: when an item closes, tick it, write the *result* into whatever
long-form record you keep, and delete the detail from here. A session should be able to pick up the
work by reading this file alone.

## The standing schema gate — never ticked, checked at every bump

**An update must migrate an existing install without losing anything** (ADR-0001). This item does not
close. Whenever `APP_SCHEMA_VERSION` changes, all five hold before the release goes out:

1. `MIGRATION_x_y` written **and registered in** `APP_MIGRATIONS` for every step. A migration Room
   never runs is not a migration, and nothing about the build complains.
2. The exported `app/schemas/*/N.json` committed — every later migration is transcribed from it,
   never from the entity classes.
3. `SchemaGateTest` extended, so the **launch gate** is proven to let the upgrade through. Every
   migration test opens the database directly and walks past that gate; this assertion is the only
   thing standing in front of it.
4. A migration test proving the **rows survive** — read values back, do not merely assert nothing
   threw. `runMigrationsAndValidate` compares *shapes* and is blind to a table emptied by a cascading
   `DROP`.
5. **An actual upgrade watched on a device**: install the previous build, put real data in it, install
   the new one over the top, confirm the app opens and the data is there.
   `./gradlew assembleDebug -PreleaseShapedDebug` is how to do this without touching a Play install —
   it is minified *and* `BuildConfig.DEBUG == false`, so migrations are registered rather than the
   destructive fallback. ⚠️ **Both halves are load-bearing**: a plain debug build takes the fallback
   and proves the opposite of what you wanted.

`scripts/schema-gate.py` enforces 1–3 mechanically in CI. Items 4 and 5 are yours.

## Before the first upload

- [x] **Delete what the app does not need.** Phase 0 deleted the reminders with their WorkManager jobs, and
      the placeholder domain ([`optional-modules.md`](optional-modules.md)). Room, photos and backup
      stay on purpose: Room for phase 5's planned routes ([`phase-0.md`](phase-0.md) § *Not in*),
      photos and backup for phase 6's hike log ([`PLAN.md`](PLAN.md) § *Phase 0*). That is decided
      before the door because a tester's install keeps them: from phase 5 on, deleting one loses a
      tester's data ([`phase-3.md`](phase-3.md) § *The door to closed testing*).
- [x] **Decide `minSdk` deliberately.** Kept at 26 ([`phase-0.md`](phase-0.md) § 0).
- [x] **Replace the placeholder mark.** The chamois on its crag, drawn as anchor points in
      `art/draw-mark.py`, no image traced; provenance in `art/README.md`.
- [x] **Drop the dormant `items` table.** Phase 5's `MIGRATION_1_2` replaced it with saved Routes
      ([`phase-5.md`](phase-5.md) § 5).
- [x] **Rewrite `scripts/edge-to-edge.py`'s SCENES** against your screens. Home and Settings; each
      new screen brings its own scenes.
- [x] **Run the artifact gates before the first tag.** Overtaken: all four never ran locally before a
      tag. Phase 0's R1 ran `aab-permissions.py` on a local `bundleRelease` (versionCode 13,
      2026-10-18), and v0.2.0, the first tag, passed all four in `publish-play.yml` run 39339727698
      on its first try: versionCode 88 matching, 3 permissions all accounted for and none of the 8
      forbidden, all 47 `pl` strings present, 3 reflection classes intact. The failure this item
      guarded against, a stale table found after the tag with the version spent, never happened.
      Every release since runs all four before Play sees the bundle, `publish-play-closed.yml`
      promotes those same bytes, and the standing *every release* check below keeps it.
- [x] **Choose the palette.** Chamois ochre, rock, slate and stone in `scripts/gen_scheme.py`; every
      contrast pair passes. Phase 2's R9 read it against the Trail colours on the map: no seed moved.
- [x] **Choose a dedicated support address.** `kozica.support@maksforge.com` since 2026-10-20, on
      the owner's own domain: Cloudflare Email Routing forwards it to the developer's Gmail, which
      sends as it. `ui/support/SupportConfig.kt` compiles it in, and the Play listing's contact
      field and the privacy policy's *Contact* give it too. The item once ruled out an alias on
      personal mail, and this is one (replies still carry the Gmail in `Return-Path`); it was relaxed
      because what a build freezes is the address, not the mailbox. Moving the mailbox is now only a
      change to the Cloudflare route; all three change only if the address does. v0.2.0 and v0.3.0
      compile in the Gmail itself, so that inbox stays read for as long as either build survives.
- [x] **Write the listing.** `docs/store-listing.md`, since phase P; phase 3 adds the Safety screen
      to it.
- [x] **Write the privacy policy** and confirm GitHub Pages is serving `docs/`. Play requires a
      *hosted* URL, and an offline app has no server of its own. First served at
      `https://srednimax.github.io/kozica/privacy-policy.html` on 2026-10-20; that URL was retired
      on 2026-11-07 and now returns 404, and the policy is at
      <https://maksforge.com/kozica/privacy-policy>.
- [x] **Answer the Console's App content pages and write [`play-app-content.md`](play-app-content.md)
      in the same sitting.** All ten answered 2026-10-20.
- [x] **`LICENSE` and `README.md`.** All rights reserved, source-available as the owner's other
      apps are, the map data under ODbL apart from it; *Contributing* says pull requests are not
      accepted.
- [x] **Create the upload keystore, outside the repo.** Recorded in [`RELEASING.md`](RELEASING.md)
      § *The upload key*.
- [x] **Set up the GitHub repository.** The `main: require CI` ruleset, rebase merges, Pages and
      `RELEASE_PLEASE_TOKEN`. Pages was turned off on 2026-11-07.
- [x] **Set the five Play secrets and create the service account.** All in place; 0.2.0 reached
      internal testing by itself on 2026-10-20.
- [ ] **Plan the closed test.** A new personal developer account reaches production only after a
      closed test — at the time of writing 12 testers opted in for 14 continuous days, then an access
      application. Recruit the testers before the build is ready, not after; `publish-play-closed.yml`
      moves each build to them.
- [x] **Decide the `applicationId` deliberately.** `io.github.srednimax.kozica`, fixed by the Play
      entry.
- [x] **Take checkpoint E, phase 2b's since phase 2 split.** Passed with 0.3.0 (R4, R7, R8, R9);
      the Console asked no foreground-service declaration for `dataSync` (2026-10-27,
      [`play-app-content.md`](play-app-content.md)). The one-pin update was read twice on 2026-11-01
      ([`phase-2b.md`](phase-2b.md) R10, R11); the owner's verdict, keeping the format, is under
      *After the closed test*.
- [x] **Take phase 1's phone readings before phase 2 builds on MapLibre.** Taken 2026-10-19: R1
      verdict 1, R8 verdict 3 (undecided until E), R9 after fixing a crash, R10's screen
      ([`phase-1.md`](phase-1.md) § 1).

## After the closed test

- [x] **The production-access application is in, with its answers recorded verbatim** in
      [`play-app-content.md`](play-app-content.md). Until it is granted the production track does not
      exist. The app this template was extracted from waited one day; that is one datapoint, not a
      promise. **If it comes back rejected, read the reason before changing any artifact.** Changing
      the build first leaves you arguing the reason against a different build.
- [ ] **Decide the first production number.** release-please never jumps to `1.0.0` by itself (with
      the major at 0 even `feat!:` bumps the minor), so it takes a `Release-As: 1.0.0` footer
      ([`RELEASING.md`](RELEASING.md)), or a deliberate decision to stay on 0.x. In order: production
      access granted, *then* that version's release notes in every locale on an ordinary branch (never
      on release-please's own, which it force-pushes), *then* the AAB and the listing go up together.

- [x] **Tell a pause from a stall in the frame log, before the next frame gate.** Done 2026-10-30;
      the rule is `frame-stats.py`'s, R3's reread is in [`phase-6c.md`](phase-6c.md).
- [ ] **Weigh the position dot's redraws at rest against real use.** With the dot shown the map draws
      frames with the camera at rest; the figures are in R3's row of [`phase-6c.md`](phase-6c.md), and
      the 25 frames that remain with location revoked are unexplained. Provisional until real use,
      such as a hike with the dot shown on the owner's phone, shows what it costs the battery; if it
      matters, find what about the dot draws them (its animation, compass bearing updates, or the
      fixes it is fed) before changing anything; either way, tick it and record the verdict.
- [x] **Take R10 and R11's verdict on the map format.** Two releases that only move the pin, read
      through the internal track: 0.14.1 → 0.14.2 queued 361.7 MB 33 minutes after its upload,
      patching six Regions on the sixth request after five refused; 0.14.2 → 0.14.3 queued 562.8 MB
      2 hours after its upload, at the first request, and patched nothing. The unchanged relief,
      268 MB, came whole every time, as it did with every pack in a code-only update
      ([`phase-2b.md`](phase-2b.md) R10, R11). Still unread: whether a later request, or a second
      device's, gets patches for a pair Play first sent whole, and whether Play ever patches or skips
      the relief. § 1's verdict 3, decided with the owner on 2026-11-01: the format stays, and the
      relief, the largest pack and sent whole every time, keeps whole metres instead of tenths, 86 MB
      instead of 257 MB with no visible change (ADR-0009's 2026-11-01 amendment).

## Standing checks that never close

- [ ] **Every release: run the artifact checks on the built AAB**, not on the source.
      `aab-permissions.py` is the one that finds what a *dependency* merged into your manifest —
      a permission you never declared, or a `uses-feature` that quietly filters the app off devices.
- [ ] **Every release: read the release notes gate's output** rather than trusting it passed.
- [ ] **Every dependency bump: re-run `licensee`.** The build fails on an unallowed licence, which is
      the point — but the fix is two things, the allowlist *and* the bundled text.
- [ ] **Test the release-shaped build on a device.** A missing R8 keep rule does not crash; it makes
      a feature silently stop working.
