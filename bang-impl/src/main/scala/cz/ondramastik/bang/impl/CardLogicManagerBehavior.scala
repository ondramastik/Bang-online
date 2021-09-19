package cz.ondramastik.bang.impl

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import cz.ondramastik.bang.domain.CardType
import cz.ondramastik.bang.impl.CardLogicManagerBehavior._
import cz.ondramastik.bang.impl.cardlogicbehaviors.WellsFargoBehavior

import cz.ondramastik.bang.impl.cardlogicbehaviors.{Command => CardLogicCommand}

object CardLogicManagerBehavior {

  def apply(): Behavior[Command] =
    Behaviors.setup(context => new CardLogicManagerBehavior(context))

  trait CardLogicCommandSerializable

  sealed trait Command
    extends CardLogicCommandSerializable

  case class RequestCardLogicRef(cardType: CardType.Value, replyTo: ActorRef[CardBehavior.Command])
    extends Command
}

class CardLogicManagerBehavior(context: ActorContext[Command])
  extends AbstractBehavior[Command](context) {

  private def getCardLogicRef(cardType: CardType.Value): ActorRef[CardLogicCommand] = cardType match {
    case CardType.WellsFargo => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Bang => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Birra => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.CatBalou => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Diligenza => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Duello => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Emporio => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Gatling => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Indiani => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Mancato => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Panico => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Saloon => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.WellsFargo => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Appaloosa => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Barile => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Dinamite => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Gun => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Mustang => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Prigione => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Remington => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.RevCarabine => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Schofield => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Volcanic => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")
    case CardType.Winchester => context.spawn(WellsFargoBehavior.apply(), "WellsFargoBehavior")

  }

  override def onMessage(msg: Command): Behavior[Command] =
    msg match {
      case RequestCardLogicRef(cardType, replyTo) =>
        replyTo ! CardBehavior.SupplyCardLogicRef(getCardLogicRef(cardType))
        this
    }

}