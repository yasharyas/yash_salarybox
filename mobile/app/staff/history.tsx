import { FlatList, View } from 'react-native';

import { historyFor } from '@/src/data/repository';
import { useSession } from '@/src/data/session';
import type { AttendanceRecord } from '@/src/data/types';
import { Screen } from '@/src/ui/Screen';
import { PixelEmptyState, PixelSpinner } from '@/src/ui/pixel';
import { useAsyncData } from '@/src/ui/useAsyncData';
import { RecordRow } from './index';

export default function StaffHistory() {
  const session = useSession();
  const staffId = session.status === 'resolved' ? session.session?.staffId : null;

  const { data, loading } = useAsyncData<AttendanceRecord[]>(
    () => (staffId ? historyFor(staffId) : Promise.resolve([])),
    [staffId],
  );

  return (
    <Screen title="Your attendance" leading="back">
      {loading && !data ? (
        <View className="flex-1 items-center justify-center">
          <PixelSpinner size={48} />
        </View>
      ) : (
        <FlatList
          data={data ?? []}
          keyExtractor={(record) => String(record.id)}
          contentContainerClassName="px-5 pb-10"
          ListEmptyComponent={
            <PixelEmptyState
              title="No records yet"
              body="Once you mark attendance, every entry shows up here with its time and place."
            />
          }
          renderItem={({ item }) => <RecordRow record={item} />}
        />
      )}
    </Screen>
  );
}
