package cz.ondramastik.bang.impl

import cz.ondramastik.bang.api.BangService

import scala.concurrent.ExecutionContext

class BangServiceImpl()(implicit ec: ExecutionContext) extends BangService {}
