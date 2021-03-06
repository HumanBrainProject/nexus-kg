package ch.epfl.bluebrain.nexus.kg.resolve

import ch.epfl.bluebrain.nexus.commons.types.search.{QueryResult, QueryResults}
import ch.epfl.bluebrain.nexus.iam.client.types.Identity
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.{AuthenticatedRef, GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.kg.config.Contexts._
import ch.epfl.bluebrain.nexus.kg.config.Vocabulary._
import ch.epfl.bluebrain.nexus.kg.resolve.Resolver.{CrossProjectResolver, InAccountResolver, InProjectResolver}
import ch.epfl.bluebrain.nexus.kg.search.QueryResultEncoder._
import ch.epfl.bluebrain.nexus.rdf.Graph.Triple
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.Node._
import ch.epfl.bluebrain.nexus.rdf.Vocabulary._
import ch.epfl.bluebrain.nexus.rdf.encoder.GraphEncoder
import ch.epfl.bluebrain.nexus.rdf.syntax.circe.context._
import ch.epfl.bluebrain.nexus.rdf.syntax.node._
import ch.epfl.bluebrain.nexus.rdf.{Graph, Node}
import io.circe.Encoder

/**
  * Encoders for [[Resolver]]
  */
object ResolverEncoder {

  implicit def qrResolverEncoder: Encoder[QueryResults[Resolver]] =
    qrsEncoder[Resolver](resolverCtx mergeContext resourceCtx) mapJson (_ addContext resolverCtxUri)

  implicit val resolverGraphEncoder: GraphEncoder[Resolver] = GraphEncoder {
    case resolver: InProjectResolver => IriNode(resolver.id) -> Graph(resolver.mainTriples(nxv.InProject))
    case resolver @ CrossProjectResolver(resourceTypes, projects, identities, _, id, _, _, _) =>
      val s                            = IriNode(id)
      val projectsTriples: Set[Triple] = projects.map(r => (s: IriOrBNode, nxv.projects, r.id: Node))
      s -> Graph(
        resolver.mainTriples(nxv.CrossProject) ++ resolver.triplesFor(identities) ++ resolver
          .triplesFor(resourceTypes) ++ projectsTriples)
    case resolver @ InAccountResolver(resourceTypes, identities, _, _, id, _, _, _) =>
      val s = IriNode(id)
      s -> Graph(
        resolver.mainTriples(nxv.InAccount) ++ resolver.triplesFor(identities) ++ resolver.triplesFor(resourceTypes))
  }

  private implicit def qqResolverEncoder(implicit enc: GraphEncoder[Resolver]): GraphEncoder[QueryResult[Resolver]] =
    GraphEncoder { res =>
      val encoded = enc(res.source)
      encoded.subject -> encoded.graph
    }

  private implicit class ResolverSyntax(resolver: Resolver) {
    private val s = IriNode(resolver.id)

    def mainTriples(tpe: AbsoluteIri): Set[Triple] =
      Set(
        (s, rdf.tpe, nxv.Resolver),
        (s, rdf.tpe, tpe),
        (s, nxv.priority, resolver.priority),
        (s, nxv.deprecated, resolver.deprecated),
        (s, nxv.rev, resolver.rev)
      )

    def triplesFor(identities: List[Identity]): Set[Triple] =
      identities.foldLeft(Set.empty[Triple]) { (acc, identity) =>
        val (bNode, triples) = triplesFor(identity)
        acc + ((s, nxv.identities, bNode)) ++ triples
      }

    def triplesFor(resourceTypes: Set[AbsoluteIri]): Set[Triple] =
      resourceTypes.map(r => (s: IriOrBNode, nxv.resourceTypes, IriNode(r): Node))

    private def triplesFor(identity: Identity): (BNode, Set[Triple]) = {
      val ss = blank
      identity match {
        case UserRef(realm, sub)           => ss -> Set((ss, rdf.tpe, nxv.UserRef), (ss, nxv.realm, realm), (ss, nxv.sub, sub))
        case GroupRef(realm, g)            => ss -> Set((ss, rdf.tpe, nxv.GroupRef), (ss, nxv.realm, realm), (ss, nxv.group, g))
        case AuthenticatedRef(Some(realm)) => ss -> Set((ss, rdf.tpe, nxv.AuthenticatedRef), (ss, nxv.realm, realm))
        case AuthenticatedRef(_)           => ss -> Set((ss, rdf.tpe, nxv.AuthenticatedRef))
        case _                             => ss -> Set((ss, rdf.tpe, nxv.Anonymous))
      }
    }
  }
}
