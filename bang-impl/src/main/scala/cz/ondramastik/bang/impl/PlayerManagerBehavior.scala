package cz.ondramastik.bang.impl

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import cz.ondramastik.bang.impl.PlayerManagerBehavior._

object PlayerManagerBehavior {

  def apply(): Behavior[Command] =
    Behaviors.setup(context => new PlayerManagerBehavior(context))

  trait PlayerManagerCommandSerializable

  sealed trait Command
    extends PlayerManagerCommandSerializable

  case class RequestPlayerGroupRef(groupId: String, replyTo: ActorRef[GameManagerBehavior.Command])
    extends Command
}

class PlayerManagerBehavior(context: ActorContext[Command])
  extends AbstractBehavior[Command](context) {

  var groupIdToActor = Map.empty[String, ActorRef[PlayerGroupBehavior.Command]]

  override def onMessage(msg: Command): Behavior[Command] =
    msg match {
      case RequestPlayerGroupRef(groupId, replyTo) =>
        groupIdToActor.get(groupId) match {
          case Some(ref) =>
            replyTo ! GameManagerBehavior.SupplyPlayerGroupRef(ref)
          case None =>
            context.log.info("Creating device group actor for {}", groupId)
            val groupActor = context.spawn(PlayerGroupBehavior.create(groupId), "group-" + groupId)
            replyTo ! GameManagerBehavior.SupplyPlayerGroupRef(groupActor)
            groupIdToActor += groupId -> groupActor
        }
        this
    }

}