/**
 * The shell every screen sits in: safe areas, a title bar, consistent padding.
 *
 * Built once so the screens stay about their own content. The back affordance
 * and the title are the two things a user needs to orient themselves, and
 * having them in one component means they cannot drift apart between screens.
 */

import { router } from 'expo-router';
import type { ReactNode } from 'react';
import { Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, ButtonText } from '@/components/ui/button';
import { Icon } from '@/components/ui/icon';
import { ArrowLeftIcon, CloseIcon } from '@/components/ui/icon';

export interface ScreenProps {
  title?: string;
  subtitle?: string;
  /** 'back' for a stack push, 'close' for something modal-feeling. */
  leading?: 'back' | 'close' | 'none';
  onLeading?: () => void;
  actions?: ReactNode;
  children: ReactNode;
}

export function Screen({
  title,
  subtitle,
  leading = 'none',
  onLeading,
  actions,
  children,
}: ScreenProps) {
  const goBack = () => {
    if (onLeading) {
      onLeading();
      return;
    }
    // A deep link opened directly has nothing to pop to, and a back button
    // that silently does nothing is worse than one that goes somewhere sane.
    if (router.canGoBack()) router.back();
    else router.replace('/');
  };

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['top', 'bottom']}>
      {/*
        Phone-shaped on a desktop browser, rather than a phone layout stretched
        across 1400px. The PWA is meant for a phone, but the same URL opens on
        a laptop, and a column that stays readable there costs one wrapper.
      */}
      <View className="w-full flex-1 self-center max-w-[560px]">
      {title || leading !== 'none' || actions ? (
        <View className="flex-row items-center gap-2 px-3 py-2">
          {leading !== 'none' ? (
            <Button variant="ghost" size="icon" onPress={goBack} accessibilityLabel="Go back">
              <Icon as={leading === 'back' ? ArrowLeftIcon : CloseIcon} className="h-5 w-5" />
            </Button>
          ) : (
            <View className="w-2" />
          )}
          <View className="flex-1">
            {title ? (
              <Text className="text-xl font-bold leading-tight text-foreground" numberOfLines={1}>
                {title}
              </Text>
            ) : null}
            {subtitle ? (
              <Text className="text-xs text-muted-foreground" numberOfLines={1}>
                {subtitle}
              </Text>
            ) : null}
          </View>
          {actions}
        </View>
      ) : null}
        <View className="flex-1">{children}</View>
      </View>
    </SafeAreaView>
  );
}

/** A quiet grouping label for lists. */
export function SectionHeader({ children }: { children: ReactNode }) {
  return (
    <Text className="px-5 pb-2 pt-5 text-xs font-semibold uppercase tracking-widest text-muted-foreground">
      {children}
    </Text>
  );
}

/** Full-width primary action, pinned to the bottom of a form. */
export function BottomBar({ children }: { children: ReactNode }) {
  return <View className="gap-3 border-t border-border px-5 py-4">{children}</View>;
}

export { Button, ButtonText };
