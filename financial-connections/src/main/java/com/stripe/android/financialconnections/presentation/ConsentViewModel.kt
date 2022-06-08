package com.stripe.android.financialconnections.presentation

import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelFactory
import com.airbnb.mvrx.ViewModelContext
import com.stripe.android.financialconnections.di.subComponentBuilderProvider
import com.stripe.android.financialconnections.domain.AcceptConsent
import com.stripe.android.financialconnections.domain.GoNext
import com.stripe.android.financialconnections.domain.UpdateManifest
import com.stripe.android.financialconnections.model.FinancialConnectionsSessionManifest
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class ConsentViewModel @Inject constructor(
    initialState: ConsentState,
    private val acceptConsent: AcceptConsent,
    private val updateManifest: UpdateManifest,
    private val goNext: GoNext
) : MavericksViewModel<ConsentState>(initialState) {

    fun onContinueClick() {
        viewModelScope.launch {
            val manifest: FinancialConnectionsSessionManifest = acceptConsent()
            updateManifest(manifest)
            goNext(manifest)
        }
    }

    fun onManifestChanged(manifest: FinancialConnectionsSessionManifest) {
        setState {
            copy(
                title = manifest.businessName + " works with Stripe to link your accounts"
            )
        }
    }

    companion object : MavericksViewModelFactory<ConsentViewModel, ConsentState> {

        override fun create(
            viewModelContext: ViewModelContext,
            state: ConsentState
        ): ConsentViewModel {
            return viewModelContext.subComponentBuilderProvider
                .consentSubComponentBuilder.get()
                .initialState(state)
                .build()
                .viewModel
        }
    }
}

internal data class ConsentState(
    val title: String = ""
) : MavericksState