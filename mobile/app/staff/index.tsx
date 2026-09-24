import { router } from 'expo-router';
import { useEffect } from 'react';
import { Image, Pressable, ScrollView, Text, View } from 'react-native';

import { Button, ButtonText } from '@/components/ui/button';
import { Icon, CheckCircleIcon, ClockIcon } from '@/components/ui/icon';
import { warmUp } from '@/src/face/pipeline';
import { getStaff, historyFor, templatesFor } from '@/src/data/repository';
import { signOut } from '@/src/data/session';
import type { AttendanceRecord, Staff } from '@/src/data/types';
import { Screen } from '@/src/ui/Screen';
import { useRequireRole } from '@/src/ui/guards';
import { firstName, formatDay, formatTime } from '@/src/ui/format';
import { PixelEmptyState, PixelPin, PixelSpinner, PixelSprite, FACE_UNKNOWN_SPRITE, usePixelPalette } from '@/src/ui/pixel';
import { useAsyncData } from '@/src/ui/useAsyncData';

interface Home {
  staff: Staff | undefined;
  enrolled: boolean;
  history: AttendanceRecord[];
  markedToday: AttendanceRecord | undefined;
}

export default function StaffHome() {
  const palette = usePixelPalette();
  const session = useRequireRole('STAFF');
  const staffId = session.status === 'resolved' ? session.session?.staffId : null;

  /**
   * Start downloading the model while the user reads this screen.
   *
   * The pipeline is about 20 MB of model and wasm. Waiting until they tap
   * "Mark attendance" puts that entire wait between the tap and the camera,
   * which is exactly where it is least tolerable.
   */
  useEffect(() => {
    warmUp();
  }, []);

  const { data, loading } = useAsyncData<Home>(async () => {
    if (!staffId) return { staff: undefined, enrolled: false, history: [], markedToday: undefined };
    const [staff, templates, history] = await Promise.all([
      getStaff(staffId),
      templatesFor(staffId),
      historyFor(staffId),
    ]);
    const startOfToday = new Date();
    startOfToday.setHours(0, 0, 0, 0);
    return {
      staff,
      enrolled: templates.length > 0,
      history,
      markedToday: history.find((record) => record.markedAt >= startOfToday.getTime()),
    };
  }, [staffId]);

  if (loading && !data) {
    return (
      <Screen title="">
        <View className="flex-1 items-center justify-center">
          <PixelSpinner size={48} />
        </View>
      </Screen>
    );
  }

  const staff = data?.staff;
  const marked = data?.markedToday;
  const enrolled = data?.enrolled ?? false;
  const recent = (data?.history ?? []).slice(0, 3);

  return (
    <Screen
      title={staff ? `Hello, ${firstName(staff.name)}` : 'Hello'}
      subtitle={staff?.employeeId}
      actions={
        <Button
          variant="ghost"
          size="sm"
          onPress={() => {
            signOut();
            router.replace('/login');
          }}
        >
          <ButtonText>Sign out</ButtonText>
        </Button>
      }
    >
      <ScrollView contentContainerClassName="gap-5 px-5 pb-10">
        {!enrolled ? (
          <View className="items-center gap-3 rounded-2xl bg-secondary p-6">
            <PixelSprite rows={FACE_UNKNOWN_SPRITE} palette={palette} size={64} />
            <Text className="text-base font-semibold text-foreground">Your face is not enrolled</Text>
            <Text className="text-center text-sm text-muted-foreground">
              An admin needs to enrol your face before you can mark attendance. It takes about
              thirty seconds.
            </Text>
          </View>
        ) : marked ? (
          <View className="gap-3 rounded-2xl border border-emerald-500/40 bg-emerald-500/10 p-5">
            <View className="flex-row items-center gap-2">
              <Icon as={CheckCircleIcon} className="h-5 w-5 text-emerald-500" />
              <Text className="text-base font-semibold text-foreground">
                {`Attendance marked at ${formatTime(marked.markedAt)}`}
              </Text>
            </View>
            <Text className="text-sm text-muted-foreground">
              That is today done. Come back tomorrow.
            </Text>
          </View>
        ) : (
          <View className="gap-4 rounded-2xl border border-border p-5">
            <View className="flex-row items-center gap-2">
              <Icon as={ClockIcon} className="h-5 w-5 text-muted-foreground" />
              <Text className="text-base font-semibold text-foreground">
                You have not marked attendance today
              </Text>
            </View>
            <Button size="lg" onPress={() => router.push('/staff/mark')}>
              <ButtonText>Mark attendance</ButtonText>
            </Button>
          </View>
        )}

        <View className="gap-1">
          <View className="flex-row items-center justify-between">
            <Text className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">
              Recent
            </Text>
            {(data?.history.length ?? 0) > recent.length ? (
              <Pressable onPress={() => router.push('/staff/history')}>
                <Text className="text-xs font-semibold text-foreground">View all</Text>
              </Pressable>
            ) : null}
          </View>

          {recent.length === 0 ? (
            <PixelEmptyState
              title="No records yet"
              body="Once you mark attendance, it will show up here with the time and place."
            />
          ) : (
            recent.map((record) => <RecordRow key={record.id} record={record} />)
          )}
        </View>
      </ScrollView>
    </Screen>
  );
}

export function RecordRow({ record }: { record: AttendanceRecord }) {
  return (
    <View className="flex-row items-center gap-3 py-3">
      <Image source={{ uri: record.selfie }} style={{ width: 44, height: 44, borderRadius: 10 }} />
      <View className="flex-1">
        <Text className="text-sm font-semibold text-foreground">{formatDay(record.markedAt)}</Text>
        <View className="flex-row items-center gap-1">
          <Text className="text-xs text-muted-foreground">{formatTime(record.markedAt)}</Text>
          {record.latitude !== null && record.longitude !== null ? (
            <>
              <PixelPin size={11} />
              <Text className="text-xs text-muted-foreground">
                {record.address ?? `${record.latitude.toFixed(3)}, ${record.longitude.toFixed(3)}`}
              </Text>
            </>
          ) : (
            <Text className="text-xs text-muted-foreground">No location</Text>
          )}
        </View>
      </View>
    </View>
  );
}
