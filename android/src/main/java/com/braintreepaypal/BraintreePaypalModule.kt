package com.braintreepaypal

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import com.braintreepayments.api.paypal.PayPalAccountNonce
import com.braintreepayments.api.paypal.PayPalCheckoutRequest
import com.braintreepayments.api.paypal.PayPalClient
import com.braintreepayments.api.paypal.PayPalLauncher
import com.braintreepayments.api.paypal.PayPalPaymentAuthRequest
import com.braintreepayments.api.paypal.PayPalPaymentAuthResult
import com.braintreepayments.api.paypal.PayPalPaymentIntent
import com.braintreepayments.api.paypal.PayPalPaymentUserAction
import com.braintreepayments.api.paypal.PayPalPendingRequest
import com.braintreepayments.api.paypal.PayPalResult
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.module.annotations.ReactModule
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

@ReactModule(name = BraintreePaypalModule.NAME)
class BraintreePaypalModule(reactContext: ReactApplicationContext) :
  NativeBraintreePaypalSpec(reactContext), ActivityEventListener, LifecycleEventListener {

  // Flow state. Only read or written on the UI thread.
  private var promise: Promise? = null
  private var payPalLauncher: PayPalLauncher? = null
  private var payPalClient: PayPalClient? = null
  private var returnIntent: Intent? = null
  private var isShippingRequired = false
  private var flowStartedAt = 0L

  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .build()
  }

  init {
    reactContext.addLifecycleEventListener(this)
    reactContext.addActivityEventListener(this)
  }

  override fun getName(): String {
    return NAME
  }

  override fun showPayPal(
    serverUrl: String,
    amount: String,
    shippingRequired: Boolean,
    currency: String,
    appLink: String,
    email: String?,
    fallbackURLScheme: String?,
    promise: Promise
  ) {
    UiThreadUtil.runOnUiThread {
      startFlow(
        serverUrl, amount, shippingRequired, currency, appLink, email, fallbackURLScheme, promise
      )
    }
  }

  private fun startFlow(
    serverUrl: String,
    amount: String,
    shippingRequired: Boolean,
    currency: String,
    appLink: String,
    email: String?,
    fallbackURLScheme: String?,
    promise: Promise
  ) {
    if (this.promise != null) {
      promise.reject(ErrorCode.IN_PROGRESS, "A PayPal flow is already in progress")
      return
    }
    val activity = reactApplicationContext.currentActivity as? FragmentActivity
    if (activity == null) {
      promise.reject(ErrorCode.NO_ACTIVITY, "No activity available to launch PayPal")
      return
    }
    val request = try {
      Request.Builder().url(serverUrl).build()
    } catch (e: IllegalArgumentException) {
      promise.reject(ErrorCode.INVALID_URL, "Invalid server URL", e)
      return
    }

    this.promise = promise
    isShippingRequired = shippingRequired
    flowStartedAt = SystemClock.elapsedRealtime()
    returnIntent = null
    payPalLauncher = PayPalLauncher()
    // Drop a pending request left behind by a flow whose process was killed, so it cannot
    // settle this new flow.
    PendingRequestStore.getInstance().clearPayPalPendingRequest(reactApplicationContext)

    httpClient.newCall(request).enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        UiThreadUtil.runOnUiThread {
          rejectFlow(ErrorCode.NETWORK_ERROR, "Failed to fetch client token", e)
        }
      }

      override fun onResponse(call: Call, response: Response) {
        val statusCode = response.code
        val isSuccessful = response.isSuccessful
        val token = try {
          response.use { if (isSuccessful) it.body?.string()?.trim() else null }
        } catch (e: IOException) {
          UiThreadUtil.runOnUiThread {
            rejectFlow(ErrorCode.NETWORK_ERROR, "Failed to fetch client token", e)
          }
          return
        }

        UiThreadUtil.runOnUiThread {
          when {
            !isSuccessful -> rejectFlow(
              ErrorCode.TOKEN_ERROR, "Client token request failed with HTTP $statusCode", null
            )
            token.isNullOrEmpty() -> rejectFlow(ErrorCode.TOKEN_ERROR, "Token is empty", null)
            else -> launchPayPal(
              activity, token, amount, currency, appLink, email, fallbackURLScheme, shippingRequired
            )
          }
        }
      }
    })
  }

  private fun launchPayPal(
    activity: FragmentActivity,
    token: String,
    amount: String,
    currency: String,
    appLink: String,
    email: String?,
    fallbackURLScheme: String?,
    shippingRequired: Boolean
  ) {
    if (promise == null) return

    try {
      val client = PayPalClient(
        context = reactApplicationContext,
        authorization = token,
        appLinkReturnUrl = Uri.parse(appLink),
        fallbackURLScheme
      )
      payPalClient = client

      val checkoutRequest = PayPalCheckoutRequest(
        amount = amount,
        hasUserLocationConsent = false,
        intent = PayPalPaymentIntent.AUTHORIZE,
        userAction = PayPalPaymentUserAction.USER_ACTION_DEFAULT,
        currencyCode = currency,
        isShippingAddressRequired = shippingRequired,
        isShippingAddressEditable = shippingRequired,
        userAuthenticationEmail = email,
        shouldOfferPayLater = false
      )

      client.createPaymentAuthRequest(reactApplicationContext, checkoutRequest) { paymentAuthRequest ->
        UiThreadUtil.runOnUiThread { onPaymentAuthRequest(activity, paymentAuthRequest) }
      }
    } catch (e: Exception) {
      rejectFlow(ErrorCode.PAYPAL_ERROR, e.message ?: "Failed to start PayPal", e)
    }
  }

  private fun onPaymentAuthRequest(
    activity: FragmentActivity,
    paymentAuthRequest: PayPalPaymentAuthRequest
  ) {
    if (promise == null) return
    val launcher = payPalLauncher ?: return

    when (paymentAuthRequest) {
      is PayPalPaymentAuthRequest.ReadyToLaunch -> {
        when (val pendingRequest = launcher.launch(activity, paymentAuthRequest)) {
          is PayPalPendingRequest.Started -> {
            PendingRequestStore.getInstance()
              .putPayPalPendingRequest(reactApplicationContext, pendingRequest)
          }
          is PayPalPendingRequest.Failure -> {
            rejectFlow(
              ErrorCode.PAYPAL_ERROR,
              pendingRequest.error.message ?: "Failed to launch PayPal",
              pendingRequest.error
            )
          }
        }
      }
      is PayPalPaymentAuthRequest.Failure -> {
        rejectFlow(
          ErrorCode.PAYPAL_ERROR,
          paymentAuthRequest.error.message ?: "Failed to create PayPal request",
          paymentAuthRequest.error
        )
      }
    }
  }

  // Braintree recommends handling the return in onResume only, with the latest intent. Android
  // always delivers onNewIntent before onResume, so the return intent is stored there and consumed
  // here. Handling it in both callbacks could consume the flow with a stale intent first.
  private fun handleReturnToApp() {
    if (promise == null) return
    val launcher = payPalLauncher ?: return
    val store = PendingRequestStore.getInstance()
    val pendingRequest = store.getPayPalPendingRequest(reactApplicationContext) ?: return
    val intent = returnIntent ?: reactApplicationContext.currentActivity?.intent ?: return
    returnIntent = null
    store.clearPayPalPendingRequest(reactApplicationContext)

    when (val paymentAuthResult = launcher.handleReturnToApp(pendingRequest, intent)) {
      is PayPalPaymentAuthResult.Success -> tokenize(paymentAuthResult)
      is PayPalPaymentAuthResult.Failure -> rejectFlow(
        ErrorCode.PAYPAL_ERROR,
        paymentAuthResult.error.message ?: "PayPal flow failed",
        paymentAuthResult.error
      )
      // The user came back to the app without a result, for instance by closing the browser.
      PayPalPaymentAuthResult.NoResult -> rejectFlow(
        ErrorCode.NO_RESULT, "PayPalPaymentAuthResult.NoResult", null
      )
    }
  }

  private fun tokenize(paymentAuthResult: PayPalPaymentAuthResult.Success) {
    val client = payPalClient
    if (client == null) {
      rejectFlow(ErrorCode.PAYPAL_ERROR, "PayPal client is not initialized", null)
      return
    }

    client.tokenize(paymentAuthResult) { result ->
      UiThreadUtil.runOnUiThread {
        when (result) {
          is PayPalResult.Success -> resolveFlow(result.nonce)
          is PayPalResult.Failure -> rejectFlow(
            ErrorCode.PAYPAL_ERROR, result.error.message ?: "PayPal tokenization failed", result.error
          )
          is PayPalResult.Cancel -> rejectFlow(
            ErrorCode.USER_CANCELED, "User canceled the PayPal payment", null
          )
        }
      }
    }
  }

  // Flow lifecycle

  private fun endFlow() {
    promise = null
    payPalLauncher = null
    payPalClient = null
    returnIntent = null
  }

  private fun resolveFlow(payPalAccountNonce: PayPalAccountNonce) {
    val promise = this.promise ?: return

    val map = Arguments.createMap().apply {
      putString("nonce", payPalAccountNonce.string)
      putString("email", payPalAccountNonce.email)
      putString("firstName", payPalAccountNonce.firstName)
      putString("lastName", payPalAccountNonce.lastName)
      putString("phone", payPalAccountNonce.phone)
    }

    val shippingAddress = payPalAccountNonce.shippingAddress
    if (isShippingRequired && !shippingAddress.isEmpty) {
      val addressMap = Arguments.createMap().apply {
        putString("streetAddress", shippingAddress.streetAddress)
        putString("recipientName", shippingAddress.recipientName)
        putString("postalCode", shippingAddress.postalCode)
        putString("countryCodeAlpha2", shippingAddress.countryCodeAlpha2)
        putString("extendedAddress", shippingAddress.extendedAddress)
        putString("region", shippingAddress.region)
        putString("locality", shippingAddress.locality)
      }
      map.putMap("shippingAddress", addressMap)
    }

    endFlow()
    promise.resolve(map)
  }

  private fun rejectFlow(code: String, message: String, throwable: Throwable?) {
    val promise = this.promise ?: return

    val userInfo = Arguments.createMap().apply {
      putInt("durationMs", (SystemClock.elapsedRealtime() - flowStartedAt).toInt())
      if (throwable != null) {
        putString("errorClass", throwable.javaClass.name)
        putString("errorDescription", throwable.message)
      }
    }

    endFlow()
    promise.reject(code, message, throwable, userInfo)
  }

  override fun onHostPause() {}
  override fun onHostDestroy() {}
  override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {}

  override fun onHostResume() {
    handleReturnToApp()
  }

  override fun onNewIntent(intent: Intent) {
    if (promise != null) {
      returnIntent = intent
    }
  }

  private object ErrorCode {
    const val USER_CANCELED = "USER_CANCELED"
    const val NO_RESULT = "NO_RESULT"
    const val PAYPAL_ERROR = "paypal_error"
    const val IN_PROGRESS = "PAYPAL_IN_PROGRESS"
    const val NO_ACTIVITY = "no_activity"
    const val INVALID_URL = "invalid_url"
    const val NETWORK_ERROR = "network_error"
    const val TOKEN_ERROR = "token_error"
  }

  companion object {
    const val NAME = "BraintreePaypal"
  }
}
