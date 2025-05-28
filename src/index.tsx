import { Platform } from "react-native";
import BraintreePaypal from "./NativeBraintreePaypal";
import type { BraintreePayPalResponse } from "./types";

export async function showPayPal(
  serverUrl: string,
  amount: string,
  shippingRequired: boolean,
  currency: string,
  appLink: string,
  email?: string,
  android?: {
    deepLinkFallbackUrlScheme?: string;
  },
): Promise<BraintreePayPalResponse> {
  if (Platform.OS === "android") {
    if (!android) {
      throw new Error("android is required");
    }
    return await BraintreePaypal.showPayPal(
      serverUrl,
      amount,
      shippingRequired,
      currency,
      appLink,
      email,
      android.deepLinkFallbackUrlScheme,
    );
  }
  return await BraintreePaypal.showPayPal(
    serverUrl,
    amount,
    shippingRequired,
    currency,
    appLink,
    email,
  );
}
export * from "./types";
