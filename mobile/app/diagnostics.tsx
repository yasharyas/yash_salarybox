/**
 * Runs the Android instrumentation accuracy test, in the browser.
 *
 * FaceRecognitionAccuracyTest builds a pairwise genuine/impostor matrix over
 * the same seven photographs and asserts the threshold lands in the gap. This
 * page does the same work with the same model file, so the two sets of numbers
 * can be compared directly. If the web port had drifted, this is where it
 * would show.
 *
 * It also renders every aligned crop. That is not decoration. The worst bug in
 * the Android build was an eye-landmark swap that rotated every crop by 180
 * degrees; it passed every assertion, because enrolment and verification were
 * consistently wrong together. It was caught by looking at the pictures.
 */
import { useEffect, useState } from 'react';
import { Image, Platform, ScrollView, Text, View } from 'react-native';

import { alignFace } from '@/src/face/align';
import { MobileFaceNetEmbedder } from '@/src/face/embedder';
import { FaceLandmarkerService } from '@/src/face/landmarker';
import { DEFAULT_THRESHOLD, cosineSimilarity } from '@/src/face/match';

const FIXTURES = [
  { file: 'obama.jpg', identity: 'obama' },
  { file: 'obama2.jpg', identity: 'obama' },
  { file: 'obama-480p.jpg', identity: 'obama' },
  { file: 'obama_small.jpg', identity: 'obama' },
  { file: 'biden.jpg', identity: 'biden' },
  { file: 'alex-lacamoire.png', identity: 'alex' },
  { file: 'lin-manuel-miranda.png', identity: 'lin' },
];

interface Sample {
  file: string;
  identity: string;
  crop: string;
  embedding: Float32Array;
  norm: number;
}

interface Stats {
  count: number;
  min: number;
  mean: number;
  max: number;
}

function summarise(values: number[]): Stats {
  const sum = values.reduce((total, value) => total + value, 0);
  return {
    count: values.length,
    min: Math.min(...values),
    mean: sum / values.length,
    max: Math.max(...values),
  };
}

function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new window.Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error('Could not load ' + url));
    image.src = url;
  });
}

export default function Diagnostics() {
  const [log, setLog] = useState<string[]>([]);
  const [samples, setSamples] = useState<Sample[]>([]);
  const [genuine, setGenuine] = useState<Stats | null>(null);
  const [impostor, setImpostor] = useState<Stats | null>(null);

  useEffect(() => {
    if (Platform.OS !== 'web') return;
    let cancelled = false;
    const say = (line: string) => {
      if (!cancelled) setLog((previous) => previous.concat(line));
    };

    const run = async () => {
      try {
        say('Loading MobileFaceNet...');
        const embedder = await MobileFaceNetEmbedder.load();
        say('  input ' + embedder.inputSize + ', output ' + embedder.embeddingSize + '-d');

        say('Loading MediaPipe FaceLandmarker...');
        const landmarker = await FaceLandmarkerService.load('IMAGE');
        say('  ready');

        const found: Sample[] = [];
        let namingNoted = false;

        for (const fixture of FIXTURES) {
          const image = await loadImage('/faces/' + fixture.file);
          const detection = landmarker.detectImage(image);
          const face = detection.faces[0];
          if (!face) {
            say('  ' + fixture.file + ': NO FACE');
            continue;
          }

          if (!namingNoted) {
            namingNoted = true;
            say(
              '  iris 468 x=' +
                face.eyeA.x.toFixed(1) +
                ', iris 473 x=' +
                face.eyeB.x.toFixed(1) +
                ' -> image-left is ' +
                (face.eyeA.x <= face.eyeB.x ? '468' : '473'),
            );
            say('  iris refinement present: ' + detection.hasIris);
          }

          const crop = alignFace(image, face.eyeA, face.eyeB);
          if (!crop) {
            say('  ' + fixture.file + ': ALIGN FAILED');
            continue;
          }
          const embedding = await embedder.embed(crop);
          let sumOfSquares = 0;
          for (const value of embedding) sumOfSquares += value * value;
          found.push({
            file: fixture.file,
            identity: fixture.identity,
            crop: crop.toDataURL(),
            embedding,
            norm: Math.sqrt(sumOfSquares),
          });
          say('  ' + fixture.file + ': ok (roll ' + face.roll.toFixed(1) + ' deg)');
        }

        if (cancelled) return;
        setSamples(found);

        const genuineScores: number[] = [];
        const impostorScores: number[] = [];
        for (let i = 0; i < found.length; i += 1) {
          for (let j = i + 1; j < found.length; j += 1) {
            const score = cosineSimilarity(found[i].embedding, found[j].embedding);
            if (found[i].identity === found[j].identity) {
              genuineScores.push(score);
            } else {
              impostorScores.push(score);
            }
          }
        }
        setGenuine(summarise(genuineScores));
        setImpostor(summarise(impostorScores));
        say('Done: ' + genuineScores.length + ' genuine, ' + impostorScores.length + ' impostor');
      } catch (error) {
        say('FAILED: ' + (error as Error).message);
        console.error(error);
      }
    };

    run();

    return () => {
      cancelled = true;
    };
  }, []);

  const margin = genuine && impostor ? genuine.min - impostor.max : null;
  const insideGap =
    genuine && impostor && impostor.max < DEFAULT_THRESHOLD && genuine.min > DEFAULT_THRESHOLD;

  return (
    <ScrollView className="flex-1 bg-background" contentContainerClassName="p-5 gap-5">
      <Text className="text-2xl font-bold text-foreground">Face pipeline diagnostics</Text>
      <Text className="text-sm text-muted-foreground">
        Same model file, same alignment maths and same fixtures as the Android instrumentation
        test, re-run in this browser.
      </Text>

      <View className="gap-1 rounded-xl bg-secondary p-4">
        {log.map((line, index) => (
          <Text key={index} className="font-mono text-xs text-secondary-foreground">
            {line}
          </Text>
        ))}
      </View>

      {genuine && impostor ? (
        <View className="gap-2 rounded-xl bg-secondary p-4">
          <StatRow label="genuine" stats={genuine} />
          <StatRow label="impostor" stats={impostor} />
          <Text className="mt-2 font-mono text-xs text-secondary-foreground">
            {'margin ' +
              (margin === null ? '?' : margin.toFixed(3)) +
              ' | threshold ' +
              DEFAULT_THRESHOLD +
              ' | ' +
              (insideGap ? 'INSIDE THE GAP' : 'OUTSIDE THE GAP')}
          </Text>
        </View>
      ) : null}

      <Text className="text-base font-semibold text-foreground">
        Aligned crops (eyes must be level and upright)
      </Text>
      <View className="flex-row flex-wrap gap-3">
        {samples.map((sample) => (
          <View key={sample.file} className="items-center gap-1">
            <Image source={{ uri: sample.crop }} style={{ width: 112, height: 112 }} />
            <Text className="font-mono text-[10px] text-muted-foreground">{sample.identity}</Text>
            <Text className="font-mono text-[10px] text-muted-foreground">
              {'|v| ' + sample.norm.toFixed(3)}
            </Text>
          </View>
        ))}
      </View>
    </ScrollView>
  );
}

function StatRow({ label, stats }: { label: string; stats: Stats }) {
  return (
    <Text className="font-mono text-xs text-secondary-foreground">
      {label.padEnd(9) +
        ' n=' +
        String(stats.count).padStart(2) +
        '  min ' +
        stats.min.toFixed(3) +
        '  mean ' +
        stats.mean.toFixed(3) +
        '  max ' +
        stats.max.toFixed(3)}
    </Text>
  );
}
