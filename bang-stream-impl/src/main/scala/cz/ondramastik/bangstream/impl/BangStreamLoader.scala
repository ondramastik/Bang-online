package cz.ondramastik.bangstream.impl

import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import cz.ondramastik.bangstream.api.BangStreamService
import cz.ondramastik.bang.api.BangService
import com.softwaremill.macwire._

class BangStreamLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new BangStreamApplication(context) {
      override def serviceLocator: NoServiceLocator.type = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new BangStreamApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[BangStreamService])
}

abstract class BangStreamApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents {

  // Bind the service that this server provides
  override lazy val lagomServer: LagomServer = serverFor[BangStreamService](wire[BangStreamServiceImpl])

  // Bind the BangService client
  lazy val bangService: BangService = serviceClient.implement[BangService]
}
