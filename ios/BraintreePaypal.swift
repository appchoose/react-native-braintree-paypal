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
    clientToken: String, amount: String, shippingRequired: Bool, currency: String, appLink: String,
    email: String?,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    let paypalClient = BTPayPalClient(authorization: clientToken, universalLink: URL(string: appLink)!)
    let checkoutRequest = BTPayPalCheckoutRequest(
      amount: amount, intent: .authorize, userAction: .none, offerPayLater: false,
      currencyCode: currency,isShippingAddressEditable: shippingRequired, isShippingAddressRequired: shippingRequired,
      requestBillingAgreement: false, shippingCallbackURL: nil,
      userAuthenticationEmail: email)

    paypalClient.tokenize(checkoutRequest) { accountNonce, error in
      if let error = error {
        if let paypalError = error as? BTPayPalError {
          switch paypalError {
          case .canceled:
            reject("USER_CANCELED", "User canceled PayPal flow", nil)
            return
          default:
            reject("paypal_error", "PayPal tokenization failed", error)
            return
          }
        }
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
        let addressComponents = shippingAddress.addressComponents()
        result["shippingAddress"] = [
          "streetAddress": addressComponents["streetAddress"],
          "recipientName": addressComponents["recipientName"],
          "postalCode": addressComponents["postalCode"],
          "countryCodeAlpha2": addressComponents["countryCodeAlpha2"],
          "extendedAddress": addressComponents["extendedAddress"],
          "region": addressComponents["region"],
          "locality": addressComponents["locality"],
        ]
      }

      resolve(result)
    }
  }

  @objc
  func showPayPal(
    _ serverUrl: String, amount: String, shippingRequired: Bool, currency: String, appLink: String,
    email: String?,
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

      checkout(
        clientToken: clientToken, amount: amount, shippingRequired: shippingRequired, currency: currency,
        appLink: appLink, email: email, resolve: resolve, reject: reject)
    }
    task.resume()
  }
}
