package cz.ondramastik.bang.impl

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import cz.ondramastik.bang.domain.CardType
import cz.ondramastik.bang.domain.CardType.CardType
import cz.ondramastik.bang.impl.CardLogicManagerBehavior._
import cz.ondramastik.bang.impl.cardlogicbehaviors.WellsFargoBehavior

import cz.ondramastik.bang.impl.cardlogicbehaviors.{Command => CardLogicCommand}

object CardLogicManagerBehavior {

  def apply(): Behavior[Command] =
    Behaviors.setup(context => new CardLogicManagerBehavior(context))

  trait CardLogicCommandSerializable

  sealed trait Command
    extends CardLogicCommandSerializable

  case class RequestCardLogicRef(cardType: CardType, replyTo: ActorRef[CardBehavior.Command])
    extends Command
}

class CardLogicManagerBehavior(context: ActorContext[Command])
  extends AbstractBehavior[Command](context) {

  private def getCardLogicRef(cardType: CardType): ActorRef[CardLogicCommand] = cardType match {
    case CardType.WellsFargo => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
  }

  override def onMessage(msg: Command): Behavior[Command] =
    msg match {
      case RequestCardLogicRef(cardType, replyTo) =>
        replyTo ! CardBehavior.SupplyCardLogicRef(getCardLogicRef(cardType))
        this
    }

}