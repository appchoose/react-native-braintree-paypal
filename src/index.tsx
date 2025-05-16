import BraintreePaypal from './NativeBraintreePaypal';

export async function showPayPal(
  serverUrl: string,
  amount: string,
  shippingRequired: boolean,
  currency: string,
  email?: string
): Promise<boolean> {
  return await BraintreePaypal.showPayPal(
    serverUrl,
    amount,
    shippingRequired,
    currency,
    email
  );
}
