import { Platform } from 'react-native';
import BraintreePaypal from './NativeBraintreePaypal';
import type { PayPalResponse } from './types';

export async function showPayPal(
  serverUrl: string,
  amount: string,
  shippingRequired: boolean,
  currency: string,
  email?: string,
  android?: {
    appLinkReturnUrl: string;
    deepLinkFallbackUrlScheme?: string;
  }
): Promise<PayPalResponse> {
  if (Platform.OS === 'android') {
    if (!android) {
      throw new Error('android is required');
    }
    return await BraintreePaypal.showPayPal(
      serverUrl,
      amount,
      shippingRequired,
      currency,
      email,
      android.appLinkReturnUrl,
      android.deepLinkFallbackUrlScheme
    );
  }
  return await BraintreePaypal.showPayPal(
    serverUrl,
    amount,
    shippingRequired,
    currency,
    email
  );
}
export * from './types';
