package ch.epfl.bluebrain.nexus.kg.indexing

import cats.MonadError
import ch.epfl.bluebrain.nexus.commons.forward.client.ForwardClient
import ch.epfl.bluebrain.nexus.iam.client.types.Identity._
import ch.epfl.bluebrain.nexus.kg.resources.{Event, Id, ProjectRef}
import ch.epfl.bluebrain.nexus.kg.resources.Event._
import journal.Logger


/**
  * Instance incremental indexing logic that pushes data into an Forward indexer.
  *
  * @param client   the Forward client to use for communicating with the Forward indexer
  * @param settings the indexing settings
  * @param F        a MonadError typeclass instance for ''F[_]''
  * @tparam F the monadic effect type
  */
class InstanceForwardIndexer[F[_]](client: ForwardClient[F], settings: ForwardIndexingSettings)(
    implicit F: MonadError[F, Throwable])
    extends BaseForwardIndexer[F](client, settings){

  private val log = Logger[this.type]
  log.info(s" ==== forward index base url: ${settings.base}")

  /**
    * Indexes the event by pushing it's json ld representation into the Forward indexer while also updating the
    * existing content.
    *
    * @param event the event to index
    * @return a Unit value in the ''F[_]'' context
    */
  final def apply(event: Event): F[Unit] = {
//    val version = event.id.schemaId.version
//    val fullId = Seq(
//      event.id.schemaId.domainId.orgId.id,
//      event.id.schemaId.domainId.id,
//      event.id.schemaId.name,
//      s"v${version.major}.${version.minor}.${version.patch}",
//      event.id.id).mkString("/")
    val id = event.id.toString
    val authorId = event.identity match {
      case UserRef(_, sub) => Some(sub)
      case GroupRef(_, group) => Some(group)
      case AuthenticatedRef(realm) => realm
      case Anonymous => None
    }
//    val authorIdOpt = if (authorId.size > 0) Some(authorId) else None
    val eventTimeStamp = event.instant.toString
    val eventTimeStampOpt = if (eventTimeStamp.size > 0) Some(eventTimeStamp) else None

    event match {
      case Created(_, _ , _, _, source, _, _) =>
        log.info(s"Forward Indexing - CREATE [${authorId} at ${eventTimeStamp}] id: '${id}'")
        client.create(id, source, authorId,  eventTimeStampOpt)

      case Updated(_, rev, _, source, _, _) =>
        val fullIdWithRev = s"${id}/${rev}"
        log.info(s"Forward Indexing - UPDATE [${authorId} at $eventTimeStamp}] id: '${fullIdWithRev}'")
        client.update(fullIdWithRev, source, authorId, eventTimeStampOpt)

      case Deprecated(_, rev, _, _ , _) =>
        log.info(s"Forward Indexing - DEPRECATE [${authorId} at ${eventTimeStamp}] id: '${id}' rev: ${rev}")
        client.delete(id, Some(rev.toString), authorId, eventTimeStampOpt)

      // TODO not used yet
      case TagAdded(_, _, _, _, _, _) =>
        log.info(s"Forward Indexing - ADDTAG [${authorId} at ${eventTimeStamp}] id: '${id}'")
        F.pure(())

      // TODO not used yet
      case AttachmentAdded(_, _, _, _, _) =>
        log.info(s"Forward Indexing - CREATEATTACHEMENT [${authorId} at ${eventTimeStamp}] id: '${id}'")
        F.pure(())

      // TODO not used yet
      case AttachmentRemoved( _, _, _, _, _) =>
        log.info(s"Forward Indexing - REMOVEATTACHEMENT [${authorId} at ${eventTimeStamp}] id: '${id}'")
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
  final def apply[F[_]](client: ForwardClient[F], settings: ForwardIndexingSettings)(
      implicit F: MonadError[F, Throwable]): InstanceForwardIndexer[F] =
    new InstanceForwardIndexer(client, settings)
}
