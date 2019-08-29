package ch.epfl.bluebrain.nexus.kg.service

import _root_.io.circe.generic.extras.auto._
import _root_.io.circe.generic.extras.Configuration
import _root_.io.circe.java8.time._
import cats.instances.future._
import akka.actor.{ActorSystem}
import ch.epfl.bluebrain.nexus.kg.indexing.ForwardIndexingSettings
import ch.epfl.bluebrain.nexus.kg.indexing.instances.InstanceForwardIndexer
import ch.epfl.bluebrain.nexus.commons.forward.client.ForwardClient
import ch.epfl.bluebrain.nexus.commons.service.persistence.SequentialTagIndexer
import ch.epfl.bluebrain.nexus.kg.core.instances.InstanceEvent
import ch.epfl.bluebrain.nexus.kg.service.config.Settings

import scala.concurrent.{ExecutionContext, Future}

/**
  * Triggers the start of the indexing process from the resumable projection for all the tags available on the service:
  * instance, schema, domain, organization.
  *
  * @param settings     the app settings
  * @param forwardClient the Forward client implementation
  * @param forwardIndexingSettings       the Forward client settings
  * @param as           the implicitly available [[ActorSystem]]
  * @param ec           the implicitly available [[ExecutionContext]]
  */
// $COVERAGE-OFF$
class StartForwardIndexers(
  settings: Settings,
  forwardClient: ForwardClient[Future],
  forwardIndexingSettings: ForwardIndexingSettings,
  id: String,
  name: String
)(
  implicit
  as: ActorSystem,
  ec: ExecutionContext
) {

  implicit private val config: Configuration =
    Configuration.default.withDiscriminator("type")

  startIndexingInstances()

  private def startIndexingInstances() =
    SequentialTagIndexer.start[InstanceEvent](
      InstanceForwardIndexer[Future](forwardClient, forwardIndexingSettings)(catsStdInstancesForFuture(ec)).apply _,
      id,
      settings.Persistence.QueryJournalPlugin,
      "instance",
      name
    )

}

object StartForwardIndexers {

  /**
    * Constructs a StartForwardIndexers
    *
    * @param settings      the app settings
    * @param forwardClient the Forward client implementation
    * @param forwardIndexingSettings        the Forward client settings
    */
  final def apply(
    settings: Settings,
    forwardClient: ForwardClient[Future],
    forwardIndexingSettings: ForwardIndexingSettings,
    id: String,
    name: String
  )(
    implicit
    as: ActorSystem,
    ec: ExecutionContext
  ): StartForwardIndexers =
    new StartForwardIndexers(settings, forwardClient, forwardIndexingSettings, id, name)

}

// $COVERAGE-ON$
