/**
 * The only job of this route is to decide which half of the app you are in.
 *
 * Session is the single source of truth, so signing out from anywhere lands
 * correctly and a reload puts a signed-in user back where they belong. The
 * loading state renders a spinner rather than the sign-in form: treating "not
 * read yet" as "signed out" makes a returning user watch the login screen
 * flash past.
 */
import { Redirect } from 'expo-router';
import { View } from 'react-native';

import { useSession } from '@/src/data/session';
import { PixelSpinner } from '@/src/ui/pixel';

export default function Index() {
  const state = useSession();

  if (state.status === 'loading') {
    return (
      <View className="flex-1 items-center justify-center bg-background">
        <PixelSpinner size={48} />
      </View>
    );
  }

  if (!state.session) return <Redirect href="/login" />;
  return <Redirect href={state.session.role === 'ADMIN' ? '/admin' : '/staff'} />;
}
