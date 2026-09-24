import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { FlatList, Pressable, Text, View } from 'react-native';

import { Avatar, AvatarFallbackText } from '@/components/ui/avatar';
import { Button, ButtonText } from '@/components/ui/button';
import { Icon, AddIcon, SearchIcon, CheckCircleIcon } from '@/components/ui/icon';
import { Input, InputField, InputSlot } from '@/components/ui/input';
import { listStaff, type StaffSummary } from '@/src/data/repository';
import { signOut } from '@/src/data/session';
import { Screen } from '@/src/ui/Screen';
import { useRequireRole } from '@/src/ui/guards';
import { initials } from '@/src/ui/format';
import { PixelEmptyState, PixelSpinner, FACE_UNKNOWN_SPRITE } from '@/src/ui/pixel';
import { useAsyncData } from '@/src/ui/useAsyncData';

export default function StaffList() {
  useRequireRole('ADMIN');
  const [query, setQuery] = useState('');
  const { data, loading } = useAsyncData(listStaff, []);

  const staff = data ?? [];
  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return staff;
    return staff.filter(
      (person) =>
        person.name.toLowerCase().includes(needle) ||
        person.employeeId.toLowerCase().includes(needle),
    );
  }, [staff, query]);

  const notEnrolled = staff.filter((person) => person.templateCount === 0).length;

  return (
    <Screen
      title="Staff"
      subtitle={staff.length > 0 ? `${staff.length} people` : undefined}
      actions={
        <Button variant="ghost" size="sm" onPress={() => { signOut(); router.replace('/login'); }}>
          <ButtonText>Sign out</ButtonText>
        </Button>
      }
    >
      <View className="px-5 pb-3">
        <Input>
          <InputSlot className="pl-3">
            <Icon as={SearchIcon} className="h-4 w-4 text-muted-foreground" />
          </InputSlot>
          <InputField
            value={query}
            onChangeText={setQuery}
            placeholder="Search by name or ID"
            autoCorrect={false}
          />
        </Input>
      </View>

      {/*
        The count of people who cannot yet mark attendance is the one number an
        admin needs on this screen, so it is stated rather than left to be
        counted from the badges below.
      */}
      {notEnrolled > 0 ? (
        <View className="mx-5 mb-3 rounded-xl bg-secondary px-4 py-3">
          <Text className="text-sm text-secondary-foreground">
            {notEnrolled === 1
              ? '1 person still needs a face enrolled before they can mark attendance.'
              : `${notEnrolled} people still need a face enrolled before they can mark attendance.`}
          </Text>
        </View>
      ) : null}

      {loading && staff.length === 0 ? (
        <View className="flex-1 items-center justify-center">
          <PixelSpinner size={48} />
        </View>
      ) : (
        <FlatList
          data={filtered}
          keyExtractor={(person) => String(person.id)}
          contentContainerClassName="pb-28"
          ListEmptyComponent={
            query ? (
              <PixelEmptyState
                sprite={FACE_UNKNOWN_SPRITE}
                title="Nobody matches that"
                body={`No staff member has a name or ID like "${query}".`}
              />
            ) : (
              <PixelEmptyState
                title="No staff yet"
                body="Add your first staff member, then enrol their face so they can mark attendance."
              />
            )
          }
          renderItem={({ item }) => <StaffRow person={item} />}
        />
      )}

      <View className="absolute bottom-6 right-5">
        <Button size="lg" onPress={() => router.push('/admin/add')} className="rounded-full">
          <Icon as={AddIcon} className="h-4 w-4 text-primary-foreground" />
          <ButtonText>Add staff</ButtonText>
        </Button>
      </View>
    </Screen>
  );
}

function StaffRow({ person }: { person: StaffSummary }) {
  const enrolled = person.templateCount > 0;
  return (
    <Pressable
      onPress={() => router.push(`/admin/${person.id}`)}
      className="flex-row items-center gap-3 px-5 py-3 active:bg-accent"
    >
      <Avatar className="h-10 w-10">
        <AvatarFallbackText>{initials(person.name)}</AvatarFallbackText>
      </Avatar>
      <View className="flex-1">
        <Text className="text-base font-semibold text-foreground">{person.name}</Text>
        <Text className="font-mono text-xs text-muted-foreground">{person.employeeId}</Text>
      </View>
      <View className="items-end gap-1">
        {enrolled ? (
          <Text className="text-xs text-muted-foreground">Enrolled</Text>
        ) : (
          // Loud, because it is the exception and it blocks the person from
          // doing the one thing the app exists for.
          <View className="rounded-full bg-destructive/15 px-2 py-0.5">
            <Text className="text-xs font-semibold text-destructive">Not enrolled</Text>
          </View>
        )}
        {person.markedToday ? (
          <View className="flex-row items-center gap-1">
            <Icon as={CheckCircleIcon} className="h-3 w-3 text-emerald-500" />
            <Text className="text-xs text-emerald-500">Marked today</Text>
          </View>
        ) : null}
      </View>
    </Pressable>
  );
}
