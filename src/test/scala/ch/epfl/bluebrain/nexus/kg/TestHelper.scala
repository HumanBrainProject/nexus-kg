package ch.epfl.bluebrain.nexus.kg

import java.time.Clock
import java.util.UUID

import ch.epfl.bluebrain.nexus.iam.client.types.Identity
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Anonymous
import ch.epfl.bluebrain.nexus.kg.config.Schemas.resourceSchemaUri
import ch.epfl.bluebrain.nexus.kg.resources.ResourceF.Value
import ch.epfl.bluebrain.nexus.kg.resources.{Id, Ref, ResourceF}
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.syntax.circe._
import ch.epfl.bluebrain.nexus.rdf.syntax.circe.context._
import io.circe.syntax._
import io.circe.{Json, JsonObject}
import org.mockito.ArgumentMatchers.{argThat, isA => mockIsA}
import org.scalatest.EitherValues
import org.scalatest.matchers.{MatchResult, Matcher}

import scala.reflect.ClassTag

trait TestHelper extends MockitoMatchers with EitherValues {

  def simpleV[P](id: Id[P],
                 value: Json,
                 rev: Long = 1L,
                 types: Set[AbsoluteIri] = Set.empty,
                 deprecated: Boolean = false,
                 schema: Ref = Ref(resourceSchemaUri),
                 created: Identity = Anonymous,
                 updated: Identity = Anonymous)(implicit clock: Clock): ResourceF[P, Ref, Value] =
    ResourceF(
      id,
      rev,
      types,
      deprecated,
      Map.empty,
      Set.empty,
      clock.instant(),
      clock.instant(),
      created,
      updated,
      schema,
      Value(value, value.contextValue, value.asGraph.right.value)
    )
  def simpleV[P, S](res: ResourceF[P, S, Json])(implicit clock: Clock) = ResourceF(
    res.id,
    res.rev,
    res.types,
    res.deprecated,
    Map.empty,
    Set.empty,
    clock.instant(),
    clock.instant(),
    res.createdBy,
    res.updatedBy,
    res.schema,
    Value(res.value, res.value.contextValue, res.value.asGraph.right.value)
  )

  def uuid: String = UUID.randomUUID().toString

  def equalIgnoreArrayOrder(json: Json) = IgnoredArrayOrder(json)

  case class IgnoredArrayOrder(json: Json) extends Matcher[Json] {
    private def sortKeys(value: Json): Json = {
      def canonicalJson(json: Json): Json =
        json.arrayOrObject[Json](json,
                                 arr => Json.fromValues(arr.sortBy(_.hashCode()).map(canonicalJson)),
                                 obj => sorted(obj).asJson)

      def sorted(jObj: JsonObject): JsonObject =
        JsonObject.fromIterable(jObj.toVector.sortBy(_._1).map { case (k, v) => k -> canonicalJson(v) })

      canonicalJson(value)
    }

    override def apply(left: Json): MatchResult = {
      val leftSorted  = sortKeys(left)
      val rightSorted = sortKeys(json)
      MatchResult(leftSorted == rightSorted,
                  s"Both Json are not equal (ignoring array order)\n$leftSorted\ndid not equal\n$rightSorted",
                  "")
    }
  }
}

trait MockitoMatchers {
  def isA[T: ClassTag] =
    mockIsA(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])

  def matches[A](f: A => Boolean) = {
    argThat((argument: A) => f(argument))
  }
}
