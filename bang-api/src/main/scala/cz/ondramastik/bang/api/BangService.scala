package cz.ondramastik.bang.api

import com.lightbend.lagom.scaladsl.api.{Descriptor, Service}

object BangService {
  val TOPIC_NAME = "greetings"
}

/**
  * The Bang service interface.
  * <p>
  * This describes everything that Lagom needs to know about how to serve and
  * consume the BangService.
  */
trait BangService extends Service {

  override final def descriptor: Descriptor = {
    import Service._
    // @formatter:off
    named("bang")
      .withAutoAcl(true)
    // @formatter:on
  }
}
