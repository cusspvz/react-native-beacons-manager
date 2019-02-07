/**
 * @flow
 */

// #region imports
import React, { Component } from 'react';
import {
  NativeModules,
  AppRegistry,
  StyleSheet,
  Text,
  SectionList,
  View,
  TouchableHighlight,
  ToastAndroid,
  ImageBackground,
  DeviceEventEmitter,
  PermissionsAndroid,
} from 'react-native';
import Beacons from 'react-native-beacons-manager';
import { Avatar } from 'react-native-elements';
import moment from 'moment';
import beaconIMAGE from './images/beacons/ibeacon.png';
import { hashCode } from './helpers';
// import altBeaconIMAGE from './images/beacons/altbeacon.png';
// import eddystoneURLIMAGE from './images/beacons/eddystoneURL.png';
// import eddystoneTLMIMAGE from './images/beacons/eddystone_TLM.png';
// import eddystoneUIDIMAGE from './images/beacons/eddystone_UID.png';
// #endregion

const beaconTestManager: BeaconsManagerANDROID = NativeModules.BeaconsAndroidModule;


// #region flow types
export type DetectedBeacon = {
  identifier: string,
  uuid?: string,
  major?: number,
  minor?: number,
  proximity?: string,
  rssi?: string,
  distance?: number,
};

export type Section = {
  key: number,
  data: Array<DetectedBeacon>,
  title: string,
  sectionId: string,
};

export type Props = any;

export type State = {
  // region information
  uuid?: string,
  identifier: string,
  // all detected beacons:
  beacons: Array<Section>,
};
// #endregion

// #region constants
const IDENTIFIER = '123456';
const TIME_FORMAT = 'MM/DD/YYYY HH:mm:ss';
const UUID = '7b44b47b-52a1-5381-90c2-f09b6838c5d4';

const RANGING_TITLE = 'ranging beacons in the area:';
const RANGING_SECTION_ID = 1;
const MONITORING_ENTER_TITLE = 'monitoring enter information:';
const MONITORING_ENTER_SECTION_ID = 2;
const MONITORING_LEAVE_TITLE = 'monitoring exit information:';
const MONITORING_LEAVE_SECTION_ID = 3;
// #endregion


// monitoring:
// DeviceEventEmitter.addListener(
//   'regionDidEnter',
//   ({ identifier, uuid, minor, major }) => {
//     ToastAndroid.show(
//       `regionDidEnter: ${identifier}, ${uuid}, ${minor}, ${major}`,
//       ToastAndroid.SHORT,
//     );
//   },
// );

// DeviceEventEmitter.addListener(
//   'regionDidExit',
//   ({ identifier, uuid, minor, major }) => {
//     ToastAndroid.show(
//       `regionDidExit: ${identifier}, ${uuid}, ${minor}, ${major}`,
//       ToastAndroid.SHORT,
//     );
//   },
// );

class BeaconsDemo extends Component<Props, State> {
  // will be set as a reference to "beaconsDidRange" event:
  beaconsDidRangeEvent = null;
  // will be set as a reference to "regionDidEnter" event:
  beaconsDidEnterEvent = null;
  // will be set as a reference to "regionDidExit" event:
  beaconsDidLeaveEvent = null;
  // will be set as a reference to service did connect event:
  beaconsServiceDidConnect: any = null;

  state = {
    // region information
    uuid: UUID,
    identifier: IDENTIFIER,

    ready: false,
    monitoring: false,
    ranging: false,

    // all detected beacons:
    beacons: [
      { key: 1, data: [], title: RANGING_TITLE, sectionId: RANGING_SECTION_ID },
      {
        key: 2,
        data: [],
        title: MONITORING_ENTER_TITLE,
        sectionId: MONITORING_ENTER_SECTION_ID,
      },
      {
        key: 3,
        data: [],
        title: MONITORING_LEAVE_TITLE,
        sectionId: MONITORING_LEAVE_SECTION_ID,
      },
    ],
  };

  // #region lifecycle methods
  async componentDidMount() {
    //
    // ONLY non component state aware here in componentWillMount
    //

    //
    // component state aware here - attach events
    //

    // Ranging: Listen for beacon changes
    this.beaconsDidRangeEvent = DeviceEventEmitter.addListener(
      'beaconsDidRange',
      (response: {
        beacons: Array<{
          distance: number,
          proximity: string,
          rssi: string,
          uuid: string,
        }>,
        uuid: string,
        indetifier: string,
      }) => {
        console.log('BEACONS: ', response);

        response.beacons.forEach(beacon =>
          this.updateBeaconState(RANGING_SECTION_ID, {
            identifier: response.identifier,
            uuid: String(beacon.uuid),
            major: parseInt(beacon.major, 10) >= 0 ? beacon.major : '',
            minor: parseInt(beacon.minor, 10) >= 0 ? beacon.minor : '',
            proximity: beacon.proximity ? beacon.proximity : '',
            rssi: beacon.rssi ? beacon.rssi : '',
            distance: beacon.distance ? beacon.distance : '',
          }),
        );
      },
    );

    // monitoring:
    this.beaconsDidEnterEvent = DeviceEventEmitter.addListener(
      'regionDidEnter',
      ({ identifier, uuid, minor, major }) => {
        console.log('regionDidEnter: ', { identifier, uuid, minor, major });
        this.updateBeaconState(MONITORING_ENTER_SECTION_ID, {
          identifier,
          uuid,
          minor,
          major,
        });
      },
    );

    this.beaconsDidLeaveEvent = DeviceEventEmitter.addListener(
      'regionDidExit',
      ({ identifier, uuid, minor, major }) => {
        console.log('regionDidExit: ', { identifier, uuid, minor, major });
        this.updateBeaconState(MONITORING_LEAVE_SECTION_ID, {
          identifier,
          uuid,
          minor,
          major,
        });
      },
    );

    // we need to wait for service connection to ensure synchronization:
    this.beaconsServiceDidConnect = DeviceEventEmitter.addListener(
      'beaconServiceConnected',
      async () => {
        console.log('service connected')

        ToastAndroid.show(
          `beaconServiceConnected!`,
          ToastAndroid.SHORT,
        );

        if (!this.state.ready) {
          await this.setState({ ready: true })
        }
      },
    );

    // start iBeacon detection
    await Beacons.addParsersListToDetection([
      Beacons.PARSER_IBEACON,
      Beacons.PARSER_ESTIMOTE,
      Beacons.PARSER_ALTBEACON,
      Beacons.PARSER_EDDYSTONE_TLM,
      Beacons.PARSER_EDDYSTONE_UID,
      Beacons.PARSER_EDDYSTONE_URL
    ])

    beaconTestManager.setScanNotificationContent('Hey there', 'Android 8+ requires a pending notification to do a foreground scan');

    // await Beacons.addIBeaconsDetection()
    // await Beacons.addEddystoneUIDDetection()
    // await Beacons.addEddystoneURLDetection()
    // await Beacons.addEddystoneTLMDetection()
    // await Beacons.addAltBeaconsDetection()
    // await Beacons.addEstimotesDetection()

    //
    // Ensure that the state has the correct flags for monitoring and ranging
    //
    await this.updateState()

  }

  async componentWillUnmount() {
    const { monitoring, ranging } = this.state

    // Commented so we can test the case when the app gets killed
    // if( monitoring ) await this.toggleMonitoring();
    // if( ranging ) await this.toggleRanging();

    // remove monitiring events we registered at componentDidMount:
    if(this.beaconsDidEnterEvent) this.beaconsDidEnterEvent.remove();
    if(this.beaconsDidLeaveEvent) this.beaconsDidLeaveEvent.remove();

    // remove ranging event we registered at componentDidMount:
    if(this.beaconsDidRangeEvent) this.beaconsDidRangeEvent.remove();
    if(this.beaconsServiceDidConnect) this.beaconsServiceDidConnect.remove();
  }

  render() {
    const { beacons, ready, ranging, monitoring } = this.state;

    return (
      <ImageBackground
        style={styles.backgroundImage}
        resizeMode="center"
        source={require('./bluetooth-300-300-opacity-45.png')}
      >
        <View style={styles.container}>
          <View style={styles.actionsContainer}>
            <TouchableHighlight
              style={styles.actionButton}
              onPress={this.toggleRanging}
              disabled={!ready}
            >
              <Text style={styles.actionText}>{ranging ? 'stop' : 'start'} ranging</Text>
            </TouchableHighlight>
            <Text>{ready ? 'ready' : 'not ready'}</Text>
            <TouchableHighlight
              style={styles.actionButton}
              onPress={this.toggleMonitoring}
              disabled={!ready}
            >
              <Text style={styles.actionText}>{monitoring ? 'stop' : 'start'} monitoring</Text>
            </TouchableHighlight>
          </View>

          <SectionList
            sections={beacons}
            keyExtractor={this.sectionListKeyExtractor}
            renderSectionHeader={this.renderHeader}
            renderItem={this.renderRow}
            ListEmptyComponent={this.renderEmpty}
            // SectionSeparatorComponent={this.renderSeparator}
            ItemSeparatorComponent={this.renderSeparator}
          // shouldItemUpdate={this.shouldItemUpdate}
          />
        </View>
      </ImageBackground>
    );
  }
  // #endregion

  // #region SectionList related
  sectionListKeyExtractor = (item: DetectedBeacon, index: number) => {
    const UUID = item.uuid ? item.uuid : 'NONE';
    const ID = item.identifier ? item.identifier : 'NONE';
    const MAJOR = item.major ? item.major : 'NONE';
    const MINOR = item.minor ? item.minor : 'NONE';

    return `${UUID}-${ID}-${MAJOR}-${MINOR}`;
  };

  renderHeader = ({ section }) => (
    <Text style={styles.headline}>{section.title}</Text>
  );

  renderSeparator = () => (
    <View style={{ height: 1, backgroundColor: '#E1E1E1', marginLeft: 80 }} />
  );

  renderRow = ({ item }) => {
    console.log('rowData: ', item);
    return (
      <View style={styles.row}>
        <View style={styles.iconContainer}>
          <Avatar
            medium
            rounded
            source={beaconIMAGE}
            onPress={() => console.log('no use')}
            activeOpacity={0.7}
          />
        </View>
        <View style={styles.infoContainer}>
          <Text style={styles.smallText}>
            indentifier: {item.identifier ? item.identifier : 'NA'}
          </Text>
          <Text style={styles.smallText}>
            UUID: {item.uuid ? item.uuid : 'NA'}
          </Text>
          <View style={styles.majorMinorContainer}>
            <Text style={styles.smallText}>
              Major: {parseInt(item.major, 10) >= 0 ? item.major : 'NA'}
            </Text>
            <Text style={[styles.smallText, { marginLeft: 10 }]}>
              Minor: {parseInt(item.minor, 10) >= 0 ? item.minor : 'NA'}
            </Text>
            <Text style={[styles.smallText, { marginLeft: 10 }]}>
              RSSI: {item.rssi ? item.rssi : 'NA'}
            </Text>
          </View>

          <Text style={styles.smallText}>
            Proximity: {item.proximity ? item.proximity : 'NA'}
          </Text>
          <Text style={styles.smallText}>
            Distance: {item.distance ? item.distance.toFixed(2) : 'NA'}
          </Text>
        </View>
      </View>
    );
  };

  renderEmpty = () => (
    <View
      style={{ height: 40, alignItems: 'center', justifyContent: 'center' }}
    >
      <Text>no beacon detected</Text>
    </View>
  );
  // #endregion

  updateBeaconState = (
    forSectionId: number = 0, // section identifier
    { identifier, uuid, minor, major, ...rest }, // beacon
  ) => {
    const { beacons } = this.state;
    const time = moment().format(TIME_FORMAT);

    const updatedBeacons = beacons.map(beacon => {
      if (beacon.sectionId === forSectionId) {
        const sameBeacon = data =>
          !(
            data.uuid === uuid &&
            data.identifier === identifier &&
            data.minor === minor &&
            data.major === major
          );

        const updatedData = [].concat(...beacon.data.filter(sameBeacon), {
          identifier,
          uuid,
          minor,
          major,
          time,
          ...rest,
        });
        return { ...beacon, data: updatedData };
      }
      return beacon;
    });

    console.log('updatedBeacons', updatedBeacons)
    this.setState({ beacons: updatedBeacons });
  };

  updateState = async () => {
    const { monitoring, ranging } = this.state;
    const newState = { monitoring, ranging };


    const monitoredRegions = await Beacons.getMonitoredRegions()
    for (const { identifier, uuid } of monitoredRegions) {
      if(this.state.identifier === identifier && this.state.uuid === uuid) {
        newState.monitoring = true
      } else {
        Beacons.stopMonitoringForRegion({ identifier, uuid })
      }
    }

    const rangedBeacons = await Beacons.getRangedRegions()
    for (const { identifier, uuid } of rangedBeacons) {
      if (this.state.identifier === identifier && this.state.uuid === uuid) {
        newState.ranging = true
      } else {
        Beacons.stopRangingBeaconsInRegion({ identifier, uuid })
      }
    }

    await this.setState(newState)
  }

  handlePermissions = async () => {
    if(this.authorized) return

    const granted = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
      {
        title: 'BeaconDemo',
        message:
          'The Application needs Access to your Bluetooth ' +
          'to Track Beacons.',
      },
    )

    if (granted !== PermissionsAndroid.RESULTS.GRANTED) {
      throw new Error('Beacon Tracking Denied')
    }

    this.authorized = true
  }

  toggleMonitoring = async () => {
    const { monitoring, identifier, uuid } = this.state
    const region = { identifier, uuid }; // minor and major are null here

    try {
      await this.handlePermissions()
      await this.setState({ ready: false })

      let message
      if (!monitoring) {
        await Beacons.startMonitoringForRegion(region);
        message = 'started monitoring'
      } else {
        await Beacons.stopMonitoringForRegion(region);
        message = 'stopped monitoring'
      }

      await this.setState({ ready: true })
      ToastAndroid.showWithGravity(
        message,
        ToastAndroid.SHORT,
        ToastAndroid.CENTER,
      );

      await this.setState({ monitoring: !monitoring })
    } catch (error) {
      ToastAndroid.show(
        `Error: toggling monitoring failed: ${error && error.message || error}`,
        ToastAndroid.SHORT,
      );
    }
  };

  toggleRanging = async () => {
    const { ranging, identifier, uuid } = this.state
    const region = { identifier, uuid, major: 34, minor: 34 }; // minor and major are null here

    try {
      await this.handlePermissions()
      await this.setState({ ready: false })

      let message
      if (!ranging) {
        await Beacons.startRangingBeaconsInRegion(region);
        message = 'started ranging'
      } else {
        await Beacons.stopRangingBeaconsInRegion(region);
        message = 'stopped ranging'
      }

      await this.setState({ ready: true })
      ToastAndroid.showWithGravity(
        message,
        ToastAndroid.SHORT,
        ToastAndroid.CENTER,
      );

      await this.setState({ ranging: ! ranging })
    } catch (error) {
      ToastAndroid.show(
        `Error: toggling ranging failed: ${error && error.message || error}`,
        ToastAndroid.SHORT,
      );
    }
  };
}

const styles = StyleSheet.create({
  backgroundImage: {
    flex: 1,
    width: null,
    height: null,
  },
  container: {
    flex: 1,
  },
  btleConnectionStatus: {
    // fontSize: 20,
    // paddingTop: 20
  },
  headline: {
    fontSize: 20,
    marginHorizontal: 5,
  },
  row: {
    flexDirection: 'row',
    padding: 8,
    paddingBottom: 16,
  },
  iconContainer: {
    flexDirection: 'column',
    marginRight: 10,
  },
  infoContainer: {
    flex: 1,
    flexDirection: 'column',
  },
  majorMinorContainer: {
    flexDirection: 'row',
  },
  smallText: {
    fontSize: 11,
  },
  actionsContainer: {
    marginVertical: 10,
    marginHorizontal: 5,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  actionButton: {
    width: 160,
    backgroundColor: '#A6A6A6',
    paddingHorizontal: 5,
    paddingVertical: 10,
  },
  actionText: {
    alignSelf: 'center',
    fontSize: 11,
    color: '#F1F1F1',
  },
});

AppRegistry.registerComponent('BeaconsDemo', () => BeaconsDemo);
