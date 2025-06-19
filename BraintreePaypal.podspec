require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "BraintreePaypal"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => "https://github.com/appchoose/react-native-braintree-paypal.git", :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm,cpp,swift}"
  s.private_header_files = "ios/**/*.h"

  s.dependency 'Braintree', '6.34.0'
  s.dependency 'Braintree/DataCollector', '6.34.0'
  s.dependency 'Braintree/LocalPayment', '6.34.0'

 install_modules_dependencies(s)
end
