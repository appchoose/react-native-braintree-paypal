import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  showPayPal(
    serverUrl: string,
    amount: string,
    shippingRequired: boolean,
    currency: string,
    email?: string
  ): Promise<boolean>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('BraintreePaypal');
