import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import {NativeModules} from 'react-native';

jest.setTimeout(30000);

NativeModules.PatchRelayUpdates = {activeVersion: 0, markHealthy: jest.fn().mockResolvedValue(true)};
jest.mock('react-native-safe-area-context', () => {
  const {View} = require('react-native');
  return {SafeAreaProvider: View, SafeAreaView: View};
});
const App = require('../App').default;

test('confirms startup after mounting original bundle', async () => {
  let screen: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => { screen = ReactTestRenderer.create(<App />); });
  expect(JSON.stringify(screen!.toJSON())).toContain('Original React Native bundle');
  expect(NativeModules.PatchRelayUpdates.markHealthy).toHaveBeenCalledTimes(1);
  await ReactTestRenderer.act(async () => { screen!.unmount(); });
});
