import BraintreePaypal from './NativeBraintreePaypal';
import type { PayPalResponse } from './types';

export async function showPayPal(
  serverUrl: string,
  amount: string,
  shippingRequired: boolean,
  currency: string,
  email?: string
): Promise<PayPalResponse> {
  return await BraintreePaypal.showPayPal(
    serverUrl,
    amount,
    shippingRequired,
    currency,
    email
  );
}
export * from './types';
