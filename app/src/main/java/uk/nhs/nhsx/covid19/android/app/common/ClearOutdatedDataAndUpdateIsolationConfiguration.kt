package uk.nhs.nhsx.covid19.android.app.common

import com.jeroenmols.featureflag.framework.FeatureFlag.STORE_EXPOSURE_WINDOWS
import com.jeroenmols.featureflag.framework.RuntimeBehavior
import uk.nhs.nhsx.covid19.android.app.exposure.encounter.ExposureNotificationTokensProvider
import uk.nhs.nhsx.covid19.android.app.exposure.encounter.calculation.EpidemiologyEventProvider
import uk.nhs.nhsx.covid19.android.app.remote.IsolationConfigurationApi
import uk.nhs.nhsx.covid19.android.app.state.IsolationConfigurationProvider
import uk.nhs.nhsx.covid19.android.app.state.IsolationStateMachine
import uk.nhs.nhsx.covid19.android.app.state.State.Default
import uk.nhs.nhsx.covid19.android.app.testordering.TestResultsProvider
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class ClearOutdatedDataAndUpdateIsolationConfiguration(
    private val isolationStateMachine: IsolationStateMachine,
    private val testResultsProvider: TestResultsProvider,
    private val isolationConfigurationProvider: IsolationConfigurationProvider,
    private val isolationConfigurationApi: IsolationConfigurationApi,
    private val exposureNotificationTokensProvider: ExposureNotificationTokensProvider,
    private val epidemiologyEventProvider: EpidemiologyEventProvider,
    private val clock: Clock
) {

    @Inject
    constructor(
        isolationStateMachine: IsolationStateMachine,
        testResultsProvider: TestResultsProvider,
        isolationConfigurationProvider: IsolationConfigurationProvider,
        isolationConfigurationApi: IsolationConfigurationApi,
        exposureNotificationTokensProvider: ExposureNotificationTokensProvider,
        epidemiologyEventProvider: EpidemiologyEventProvider
    ) : this(
        isolationStateMachine,
        testResultsProvider,
        isolationConfigurationProvider,
        isolationConfigurationApi,
        exposureNotificationTokensProvider,
        epidemiologyEventProvider,
        Clock.systemDefaultZone()
    )

    suspend operator fun invoke(): Result<Unit> = runSafely {

        updateIsolationConfiguration()

        val expiryDays = isolationConfigurationProvider.durationDays.pendingTasksRetentionPeriod

        val state = isolationStateMachine.readState()
        if (state is Default) {
            if (state.previousIsolation == null) {
                testResultsProvider.clearBefore(LocalDate.now(clock).minusDays(expiryDays.toLong()))
            } else if (state.previousIsolation.expiryDate.isMoreThanOrExactlyDaysAgo(expiryDays)) {
                testResultsProvider.clearBefore(state.previousIsolation.expiryDate)
                isolationStateMachine.clearPreviousIsolation()
            }

            if (RuntimeBehavior.isFeatureEnabled(STORE_EXPOSURE_WINDOWS)) {
                if (exposureNotificationTokensProvider.tokens.isEmpty()) {
                    epidemiologyEventProvider.clear()
                }
            }
        }
    }

    private suspend fun updateIsolationConfiguration() {
        runCatching {
            val response = isolationConfigurationApi.getIsolationConfiguration()
            isolationConfigurationProvider.durationDays = response.durationDays
        }
    }

    private fun LocalDate.isMoreThanOrExactlyDaysAgo(days: Int) =
        until(
            LocalDate.now(clock),
            ChronoUnit.DAYS
        ) >= days
}
