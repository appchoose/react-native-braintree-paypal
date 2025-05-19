import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { BraintreePayPalResponse } from './types';

export interface Spec extends TurboModule {
  showPayPal(
    serverUrl: string,
    amount: string,
    shippingRequired: boolean,
    currency: string,
    appLink: string,
    email?: string,
    androidDeepLinkFallbackUrlScheme?: string
  ): Promise<BraintreePayPalResponse>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('BraintreePaypal');
