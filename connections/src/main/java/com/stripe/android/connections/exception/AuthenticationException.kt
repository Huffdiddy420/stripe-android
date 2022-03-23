package com.stripe.android.connections.exception

import com.stripe.android.core.StripeError
import com.stripe.android.core.exception.StripeException
import java.net.HttpURLConnection

/**
 * No valid API key provided.
 *
 * [Errors](https://stripe.com/docs/api/errors)
 */
internal class AuthenticationException internal constructor(
    stripeError: StripeError,
    requestId: String? = null
) : StripeException(
    stripeError,
    requestId,
    HttpURLConnection.HTTP_UNAUTHORIZED
)