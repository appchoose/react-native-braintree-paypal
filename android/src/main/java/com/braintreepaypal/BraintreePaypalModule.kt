package com.braintreepaypal

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.module.annotations.ReactModule
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri

@ReactModule(name = BraintreePaypalModule.NAME)
class BraintreePaypalModule(reactContext: ReactApplicationContext) :
  NativeBraintreePaypalSpec(reactContext), ActivityEventListener, LifecycleEventListener {

  private lateinit var payPalLauncher: PayPalLauncher

  private lateinit var currentActivityRef: FragmentActivity
  private lateinit var payPalClientRef: PayPalClient
  private lateinit var promiseRef: Promise

  private var isShippingRequired = false
  private var reactContextRef: Context = reactContext

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
    deepLinkFallbackUrlScheme: String?,
    promise: Promise
  ) {
    promiseRef = promise
    isShippingRequired = shippingRequired
    currentActivityRef = getCurrentActivity() as FragmentActivity
    payPalLauncher = PayPalLauncher()

    val client = OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .build()
    val request = Request.Builder()
      .url(serverUrl)
      .build()

    client.newCall(request).enqueue(object : okhttp3.Callback {
      override fun onFailure(call: okhttp3.Call, e: IOException) {
        promiseRef.reject(e)
      }

      override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
        val token = response.body?.string() ?: ""
        if (token.isEmpty()) {
          promiseRef.reject(Throwable("Token is empty"))
          return
        }

        // Switch to main thread for UI operations
        currentActivityRef.runOnUiThread {
          payPalClientRef = PayPalClient(
            reactContextRef,
            token,
            appLink.toUri(),
            deepLinkFallbackUrlScheme
          )
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
          payPalClientRef.createPaymentAuthRequest(reactContextRef, checkoutRequest) { paymentAuthRequest ->
            when (paymentAuthRequest) {
              is PayPalPaymentAuthRequest.ReadyToLaunch -> {
                when (val pendingRequest = payPalLauncher.launch(currentActivityRef, paymentAuthRequest)) {
                  is PayPalPendingRequest.Started -> {
                    PendingRequestStore.getInstance().putPayPalPendingRequest(reactContextRef, pendingRequest)
                  }
                  is PayPalPendingRequest.Failure -> {
                    promiseRef.reject(pendingRequest.error)
                  }
                }
              }
              is PayPalPaymentAuthRequest.Failure -> {
                promiseRef.reject(paymentAuthRequest.error)
              }
            }
          }
        }
      }
    })
  }

  private fun handleReturnToApp(intent: Intent) {
    val payPalPendingRequest: PayPalPendingRequest.Started? = getPayPalPendingRequest()

    if (payPalPendingRequest != null) {
      val paymentAuthResult = payPalLauncher.handleReturnToApp(payPalPendingRequest, intent)

      when (paymentAuthResult) {
        is PayPalPaymentAuthResult.Failure ->
          handlePayPalAccountNonceResult(null, paymentAuthResult.error)

        PayPalPaymentAuthResult.NoResult -> {
          promiseRef.reject(Throwable("PayPalPaymentAuthResult.NoResult"))
        }

        is PayPalPaymentAuthResult.Success ->
          completePayPalFlow(paymentAuthResult)
      }
      clearPayPalPendingRequest()
    }
  }

  private fun completePayPalFlow(paymentAuthResult: PayPalPaymentAuthResult.Success) {
    payPalClientRef.tokenize(paymentAuthResult) { result ->
      when (result) {
        is PayPalResult.Success -> { /* handle result.nonce */
          handlePayPalAccountNonceResult(result.nonce, null)
        }

        is PayPalResult.Failure -> { /* handle result.error */
          handlePayPalAccountNonceResult(null, result.error)
        }

        is PayPalResult.Cancel -> { /* handle user canceled */
          promiseRef.reject("USER_CANCELED", "User canceled the PayPal payment")
        }
      }
    }
  }

  private fun handlePayPalAccountNonceResult(
    payPalAccountNonce: PayPalAccountNonce?,
    error: Exception?,
  ) {
    if (error != null) {
      promiseRef.reject(error)
      return
    }
    if (payPalAccountNonce != null) {
      val map = WritableNativeMap().apply {
        putString("nonce", payPalAccountNonce.string)
        putString("email", payPalAccountNonce.email)
        putString("firstName", payPalAccountNonce.firstName)
        putString("lastName", payPalAccountNonce.lastName)
        putString("phone", payPalAccountNonce.phone)
      }

      val shippingAddress = payPalAccountNonce.shippingAddress
      if (isShippingRequired && !shippingAddress.isEmpty) {
        val addressMap = WritableNativeMap().apply {
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

      promiseRef.resolve(map)
      return
    }
    promiseRef.reject(Throwable("PayPalAccountNonce is null"))
  }

  private fun getPayPalPendingRequest(): PayPalPendingRequest.Started? {
    return PendingRequestStore.getInstance().getPayPalPendingRequest(reactContextRef)
  }

  private fun clearPayPalPendingRequest() {
    PendingRequestStore.getInstance().clearPayPalPendingRequest(reactContextRef)
  }

  override fun onHostPause() {}
  override fun onHostDestroy() {}
  override fun onActivityResult(p0: Activity, p1: Int, p2: Int, p3: Intent?) {}

  override fun onHostResume() {
    if (this::currentActivityRef.isInitialized) {
      handleReturnToApp(currentActivityRef.intent)
    }
  }

  override fun onNewIntent(intent: Intent) {
    handleReturnToApp(intent)
  }

  companion object {
    const val NAME = "BraintreePaypal"
  }
}
