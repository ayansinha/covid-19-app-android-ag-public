package uk.nhs.nhsx.covid19.android.app.onboarding.postcode

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.observe
import com.google.android.material.appbar.MaterialToolbar
import com.jeroenmols.featureflag.framework.FeatureFlag
import com.jeroenmols.featureflag.framework.RuntimeBehavior
import kotlinx.android.synthetic.main.activity_post_code.postCodeContinue
import kotlinx.android.synthetic.main.activity_post_code.postCodeView
import kotlinx.android.synthetic.main.include_onboarding_toolbar.toolbar
import timber.log.Timber
import uk.nhs.nhsx.covid19.android.app.R
import uk.nhs.nhsx.covid19.android.app.appComponent
import uk.nhs.nhsx.covid19.android.app.common.BaseActivity
import uk.nhs.nhsx.covid19.android.app.common.ViewModelFactory
import uk.nhs.nhsx.covid19.android.app.common.postcode.LocalAuthorityActivity
import uk.nhs.nhsx.covid19.android.app.common.postcode.LocalAuthorityPostCodeValidator.LocalAuthorityPostCodeValidationResult.Invalid
import uk.nhs.nhsx.covid19.android.app.common.postcode.LocalAuthorityPostCodeValidator.LocalAuthorityPostCodeValidationResult.ParseJsonError
import uk.nhs.nhsx.covid19.android.app.common.postcode.LocalAuthorityPostCodeValidator.LocalAuthorityPostCodeValidationResult.Unsupported
import uk.nhs.nhsx.covid19.android.app.common.postcode.LocalAuthorityPostCodeValidator.LocalAuthorityPostCodeValidationResult.Valid
import uk.nhs.nhsx.covid19.android.app.common.postcode.PostCodeUpdater.PostCodeUpdateState.InvalidPostDistrict
import uk.nhs.nhsx.covid19.android.app.common.postcode.PostCodeUpdater.PostCodeUpdateState.PostDistrictNotSupported
import uk.nhs.nhsx.covid19.android.app.common.postcode.PostCodeUpdater.PostCodeUpdateState.Success
import uk.nhs.nhsx.covid19.android.app.onboarding.PermissionActivity
import uk.nhs.nhsx.covid19.android.app.util.viewutils.setNavigateUpToolbar
import javax.inject.Inject

class PostCodeActivity : BaseActivity(R.layout.activity_post_code) {

    @Inject
    lateinit var factory: ViewModelFactory<PostCodeViewModel>

    private val viewModel by viewModels<PostCodeViewModel> { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appComponent.inject(this)

        setNavigateUpToolbar(
            toolbar as MaterialToolbar,
            R.string.empty,
            upIndicator = R.drawable.ic_arrow_back_primary
        )

        postCodeContinue.setOnClickListener {
            if (RuntimeBehavior.isFeatureEnabled(FeatureFlag.LOCAL_AUTHORITY)) {
                viewModel.validateMainPostCode(postCodeView.postCodeDistrict)
            } else {
                viewModel.updateMainPostCode(postCodeView.postCodeDistrict)
            }
        }

        viewModel.viewState().observe(this) { postCodeUpdateState ->
            when (postCodeUpdateState) {
                is Success -> PermissionActivity.start(this)
                PostDistrictNotSupported -> postCodeView.showPostCodeNotSupportedErrorState()
                InvalidPostDistrict -> postCodeView.showErrorState()
            }
        }

        viewModel.postCodeValidationResult().observe(this) { validationResult ->
            when (validationResult) {
                is Valid -> {
                    val intent = LocalAuthorityActivity.getIntent(this, validationResult.postCode)
                    startActivityForResult(intent, LOCAL_AUTHORITY_REQUEST)
                }
                ParseJsonError -> Timber.d("Error parsing localAuthorities.json")
                Invalid -> postCodeView.showErrorState()
                Unsupported -> postCodeView.showPostCodeNotSupportedErrorState()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == LOCAL_AUTHORITY_REQUEST && resultCode == RESULT_OK) {
            PermissionActivity.start(this)
        }
    }

    companion object {
        private const val LOCAL_AUTHORITY_REQUEST = 1338

        fun start(context: Context) = context.startActivity(getIntent(context))

        private fun getIntent(context: Context) = Intent(context, PostCodeActivity::class.java)
    }
}
