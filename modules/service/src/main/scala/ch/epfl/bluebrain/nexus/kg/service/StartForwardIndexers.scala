package ch.epfl.bluebrain.nexus.kg.service

import _root_.io.circe.generic.extras.auto._
import _root_.io.circe.generic.extras.Configuration
import _root_.io.circe.java8.time._
import cats.instances.future._
import akka.actor.{ActorSystem}
import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.kg.indexing.ForwardIndexingSettings
import ch.epfl.bluebrain.nexus.kg.indexing.instances.InstanceForwardIndexer
import ch.epfl.bluebrain.nexus.commons.forward.client.ForwardClient
import ch.epfl.bluebrain.nexus.commons.service.persistence.SequentialTagIndexer
import ch.epfl.bluebrain.nexus.kg.core.contexts.Contexts
import ch.epfl.bluebrain.nexus.kg.core.instances.InstanceEvent
import ch.epfl.bluebrain.nexus.kg.service.config.Settings

import scala.concurrent.{ExecutionContext, Future}

/**
  * Triggers the start of the indexing process from the resumable projection for all the tags available on the service:
  * instance, schema, domain, organization.
  *
  * @param settings     the app settings
  * @param forwardClient the Forward client implementation
  * @param contexts     the context operation bundle
  * @param apiUri       the service public uri + prefix
  * @param as           the implicitly available [[ActorSystem]]
  * @param ec           the implicitly available [[ExecutionContext]]
  */
// $COVERAGE-OFF$
class StartForwardIndexers(settings: Settings,
                           forwardClient: ForwardClient[Future],
                           contexts: Contexts[Future],
                           apiUri: Uri)(implicit
                                        as: ActorSystem,
                                        ec: ExecutionContext) {

  private implicit val config: Configuration =
    Configuration.default.withDiscriminator("type")

  private val indexingSettings = ForwardIndexingSettings(apiUri)

  startIndexingInstances()

  private def startIndexingInstances() =
    SequentialTagIndexer.start[InstanceEvent](
      InstanceForwardIndexer[Future](forwardClient, contexts, indexingSettings)(catsStdInstancesForFuture(ec)).apply _,
      "instance-to-forward",
      settings.Persistence.QueryJournalPlugin,
      "instance",
      "sequential-instance-forward-indexer"
    )



}

object StartForwardIndexers {

  /**
    * Constructs a StartForwardIndexers
    *
    * @param settings      the app settings
    * @param forwardClient the Forward client implementation
    * @param apiUri        the service public uri + prefix
    */
  final def apply(settings: Settings, forwardClient: ForwardClient[Future], contexts: Contexts[Future], apiUri: Uri)(
    implicit
    as: ActorSystem,
    ec: ExecutionContext): StartForwardIndexers =
    new StartForwardIndexers(settings, forwardClient, contexts, apiUri)

}

// $COVERAGE-ON$
