import { router } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, View } from 'react-native';

import { Button, ButtonSpinner, ButtonText } from '@/components/ui/button';
import { Icon, EyeIcon, EyeOffIcon } from '@/components/ui/icon';
import { Input, InputField, InputSlot } from '@/components/ui/input';
import { ADMIN_PASSWORD, ADMIN_USERNAME, DEFAULT_STAFF_PASSWORD, signIn } from '@/src/data/repository';
import { setSession } from '@/src/data/session';
import { PixelSprite, FACE, usePixelPalette } from '@/src/ui/pixel';

export default function Login() {
  const palette = usePixelPalette();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [reveal, setReveal] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const session = await signIn(username, password);
      if (!session) {
        // One message for both cases on purpose: saying which half was wrong
        // tells an attacker which usernames exist.
        setError('That username and password do not match');
        return;
      }
      setSession(session);
      router.replace(session.role === 'ADMIN' ? '/admin' : '/staff');
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const fill = (user: string, secret: string) => {
    setUsername(user);
    setPassword(secret);
    setError(null);
  };

  return (
    <KeyboardAvoidingView
      className="flex-1 bg-background"
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView contentContainerClassName="flex-grow justify-center px-6 py-12">
        {/* Same reasoning as Screen: a readable column instead of a stretched form. */}
        <View className="w-full gap-7 self-center max-w-[420px]">
        <View className="items-center gap-4">
          <PixelSprite rows={FACE} palette={palette} size={72} />
          <View className="items-center gap-1">
            <Text className="text-3xl font-bold text-foreground">Attendance</Text>
            <Text className="text-center text-sm text-muted-foreground">
              Sign in to mark attendance, or as an admin to manage staff.
            </Text>
          </View>
        </View>

        <View className="gap-4">
          <View className="gap-2">
            <Text className="text-sm font-medium text-foreground">Username</Text>
            <Input>
              <InputField
                value={username}
                onChangeText={setUsername}
                placeholder="admin or EMP-001"
                autoCapitalize="characters"
                autoCorrect={false}
                autoComplete="username"
                onSubmitEditing={submit}
              />
            </Input>
          </View>

          <View className="gap-2">
            <Text className="text-sm font-medium text-foreground">Password</Text>
            <Input>
              <InputField
                value={password}
                onChangeText={setPassword}
                placeholder="Password"
                secureTextEntry={!reveal}
                autoCapitalize="none"
                autoComplete="current-password"
                onSubmitEditing={submit}
              />
              <InputSlot className="pr-3" onPress={() => setReveal((value) => !value)}>
                <Icon as={reveal ? EyeOffIcon : EyeIcon} className="h-4 w-4 text-muted-foreground" />
              </InputSlot>
            </Input>
          </View>

          {error ? (
            <View className="rounded-lg bg-destructive/15 px-3 py-2">
              <Text className="text-sm text-destructive">{error}</Text>
            </View>
          ) : null}

          <Button onPress={submit} isDisabled={busy} className="mt-1">
            {busy ? <ButtonSpinner /> : null}
            <ButtonText>{busy ? 'Signing in' : 'Sign in'}</ButtonText>
          </Button>
        </View>

        {/*
          Demo credentials on the screen rather than only in the README. A
          reviewer opening this on a phone has no README to hand, and a sign-in
          wall with no way through is a bad first thirty seconds.
        */}
        <View className="gap-2 rounded-xl border border-border p-4">
          <Text className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">
            Demo accounts
          </Text>
          <Pressable onPress={() => fill(ADMIN_USERNAME, ADMIN_PASSWORD)}>
            <Text className="text-sm text-foreground">
              <Text className="font-semibold">Admin</Text>
              {`  ${ADMIN_USERNAME} / ${ADMIN_PASSWORD}`}
            </Text>
          </Pressable>
          <Pressable onPress={() => fill('EMP-001', DEFAULT_STAFF_PASSWORD)}>
            <Text className="text-sm text-foreground">
              <Text className="font-semibold">Staff</Text>
              {`  EMP-001 / ${DEFAULT_STAFF_PASSWORD}`}
            </Text>
          </Pressable>
          <Text className="text-xs text-muted-foreground">Tap either line to fill the form.</Text>
        </View>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
