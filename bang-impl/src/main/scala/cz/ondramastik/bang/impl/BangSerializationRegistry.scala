package cz.ondramastik.bang.impl

import com.lightbend.lagom.scaladsl.playjson.{
  JsonSerializer,
  JsonSerializerRegistry
}

import scala.collection.immutable

object BangSerializationRegistry extends JsonSerializerRegistry {

  override def serializers: immutable.Seq[JsonSerializer[_]] = Seq.empty

}
