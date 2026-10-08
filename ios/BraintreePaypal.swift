import Braintree
import Foundation
import React
import UIKit

@objc(BraintreePaypal)
class BraintreePaypal: NSObject {

  private enum ErrorCode {
    static let userCanceled = "USER_CANCELED"
    static let paypalError = "paypal_error"
    static let inProgress = "PAYPAL_IN_PROGRESS"
    static let invalidURL = "invalid_url"
    static let networkError = "network_error"
    static let tokenError = "token_error"
  }

  // Flow state. Only read or written on the main thread.
  private var payPalClient: BTPayPalClient?
  private var pendingResolve: RCTPromiseResolveBlock?
  private var pendingReject: RCTPromiseRejectBlock?
  private var flowStartedAt: Date?
  private var didEnterBackgroundDuringFlow = false
  private var backgroundObserver: NSObjectProtocol?

  @objc
  static func requiresMainQueueSetup() -> Bool {
    return false
  }

  @objc
  func showPayPal(
    _ serverUrl: String, amount: String, shippingRequired: Bool, currency: String, appLink: String,
    email: String?, fallbackURLScheme: String?,
    resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    DispatchQueue.main.async { [weak self] in
      guard let self else { return }

      // The SDK only detects duplicate sessions per client instance. A second call would open a
      // second session that never completes, so its promise would hang forever.
      guard self.pendingReject == nil else {
        reject(ErrorCode.inProgress, "A PayPal flow is already in progress", nil)
        return
      }
      guard let clientTokenURL = URL(string: serverUrl) else {
        reject(ErrorCode.invalidURL, "Invalid server URL", nil)
        return
      }
      guard let universalLink = URL(string: appLink) else {
        reject(ErrorCode.invalidURL, "Invalid app link", nil)
        return
      }

      self.startFlow(resolve: resolve, reject: reject)

      var request = URLRequest(url: clientTokenURL)
      request.setValue("text/plain", forHTTPHeaderField: "Accept")

      URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
        DispatchQueue.main.async {
          guard let self else { return }

          if let error {
            self.rejectFlow(ErrorCode.networkError, "Failed to fetch client token", underlying: error)
            return
          }
          if let httpResponse = response as? HTTPURLResponse,
            !(200...299).contains(httpResponse.statusCode)
          {
            self.rejectFlow(
              ErrorCode.tokenError,
              "Client token request failed with HTTP \(httpResponse.statusCode)",
              underlying: nil)
            return
          }
          guard
            let data,
            let clientToken = String(data: data, encoding: .utf8)?
              .trimmingCharacters(in: .whitespacesAndNewlines),
            !clientToken.isEmpty
          else {
            self.rejectFlow(ErrorCode.tokenError, "Failed to parse client token", underlying: nil)
            return
          }

          self.checkout(
            clientToken: clientToken, amount: amount, shippingRequired: shippingRequired,
            currency: currency, universalLink: universalLink,
            fallbackURLScheme: fallbackURLScheme, email: email)
        }
      }.resume()
    }
  }

  private func checkout(
    clientToken: String, amount: String, shippingRequired: Bool, currency: String,
    universalLink: URL, fallbackURLScheme: String?, email: String?
  ) {
    // Keep a strong reference for the whole flow: the SDK only holds its session weakly.
    let client = BTPayPalClient(
      authorization: clientToken, universalLink: universalLink,
      fallbackURLScheme: fallbackURLScheme)
    payPalClient = client

    let checkoutRequest = BTPayPalCheckoutRequest(
      amount: amount, intent: .authorize, userAction: .none, offerPayLater: false,
      currencyCode: currency, isShippingAddressEditable: shippingRequired,
      isShippingAddressRequired: shippingRequired,
      requestBillingAgreement: false, shippingCallbackURL: nil,
      userAuthenticationEmail: email)

    client.tokenize(checkoutRequest) { [weak self] accountNonce, error in
      DispatchQueue.main.async {
        self?.handleTokenizeResult(
          accountNonce: accountNonce, error: error, shippingRequired: shippingRequired)
      }
    }
  }

  private func handleTokenizeResult(
    accountNonce: BTPayPalAccountNonce?, error: Error?, shippingRequired: Bool
  ) {
    if let error {
      if let paypalError = error as? BTPayPalError, case .canceled = paypalError {
        // The SDK returns the same error whether PayPal redirected to its cancel URL or the
        // browser sheet was dismissed. The diagnostics in userInfo help tell those cases apart.
        rejectFlow(ErrorCode.userCanceled, "User canceled PayPal flow", underlying: error)
      } else {
        rejectFlow(ErrorCode.paypalError, "PayPal tokenization failed", underlying: error)
      }
      return
    }
    guard let accountNonce else {
      rejectFlow(ErrorCode.paypalError, "No account nonce returned", underlying: nil)
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

    resolveFlow(result)
  }

  // MARK: - Flow lifecycle

  private func startFlow(
    resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    pendingResolve = resolve
    pendingReject = reject
    flowStartedAt = Date()
    didEnterBackgroundDuringFlow = false
    backgroundObserver = NotificationCenter.default.addObserver(
      forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main
    ) { [weak self] _ in
      self?.didEnterBackgroundDuringFlow = true
    }
  }

  private func endFlow() {
    if let backgroundObserver {
      NotificationCenter.default.removeObserver(backgroundObserver)
    }
    backgroundObserver = nil
    pendingResolve = nil
    pendingReject = nil
    flowStartedAt = nil
    payPalClient = nil
  }

  private func resolveFlow(_ result: [String: Any?]) {
    guard let resolve = pendingResolve else { return }
    endFlow()
    resolve(result)
  }

  private func rejectFlow(_ code: String, _ message: String, underlying: Error?) {
    guard let reject = pendingReject else { return }

    var userInfo: [String: Any] = [
      "didEnterBackground": didEnterBackgroundDuringFlow
    ]
    if let flowStartedAt {
      userInfo["durationMs"] = Int(Date().timeIntervalSince(flowStartedAt) * 1000)
    }
    if let underlying {
      let nsError = underlying as NSError
      userInfo["errorDomain"] = nsError.domain
      userInfo["errorCode"] = nsError.code
      userInfo["errorDescription"] = nsError.localizedDescription
    }

    endFlow()
    reject(code, message, NSError(domain: "BraintreePaypal", code: 0, userInfo: userInfo))
  }
}
