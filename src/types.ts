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
