package cz.ondramastik.bang.impl

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag}
import cz.ondramastik.bang.domain.CardType.CardType
import cz.ondramastik.bang.impl.CardBehavior._
import cz.ondramastik.bang.impl.cardlogicbehaviors.{Command => CardLogicCommand, PerformAction => CardLogicPerformAction}
import play.api.libs.json.{Format, Json}

object CardBehavior {

  def create(cardGroupId: String, cardId: String): Behavior[Command] = Behaviors.setup { ctx =>
    val persistenceId: PersistenceId = PersistenceId("Card", cardId.concat(cardGroupId))

    create(persistenceId)(ctx)
      .withTagger(_ => Set(CardEvent.Tag))

  }

  private[impl] def create(persistenceId: PersistenceId)(implicit ctx: ActorContext[Command]) = EventSourcedBehavior
    .withEnforcedReplies[Command, CardEvent, CardState](
      persistenceId = persistenceId,
      emptyState = CardState.initial,
      commandHandler = (cart, cmd) => cart.applyCommand(cmd),
      eventHandler = (cart, evt) => cart.applyEvent(evt)
    )

  trait CardCommandSerializable

  sealed trait Command
    extends CardCommandSerializable

  case class Initialise(cardId: String, cardType: CardType, cardGroupRef: ActorRef[CardGroupBehavior.Command]) extends Command

  case class AssignToHand(playerRef: ActorRef[PlayerBehavior.Command]) extends Command

  case class SupplyCardLogicRef(cardLogicRef: ActorRef[CardLogicCommand]) extends Command

  case class AssignToTable(playerRef: ActorRef[PlayerBehavior.Command]) extends Command

  case class PlayCard(replyTo: ActorRef[CardGroupBehavior.Command]) extends Command

  case class FinishCard(replyTo: ActorRef[Command]) extends Command

}

/**
 * The current state of the Aggregate.
 */
abstract class CardState {
  def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[CardEvent, CardState]

  def applyEvent(evt: CardEvent): CardState
}

object CardState {

  def initial: CardState = new Empty

  val typeKey = EntityTypeKey[Command]("CardState")

  implicit val format: Format[CardState] = Json.format
}

class Empty extends CardState {

  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[CardEvent, CardState] =
    cmd match {
      case Initialise(cardId, cardType, ref) => Effect
        .persist(InitialisationStarted(cardId, ref))
        .thenRun((_: CardState) => ctx.spawn(CardLogicManagerBehavior.apply(), "CardLogicManagerBehavior")
          ! CardLogicManagerBehavior.RequestCardLogicRef(cardType, ctx.self))
        .thenNoReply()
    }

  override def applyEvent(evt: CardEvent): CardState =
    evt match {
      case InitialisationStarted(cardId, ref) => Initialising(cardId, ref)
    }
}

case class Initialising(cardId: String, cardGroupRef: ActorRef[CardGroupBehavior.Command]) extends CardState {

  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[CardEvent, CardState] =
    cmd match {
      case SupplyCardLogicRef(cardBehaviorRef) => Effect
        .persist(Initialised(cardBehaviorRef))
        .thenReply(cardGroupRef)((_: CardState) => CardGroupBehavior.SupplyCardRef(cardId, ctx.self))
    }

  override def applyEvent(evt: CardEvent): CardState =
    evt match {
      case Initialised(cardBehaviorRef) => InDeck(cardBehaviorRef)
    }
}

case class InDeck(cardLogicRef: ActorRef[CardLogicCommand]) extends CardState {

  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[CardEvent, CardState] =
    cmd match {
      case AssignToHand(playerRef) => Effect
        .persist(AssignedToHand(playerRef))
        .thenNoReply()
      case AssignToTable(playerRef) => Effect
        .persist(AssignedToTable(playerRef))
        .thenNoReply()
    }

  override def applyEvent(evt: CardEvent): CardState =
    evt match {
      case AssignedToHand(playerRef) => InHand(playerRef, cardLogicRef)
      /*case AssignedToTable(playerRef) => OnTable(playerRef, cardLogicRef)*/
    }
}

case class InHand(
  playerRef: ActorRef[PlayerBehavior.Command],
  cardLogicRef: ActorRef[CardLogicCommand]
) extends CardState {
  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[CardEvent, CardState] =
    cmd match {
      case PlayCard(_) => Effect
        .persist(CardPlayed(playerRef))
        .thenRun((_: CardState) => cardLogicRef ! CardLogicPerformAction(playerRef, None, ctx.self))
        .thenNoReply()
      case FinishCard(replyTo) =>
        Effect
        .persist(CardFinished(playerRef))
        .thenReply(cardGroupRef)((_: CardState) => CardGroupBehavior.FinishCard("cardId"))
    }

  override def applyEvent(evt: CardEvent): CardState =
    evt match {
      case CardFinished(_) => InDeck(cardLogicRef)
    }
}

/*case class OnTable(
  playerRef: ActorRef[PlayerBehavior.Command],
  cardBehaviorRef: ActorRef[CardLogicCommand]
) extends CardState {
  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[CardEvent, CardState] =
    cmd match {
      case PlayCard(ref) => Effect
        .persist(CardPlayed(ref))
        .thenNoReply()
    }

  override def applyEvent(evt: CardEvent): CardState =
    evt match {
      case InitialisationStarted(ref) => WaitingForCard(playerGroupRef, CardRef)
    }
}*/

/**
 * This interface defines all the events that the CardAggregate supports.
 */
sealed trait CardEvent extends AggregateEvent[CardEvent] {
  def aggregateTag: AggregateEventTag[CardEvent] = CardEvent.Tag
}

object CardEvent {
  val Tag: AggregateEventTag[CardEvent] = AggregateEventTag[CardEvent]
}

case class InitialisationStarted(cardId: String, cardGroupRef: ActorRef[CardGroupBehavior.Command]) extends CardEvent

object InitialisationStarted {

  implicit val format: Format[InitialisationStarted] = Json.format
}

case class Initialised(cardBehaviorRef: ActorRef[CardLogicCommand]) extends CardEvent

object Initialised {

  implicit val format: Format[Initialised] = Json.format
}

case class AssignedToHand(playerRef: ActorRef[PlayerBehavior.Command]) extends CardEvent

object AssignedToHand {

  implicit val format: Format[AssignedToHand] = Json.format
}

case class AssignedToTable(playerRef: ActorRef[PlayerBehavior.Command]) extends CardEvent

object AssignedToTable {

  implicit val format: Format[AssignedToTable] = Json.format
}

case class CardPlayed(playerRef: ActorRef[PlayerBehavior.Command]) extends CardEvent

object CardPlayed {

  implicit val format: Format[CardPlayed] = Json.format
}

case class CardFinished(playerRef: ActorRef[PlayerBehavior.Command]) extends CardEvent

object CardFinished {

  implicit val format: Format[CardFinished] = Json.format
}
