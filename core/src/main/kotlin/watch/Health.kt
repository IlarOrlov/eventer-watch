package watch

enum class Outcome { OK, FAILED, SHAPE_CHANGED }

enum class HealthEvent { WENT_BLIND, RECOVERED }

class Health(val blindAfter: Int = 5) {
    private var failures = 0

    val isBlind: Boolean get() = failures >= blindAfter

    fun record(o: Outcome): HealthEvent? {
        val wasBlind = isBlind
        if (o == Outcome.OK) {
            failures = 0
            return if (wasBlind) HealthEvent.RECOVERED else null
        }
        failures++
        return if (!wasBlind && isBlind) HealthEvent.WENT_BLIND else null
    }
}
