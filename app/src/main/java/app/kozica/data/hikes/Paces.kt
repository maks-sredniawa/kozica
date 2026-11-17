package app.kozica.data.hikes

import app.kozica.data.OutingSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The user's [Pace], read again whenever the ended Hikes or the relief change; nothing of it is
 * stored, so a trim, a delete or a restore changes it (docs/phase-6b.md § 2).
 *
 * @param hikes newest first.
 * @param relief whether the relief is on the phone, which decides whether a Track's heights are the grid's.
 * @param ruleSecondsOf the rule's time for a Hike's Track, which reads its heights; null where it cannot be timed, and throws where the read failed.
 */
class Paces(
    hikes: Flow<List<OutingSummary>>,
    relief: Flow<Boolean>,
    private val ruleSecondsOf: suspend (id: String) -> Double?,
) {
    // By id and length, since a trim changes the length, and for one state of the relief.
    private val timed = mutableMapOf<Pair<String, Double>, Double?>()
    private var timedWithRelief: Boolean? = null
    private val readFailed = mutableSetOf<String>()

    val pace: Flow<Pace?> =
        combine(hikes, relief.distinctUntilChanged()) { hikes, relief ->
            if (relief != timedWithRelief) {
                timed.clear()
                timedWithRelief = relief
            }
            val samples = samples(hikes)
            paceOf(samples).also { pace ->
                logPace(hikes.mapIndexed { i, hike -> hike.id to samples.getOrNull(i) }, pace, readFailed)
            }
        }.distinctUntilChanged()

    private suspend fun samples(hikes: List<OutingSummary>): List<PaceSample> {
        val samples = mutableListOf<PaceSample>()
        for (hike in hikes) {
            if (samples.count { it.counts } == PACE_HIKES_READ) break
            val sample = PaceSample(hike.metres, hike.movingSeconds, ruleSeconds = null)
            samples += if (sample.mayCount) sample.copy(ruleSeconds = ruleSeconds(hike)) else sample
        }
        return samples
    }

    private suspend fun ruleSeconds(hike: OutingSummary): Double? {
        val key = hike.id to hike.metres
        if (key in timed) return timed[key]
        return try {
            ruleSecondsOf(hike.id).also {
                timed[key] = it
                readFailed -= hike.id
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            readFailed += hike.id
            null
        }
    }
}
