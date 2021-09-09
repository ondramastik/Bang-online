package cz.ondramastik.bang.impl

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag}
import cz.ondramastik.bang.impl.CardGroupBehavior._
import play.api.libs.json.{Format, Json}

object CardGroupBehavior {

  def create(gameId: String): Behavior[Command] =  Behaviors.setup { ctx =>
  val persistenceId: PersistenceId = PersistenceId("CardGroup", gameId)

    create(persistenceId)(ctx)
      .withTagger(_ => Set(CardGroupEvent.Tag))

  }

  private[impl] def create(persistenceId: PersistenceId)(implicit ctx: ActorContext[Command]) = EventSourcedBehavior
    .withEnforcedReplies[Command, CardGroupEvent, CardGroupState](
      persistenceId = persistenceId,
      emptyState = CardGroupState.initial,
      commandHandler = (cart, cmd) => cart.applyCommand(cmd),
      eventHandler = (cart, evt) => cart.applyEvent(evt)
    )

  trait CardGroupCommandSerializable

  sealed trait Command
    extends CardGroupCommandSerializable


  case class Initialise(gameManagerRef: ActorRef[GameManagerBehavior.Command], cardIds: Seq[String]) extends Command

  case class SupplyCardRef(cardId: String, ref: ActorRef[CardBehavior.Command]) extends Command

  case class PlayCard(cardId: String) extends Command

  case class FinishCard(cardId: String) extends Command

}

/**
 * The current state of the Aggregate.
 */
abstract class CardGroupState {
  def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[CardGroupEvent, CardGroupState]

  def applyEvent(evt: CardGroupEvent): CardGroupState
}

object CardGroupState {

  def initial: CardGroupState = new Empty

  val typeKey = EntityTypeKey[Command]("CardGroupState")

  implicit val format: Format[CardGroupState] = Json.format
}

class Empty extends CardGroupState {

  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[CardGroupEvent, CardGroupState] =
    cmd match {
      case Initialise(gameManagerRef, cardIds) => Effect
        .persist(InitialisationStarted(gameManagerRef, cardIds))
        .thenNoReply()
    }

  override def applyEvent(evt: CardGroupEvent): CardGroupState =
    evt match {
      case InitialisationStarted(gameManagerRef, cardIds) => Initialising(gameManagerRef, cardIds, Map.empty)
    }
}
case class Initialising(
  gameManagerRef: ActorRef[GameManagerBehavior.Command], // TODO: Like this?
  cardIds: Seq[String],
  cardIdToActor: Map[String, ActorRef[CardBehavior.Command]]
) extends CardGroupState {

  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[CardGroupEvent, CardGroupState] =
    cmd match {
      case SupplyCardRef(cardId, ref) => Effect
        .persist(CardRefSupplied(cardId, ref))
        .thenNoReply
    }

  override def applyEvent(evt: CardGroupEvent): CardGroupState =
    evt match {
      case CardRefSupplied(cardId, ref) => cardIds.length match {
        case cardIdToActor.size - 1 => Initialised(gameManagerRef, cardIdToActor + (cardId -> ref))
        case _ => Initialising(gameManagerRef, cardIds, cardIdToActor + (cardId -> ref))
      }
    }
}
case class Initialised(
  gameManagerRef: ActorRef[GameManagerBehavior.Command],
  cardIdToActor: Map[String, ActorRef[CardBehavior.Command]]
) extends CardGroupState {

  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[CardGroupEvent, CardGroupState] =
    cmd match {
      case PlayCard(cardId) => Effect.none
        .thenReply(cardIdToActor(cardId))((_: CardGroupState) => CardBehavior.PlayCard(ctx.self))
      case FinishCard(cardId) => Effect.none
        .thenReply(gameManagerRef)((_: CardGroupState) => GameManagerBehavior.CardFinished("Super, dokonceno, karta ".concat(cardId)))
    }

  override def applyEvent(evt: CardGroupEvent): CardGroupState = throw new IllegalStateException("No events for state Initialised")
}


sealed trait CardGroupEvent extends AggregateEvent[CardGroupEvent] {
  def aggregateTag: AggregateEventTag[CardGroupEvent] = CardGroupEvent.Tag
}

object CardGroupEvent {
  val Tag: AggregateEventTag[CardGroupEvent] = AggregateEventTag[CardGroupEvent]
}

case class InitialisationStarted(gameManagerRef: ActorRef[GameManagerBehavior.Command], cardIds: Seq[String]) extends CardGroupEvent

object InitialisationStarted {

  implicit val format: Format[InitialisationStarted] = Json.format
}

case class CardRefSupplied(cardId: String, ref: ActorRef[CardBehavior.Command]) extends CardGroupEvent

object CardRefSupplied {

  implicit val format: Format[CardRefSupplied] = Json.format
}
