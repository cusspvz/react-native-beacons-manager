// flow

import { Platform } from 'react-native';

const RNiBeaconsModule = Platform.select({
  ios: () => require('./index.ios.js'),
  android: () => require('./index.android.js'),
})();

export default RNiBeaconsModule;
