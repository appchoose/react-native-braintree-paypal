import Braintree
import Foundation
import React

@objc(BraintreePaypal)
class BraintreePaypal: NSObject {

  @objc
  static func requiresMainQueueSetup() -> Bool {
    return false
  }

  private func checkout(
    client: BTAPIClient, amount: String, shippingRequired: Bool, currency: String, appLink: String, email: String?,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    var paypalClient = BTPayPalClient(apiClient: client, universalLink: URL(string: appLink)!)
    var checkoutRequest = BTPayPalCheckoutRequest(
      amount: amount, intent: .authorize, userAction: .none, offerPayLater: false,
      currencyCode: currency, requestBillingAgreement: false, shippingCallbackURL: nil,
      userAuthenticationEmail: email)
    checkoutRequest.isShippingAddressRequired = shippingRequired
    checkoutRequest.isShippingAddressEditable = shippingRequired

    paypalClient.tokenize(checkoutRequest) { accountNonce, error in
      if let error = error {
        reject("paypal_error", "PayPal tokenization failed", error)
        return
      }
      guard let accountNonce = accountNonce else {
        reject("paypal_error", "No account nonce returned", nil)
        return
      }

      var result: [String: Any?] = [
        "email": accountNonce.email,
        "firstName": accountNonce.firstName,
        "lastName": accountNonce.lastName,
        "phone": accountNonce.phone,
        "nonce": accountNonce.nonce,
      ]

      if let shippingAddress = accountNonce.shippingAddress, shippingRequired {
        result["shippingAddress"] = [
          "streetAddress": shippingAddress.streetAddress,
          "recipientName": shippingAddress.recipientName,
          "postalCode": shippingAddress.postalCode,
          "countryCodeAlpha2": shippingAddress.countryCodeAlpha2,
          "extendedAddress": shippingAddress.extendedAddress,
          "region": shippingAddress.region,
          "locality": shippingAddress.locality,
        ]
      }

      resolve(result)
    }
  }

  @objc
  func showPayPal(
    _ serverUrl: String, amount: String, shippingRequired: Bool, currency: String, appLink: String, email: String?,
    resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    guard let clientTokenURL = URL(string: serverUrl) else {
      reject("invalid_url", "Invalid server URL", nil)
      return
    }

    var request = URLRequest(url: clientTokenURL)
    request.setValue("text/plain", forHTTPHeaderField: "Accept")

    let task = URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
      guard let self = self else { return }
      if let error = error {
        reject("network_error", "Failed to fetch client token", error)
        return
      }
      guard let data = data, let clientToken = String(data: data, encoding: .utf8) else {
        reject("token_error", "Failed to parse client token", nil)
        return
      }

      let braintreeClient = BTAPIClient(authorization: clientToken)
      guard let client = braintreeClient else {
        return resolve(false)
      }
      checkout(
        client: client, amount: amount, shippingRequired: shippingRequired, currency: currency,
        appLink: appLink, email: email, resolve: resolve, reject: reject)
    }
    task.resume()
  }
}
