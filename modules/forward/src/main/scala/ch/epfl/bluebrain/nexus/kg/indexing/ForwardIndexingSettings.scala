package ch.epfl.bluebrain.nexus.kg.indexing

import akka.http.scaladsl.model.Uri

/**
  * Collection of configurable settings specific to organization indexing in the Forward indexer.
  *
  * @param base         the application base uri for operating on resources
  */
final case class ForwardIndexingSettings(base: Uri)
