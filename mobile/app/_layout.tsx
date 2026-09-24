import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { GluestackUIProvider } from '@/components/ui/gluestack-ui-provider';
import { ensureSeeded } from '@/src/data/repository';
import { hydrateSession } from '@/src/data/session';
import '@/global.css';

export default function RootLayout() {
  useEffect(() => {
    hydrateSession();
    // Seeding is awaited by sign-in as well, so this is only a head start.
    // Firing it without awaiting was how the Android build ended up rejecting
    // correct credentials on the very first launch and no other launch.
    ensureSeeded().catch(() => {});
  }, []);

  return (
    <GluestackUIProvider mode="system">
      <SafeAreaProvider>
        <StatusBar style="auto" />
        <Stack
          screenOptions={{
            headerShown: false,
            animation: 'slide_from_right',
            contentStyle: { backgroundColor: 'transparent' },
          }}
        />
      </SafeAreaProvider>
    </GluestackUIProvider>
  );
}
