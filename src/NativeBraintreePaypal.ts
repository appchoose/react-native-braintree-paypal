import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { PayPalResponse } from './types';

export interface Spec extends TurboModule {
  showPayPal(
    serverUrl: string,
    amount: string,
    shippingRequired: boolean,
    currency: string,
    email?: string,
    androidAppLinkReturnUrl?: string,
    androidDeepLinkFallbackUrlScheme?: string
  ): Promise<PayPalResponse>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('BraintreePaypal');
