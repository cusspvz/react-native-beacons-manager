var path = require('path')
module.exports = {
  watchFolders: [
    path.join(__dirname, '../..')
  ],
  extraNodeModules: {
    'react-native-beacons-manager': path.join(__dirname, '../..'),
  },
};
