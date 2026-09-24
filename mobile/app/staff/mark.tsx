/**
 * The screen the whole assignment is about: prove it is you, then record it.
 *
 * The order matters. The face is matched BEFORE anything is written, so a
 * failed match leaves no trace and a successful one cannot be recorded without
 * a score. Location is fetched after the match rather than before, so someone
 * who is going to be rejected is never asked for their whereabouts.
 */
import { router } from 'expo-router';
import { useState } from 'react';
import { Text, View } from 'react-native';

import { Button, ButtonText } from '@/components/ui/button';
import { DEFAULT_THRESHOLD, match } from '@/src/face/match';
import { getEmbedder } from '@/src/face/pipeline';
import { currentPosition } from '@/src/data/location';
import {
  getStaff,
  hasMarkedToday,
  recordAttendance,
  templatesFor,
} from '@/src/data/repository';
import { useSession } from '@/src/data/session';
import { Screen } from '@/src/ui/Screen';
import { formatTime } from '@/src/ui/format';
import { PixelCheck, PixelCross, PixelPin } from '@/src/ui/pixel';
import { FaceCapture, type CaptureFrame } from '@/src/ui/capture/FaceCapture';

type Outcome =
  | { kind: 'marked'; at: number; hasLocation: boolean }
  | { kind: 'not-recognised' }
  | { kind: 'not-enrolled' }
  | { kind: 'already-marked'; at: number }
  | { kind: 'error'; message: string };

export default function MarkAttendance() {
  const session = useSession();
  const staffId = session.status === 'resolved' ? session.session?.staffId : null;

  const [busy, setBusy] = useState(false);
  const [outcome, setOutcome] = useState<Outcome | null>(null);

  const onCapture = async (frame: CaptureFrame) => {
    if (!staffId) return;
    setBusy(true);
    try {
      const existing = await hasMarkedToday(staffId);
      if (existing) {
        const { historyFor } = await import('@/src/data/repository');
        const history = await historyFor(staffId);
        setOutcome({ kind: 'already-marked', at: history[0]?.markedAt ?? Date.now() });
        return;
      }

      const templates = await templatesFor(staffId);
      if (templates.length === 0) {
        setOutcome({ kind: 'not-enrolled' });
        return;
      }

      const embedder = await getEmbedder();
      const embedding = await embedder.embed(frame.aligned);
      const result = match(
        embedding,
        templates.map((template) => Float32Array.from(template.embedding)),
      );

      if (!result.matched) {
        setOutcome({ kind: 'not-recognised' });
        return;
      }

      const staff = await getStaff(staffId);
      const fix = await currentPosition();
      const markedAt = Date.now();
      await recordAttendance({
        staffId,
        employeeId: staff?.employeeId ?? '',
        markedAt,
        selfie: frame.selfie,
        matchScore: result.score,
        threshold: result.threshold,
        latitude: fix?.latitude ?? null,
        longitude: fix?.longitude ?? null,
        accuracy: fix?.accuracy ?? null,
        address: null,
      });
      setOutcome({ kind: 'marked', at: markedAt, hasLocation: fix !== null });
    } catch (error) {
      setOutcome({ kind: 'error', message: (error as Error).message });
    } finally {
      setBusy(false);
    }
  };

  const retry = () => {
    setOutcome(null);
    setBusy(false);
  };

  return (
    <Screen title="Mark attendance" leading="close">
      <View className="flex-1 px-5 pb-4">
        {outcome ? (
          <OutcomePanel outcome={outcome} onRetry={retry} onDone={() => router.back()} />
        ) : (
          <FaceCapture active={!busy} onCapture={onCapture} />
        )}
      </View>
    </Screen>
  );
}

function OutcomePanel({
  outcome,
  onRetry,
  onDone,
}: {
  outcome: Outcome;
  onRetry: () => void;
  onDone: () => void;
}) {
  if (outcome.kind === 'marked') {
    return (
      <View className="flex-1 items-center justify-center gap-4">
        <PixelCheck size={116} />
        <Text className="text-2xl font-bold text-foreground">Attendance marked</Text>
        <Text className="text-base text-muted-foreground">{formatTime(outcome.at)}</Text>
        <View className="flex-row items-center gap-1">
          <PixelPin size={13} />
          <Text className="text-xs text-muted-foreground">
            {outcome.hasLocation
              ? 'Time, selfie and location saved'
              : 'Time and selfie saved, location unavailable'}
          </Text>
        </View>
        <Button className="mt-4 w-full" onPress={onDone}>
          <ButtonText>Done</ButtonText>
        </Button>
      </View>
    );
  }

  const copy = {
    'not-recognised': {
      title: 'That is not a match',
      body: 'Your face did not match the one enrolled for this account. Try again in better light, straight on to the camera.',
      retry: 'Try again',
    },
    'not-enrolled': {
      title: 'No face enrolled',
      body: 'An admin needs to enrol your face before you can mark attendance.',
      retry: null,
    },
    'already-marked': {
      title: 'Already marked today',
      body: 'Attendance is recorded once a day, and today is already done.',
      retry: null,
    },
    error: { title: 'Something went wrong', body: '', retry: 'Try again' },
  }[outcome.kind];

  const body =
    outcome.kind === 'already-marked'
      ? `You marked attendance at ${formatTime(outcome.at)}. Attendance is recorded once a day.`
      : outcome.kind === 'error'
        ? outcome.message
        : copy.body;

  return (
    <View className="flex-1 items-center justify-center gap-4">
      <PixelCross size={116} />
      <Text className="text-2xl font-bold text-foreground">{copy.title}</Text>
      <Text className="px-4 text-center text-sm leading-5 text-muted-foreground">{body}</Text>
      {/*
        The score is deliberately not shown here. A staff member told "you
        scored 0.48" can do nothing with that except worry about it; the admin,
        who can act on a pattern of marginal scores, does see it.
      */}
      <View className="mt-4 w-full gap-2">
        {copy.retry ? (
          <Button onPress={onRetry}>
            <ButtonText>{copy.retry}</ButtonText>
          </Button>
        ) : null}
        <Button variant="outline" onPress={onDone}>
          <ButtonText>Back</ButtonText>
        </Button>
      </View>
    </View>
  );
}

export { DEFAULT_THRESHOLD };
