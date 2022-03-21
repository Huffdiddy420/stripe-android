package com.stripe.android.payments.bankaccount.ui

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.stripe.android.connections.ConnectionsSheetResult
import com.stripe.android.connections.ConnectionsSheetResult.Canceled
import com.stripe.android.connections.ConnectionsSheetResult.Completed
import com.stripe.android.connections.ConnectionsSheetResult.Failed
import com.stripe.android.core.Logger
import com.stripe.android.payments.bankaccount.CollectBankAccountConfiguration.USBankAccount
import com.stripe.android.payments.bankaccount.CollectBankAccountResponse
import com.stripe.android.payments.bankaccount.di.DaggerCollectBankAccountComponent
import com.stripe.android.payments.bankaccount.domain.AttachLinkAccountSession
import com.stripe.android.payments.bankaccount.domain.CreateLinkAccountSession
import com.stripe.android.payments.bankaccount.navigation.CollectBankAccountContract
import com.stripe.android.payments.bankaccount.navigation.CollectBankAccountContract.Args.ForPaymentIntent
import com.stripe.android.payments.bankaccount.navigation.CollectBankAccountContract.Args.ForSetupIntent
import com.stripe.android.payments.bankaccount.navigation.CollectBankAccountResult
import com.stripe.android.payments.bankaccount.ui.CollectBankAccountViewEffect.OpenConnectionsFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class CollectBankAccountViewModel @Inject constructor(
    private val args: CollectBankAccountContract.Args,
    private val createLinkAccountSession: CreateLinkAccountSession,
    private val attachLinkAccountSession: AttachLinkAccountSession,
    private val logger: Logger
) : ViewModel() {

    private val _viewEffect = MutableSharedFlow<CollectBankAccountViewEffect>()
    val viewEffect: SharedFlow<CollectBankAccountViewEffect> = _viewEffect

    init {
        viewModelScope.launch {
            createLinkAccountSession()
        }
    }

    private suspend fun createLinkAccountSession() {
        when (val params = args.params) {
            is USBankAccount -> when (args) {
                is ForPaymentIntent -> createLinkAccountSession.forPaymentIntent(
                    publishableKey = args.publishableKey,
                    clientSecret = args.clientSecret,
                    customerName = params.name,
                    customerEmail = params.email
                )
                is ForSetupIntent -> createLinkAccountSession.forSetupIntent(
                    publishableKey = args.publishableKey,
                    clientSecret = args.clientSecret,
                    customerName = params.name,
                    customerEmail = params.email
                )
            }
                .onSuccess { linkedAccountSession ->
                    logger.debug("Bank account session created! $linkedAccountSession.")
                    _viewEffect.emit(
                        OpenConnectionsFlow(
                            linkedAccountSessionClientSecret = linkedAccountSession,
                            publishableKey = args.publishableKey
                        )
                    )
                }
                .onFailure { finishWithError(it) }
        }
    }

    fun onConnectionsResult(result: ConnectionsSheetResult) {
        viewModelScope.launch {
            when (result) {
                Canceled -> finishWithResult(CollectBankAccountResult.Cancelled)
                is Failed -> finishWithError(result.error)
                is Completed -> attachLinkAccountSessionToIntent(result.linkAccountSession.id)
            }
        }
    }

    private suspend fun finishWithResult(result: CollectBankAccountResult) {
        _viewEffect.emit(CollectBankAccountViewEffect.FinishWithResult(result))
    }

    private fun attachLinkAccountSessionToIntent(linkedAccountSessionId: String) {
        viewModelScope.launch {
            when (args) {
                is ForPaymentIntent -> attachLinkAccountSession.forPaymentIntent(
                    publishableKey = args.publishableKey,
                    clientSecret = args.clientSecret,
                    linkedAccountSessionId = linkedAccountSessionId
                ).mapCatching {
                    CollectBankAccountResult.Completed(CollectBankAccountResponse(it.value))
                }
                is ForSetupIntent -> attachLinkAccountSession.forSetupIntent(
                    publishableKey = args.publishableKey,
                    clientSecret = args.clientSecret,
                    linkedAccountSessionId = linkedAccountSessionId
                ).mapCatching {
                    CollectBankAccountResult.Completed(CollectBankAccountResponse(it.value))
                }
            }
                .onSuccess { result: CollectBankAccountResult.Completed ->
                    logger.debug("Bank account session attached to  intent!!")
                    finishWithResult(result)
                }
                .onFailure { finishWithError(it) }
        }
    }

    private suspend fun finishWithError(throwable: Throwable) {
        logger.error("Error", Exception(throwable))
        finishWithResult(CollectBankAccountResult.Failed(throwable))
    }

    class Factory(
        private val applicationSupplier: () -> Application,
        private val argsSupplier: () -> CollectBankAccountContract.Args,
        owner: SavedStateRegistryOwner,
        defaultArgs: Bundle? = null
    ) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(
            key: String,
            modelClass: Class<T>,
            savedStateHandle: SavedStateHandle
        ): T {
            return DaggerCollectBankAccountComponent.builder()
                .application(applicationSupplier())
                .configuration(argsSupplier()).build()
                .viewModel as T
        }
    }
}
