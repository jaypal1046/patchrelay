import React, {useEffect, useState} from 'react';
import {Button, NativeModules, ScrollView, StyleSheet, Text, View} from 'react-native';
import {SafeAreaProvider, SafeAreaView} from 'react-native-safe-area-context';

const updates = NativeModules.PatchRelayUpdates;

export default function UpdateScreen({title, color, message}: {title: string; color: string; message: string}) {
  const [status, setStatus] = useState('Ready');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    updates.markHealthy().catch((error: Error) => setStatus(error.message));
  }, []);

  async function checkUpdate() {
    setBusy(true);
    setStatus('Downloading and verifying signed bundle…');
    try {
      const pending = await updates.checkForUpdate();
      setStatus(pending ? 'Update verified. Open app info, force stop, then reopen this app.' : 'No newer update.');
    } catch (error) {
      setStatus(error instanceof Error ? error.message : String(error));
    } finally {
      setBusy(false);
    }
  }

  return <SafeAreaProvider><SafeAreaView style={styles.safe}><ScrollView contentContainerStyle={styles.content}>
    <Text style={styles.label}>PatchRelay · React Native Android</Text>
    <View style={[styles.card, {borderColor: color}]}>
      <Text accessibilityRole="header" style={[styles.title, {color}]}>{title}</Text>
      <Text style={styles.body}>{message}</Text>
    </View>
    <Text style={styles.body}>Active bundle: {updates.activeVersion}</Text>
    <Text style={styles.body} accessibilityLiveRegion="polite">{status}</Text>
    <Button title={busy ? 'Downloading…' : 'Check GitHub update'} disabled={busy} onPress={checkUpdate} />
    <Button title="App info — force stop and reopen" onPress={() => updates.openAppInfo()} />
  </ScrollView></SafeAreaView></SafeAreaProvider>;
}

const styles = StyleSheet.create({
  safe: {flex: 1, backgroundColor: '#FFFFFF'},
  content: {padding: 24, gap: 24},
  label: {fontSize: 16, color: '#374151'},
  card: {padding: 24, borderWidth: 3, borderRadius: 12, gap: 16},
  title: {fontSize: 30, fontWeight: '700'},
  body: {fontSize: 18, lineHeight: 28, color: '#111827'},
});
