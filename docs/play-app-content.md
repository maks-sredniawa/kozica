# Play Console — App content answers

The answers Kozica gave in the Console's **App content** pages, what each one rests on, and **the
fact that would change it**. The Console shows what was answered but never why, and Play asks again:
when the app changes, when the questionnaire changes, and once a year. A re-declaration made from
memory a year from now is how a true answer turns false.

**Status:** all ten pages answered 2026-10-20 and **not yet reviewed**. Play reviews them with the
first release sent for review, the closed-testing one; re-read every section against that build first.

Two rules keep this file honest:

- **The privacy policy and the Data safety form describe the same facts to two audiences**, and Play
  cross-checks them. [`privacy-policy.md`](privacy-policy.md) and this file move in the same commit.
  A change to one without the other is the drift this file exists to stop.
- **A claim about permissions is read off the built artifact, never off the manifest.** A dependency
  can merge a permission the source never declared, and that is invisible from `AndroidManifest.xml`.
  `scripts/aab-permissions.py` is the reader, it runs before every upload, and its `EXPECTED` and
  `FORBIDDEN` tables are this section's twin — edit them together.

## What the artifact carries

Read at **versionCode _, versionName _** by `publish-play.yml`'s verify step on _<date>_:

> _paste the summary line `aab-permissions.py` prints_

| Permission | Whose | What it is for | Console consequence |
| --- | --- | --- | --- |
| `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX | its own non-exported receivers, signature-level | none: not user-visible |
| `ACCESS_FINE_LOCATION` | Kozica | the position button: the dot, its accuracy circle and heading on the map, read on the phone only; recording a Hike, whose Track is stored on the phone; and following a Route, whose Route and Progress are kept in the app's settings on the phone until following ends, or the next open once 24 hours have passed | the location permission declaration; Data safety stays "no data collected", because nothing leaves the device |
| `ACCESS_COARSE_LOCATION` | Kozica | asked with fine, as Android requires from API 31 for the approximate option | as fine |
| `FOREGROUND_SERVICE_LOCATION` | Kozica | the Hike recording service, started from the visible app at Record | the foreground service declaration below |
| `POST_NOTIFICATIONS` | Kozica | the recording's notification, asked once at the first Record | none |

**Features**: `android.hardware.location` and `android.hardware.location.gps`, both
`required="false"`. Declaring a location permission makes Play assume the hardware is required
unless the feature says otherwise, and the position is optional to the map: a phone without GPS
still gets the app, and the map without a dot.

**Forbidden, and asserted absent**: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`,
`AD_ID`, `QUERY_ALL_PACKAGES`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `CAMERA`, `USE_EXACT_ALARM`.
The network three are [ADR-0008](adr/0008-kozica-never-opens-a-network-connection.md); `INTERNET`
and `AD_ID` are what the Data safety and advertising-ID answers rest on.

**No asks that are not permissions, either.** The battery-optimisation and Xiaomi autostart
hand-offs left with the reminders; `PLAN.md` rule 4 brings them back only on a reading, and the
privacy policy names them in the same commit if they do.

---

## Ads

**No.** Answered 2026-10-20.

Would change: any ad SDK, ever. `AD_ID` absent from the artifact is the check that would catch one
arriving through a dependency.

## Sign-in details

The Console's former *App access* page. **No part of the app is restricted.** Answered 2026-10-20.

There is no account and no sign-in. Would change: a feature behind a login.

## Content rating

**The IARC questionnaire.** Answered 2026-10-20, category *All other app types*, rating contact the
developer account's Gmail. "No" to every question; the ratings came back Everyone (ESRB) and All
ages (ClassInd).

Would change: user-generated content, communication between users, or a link out to anything that
has either. A support `mailto:` hand-off is not user-to-user communication — it opens the user's own
mail app, addressed to the developer — and a rate row opens Play's own listing, whose reviews are
Play's surface rather than the app's.

## Target audience and content

**18 and over.** Answered 2026-10-20.

Would change: lowering it. Any age band under 18 brings in the Families policy and its design and
SDK requirements.

## Government, financial and health features

**No to all three.** Answered 2026-10-20.

Health is the one to guard, for an app anywhere near wellbeing. **Keep health claims out of the
listing, the site and the tags** — Play's enforcement has treated a linked page as part of the
listing. Would change: copy or a tag that sells the app as good for you.

## Data safety

**No data collected. No data shared.** Answered 2026-10-20, with the privacy policy URL set the same
day.

Play's definitions, from its Data safety help page (support.google.com, answer 10787469) — re-read
them at each re-declaration rather than trusting this quote:

> "'Collect' means transmitting data from your app off a user's device."
>
> "User data accessed by your app that is only processed locally on the user's device and not sent
> off device does **not** need to be disclosed."

The answer rests on one fact per route data could take:

1. **The app cannot transmit anything.** `INTERNET` is absent from the artifact, and
   `aab-permissions.py` fails the upload if it ever appears. This one fact is most of the answer.
2. **Auto Backup is still "no data collected".** The platform moves the user's backup to the user's
   own Google Drive; the developer never sees it. That is Android transmitting the user's backup, not
   the app transmitting to the developer. ⚠ Every file the backup carries lands on the user's *next*
   phone too — see ADR-0005 for what is included and why.
3. **A support mail is still "no data collected", and the reason is the definition rather than an
   exemption.** A `mailto:` hands a draft to **another app**; the user reads it, can edit every line,
   and sends it from their own mail client, or does not. ⚠ **Do not write "Play exempts support
   flows" in a re-declaration.** The help page has no such exemption; the claim is true because of
   what "collect" means, and that is the reason to give. What the developer then holds is a mail in
   a mailbox, kept until the sender asks for it to be deleted, as the policy's *Deleting your data*
   states.
4. **A saved Route is not location.** It is a name and the points the user picked on the map or
   from search, never a fix from the phone, and it lives in the database that point 2's backup
   already carries. Since phase 5's checkpoint D; still no data collected.
5. **A recorded Hike is location, stored and not collected.** Its Track is the phone's fixes, kept
   in the database on the device, and leaves it only by point 2's backup, which is the user's own,
   or by a share the user makes. Play's "collected" means transmitted off the device by the app,
   which never happens. Since phase 6's checkpoint B; still no data collected.
6. **Following's Route and Progress are location, stored and not collected.** While following,
   the Route's points and how far along it the user is are kept in the app's settings on the
   device, and leave it only by point 2's backup, which is the user's own, or by a backup file the
   user exports. Since phase 6b's checkpoint E; still no data collected.

Would change any of it:

- **`INTERNET` on the artifact**, from our code or a dependency. Every answer here becomes a question.
- **Anything the app sends by itself**, such as crash reporting or analytics. There is none today.
- **A photo's metadata surviving into storage or a backup.** Reading the location to draw the dot is
  not collection: it never leaves the device.
- **The preferences file joining the backup.** It holds the map's last centre, which is the user's
  position at the fix's precision after the position button centred it. `AppBackupAgent` carries
  only the database and thumbnails today, so that centre stays on the phone; the policy says so.

## Advertising ID

**No.** The artifact carries no `com.google.android.gms.permission.AD_ID`, and `aab-permissions.py`
forbids it. Answered 2026-10-20.

## Foreground service permissions

**`dataSync`, for Play Asset Delivery's extraction service**, from phase 2b's checkpoint B
([`phase-2b.md`](phase-2b.md) § 2). Kozica starts no foreground service of its own; the library's
service unpacks each Region's pack, and the relief's ([`phase-4.md`](phase-4.md) § 3), after the
Play Store has downloaded it, right after the install ([`phase-2c.md`](phase-2c.md)). **Not asked by
the Console**: on 2026-10-27, with 0.9.0 live on internal, the form listed only
`FOREGROUND_SERVICE_LOCATION`, so nothing was declared for `dataSync`. If a later form lists it,
the answers to give:

- **What the user sees:** the maps and the relief that arrive with the app after it is installed
  being made ready to use offline, with the system's foreground-service notification while it runs.
- **Why it cannot wait:** the maps are what the app is installed for, and they must be unpacked as
  soon as Play has downloaded them, while the app may be in the background, or the map is missing
  on the trail where there is no signal.
- **Type:** `dataSync`, which covers transferring to the device the data the app needs to work.

**`location`, for recording a Hike**, from phase 6's checkpoint B ([`phase-6.md`](phase-6.md) § 2).
**Declared 2026-10-27**, for 0.9.0 (versionCode 301). The Console's form appeared only once 301 was
live on internal testing: the API upload was refused at commit ("You must let us know whether your
app uses any Foreground Service permissions") while the form still listed nothing, so 301 went up
through the Console and the form was answered after. It asks no free text: *Background location
updates → Other*, with a video link (Record, the recording notification, Stop, recorded on the
owner's phone and shared from Drive). What the form does not ask, kept for a reviewer's question:

- **What the user sees:** after pressing Record, a notification that a hike is recording, with its
  time and distance and Pause and Stop, until the user stops it.
- **Why it cannot wait:** the user walks with the phone in a pocket and the screen off for hours,
  and the track is the feature; a fix missed is a part of the hike lost.
- **Type:** `location`, started from the visible app; no background location permission is asked.

Each foreground service the app gains is recorded here with its type and the declaration's answers.

## Privacy policy

**`https://maksforge.com/kozica/privacy-policy.html`**, switched in the Console on 2026-10-25, when
the `maksforge` organization took the `maksforge.com` domain; before that it was
`https://maksforge.github.io/kozica/privacy-policy.html`, set earlier the same day when the repo moved
to the `maksforge` organization, and before that `https://srednimax.github.io/kozica/privacy-policy.html`,
set 2026-10-20. Both were retired on 2026-11-07 and now return 404. Served from `docs/` by
maksforge.com, so it updates within about 15 minutes of a merge to `main` with no release, which is
why it has to be right before the app it describes ships rather than after.

## Store settings (not App content, recorded here because it is asked for the same reason)

- **Category:** Maps & navigation, set 2026-10-20. Not Travel & local, which is bookings and guides,
  and not Health & fitness, whose claims this file keeps out of the listing.
- **Tags:** none.
- **Contact email:** `kozica.support@maksforge.com`, public on the listing and the same address
  `SupportHandoff.kt` compiles in. The Console field changes without a release; the app's copy needs
  one.

## Production access — the application, and the answers it was given

**DRAFT, 2026-11-11 — not submitted.** Typed into the Console's form, which waits on the owner's
review and their own Submit. Once it is sent, change this line to *Submitted <date>* and keep the
answers below **verbatim**: the Console asks the same set again for the next app on this account, and
an answer re-read a year later has to be the one that was given. Each box takes at most 300
characters; the count is beside each question.

Urgent's application, the same questions on this account, came back on 2026-11-03: *"Your app
requires more testing to access Google Play production. We've reviewed your application and have
determined that your app requires more testing before you can access production."* Its answers
rested on testers staying opted in and saying they liked the interface. These rest on what the
Console itself shows.

**About your closed test**

- *How did you recruit users for your closed test? For example, did you ask friends and family or use a paid testing provider?* (254/300) —
  Through a community app where Android developers test each other's closed tests, whose Google Group is on the track, and my own MaksForge testers' Google Groups. No paid provider. Testers joined within a day of the build reaching the track on 27 October.
- *How easy was it to recruit testers for your app?* (a choice) — Easy.
- *Describe the engagement you received from testers during your closed test. Include whether or not testers utilised all of the features in your app and whether tester usage was consistent with how you would expect a real user to use your app.* (298/300) —
  Play Console: installs rose from 2 to 13 the day after the 27 Oct build reached testers and held 13 to 16 to 8 Nov; 12 took the 30 Oct update within 3 days; no crash or ANR. No internet or analytics, so I can't see features used. 13 of 15 devices are outside Poland, so few could use it on a trail.
- *Provide a summary of the feedback that you received from testers. Include how you collected the feedback.* (281/300) —
  Two channels: Play's private testing feedback, and Report a problem and Suggest a feature in the app's Settings, which open an email to kozica.support@maksforge.com. Play's Testing feedback page shows no entries from the test, and Android vitals show no crash or ANR in the period.

**About your app**

- *Who is the intended audience of your app?* (213/300) —
  Adults (18+) who hike the marked trails of the Polish mountains, and the Slovak and Czech sides of the border, and want a map that works with no signal, drawn in the signposts' colours, with no account and no ads.
- *Describe how your app provides value to users* (296/300) —
  All ranges from the Karkonosze to the Bieszczady arrive with the app and work with no signal. Trails are drawn in their signpost colours. Plan routes over marked trails with signpost walking times, follow them, record hikes with the screen off, export GPX. No internet permission, ads or account.
- *How many installs do you expect your app to have in your first year?* (a choice) — 0 – 10K.

**Your production readiness**

- *What changes did you make to your app based on what you learned during your closed test?* (300/300) —
  Testers sent no change requests through Play, so these came from my own use in the same weeks: Play re-sent the relief whole on every update, so I cut its download to a third; following resumes after the app is closed; route buttons work in landscape; trails mapped as proposed or abandoned are gone.
- *How did you decide that your app is ready for production?* (278/300) —
  Read on two Xiaomi phones (Android 11 and 16) and emulators: a 2 h 15 min recording, screen locked, kept 1,593 fixes with no gap over 60 s; recording and following survive a killed app; walking times checked against 10 signposts; no crash or ANR in Android vitals over the test.

*Rests on* (not part of the answers), each read on 2026-11-11:

- **Recruiting:** the *Closed testing - Alpha* track's Testers tab lists three Google Groups,
  `testers-community@googlegroups.com`, `maks-forge-testers@googlegroups.com` and
  `gloam-testers@googlegroups.com`. Its *Feedback URL or email address* field is empty. Release
  history: 0.1.0 (versionCode 6) 2026-10-20, 0.10.0 (315) 2026-10-27 21:30, 0.13.0 (393) 2026-10-30
  22:15, the track's latest. 0.14.0 to 0.16.0 went to internal testing only.
- **Engagement:** Statistics, *Installed audience*, daily, by app version: all versions at about 2 until
  27 Oct, 13 on 28 Oct, 13 to 16 from 29 Oct to 8 Nov; 0.13.0 at 3 on 30 Oct, 8 on 31 Oct, 9 on 1 Nov,
  12 on 2 Nov, 12 on 8 Nov. By country on 8 Nov: 15 in all, Poland 2, India 2, Pakistan 2, Austria 1.
  The Console's app list, last 28 days: 26 device acquisitions, 19 first opens, 19 monthly active
  devices. The Dashboard ticks all three closed-test criteria.
- **Feedback:** *Testing feedback* shows its empty state. The app's support hand-off has mailed
  `kozica.support@maksforge.com` since 0.4.1. What testers said in the community app is not visible in
  the Console and is not in this answer.
- **Stability:** *Crashes and ANRs*, all types, 14 Oct to 11 Nov: no issues, no affected users. No
  pre-launch report was generated.
- **Changes:** the release notes of 0.14.0, 0.14.1, 0.14.4 and 0.15.0 in
  [`store-listing.md`](store-listing.md); the relief's download in [`DOD.md`](DOD.md) (268 MB sent
  whole with every update; 86 MB instead of 257 MB, ADR-0009's 2026-11-01 amendment). No commit or
  issue in the test's window came from a tester.
- **Readiness:** [`phase-6.md`](phase-6.md) R2a (owner's phone, HyperOS, Android 16, 2026-10-26,
  17:26–19:41, screen locked, unplugged, no exemption: 1,593 fixes, no gap over 60 s) and R4
  (recording after `kill -9`, API 33 emulator); [`phase-6b.md`](phase-6b.md) R4 (following after a
  force-stop, test phone, Android 11); [`phase-5.md`](phase-5.md) R2 (ten signposts, five in the
  Tatry and five in the Beskidy).
- **Audience:** *Target audience and content* above, 18 and over.

**Three things the first app built from this template kept deliberate, so a later answer does not undo
them:**

1. **The audience answer agrees with *Target audience* above.** A reviewer reads the two side by side.
   A mismatch is a question rather than a rejection, but it can cost days.
2. **Engagement is answered from what the app can know.** With no `INTERNET` permission there is no
   telemetry, so *"I do not know how many used X"* is the true answer. A guessed figure would
   contradict *Data safety* in this same file.
3. **The changes answer says where the changes came from.** If the testers reported nothing, say so and
   name what came from your own use instead. Presenting your own work as tester-driven is an easy
   dishonest sentence, and the application does not need one.

## A tip (Payments policy — not a question Play asks, but one a reviewer answers)

**Default: no tip on any surface the release controls.** Not in the app, and not on the pages the
listing links to. Read this before adding one, because the policy text and its enforcement disagree.

- **The text allows it.** Payments policy §3.2 treats a tip as a peer-to-peer payment, outside Play
  Billing, when **100% reaches the creator** and **nothing unlocks** — no badge, no theme, no
  thanks-screen. §4's anti-steering rule excepts §3. So: one personal payment link, worded as a *tip to
  a person*, never a *donation to a project*.
- **Enforcement has rejected apps anyway.** StreetComplete (February 2022) was rejected for in-app
  Patreon, Liberapay and GitHub Sponsors links — and **for a link to its own home page, because that
  page carried donation information**. A Buy Me a Coffee link in the app this template was extracted
  from was flagged the same way. None of those is the §3.2 shape (tiers unlock things, a platform takes
  a cut, a project is not a person), but the distinction has to survive a reviewer skimming for
  "external payment link", and a pre-launch rejection costs the closed-test window.
- **The reach is one level out.** The first app built from this template moved its tip from the app to
  the repository and the app's site, then noticed that the listing's *Website* field and the last line
  of its full description point at exactly those pages. So "nothing the app links to" includes what
  the **listing** links to.
- **When to revisit:** once the app is live, with a track record to appeal from. The cheapest shape
  then is a tip on the repository alone, with the listing's Website field and full description kept
  off it. A Play Billing consumable is the unambiguous alternative, at the cost of a billing
  dependency, Google's cut, and an *in-app purchases* badge on the listing.

Region carve-outs (US external links since October 2025, the EEA's external-offers program) do not
help: both are fee programs with reporting obligations, which is not a serious route for occasional
tips. A sentence promising a *future* tip, with nothing to click, is a statement about pricing and is
fine anywhere.

---

## When Play asks again

1. Run `python3 scripts/aab-permissions.py` against the newest bundle, and compare its table with the
   one above. A new row is a new question.
2. Read `docs/privacy-policy.md` against the answers here. If one has moved, both move, in one commit.
3. Copy any value this file does not hold while the Console is open — a category, a date, a
   declaration's exact wording — so the next re-declaration can compare against it.
4. Re-date the sections you re-answered.
