export type PayPalShippingAddress = {
  countryCodeAlpha2: string;
  extendedAddress: string;
  locality: string;
  postalCode: string;
  recipientName: string;
  region: string;
  streetAddress: string;
};

export type PayPalResponse = {
  email: string;
  firstName: string;
  lastName: string;
  nonce: string;
  phone: string;
  shippingAddress?: PayPalShippingAddress;
};
