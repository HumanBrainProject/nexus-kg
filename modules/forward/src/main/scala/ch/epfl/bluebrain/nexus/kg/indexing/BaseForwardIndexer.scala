package ch.epfl.bluebrain.nexus.kg.indexing


import cats.MonadError
import ch.epfl.bluebrain.nexus.commons.forward.client.ForwardClient
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.kg.core.instances.InstanceId
import ch.epfl.bluebrain.nexus.kg.core.{ConfiguredQualifier, Qualifier}

/**
  * Base incremental indexing logic that pushes data into an Forward indexer.
  *
  * @param client   the Forward client to use for communicating with the Forward indexer
  * @param settings the indexing settings
  * @tparam F the monadic effect type
  */
private[indexing] abstract class BaseForwardIndexer[F[_]](client: ForwardClient[F], settings: ForwardIndexingSettings)(
    implicit F: MonadError[F, Throwable])
    extends Resources {
  val ForwardIndexingSettings(base) = settings

  implicit val instanceIdQualifier: ConfiguredQualifier[InstanceId]   = Qualifier.configured[InstanceId](base)

  def testPost(): F[Unit] = {
    client.toString
    F.pure(())
  }

}