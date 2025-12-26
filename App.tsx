import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  NativeModules,
  StyleSheet,
  Alert,
  AppState,
  TouchableOpacity,
} from 'react-native';

const { EsewaModule } = NativeModules;

export default function App() {
  const [status, setStatus] = useState('');
  const [isAccessGranted, setIsAccessGranted] = useState(false);
  const [isSpeechEnabled, setIsSpeechEnabled] = useState(true);

  const checkAccessStatus = useCallback(async () => {
    try {
      const granted = await EsewaModule.isNotificationServiceEnabled();
      setIsAccessGranted(granted);
    } catch (e) {
      console.error('Error checking notification access:', e);
    }
  }, []);

  useEffect(() => {
    checkAccessStatus();
    const subscription = AppState.addEventListener('change', nextAppState => {
      if (nextAppState === 'active') checkAccessStatus();
    });
    return () => subscription.remove();
  }, [checkAccessStatus]);

  const handleTestVoice = () => {
    if (!isSpeechEnabled) {
      setStatus('🔇 Speech is disabled.');
      return;
    }

    if (!isAccessGranted) {
      Alert.alert(
        'Permission Required',
        'Please grant Notification Access to enable voice.',
      );
      return;
    }

    EsewaModule.speakTextWithBell(
      '20 rupees received',
      1.0, // volume
      300, // word gap
    );

    setStatus('🔊 Voice test triggered!');
    setTimeout(() => setStatus(''), 2000);
  };

  const handleGrantAccess = () => {
    EsewaModule.openNotificationSettings();
    setStatus('Opening Settings...');
    setTimeout(() => setStatus(''), 2000);
  };

  const toggleSpeech = () => {
    setIsSpeechEnabled(prev => !prev);
    setStatus(isSpeechEnabled ? '🔇 Speech Disabled' : '🔊 Speech Enabled');
    setTimeout(() => setStatus(''), 2000);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>eSewa Announcer</Text>

      {/* Access Status Card */}
      <View
        style={[
          styles.card,
          isAccessGranted ? styles.cardGranted : styles.cardPending,
        ]}
      >
        <Text style={styles.cardText}>
          {isAccessGranted
            ? '✅ Notification Access Granted!'
            : '⚠️ Notification Access Not Granted'}
        </Text>
        {!isAccessGranted && (
          <TouchableOpacity
            style={styles.cardButton}
            onPress={handleGrantAccess}
          >
            <Text style={styles.cardButtonText}>Grant Access</Text>
          </TouchableOpacity>
        )}
      </View>

      {/* Test Voice Card */}
      <View style={styles.card}>
        <Text style={styles.cardText}>Test the voice notification</Text>
        <TouchableOpacity
          style={[
            styles.cardButton,
            !isAccessGranted && { backgroundColor: '#ccc' },
          ]}
          onPress={handleTestVoice}
          disabled={!isAccessGranted}
        >
          <Text style={styles.cardButtonText}>Test Voice</Text>
        </TouchableOpacity>
      </View>

      {/* Speech Toggle Card */}
      <View style={styles.card}>
        <Text style={styles.cardText}>Enable / Disable Speech</Text>
        <TouchableOpacity
          style={[
            styles.cardButton,
            { backgroundColor: isSpeechEnabled ? '#FF5555' : '#33CC66' },
          ]}
          onPress={toggleSpeech}
        >
          <Text style={styles.cardButtonText}>
            {isSpeechEnabled ? 'Disable Speech' : 'Enable Speech'}
          </Text>
        </TouchableOpacity>
      </View>

      {/* FIX: Reserve space for status */}
      <View style={styles.statusContainer}>
        <Text style={styles.status}>{status || ' '}</Text>
      </View>
      {/* Footer */}
      <Text style={styles.footerText}>Developed by Mohan Lal Rana</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f5f6fa',
    padding: 20,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    marginBottom: 30,
    color: '#2f3640',
  },
  card: {
    width: '100%',
    backgroundColor: '#fff',
    padding: 20,
    borderRadius: 15,
    marginVertical: 10,
    shadowColor: '#000',
    shadowOpacity: 0.1,
    shadowOffset: { width: 0, height: 5 },
    shadowRadius: 10,
    elevation: 5,
    alignItems: 'center',
  },
  cardGranted: { borderLeftWidth: 5, borderLeftColor: '#33CC66' },
  cardPending: { borderLeftWidth: 5, borderLeftColor: '#FF5733' },
  cardText: {
    fontSize: 16,
    marginBottom: 15,
    color: '#2f3640',
    textAlign: 'center',
  },
  cardButton: {
    backgroundColor: '#0984e3',
    paddingVertical: 12,
    paddingHorizontal: 25,
    borderRadius: 10,
  },
  cardButtonText: { color: '#fff', fontWeight: 'bold', fontSize: 16 },
  statusContainer: {
    height: 30,
    justifyContent: 'center',
    alignItems: 'center',
  }, // Fixed height
  status: { fontSize: 16, fontWeight: 'bold', color: '#2f3640' },
  footerText: {
    position: 'absolute',
    bottom: 40,
    fontSize: 14,
    color: '#636e72',
    fontWeight: '500',
  },
});
