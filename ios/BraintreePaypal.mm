#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE (BraintreePaypal, NSObject)

RCT_EXTERN_METHOD(showPayPal
                  : (NSString *)serverUrl amount
                  : (NSString *)amount shippingRequired
                  : (BOOL)shippingRequired currency
                  : (NSString *)currency appLink
                  : (NSString *)appLink email
                  : (NSString *)email resolve
                  : (RCTPromiseResolveBlock)resolve reject
                  : (RCTPromiseRejectBlock)reject)

@end
