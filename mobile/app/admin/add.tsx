import { router } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, Text, View } from 'react-native';

import { Button, ButtonSpinner, ButtonText } from '@/components/ui/button';
import { Input, InputField } from '@/components/ui/input';
import { addStaff, validateEmployeeId, validateName } from '@/src/data/repository';
import { BottomBar, Screen } from '@/src/ui/Screen';

export default function AddStaff() {
  const [name, setName] = useState('');
  const [employeeId, setEmployeeId] = useState('');
  const [nameError, setNameError] = useState<string | null>(null);
  const [idError, setIdError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  /**
   * Validation runs on blur, not on every keystroke. Telling someone their name
   * is invalid while they are still typing the second letter of it is a
   * well-meaning way to be annoying.
   */
  const save = async (thenEnrol: boolean) => {
    if (busy) return;
    const nameProblem = validateName(name);
    const idProblem = validateEmployeeId(employeeId);
    setNameError(nameProblem);
    setIdError(idProblem);
    if (nameProblem || idProblem) return;

    setBusy(true);
    const result = await addStaff(name, employeeId);
    setBusy(false);

    if (!result.ok) {
      setIdError(result.error ?? 'Could not save');
      return;
    }
    if (thenEnrol && result.staffId) {
      router.replace(`/enrol/${result.staffId}`);
    } else {
      router.back();
    }
  };

  return (
    <Screen title="Add staff" leading="close">
      <KeyboardAvoidingView
        className="flex-1"
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView contentContainerClassName="gap-5 px-5 py-4">
          <View className="gap-2">
            <Text className="text-sm font-medium text-foreground">Full name</Text>
            <Input isInvalid={!!nameError}>
              <InputField
                value={name}
                onChangeText={(value) => {
                  setName(value);
                  if (nameError) setNameError(null);
                }}
                onBlur={() => setNameError(validateName(name))}
                placeholder="Priya Sharma"
                autoCapitalize="words"
              />
            </Input>
            {nameError ? <Text className="text-xs text-destructive">{nameError}</Text> : null}
          </View>

          <View className="gap-2">
            <Text className="text-sm font-medium text-foreground">Employee ID</Text>
            <Input isInvalid={!!idError}>
              <InputField
                value={employeeId}
                onChangeText={(value) => {
                  setEmployeeId(value.toUpperCase());
                  if (idError) setIdError(null);
                }}
                onBlur={() => setIdError(validateEmployeeId(employeeId))}
                placeholder="EMP-004"
                autoCapitalize="characters"
                autoCorrect={false}
              />
            </Input>
            {idError ? (
              <Text className="text-xs text-destructive">{idError}</Text>
            ) : (
              <Text className="text-xs text-muted-foreground">
                They will sign in with this ID and the password staff123.
              </Text>
            )}
          </View>
        </ScrollView>

        {/*
          Two exits from one form. "Save and enrol" is first and primary
          because a staff member without a face cannot mark attendance, so
          stopping at save leaves the job half done.
        */}
        <BottomBar>
          <Button onPress={() => save(true)} isDisabled={busy}>
            {busy ? <ButtonSpinner /> : null}
            <ButtonText>Save and enrol face</ButtonText>
          </Button>
          <Button variant="outline" onPress={() => save(false)} isDisabled={busy}>
            <ButtonText>Save for now</ButtonText>
          </Button>
        </BottomBar>
      </KeyboardAvoidingView>
    </Screen>
  );
}
