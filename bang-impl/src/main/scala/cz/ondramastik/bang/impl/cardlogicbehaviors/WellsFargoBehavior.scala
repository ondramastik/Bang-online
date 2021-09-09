package cz.ondramastik.bang.impl.cardlogicbehaviors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import cz.ondramastik.bang.impl.{CardBehavior, PlayerBehavior, PlayerManagerBehavior}

object WellsFargoBehavior {

  def apply(): Behavior[Command] =
    Behaviors.setup(context => new WellsFargoBehavior(context))
}

class WellsFargoBehavior(context: ActorContext[Command])
  extends AbstractBehavior[Command](context) {

  override def onMessage(msg: Command): Behavior[Command] =
    msg match {
      case PerformAction(initiator, _, replyTo) =>
        initiator ! PlayerBehavior.Draw(3)
        replyTo ! CardBehavior.FinishCard(initiator)
        this
    }

}