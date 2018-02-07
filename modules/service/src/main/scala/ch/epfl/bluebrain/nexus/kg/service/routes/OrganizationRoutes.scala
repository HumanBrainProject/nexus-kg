package ch.epfl.bluebrain.nexus.kg.service.routes

import java.time.Clock

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.http.ContextUri
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.commons.iam.IamClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.Permission._
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlClient
import ch.epfl.bluebrain.nexus.kg.core.CallerCtx._
import ch.epfl.bluebrain.nexus.kg.core.contexts.Contexts
import ch.epfl.bluebrain.nexus.kg.core.organizations.{OrgId, Organizations}
import ch.epfl.bluebrain.nexus.kg.core.queries.filtering.FilteringSettings
import ch.epfl.bluebrain.nexus.kg.indexing.query.builder.FilterQueries
import ch.epfl.bluebrain.nexus.kg.indexing.query.{QuerySettings, SparqlQuery}
import ch.epfl.bluebrain.nexus.kg.service.config.Settings.PrefixUris
import ch.epfl.bluebrain.nexus.kg.service.directives.AuthDirectives._
import ch.epfl.bluebrain.nexus.kg.service.directives.QueryDirectives._
import ch.epfl.bluebrain.nexus.kg.service.directives.ResourceDirectives._
import ch.epfl.bluebrain.nexus.kg.service.io.PrinterSettings._
import ch.epfl.bluebrain.nexus.kg.service.routes.SearchResponse._
import ch.epfl.bluebrain.nexus.kg.service.routes.encoders.IdToEntityRetrieval._
import ch.epfl.bluebrain.nexus.kg.service.routes.encoders.OrgCustomEncoders
import io.circe.Json
import kamon.akka.http.KamonTraceDirectives.operationName

import scala.concurrent.{ExecutionContext, Future}

/**
  * Http route definitions for organization specific functionality.
  *
  * @param orgs              the organization operation bundle
  * @param orgQueries        query builder for organizations
  * @param base              the service public uri + prefix
  * @param prefixes          the service context URIs
  */
final class OrganizationRoutes(orgs: Organizations[Future], orgQueries: FilterQueries[Future, OrgId], base: Uri)(
    implicit
    contexts: Contexts[Future],
    querySettings: QuerySettings,
    filteringSettings: FilteringSettings,
    iamClient: IamClient[Future],
    ec: ExecutionContext,
    clock: Clock,
    orderedKeys: OrderedKeys,
    prefixes: PrefixUris)
    extends DefaultRouteHandling(contexts) {

  private implicit val coreContext: ContextUri     = prefixes.CoreContext
  private implicit val encoders: OrgCustomEncoders = new OrgCustomEncoders(base, prefixes)
  import encoders._
  private implicit val _ = orgIdToEntityRetrieval(orgs)

  protected def searchRoutes(implicit credentials: Option[OAuth2BearerToken]): Route =
    (pathEndOrSingleSlash & get & paramsToQuery) { (pagination, query) =>
      (operationName("searchOrganizations") & authenticateCaller) { implicit caller =>
        implicit val _ = orgIdToEntityRetrieval(orgs)
        orgQueries.list(query, pagination).buildResponse(query.fields, base, prefixes, pagination)
      }
    }

  protected def readRoutes(implicit credentials: Option[OAuth2BearerToken]): Route =
    (extractOrgId & pathEndOrSingleSlash) { orgId =>
      (get & authorizeResource(orgId, Read) & format) { format =>
        parameter('rev.as[Long].?) {
          case Some(rev) =>
            operationName("getOrganizationRevision") {
              onSuccess(orgs.fetch(orgId, rev)) {
                case Some(org) => formatOutput(org, format)
                case None      => complete(StatusCodes.NotFound)
              }
            }
          case None =>
            operationName("getOrganization") {
              onSuccess(orgs.fetch(orgId)) {
                case Some(org) => formatOutput(org, format)
                case None      => complete(StatusCodes.NotFound)
              }
            }
        }
      }
    }

  protected def writeRoutes(implicit credentials: Option[OAuth2BearerToken]): Route =
    (extractOrgId & pathEndOrSingleSlash) { orgId =>
      (put & entity(as[Json])) { json =>
        (authenticateCaller & authorizeResource(orgId, Write)) { implicit caller =>
          parameter('rev.as[Long].?) {
            case Some(rev) =>
              operationName("updateOrganization") {
                onSuccess(orgs.update(orgId, rev, json)) { ref =>
                  complete(StatusCodes.OK -> ref)
                }
              }
            case None =>
              operationName("createOrganization") {
                onSuccess(orgs.create(orgId, json)) { ref =>
                  complete(StatusCodes.Created -> ref)
                }
              }
          }
        }
      } ~
        (delete & parameter('rev.as[Long])) { rev =>
          (authenticateCaller & authorizeResource(orgId, Write)) { implicit caller =>
            operationName("deprecateOrganization") {
              onSuccess(orgs.deprecate(orgId, rev)) { ref =>
                complete(StatusCodes.OK -> ref)
              }
            }
          }
        }
    }

  def routes: Route = combinedRoutesFor("organizations")
}

object OrganizationRoutes {

  /**
    * Constructs a new ''OrganizationRoutes'' instance that defines the http routes specific to organizations.
    *
    * @param orgs          the organization operation bundle
    * @param client        the sparql client
    * @param querySettings query parameters form settings
    * @param base          the service public uri + prefix
    * @param prefixes      the service context URIs
    * @return a new ''OrganizationRoutes'' instance
    */
  final def apply(orgs: Organizations[Future], client: SparqlClient[Future], querySettings: QuerySettings, base: Uri)(
      implicit
      contexts: Contexts[Future],
      ec: ExecutionContext,
      iamClient: IamClient[Future],
      filteringSettings: FilteringSettings,
      clock: Clock,
      orderedKeys: OrderedKeys,
      prefixes: PrefixUris): OrganizationRoutes = {

    implicit val qs: QuerySettings = querySettings
    val orgQueries =
      FilterQueries[Future, OrgId](SparqlQuery[Future](client))
    new OrganizationRoutes(orgs, orgQueries, base)
  }
}
