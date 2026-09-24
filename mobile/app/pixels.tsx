/**
 * A sheet of every sprite, at the sizes the app actually uses them.
 *
 * Pixel art is the one thing in a codebase that genuinely cannot be reviewed
 * by reading the source: a grid of characters that looks plausible in an editor
 * can render as mush. This page exists so the sprites get looked at.
 */
import { ScrollView, Text, View } from 'react-native';

import {
  CHECK,
  CLIPBOARD,
  CROSS,
  FACE,
  FACE_UNKNOWN,
  PIN,
  PixelCheck,
  PixelCross,
  PixelScanLoader,
  PixelSpinner,
  PixelSprite,
  usePixelPalette,
} from '@/src/ui/pixel';

const STATIC = [
  { name: 'FACE', rows: FACE },
  { name: 'FACE_UNKNOWN', rows: FACE_UNKNOWN },
  { name: 'CHECK', rows: CHECK },
  { name: 'CROSS', rows: CROSS },
  { name: 'CLIPBOARD', rows: CLIPBOARD },
  { name: 'PIN', rows: PIN },
];

export default function Pixels() {
  const palette = usePixelPalette();
  return (
    <ScrollView className="flex-1 bg-background" contentContainerClassName="p-6 gap-8">
      <Text className="text-2xl font-bold text-foreground">Pixel sprite sheet</Text>

      <View className="gap-3">
        <Text className="text-sm font-semibold text-muted-foreground">Static, at 64 and 24</Text>
        <View className="flex-row flex-wrap gap-6">
          {STATIC.map((item) => (
            <View key={item.name} className="items-center gap-2">
              <PixelSprite rows={item.rows} palette={palette} size={64} />
              <PixelSprite rows={item.rows} palette={palette} size={24} />
              <Text className="font-mono text-[10px] text-muted-foreground">{item.name}</Text>
            </View>
          ))}
        </View>
      </View>

      <View className="gap-3">
        <Text className="text-sm font-semibold text-muted-foreground">Animated</Text>
        <View className="flex-row flex-wrap items-center gap-8">
          <View className="items-center gap-2">
            <PixelScanLoader size={96} />
            <Text className="font-mono text-[10px] text-muted-foreground">scan</Text>
          </View>
          <View className="items-center gap-2">
            <PixelSpinner size={64} />
            <Text className="font-mono text-[10px] text-muted-foreground">spinner</Text>
          </View>
          <View className="items-center gap-2">
            <PixelCheck size={96} />
            <Text className="font-mono text-[10px] text-muted-foreground">check</Text>
          </View>
          <View className="items-center gap-2">
            <PixelCross size={96} />
            <Text className="font-mono text-[10px] text-muted-foreground">cross</Text>
          </View>
        </View>
      </View>
    </ScrollView>
  );
}
