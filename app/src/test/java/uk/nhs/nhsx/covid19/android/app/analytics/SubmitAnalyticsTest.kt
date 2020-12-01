package uk.nhs.nhsx.covid19.android.app.analytics

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Test
import uk.nhs.nhsx.covid19.android.app.analytics.legacy.AggregateAnalytics
import uk.nhs.nhsx.covid19.android.app.analytics.legacy.AnalyticsAlarm
import uk.nhs.nhsx.covid19.android.app.analytics.legacy.AnalyticsEventsStorage
import uk.nhs.nhsx.covid19.android.app.common.Result.Success
import uk.nhs.nhsx.covid19.android.app.remote.AnalyticsApi
import uk.nhs.nhsx.covid19.android.app.remote.data.AnalyticsPayload
import uk.nhs.nhsx.covid19.android.app.remote.data.AnalyticsWindow
import uk.nhs.nhsx.covid19.android.app.remote.data.Metadata
import uk.nhs.nhsx.covid19.android.app.remote.data.Metrics
import uk.nhs.nhsx.covid19.android.app.util.toISOSecondsFormat
import java.time.Instant
import kotlin.test.assertEquals

class SubmitAnalyticsTest {
    private val analyticsMetricsLogStorage = mockk<AnalyticsMetricsLogStorage>(relaxed = true)
    private val analyticsApi = mockk<AnalyticsApi>(relaxed = true)
    private val groupAnalyticsEvents = mockk<GroupAnalyticsEvents>(relaxed = true)
    private val aggregateAnalytics = mockk<AggregateAnalytics>(relaxed = true)
    private val analyticsEventsStorage = mockk<AnalyticsEventsStorage>(relaxed = true)
    private val analyticsAlarm = mockk<AnalyticsAlarm>(relaxed = true)

    private val testSubject = SubmitAnalytics(
        analyticsMetricsLogStorage,
        analyticsApi,
        groupAnalyticsEvents,
        aggregateAnalytics,
        analyticsEventsStorage,
        analyticsAlarm
    )

    @Test
    fun `migration test will successfully send events from previous verions`() = runBlocking {
        coEvery { groupAnalyticsEvents.invoke() } returns stubAnalyticsPayload(0)

        every { analyticsEventsStorage.value } returns stubAnalyticsPayload(2).value

        testSubject.invoke()

        verify(exactly = 1) { analyticsAlarm.cancel() }

        coVerify(exactly = 1) { aggregateAnalytics.invoke() }

        coVerify(exactly = 2) { analyticsApi.submitAnalytics(any()) }

        coVerify(exactly = 1) { analyticsEventsStorage.value = null }
    }

    @Test
    fun `successfully submit analytics events`() = runBlocking {
        coEvery { groupAnalyticsEvents.invoke() } returns stubAnalyticsPayload(2)

        testSubject.invoke()

        coVerify(exactly = 2) { analyticsApi.submitAnalytics(any()) }
        verify(exactly = 2) { analyticsMetricsLogStorage.remove(any(), any()) }

        val result = testSubject.invoke()

        assertEquals(Success(Unit), result)
    }

    @Test
    fun `on submission error will clear events`() = runBlocking {
        val testException = Exception()
        coEvery { groupAnalyticsEvents.invoke() } returns stubAnalyticsPayload(3)

        coEvery { analyticsApi.submitAnalytics(any()) } throws testException

        val result = testSubject.invoke()

        verify(exactly = 3) { analyticsMetricsLogStorage.remove(any(), any()) }

        assertEquals(Success(Unit), result)
    }

    private val startDate = Instant.parse("2020-09-25T00:00:00Z")

    private val endDate = Instant.parse("2020-09-26T00:00:00Z")

    private fun stubAnalyticsPayload(size: Int) =
        Success(
            mutableListOf<AnalyticsPayload>().apply {
                repeat(size) {
                    add(
                        AnalyticsPayload(
                            analyticsWindow = AnalyticsWindow(
                                startDate = startDate.toISOSecondsFormat(),
                                endDate = endDate.toISOSecondsFormat()
                            ),
                            includesMultipleApplicationVersions = false,
                            metadata = Metadata(
                                deviceModel = "",
                                latestApplicationVersion = "",
                                operatingSystemVersion = "",
                                postalDistrict = ""
                            ),
                            metrics = Metrics()
                        )
                    )
                }
            }
        )
}
