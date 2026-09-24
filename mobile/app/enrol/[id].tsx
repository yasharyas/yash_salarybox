import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Image, Text, View } from 'react-native';

import { Button, ButtonText } from '@/components/ui/button';
import { getEmbedder } from '@/src/face/pipeline';
import { cosineSimilarity } from '@/src/face/match';
import { getStaff, saveEnrolment } from '@/src/data/repository';
import type { Staff } from '@/src/data/types';
import { BottomBar, Screen } from '@/src/ui/Screen';
import { firstName } from '@/src/ui/format';
import { PixelCheck } from '@/src/ui/pixel';
import { FaceCapture, type CaptureFrame } from '@/src/ui/capture/FaceCapture';
import { useAsyncData } from '@/src/ui/useAsyncData';

/**
 * Three samples, not one.
 *
 * Matching takes the best score across enrolled samples, so each extra sample
 * is another chance for a genuine attempt to land above the threshold. Three
 * is where the returns flatten: it covers the ordinary variation in how
 * someone holds a phone without turning enrolment into a chore.
 */
const STEPS = [
  { prompt: 'Look straight at the camera', hint: 'Fill the oval with your face.' },
  { prompt: 'Tilt your head slightly', hint: 'A small tilt is plenty.' },
  { prompt: 'Relax your expression', hint: 'Last one.' },
];

/**
 * Below this, the new sample almost certainly is not the same person as the
 * first one. Well under the 0.55 accept threshold, because the cost of a false
 * warning here is an annoyed admin, while the cost of missing it is a staff
 * record that matches two different faces.
 */
const SAME_PERSON_FLOOR = 0.4;

interface Sample {
  embedding: Float32Array;
  thumbnail: string;
}

export default function Enrol() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const staffId = Number(id);
  const { data: staff } = useAsyncData<Staff | undefined>(() => getStaff(staffId), [staffId]);

  const [samples, setSamples] = useState<Sample[]>([]);
  const [busy, setBusy] = useState(false);
  // Bumped once each capture has been dealt with, to re-arm the camera. See
  // the `attempt` prop on FaceCapture for why this is not just `!busy`.
  const [attempt, setAttempt] = useState(0);
  const [warning, setWarning] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const step = samples.length;
  const finished = step >= STEPS.length;

  const onCapture = async (frame: CaptureFrame) => {
    setBusy(true);
    setWarning(null);
    try {
      const embedder = await getEmbedder();
      const embedding = await embedder.embed(frame.aligned);

      if (samples.length > 0) {
        const best = Math.max(
          ...samples.map((sample) => cosineSimilarity(embedding, sample.embedding)),
        );
        if (best < SAME_PERSON_FLOOR) {
          setWarning('That did not look like the same person. Try that one again.');
          return;
        }
      }

      setSamples((previous) => [
        ...previous,
        { embedding, thumbnail: frame.aligned.toDataURL('image/jpeg', 0.85) },
      ]);
    } catch (error) {
      setWarning((error as Error).message);
    } finally {
      setBusy(false);
      setAttempt((value) => value + 1);
    }
  };

  const save = async () => {
    setSaving(true);
    await saveEnrolment(staffId, samples);
    router.back();
  };

  return (
    <Screen
      title={staff ? `Enrol ${firstName(staff.name)}` : 'Enrol face'}
      subtitle={finished ? 'All three captured' : `Step ${step + 1} of ${STEPS.length}`}
      leading="close"
    >
      <View className="flex-1 px-5 pb-2">
        {finished ? (
          <View className="flex-1 items-center justify-center gap-4">
            <PixelCheck size={104} />
            <Text className="text-lg font-semibold text-foreground">Three good captures</Text>
            <Text className="px-6 text-center text-sm text-muted-foreground">
              {staff
                ? `${firstName(staff.name)} can now mark attendance, and only their face will be accepted.`
                : ''}
            </Text>
          </View>
        ) : (
          <FaceCapture
            active={!busy}
            attempt={attempt}
            onCapture={onCapture}
            prompt={STEPS[step].prompt}
          />
        )}
      </View>

      <View className="flex-row items-center justify-center gap-3 py-3">
        {STEPS.map((_, index) => {
          const sample = samples[index];
          return sample ? (
            <Image
              key={index}
              source={{ uri: sample.thumbnail }}
              style={{ width: 44, height: 44, borderRadius: 10 }}
            />
          ) : (
            <View
              key={index}
              className={
                'h-11 w-11 rounded-[10px] border-2 border-dashed ' +
                (index === step ? 'border-foreground' : 'border-border')
              }
            />
          );
        })}
      </View>

      {warning ? (
        <View className="mx-5 mb-2 rounded-lg bg-destructive/15 px-3 py-2">
          <Text className="text-sm text-destructive">{warning}</Text>
        </View>
      ) : !finished ? (
        <Text className="px-5 pb-2 text-center text-xs text-muted-foreground">
          {STEPS[step].hint}
        </Text>
      ) : null}

      <BottomBar>
        {finished ? (
          <>
            <Button onPress={save} isDisabled={saving}>
              <ButtonText>{saving ? 'Saving' : 'Save enrolment'}</ButtonText>
            </Button>
            <Button
              variant="outline"
              onPress={() => {
                setSamples([]);
                setAttempt((value) => value + 1);
              }}
              isDisabled={saving}
            >
              <ButtonText>Retake all three</ButtonText>
            </Button>
          </>
        ) : (
          <Button variant="outline" onPress={() => router.back()}>
            <ButtonText>Cancel</ButtonText>
          </Button>
        )}
      </BottomBar>
    </Screen>
  );
}
