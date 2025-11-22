package net.bonstio.traintimes

/**
 * Data class representing a train service departure.
 *
 * @property std The scheduled time of departure.
 * @property destination The destination of the train.
 * @property platform The platform number (nullable as it might not be assigned yet).
 * @property status The status of the train (e.g., "On time", "Cancelled", "Exp HH:MM").
 * @property subsequentCallingPoints A list of names of subsequent stations this train will call at.
 */
data class TrainService(
    val std: String,
    val destination: String,
    val platform: String?,
    val status: String,
    val subsequentCallingPoints: List<String>
)