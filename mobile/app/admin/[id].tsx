import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { FlatList, Image, Text, View } from 'react-native';

import {
  AlertDialog,
  AlertDialogBackdrop,
  AlertDialogBody,
  AlertDialogContent,
  AlertDialogFooter,
  AlertDialogHeader,
} from '@/components/ui/alert-dialog';
import { Button, ButtonText } from '@/components/ui/button';
import { Icon, CheckCircleIcon, TrashIcon } from '@/components/ui/icon';
import {
  getStaff,
  historyFor,
  removeStaff,
  templatesFor,
} from '@/src/data/repository';
import type { AttendanceRecord, FaceTemplate, Staff } from '@/src/data/types';
import { Screen, SectionHeader } from '@/src/ui/Screen';
import { firstName, formatDateTime, formatDay, formatPercent, formatTime } from '@/src/ui/format';
import { PixelEmptyState, PixelPin, PixelSpinner, PixelSprite, FACE_UNKNOWN_SPRITE, usePixelPalette } from '@/src/ui/pixel';
import { useAsyncData } from '@/src/ui/useAsyncData';

interface Profile {
  staff: Staff | undefined;
  templates: FaceTemplate[];
  history: AttendanceRecord[];
}

export default function StaffProfile() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const staffId = Number(id);
  const [confirming, setConfirming] = useState(false);

  const { data, loading } = useAsyncData<Profile>(async () => {
    const [staff, templates, history] = await Promise.all([
      getStaff(staffId),
      templatesFor(staffId),
      historyFor(staffId),
    ]);
    return { staff, templates, history };
  }, [staffId]);

  const staff = data?.staff;
  const templates = data?.templates ?? [];
  const history = data?.history ?? [];
  const enrolled = templates.length > 0;

  const confirmDelete = async () => {
    setConfirming(false);
    await removeStaff(staffId);
    router.back();
  };

  if (loading && !staff) {
    return (
      <Screen title="" leading="back">
        <View className="flex-1 items-center justify-center">
          <PixelSpinner size={48} />
        </View>
      </Screen>
    );
  }

  return (
    <Screen
      title={staff?.name ?? 'Staff member'}
      subtitle={staff?.employeeId}
      leading="back"
      actions={
        <Button
          variant="ghost"
          size="icon"
          onPress={() => setConfirming(true)}
          accessibilityLabel="Remove staff member"
        >
          <Icon as={TrashIcon} className="h-5 w-5 text-destructive" />
        </Button>
      }
    >
      <FlatList
        data={history}
        keyExtractor={(record) => String(record.id)}
        contentContainerClassName="pb-10"
        ListHeaderComponent={
          <View>
            <EnrolmentCard
              enrolled={enrolled}
              name={staff?.name ?? ''}
              templates={templates}
              enrolledAt={staff?.enrolledAt ?? null}
              onEnrol={() => router.push(`/enrol/${staffId}`)}
            />
            <SectionHeader>Attendance history</SectionHeader>
          </View>
        }
        ListEmptyComponent={
          <PixelEmptyState
            title="Nothing recorded yet"
            body={
              enrolled
                ? `Records appear here once ${firstName(staff?.name ?? 'they')} marks attendance.`
                : 'Nothing can be recorded until a face is enrolled.'
            }
          />
        }
        renderItem={({ item }) => <HistoryRow record={item} />}
      />

      <AlertDialog isOpen={confirming} onClose={() => setConfirming(false)}>
        <AlertDialogBackdrop />
        <AlertDialogContent>
          <AlertDialogHeader>
            <Text className="text-lg font-semibold text-foreground">
              {`Remove ${staff?.name ?? 'this person'}?`}
            </Text>
          </AlertDialogHeader>
          <AlertDialogBody>
            {/* A destructive dialog names the object and names the consequence. */}
            <Text className="text-sm text-muted-foreground">
              Their attendance history will be kept, but they will no longer be able to sign in or
              mark attendance.
            </Text>
          </AlertDialogBody>
          <AlertDialogFooter>
            <Button variant="outline" onPress={() => setConfirming(false)}>
              <ButtonText>Cancel</ButtonText>
            </Button>
            {/* "Remove", not "OK". The button says what it does. */}
            <Button variant="destructive" onPress={confirmDelete}>
              <ButtonText>Remove</ButtonText>
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Screen>
  );
}

/**
 * Enrolment status is the hero of this screen, and the two states are styled
 * asymmetrically on purpose: not-enrolled is a coloured call to action,
 * enrolled is quiet. The exception should be loud; the normal state should not
 * shout.
 */
function EnrolmentCard({
  enrolled,
  name,
  templates,
  enrolledAt,
  onEnrol,
}: {
  enrolled: boolean;
  name: string;
  templates: FaceTemplate[];
  enrolledAt: number | null;
  onEnrol: () => void;
}) {
  const palette = usePixelPalette();

  if (!enrolled) {
    return (
      <View className="mx-5 mt-2 gap-3 rounded-2xl bg-secondary p-5">
        <View className="flex-row items-center gap-3">
          <PixelSprite rows={FACE_UNKNOWN_SPRITE} palette={palette} size={44} />
          <View className="flex-1">
            <Text className="text-base font-semibold text-foreground">Face not enrolled</Text>
            <Text className="text-sm text-muted-foreground">
              {`${firstName(name)} cannot mark attendance until their face is enrolled.`}
            </Text>
          </View>
        </View>
        <Button onPress={onEnrol}>
          <ButtonText>Enrol face now</ButtonText>
        </Button>
      </View>
    );
  }

  return (
    <View className="mx-5 mt-2 gap-3 rounded-2xl border border-border p-5">
      <View className="flex-row gap-2">
        {templates.map((template) => (
          <Image
            key={template.id}
            source={{ uri: template.thumbnail }}
            style={{ width: 52, height: 52, borderRadius: 10 }}
          />
        ))}
      </View>
      <View className="flex-row items-center gap-2">
        <Icon as={CheckCircleIcon} className="h-4 w-4 text-emerald-500" />
        <Text className="text-sm font-semibold text-foreground">
          {`Face enrolled from ${templates.length} angles`}
        </Text>
      </View>
      {enrolledAt ? (
        <Text className="text-xs text-muted-foreground">{`Enrolled ${formatDateTime(enrolledAt)}`}</Text>
      ) : null}
      <Button variant="outline" size="sm" onPress={onEnrol}>
        <ButtonText>Re-enrol</ButtonText>
      </Button>
    </View>
  );
}

function HistoryRow({ record }: { record: AttendanceRecord }) {
  return (
    <View className="flex-row items-center gap-3 px-5 py-3">
      <Image
        source={{ uri: record.selfie }}
        style={{ width: 44, height: 44, borderRadius: 10 }}
      />
      <View className="flex-1">
        <Text className="text-sm font-semibold text-foreground">{formatDay(record.markedAt)}</Text>
        <View className="flex-row items-center gap-1">
          <Text className="text-xs text-muted-foreground">{formatTime(record.markedAt)}</Text>
          {record.latitude !== null ? (
            <>
              <PixelPin size={11} />
              <Text className="text-xs text-muted-foreground">
                {record.address ?? `${record.latitude.toFixed(3)}, ${record.longitude?.toFixed(3)}`}
              </Text>
            </>
          ) : (
            <Text className="text-xs text-muted-foreground">No location</Text>
          )}
        </View>
      </View>
      {/*
        The confidence score is shown to the admin and never to the staff
        member: an admin can act on a consistently marginal score, whereas a
        staff member can only worry about it.
      */}
      <Text className="font-mono text-xs text-muted-foreground">
        {formatPercent(record.matchScore)}
      </Text>
    </View>
  );
}
