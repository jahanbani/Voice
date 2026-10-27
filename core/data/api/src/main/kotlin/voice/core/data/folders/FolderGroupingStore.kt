package voice.core.data.folders

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import voice.core.datastore.VoiceDataStoreFactory

@Serializable
public enum class FolderGrouping {
  OneBook,
  Collection,
}

@SingleIn(AppScope::class)
public class FolderGroupingStore(store: DataStore<Map<String, FolderGrouping>>) : DataStore<Map<String, FolderGrouping>> by store {
  @Inject
  public constructor(factory: VoiceDataStoreFactory) : this(
    factory.create(
      serializer = MapSerializer(String.serializer(), FolderGrouping.serializer()),
      fileName = "folderGrouping",
      defaultValue = emptyMap(),
    ),
  )
}
