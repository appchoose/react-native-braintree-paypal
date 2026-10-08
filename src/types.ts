export type BraintreePayPalShippingAddress = {
  countryCodeAlpha2?: string;
  extendedAddress?: string;
  locality?: string;
  postalCode?: string;
  recipientName?: string;
  region?: string;
  streetAddress?: string;
};

export type BraintreePayPalResponse = {
  email?: string;
  firstName?: string;
  lastName?: string;
  nonce?: string;
  phone?: string;
  shippingAddress?: BraintreePayPalShippingAddress;
};

/**
 * Codes used to reject the `showPayPal` promise.
 */
export const BraintreePayPalErrorCode = {
  /** The user canceled the PayPal flow, or the browser sheet was dismissed. */
  USER_CANCELED: "USER_CANCELED",
  /** Android only: the user came back to the app without a result, for instance by closing the browser. */
  NO_RESULT: "NO_RESULT",
  /** The Braintree SDK failed. Details are in `userInfo`. */
  PAYPAL_ERROR: "paypal_error",
  /** `showPayPal` was called while another PayPal flow was still running. */
  IN_PROGRESS: "PAYPAL_IN_PROGRESS",
  /** Android only: no activity was available to launch PayPal. */
  NO_ACTIVITY: "no_activity",
  INVALID_URL: "invalid_url",
  NETWORK_ERROR: "network_error",
  TOKEN_ERROR: "token_error",
} as const;

export type BraintreePayPalErrorCode =
  (typeof BraintreePayPalErrorCode)[keyof typeof BraintreePayPalErrorCode];

/**
 * Diagnostics attached to the `userInfo` of a rejected `showPayPal` promise.
 */
export type BraintreePayPalErrorUserInfo = {
  /** Time elapsed since the flow started, in milliseconds. */
  durationMs?: number;
  /** iOS only: whether the app went to the background during the flow, for instance to a bank app. */
  didEnterBackground?: boolean;
  /** iOS only: domain of the underlying error. */
  errorDomain?: string;
  /** iOS only: code of the underlying error. */
  errorCode?: number;
  /** Android only: class of the underlying error. */
  errorClass?: string;
  /** Description of the underlying error. */
  errorDescription?: string;
};
