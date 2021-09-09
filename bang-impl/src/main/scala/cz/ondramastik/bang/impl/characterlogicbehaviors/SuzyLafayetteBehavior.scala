package cz.ondramastik.bang.impl.characterlogicbehaviors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import cz.ondramastik.bang.impl.{CardBehavior, PlayerBehavior}

object SuzyLafayetteBehavior {

  def apply(): Behavior[Command] =
    Behaviors.setup(context => new SuzyLafayetteBehavior(context))
}

class SuzyLafayetteBehavior(context: ActorContext[Command])
  extends AbstractBehavior[Command](context) {

  override def onMessage(msg: Command): Behavior[Command] =
    msg match {
      case PerformAction(initiator, _, replyTo) =>
        initiator ! PlayerBehavior.Draw(3)
        replyTo ! CardBehavior.FinishCard()
        this
    }

}