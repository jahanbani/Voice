package voice.core.data.store

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.builtins.ListSerializer
import voice.core.data.BookCollection
import voice.core.datastore.VoiceDataStoreFactory

@SingleIn(AppScope::class)
public class BookCollectionsStore(store: DataStore<List<BookCollection>>) : DataStore<List<BookCollection>> by store {

  @Inject
  public constructor(factory: VoiceDataStoreFactory) : this(
    factory.create(
      serializer = ListSerializer(BookCollection.serializer()),
      fileName = "bookCollections",
      defaultValue = emptyList(),
    ),
  )
}
