# react-native-braintree-paypal

RN Braintree for PayPal payment

## Installation

```sh
yarn add @appchoose/react-native-braintree-paypal
```

## Usage

```ts
import {
  BraintreePayPalErrorCode,
  type BraintreePayPalErrorUserInfo,
  showPayPal,
} from "@appchoose/react-native-braintree-paypal";

try {
  const { nonce } = await showPayPal(
    clientTokenUrl,
    "42.00",
    true, // shipping address required
    "EUR",
    "https://example.com/braintree-payments", // app link used for the return
    customerEmail,
    { deepLinkFallbackUrlScheme: "com.example.app.braintree" }, // Android
  );
} catch (error) {
  const { code, userInfo } = error as {
    code: string;
    userInfo?: BraintreePayPalErrorUserInfo;
  };
  if (code === BraintreePayPalErrorCode.USER_CANCELED) {
    // ...
  }
}
```

Only one PayPal flow can run at a time. A second call made while a flow is still running is rejected with `PAYPAL_IN_PROGRESS`.

## Errors

| Code | Platform | Meaning |
| --- | --- | --- |
| `USER_CANCELED` | iOS, Android | The user canceled the flow. On iOS, this also covers the browser sheet being dismissed. |
| `NO_RESULT` | Android | The user came back to the app without a result, for instance by closing the browser. |
| `paypal_error` | iOS, Android | The Braintree SDK failed. |
| `PAYPAL_IN_PROGRESS` | iOS, Android | Another PayPal flow is still running. |
| `no_activity` | Android | No activity was available to launch PayPal. |
| `invalid_url` | iOS, Android | The client token URL or the app link is invalid. |
| `network_error` | iOS, Android | The client token request failed. |
| `token_error` | iOS, Android | The client token request returned an error status or an empty body. |

Rejected promises carry diagnostics in `userInfo`:

| Key | Platform | Meaning |
| --- | --- | --- |
| `durationMs` | iOS, Android | Time elapsed since the flow started. |
| `didEnterBackground` | iOS | Whether the app went to the background during the flow, for instance to a bank app. |
| `errorDomain`, `errorCode` | iOS | Domain and code of the underlying error. |
| `errorClass` | Android | Class of the underlying error. |
| `errorDescription` | iOS, Android | Description of the underlying error. |


## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
