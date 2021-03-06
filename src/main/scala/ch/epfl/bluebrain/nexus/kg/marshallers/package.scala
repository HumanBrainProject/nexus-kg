package ch.epfl.bluebrain.nexus.kg

import cats.Show
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.commons.shacl.topquadrant.ValidationReport
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection
import ch.epfl.bluebrain.nexus.kg.config.Contexts._
import ch.epfl.bluebrain.nexus.kg.resources.Rejection
import ch.epfl.bluebrain.nexus.rdf.syntax.circe.context._
import io.circe.Encoder
import io.circe.generic.extras.auto._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveEncoder
import io.circe.refined._

package object marshallers {

  private implicit def aEncoder[A: Show]: Encoder[A] = Encoder.encodeString.contramap(_.show)

  /**
    * The discriminator is enough to give us a Json representation (the name of the class)
    */
  private[marshallers] implicit val rejectionConfig: Configuration = Configuration.default.withDiscriminator("code")

  private[marshallers] implicit val validationReportEnc: Encoder[ValidationReport] =
    Encoder.instance(_.json)

  private[marshallers] implicit val rejectionEncoder: Encoder[Rejection] = {
    val enc = deriveEncoder[Rejection]
    Encoder(enc(_).addContext(errorCtxUri))
  }

  private[marshallers] implicit val httpRejectionEncoder: Encoder[HttpRejection] = {
    val enc = deriveEncoder[HttpRejection]
    Encoder(enc(_).addContext(errorCtxUri))
  }
}
