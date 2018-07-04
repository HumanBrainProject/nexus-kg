package ch.epfl.bluebrain.nexus.kg.config

import java.time.Clock

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import ch.epfl.bluebrain.nexus.admin.client.config.AdminConfig
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination
import ch.epfl.bluebrain.nexus.kg.config.AppConfig._
import ch.epfl.bluebrain.nexus.kg.config.Contexts.{resourceCtxUri, tagCtxUri}
import ch.epfl.bluebrain.nexus.kg.resolve.StaticResolution
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import monix.eval.Task

import scala.concurrent.duration.Duration

/**
  * Application
  *
  * @param description service description
  * @param http        http interface configuration
  * @param cluster     akka cluster configuration
  * @param persistence persistence configuration
  * @param attachments attachments configuration
  * @param admin       admin client configuration
  * @param iam         IAM client configuration
  * @param sparql      Sparql endpoint configuration
  * @param elastic     ElasticSearch endpoint configuration
  * @param pagination  Pagination configuration
  */
final case class AppConfig(description: Description,
                           http: HttpConfig,
                           cluster: ClusterConfig,
                           persistence: PersistenceConfig,
                           attachments: AttachmentsConfig,
                           admin: AdminConfig,
                           iam: IamConfig,
                           sparql: SparqlConfig,
                           elastic: ElasticConfig,
                           pagination: PaginationConfig)

object AppConfig {

  /**
    * Service description
    *
    * @param name service name
    */
  final case class Description(name: String) {

    /**
      * @return the version of the service
      */
    val version: String = BuildInfo.version.replaceAll("\\W", "-")

    /**
      * @return the full name of the service (name + version)
      */
    val fullName: String = s"$name-$version"

  }

  /**
    * HTTP configuration
    *
    * @param interface  interface to bind to
    * @param port       port to bind to
    * @param prefix     prefix to add to HTTP routes
    * @param publicUri  public URI of the service
    */
  final case class HttpConfig(interface: String, port: Int, prefix: String, publicUri: Uri)

  /**
    *  Cluster configuration
    * @param passivationTimeout actor passivation timeout
    * @param shards             number of shards in the cluster
    * @param seeds              seed nodes in the cluster
    */
  final case class ClusterConfig(passivationTimeout: Duration, shards: Int, seeds: Option[String])

  /**
    * Persistence configuration
    * @param journalPlugin        plugin for storing events
    * @param snapshotStorePlugin  plugin for storing snapshots
    * @param queryJournalPlugin   plugin for querying journal events
    */
  final case class PersistenceConfig(journalPlugin: String, snapshotStorePlugin: String, queryJournalPlugin: String)

  /**
    * Attachments configuration
    *
    * @param volume          the base Iri where the attachments are stored
    * @param digestAlgorithm algorithm for checksum calculation
    */
  final case class AttachmentsConfig(volume: AbsoluteIri, digestAlgorithm: String)

  /**
    * IAM config
    * @param baseUri base URI of IAM service
    */
  final case class IamConfig(baseUri: Uri)

  /**
    * Collection of configurable settings specific to the Sparql indexer.
    *
    * @param base         the base uri
    * @param username     the SPARQL endpoint username
    * @param password     the SPARQL endpoint password
    * @param defaultIndex the SPARQL default index
    */
  final case class SparqlConfig(base: Uri, username: Option[String], password: Option[String], defaultIndex: String) {

    val akkaCredentials: Option[BasicHttpCredentials] =
      for {
        user <- username
        pass <- password
      } yield BasicHttpCredentials(user, pass)
  }

  /**
    * Collection of configurable settings specific to the ElasticSearch indexer.
    *
    * @param base         the application base uri for operating on resources
    * @param indexPrefix  the prefix of the index
    * @param docType      the name of the `type`
    * @param defaultIndex the default index
    */
  final case class ElasticConfig(base: Uri, indexPrefix: String, docType: String, defaultIndex: String)

  /**
    * Pagination configuration
    *
    * @param from      the start offset
    * @param size      the default number of results per page
    * @param sizeLimit the maximum number of results per page
    */
  final case class PaginationConfig(from: Long, size: Int, sizeLimit: Int) {
    val pagination: Pagination = Pagination(from, size)
  }

  /**
    * Default instance of [[StaticResolution]]
    */
  val staticResolution: StaticResolution[Task] = {
    implicit val clock: Clock = Clock.systemUTC
    StaticResolution[Task](
      Map(
        tagCtxUri      -> "/contexts/tags-context.json",
        resourceCtxUri -> "/contexts/resource-context.json"
      ))
  }

  implicit def toSparql(implicit appConfig: AppConfig): SparqlConfig         = appConfig.sparql
  implicit def toElastic(implicit appConfig: AppConfig): ElasticConfig       = appConfig.elastic
  implicit def toPagination(implicit appConfig: AppConfig): PaginationConfig = appConfig.pagination

}
