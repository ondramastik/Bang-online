package cz.ondramastik.bang.impl

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{
  Effect,
  EventSourcedBehavior,
  ReplyEffect
}
import com.lightbend.lagom.scaladsl.persistence.{
  AggregateEvent,
  AggregateEventTag
}
import cz.ondramastik.bang.domain.CardType
import cz.ondramastik.bang.impl.cardlogicbehaviors.{
  Command => CardLogicCommand,
  PerformAction => CardLogicPerformAction
}

import java.util.UUID

object CardBehavior {

  def create(): Behavior[Command] =
    Behaviors.setup { ctx =>
      val id = UUID.randomUUID().toString
      val persistenceId: PersistenceId =
        PersistenceId("Card", id)

      create(persistenceId, id)(ctx)
        .withTagger(_ => Set(Event.Tag.toString))

    }

  private[impl] def create(
    persistenceId: PersistenceId,
    cardId: String
  )(implicit ctx: ActorContext[Command]) =
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, CardState](
        persistenceId = persistenceId,
        emptyState = CardState.initial(cardId),
        commandHandler = (cart, cmd) => cart.applyCommand(cmd),
        eventHandler = (cart, evt) => cart.applyEvent(evt)
      )

  trait Serializable

  sealed trait Command extends Serializable

  case class Initialise(
    cardType: CardType.Value,
    cardGroupRef: ActorRef[CardsDeckBehavior.Command]
  ) extends Command

  case class AssignToHand(playerRef: ActorRef[PlayerBehavior.Command])
      extends Command

  case class SupplyCardLogicRef(cardLogicRef: ActorRef[CardLogicCommand])
      extends Command

  case class AssignToTable(playerRef: ActorRef[PlayerBehavior.Command])
      extends Command

  case class PlayCard(replyTo: ActorRef[CardsDeckBehavior.Command])
      extends Command

  case class FinishCard(replyTo: String) extends Command

  /**
    * The current state of the Aggregate.
    */
  sealed trait CardState {
    def applyCommand(cmd: Command)(
      implicit ctx: ActorContext[Command]
    ): ReplyEffect[Event, CardState]

    def applyEvent(evt: Event): CardState
  }

  object CardState {

    def initial(cardId: String): CardState = new Empty(cardId)

    val typeKey = EntityTypeKey[Command]("CardState")
  }

  class Empty(cardId: String) extends CardState {

    override def applyCommand(
      cmd: Command
    )(implicit ctx: ActorContext[Command]): ReplyEffect[Event, CardState] =
      cmd match {
        case Initialise(cardType, ref) =>
          Effect
            .persist(InitialisationStarted(cardId, ref))
            .thenRun(
              (_: CardState) =>
                ctx.spawn(
                  CardLogicManagerBehavior.apply(),
                  "CardLogicManagerBehavior"
                ) ! CardLogicManagerBehavior
                  .RequestCardLogicRef(cardType, ctx.self)
            )
            .thenNoReply()
      }

    override def applyEvent(evt: Event): CardState =
      evt match {
        case InitialisationStarted(cardId, ref) => Initialising(cardId, ref)
      }
  }

  case class Initialising(
    cardId: String,
    cardGroupRef: ActorRef[CardsDeckBehavior.Command]
  ) extends CardState {

    override def applyCommand(
      cmd: Command
    )(implicit ctx: ActorContext[Command]): ReplyEffect[Event, CardState] =
      cmd match {
        case SupplyCardLogicRef(cardBehaviorRef) =>
          Effect
            .persist(Initialised(cardBehaviorRef))
            .thenReply(cardGroupRef)(
              (_: CardState) =>
                CardsDeckBehavior.SupplyCardRef(cardId, ctx.self)
            )
      }

    override def applyEvent(evt: Event): CardState =
      evt match {
        case Initialised(cardBehaviorRef) => InDeck(cardBehaviorRef)
      }
  }

  case class InDeck(cardLogicRef: ActorRef[CardLogicCommand])
      extends CardState {

    override def applyCommand(
      cmd: Command
    )(implicit ctx: ActorContext[Command]): ReplyEffect[Event, CardState] =
      cmd match {
        case AssignToHand(playerRef) =>
          Effect
            .persist(AssignedToHand(playerRef))
            .thenNoReply()
        case AssignToTable(playerRef) =>
          Effect
            .persist(AssignedToTable(playerRef))
            .thenNoReply()
      }

    override def applyEvent(evt: Event): CardState =
      evt match {
        case AssignedToHand(playerRef) => InHand(playerRef, cardLogicRef)
        /*case AssignedToTable(playerRef) => OnTable(playerRef, cardLogicRef)*/
      }
  }

  case class InHand(
    playerRef: ActorRef[PlayerBehavior.Command],
    cardLogicRef: ActorRef[CardLogicCommand]
  ) extends CardState {
    override def applyCommand(
      cmd: Command
    )(implicit ctx: ActorContext[Command]): ReplyEffect[Event, CardState] =
      cmd match {
        case PlayCard(_) =>
          Effect
            .persist(CardPlayed(playerRef))
            .thenRun(
              (_: CardState) =>
                cardLogicRef ! CardLogicPerformAction(playerRef, None, ctx.self)
            )
            .thenNoReply()
        case FinishCard(_) =>
          Effect
            .persist(CardFinished(playerRef))
            .thenNoReply() // TODO. Reply to card group
      }

    override def applyEvent(evt: Event): CardState =
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

  sealed trait Event extends AggregateEvent[Event] with Serializable {
    def aggregateTag: AggregateEventTag[Event] = Event.Tag
  }

  object Event {
    val Tag: AggregateEventTag[Event] = AggregateEventTag[Event]
  }

  case class InitialisationStarted(
    cardId: String,
    cardGroupRef: ActorRef[CardsDeckBehavior.Command]
  ) extends Event

  case class Initialised(cardBehaviorRef: ActorRef[CardLogicCommand])
      extends Event

  case class AssignedToHand(playerRef: ActorRef[PlayerBehavior.Command])
      extends Event

  case class AssignedToTable(playerRef: ActorRef[PlayerBehavior.Command])
      extends Event

  case class CardPlayed(playerRef: ActorRef[PlayerBehavior.Command])
      extends Event

  case class CardFinished(playerRef: ActorRef[PlayerBehavior.Command])
      extends Event

}
