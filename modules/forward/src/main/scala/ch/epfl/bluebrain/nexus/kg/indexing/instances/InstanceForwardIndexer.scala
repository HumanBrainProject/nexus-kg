package ch.epfl.bluebrain.nexus.kg.indexing.instances

import cats.MonadError
import ch.epfl.bluebrain.nexus.commons.forward.client.ForwardClient
import ch.epfl.bluebrain.nexus.kg.core.contexts.Contexts
import ch.epfl.bluebrain.nexus.kg.core.instances.InstanceEvent._
import ch.epfl.bluebrain.nexus.kg.core.instances.InstanceEvent
import ch.epfl.bluebrain.nexus.kg.indexing.{BaseForwardIndexer, ForwardIndexingSettings}
import journal.Logger


/**
  * Instance incremental indexing logic that pushes data into an Forward indexer.
  *
  * @param client   the Forward client to use for communicating with the Forward indexer
  * @param settings the indexing settings
  * @param F        a MonadError typeclass instance for ''F[_]''
  * @tparam F the monadic effect type
  */
class InstanceForwardIndexer[F[_]](client: ForwardClient[F], contexts: Contexts[F], settings: ForwardIndexingSettings)(
    implicit F: MonadError[F, Throwable])
    extends BaseForwardIndexer[F](client, settings){

  private val log = Logger[this.type]
  log.info(s"forward index to: ${settings.base}")
  log.info(s"${contexts.toString}")
  F.pure(())
  /**
    * Indexes the event by pushing it's json ld representation into the Forward indexer while also updating the
    * existing content.
    *
    * @param event the event to index
    * @return a Unit value in the ''F[_]'' context
    */
  final def apply(event: InstanceEvent): F[Unit] = {
    val version = event.id.schemaId.version
    val fullId = Seq(
      event.id.schemaId.domainId.orgId.id,
      event.id.schemaId.domainId.id,
      event.id.schemaId.name,
      s"v${version.major}.${version.minor}.${version.patch}",
      event.id.id).mkString("/")

    event match {
      case InstanceCreated(_, _, _, value) =>
        log.info(s"Indexing 'InstanceCreated' event for id: '${fullId}'")
        client.create(fullId, value)

      case InstanceUpdated(_, rev, _, value) =>
        val fullIdWithRev = s"${fullId}/${rev}"
        log.info(s"Indexing 'InstanceUpdated' event for id: '${fullIdWithRev}'")
        client.update(fullIdWithRev, value)

      case InstanceDeprecated(_, rev, _) =>
        log.info(s"Indexing 'InstanceDeprecated' event for id: '${fullId}'")
        client.delete(fullId, Some(rev.toString))

      case InstanceAttachmentCreated(_, _, _, _) =>
        log.info(s"Indexing 'InstanceAttachmentCreated' event for id: '${fullId}'")
        F.pure(())





      case InstanceAttachmentRemoved(_, _, _) =>
        log.info(s"Indexing 'InstanceAttachmentRemoved' event for id: '${fullId}'")
        F.pure(())
    }
  }

}

object InstanceForwardIndexer {

  /**
    * Constructs an instance incremental indexer that pushes data into an Forward indexer.
    *
    * @param client   the Forward client to use for communicating with the Forward indexer
    * @param settings the indexing settings
    * @param F        a MonadError typeclass instance for ''F[_]''
    * @tparam F the monadic effect type
    */
  final def apply[F[_]](client: ForwardClient[F], contexts: Contexts[F], settings: ForwardIndexingSettings)(
      implicit F: MonadError[F, Throwable]): InstanceForwardIndexer[F] =
    new InstanceForwardIndexer(client, contexts, settings)
}
