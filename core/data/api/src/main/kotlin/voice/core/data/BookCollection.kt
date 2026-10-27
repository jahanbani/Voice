package voice.core.data

import kotlinx.serialization.Serializable

@Serializable
public data class BookCollection(
  val id: BookId,
  val name: String,
  val parts: List<BookCollectionPart>,
)

@Serializable
public data class BookCollectionPart(
  val id: BookId,
  val name: String,
)
