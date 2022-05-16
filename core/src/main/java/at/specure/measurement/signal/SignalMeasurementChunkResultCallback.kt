package at.specure.measurement.signal

import at.specure.data.entity.SignalMeasurementInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi

interface SignalMeasurementChunkResultCallback {

    @ExperimentalCoroutinesApi
    fun newUUIDSent(respondedUuid: String, info: SignalMeasurementInfo)
}