package cz.ondramastik.bang.impl

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import cz.ondramastik.bang.impl.CardManagerBehavior._

object CardManagerBehavior {

  def apply(): Behavior[Command] =
    Behaviors.setup(context => new CardManagerBehavior(context))

  trait CardManagerCommandSerializable

  sealed trait Command
    extends CardManagerCommandSerializable

  case class RequestCardGroupRef(groupId: String, replyTo: ActorRef[GameManagerBehavior.Command])
    extends Command
}

class CardManagerBehavior(context: ActorContext[Command])
  extends AbstractBehavior[Command](context) {

  var groupIdToActor = Map.empty[String, ActorRef[PlayerGroupBehavior.Command]]

  override def onMessage(msg: Command): Behavior[Command] =
    msg match {
      case RequestCardGroupRef(groupId, replyTo) =>
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