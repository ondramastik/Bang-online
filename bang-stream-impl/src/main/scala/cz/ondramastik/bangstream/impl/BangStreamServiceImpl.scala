package cz.ondramastik.bangstream.impl

import com.lightbend.lagom.scaladsl.api.ServiceCall
import cz.ondramastik.bangstream.api.BangStreamService
import cz.ondramastik.bang.api.BangService

import scala.concurrent.Future

/**
  * Implementation of the BangStreamService.
  */
class BangStreamServiceImpl(bangService: BangService) extends BangStreamService {
  def stream = ServiceCall { hellos =>
    Future.successful(hellos.mapAsync(8)(bangService.hello(_).invoke()))
  }
}
