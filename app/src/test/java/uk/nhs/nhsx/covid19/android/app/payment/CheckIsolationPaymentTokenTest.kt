package uk.nhs.nhsx.covid19.android.app.payment

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Test
import uk.nhs.nhsx.covid19.android.app.common.postcode.PostCodeDistrict
import uk.nhs.nhsx.covid19.android.app.common.postcode.PostalDistrictProviderWrapper
import uk.nhs.nhsx.covid19.android.app.payment.IsolationPaymentTokenState.Disabled
import uk.nhs.nhsx.covid19.android.app.payment.IsolationPaymentTokenState.Token
import uk.nhs.nhsx.covid19.android.app.payment.IsolationPaymentTokenState.Unresolved
import uk.nhs.nhsx.covid19.android.app.remote.IsolationPaymentApi
import uk.nhs.nhsx.covid19.android.app.remote.data.IsolationPaymentCountry.ENGLAND
import uk.nhs.nhsx.covid19.android.app.remote.data.IsolationPaymentCountry.WALES
import uk.nhs.nhsx.covid19.android.app.remote.data.IsolationPaymentCreateTokenRequest
import uk.nhs.nhsx.covid19.android.app.remote.data.IsolationPaymentCreateTokenResponse

class CheckIsolationPaymentTokenTest {

    private val canClaimIsolationPayment = mockk<CanClaimIsolationPayment>(relaxed = true)
    private val isolationPaymentTokenProvider = mockk<IsolationPaymentTokenStateProvider>(relaxed = true)
    private val isolationPaymentApi = mockk<IsolationPaymentApi>(relaxed = true)
    private val postalDistrictProviderWrapper = mockk<PostalDistrictProviderWrapper>(relaxed = true)

    private val testSubject = CheckIsolationPaymentToken(
        canClaimIsolationPayment,
        isolationPaymentTokenProvider,
        isolationPaymentApi,
        postalDistrictProviderWrapper
    )

    @Test
    fun `sets token state to unresolved if user cannot claim isolation payment`() = runBlocking {
        every { canClaimIsolationPayment() } returns false

        testSubject()

        verify { isolationPaymentTokenProvider setProperty "tokenState" value eq(Unresolved) }
        coVerify(exactly = 0) { isolationPaymentApi.createToken(any()) }
    }

    @Test
    fun `calls create token and stores unresolved if user can claim isolation payment, has an unresolved token state, is in England, and response is enabled but returns a null token`() = runBlocking {
        // Note that this would be a backend error, but we still try to handle it

        every { canClaimIsolationPayment() } returns true
        every { isolationPaymentTokenProvider.tokenState } returns Unresolved
        coEvery { postalDistrictProviderWrapper.getPostCodeDistrict() } returns PostCodeDistrict.ENGLAND
        coEvery { isolationPaymentApi.createToken(any()) } returns IsolationPaymentCreateTokenResponse(
            true,
            null
        )

        testSubject()

        coVerify { isolationPaymentApi.createToken(IsolationPaymentCreateTokenRequest(ENGLAND)) }
        verify { isolationPaymentTokenProvider setProperty "tokenState" value eq(Unresolved) }
    }

    @Test
    fun `calls create token and stores token if user can claim isolation payment, has an unresolved token state, is in England, and response is enabled`() = runBlocking {
        val token = "token"
        every { canClaimIsolationPayment() } returns true
        every { isolationPaymentTokenProvider.tokenState } returns Unresolved
        coEvery { postalDistrictProviderWrapper.getPostCodeDistrict() } returns PostCodeDistrict.ENGLAND
        coEvery { isolationPaymentApi.createToken(any()) } returns IsolationPaymentCreateTokenResponse(
            true,
            token
        )

        testSubject()

        coVerify { isolationPaymentApi.createToken(IsolationPaymentCreateTokenRequest(ENGLAND)) }
        verify { isolationPaymentTokenProvider setProperty "tokenState" value eq(Token(token)) }
    }

    @Test
    fun `calls create token and stores token if user can claim isolation payment, has an unresolved token state, is in Wales, and response is enabled`() = runBlocking {
        val token = "token"
        every { canClaimIsolationPayment() } returns true
        every { isolationPaymentTokenProvider.tokenState } returns Unresolved
        coEvery { postalDistrictProviderWrapper.getPostCodeDistrict() } returns PostCodeDistrict.WALES
        coEvery { isolationPaymentApi.createToken(any()) } returns IsolationPaymentCreateTokenResponse(
            true,
            token
        )

        testSubject()

        coVerify { isolationPaymentApi.createToken(IsolationPaymentCreateTokenRequest(WALES)) }
        verify { isolationPaymentTokenProvider setProperty "tokenState" value eq(Token(token)) }
    }

    @Test
    fun `calls create token and stores disabled state if user can claim isolation payment, has an unresolved token state, is in England, and response is disabled`() = runBlocking {
        val token = "token"
        every { canClaimIsolationPayment() } returns true
        every { isolationPaymentTokenProvider.tokenState } returns Unresolved
        coEvery { postalDistrictProviderWrapper.getPostCodeDistrict() } returns PostCodeDistrict.ENGLAND
        coEvery { isolationPaymentApi.createToken(any()) } returns IsolationPaymentCreateTokenResponse(
            false,
            token
        )

        testSubject()

        coVerify { isolationPaymentApi.createToken(IsolationPaymentCreateTokenRequest(ENGLAND)) }
        verify { isolationPaymentTokenProvider setProperty "tokenState" value eq(Disabled) }
    }

    @Test
    fun `does nothing if user can claim isolation payment, has an unresolved token state, and is in Scotland`() = runBlocking {
        every { canClaimIsolationPayment() } returns true
        every { isolationPaymentTokenProvider.tokenState } returns Unresolved
        coEvery { postalDistrictProviderWrapper.getPostCodeDistrict() } returns PostCodeDistrict.SCOTLAND

        testSubject()

        coVerify(exactly = 0) { isolationPaymentApi.createToken(any()) }
        verify(exactly = 0) { isolationPaymentTokenProvider setProperty "tokenState" value any<IsolationPaymentTokenState>() }
    }

    @Test
    fun `does nothing if user can claim isolation payment and already has a token`() = runBlocking {
        every { canClaimIsolationPayment() } returns true
        every { isolationPaymentTokenProvider.tokenState } returns Token("token")

        testSubject()

        coVerify(exactly = 0) { isolationPaymentApi.createToken(any()) }
        verify(exactly = 0) { isolationPaymentTokenProvider setProperty "tokenState" value any<IsolationPaymentTokenState>() }
    }

    @Test
    fun `does nothing if user can claim isolation payment and token is disabled`() = runBlocking {
        every { canClaimIsolationPayment() } returns true
        every { isolationPaymentTokenProvider.tokenState } returns Disabled

        testSubject()

        coVerify(exactly = 0) { isolationPaymentApi.createToken(any()) }
        verify(exactly = 0) { isolationPaymentTokenProvider setProperty "tokenState" value any<IsolationPaymentTokenState>() }
    }

    @Test
    fun `does not crash if creating token throws an exception`() = runBlocking {
        every { canClaimIsolationPayment() } returns true
        every { isolationPaymentTokenProvider.tokenState } returns Unresolved
        coEvery { isolationPaymentApi.createToken(any()) } throws Exception()

        testSubject()

        return@runBlocking
    }
}
