import React from 'react';
import {AppRegistry} from 'react-native';
import UpdateScreen from './UpdateScreen';
import {name as appName} from './app.json';

function PatchedApp() {
  return <UpdateScreen title="Updated from GitHub" color="#1D4ED8" message="This React Native screen was downloaded after installation. The native APK is unchanged." />;
}
AppRegistry.registerComponent(appName, () => PatchedApp);
