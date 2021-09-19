package cz.ondramastik.bang.impl

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag}
import cz.ondramastik.bang.domain.{Card, CardSymbol, CardType, CardValue}

import java.util.UUID

object CardsDeckBehavior {

  def create(gameId: String): Behavior[Command] = Behaviors.setup { ctx =>
    val persistenceId: PersistenceId = PersistenceId("CardsDeck", gameId)

    create(persistenceId)(ctx)
      .withTagger(_ => Set(Event.Tag.toString))

  }

  private[impl] def create(
    persistenceId: PersistenceId
  )(implicit ctx: ActorContext[Command]) =
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        persistenceId = persistenceId,
        emptyState = State.initial,
        commandHandler = (cart, cmd) => cart.applyCommand(cmd),
        eventHandler = (cart, evt) => cart.applyEvent(evt)
      )

  trait Serializable

  sealed trait Command extends Serializable

  case class Initialise(gameManagerRef: ActorRef[GameManagerBehavior.Command])
      extends Command

  case class SupplyCardRef(cardId: String, ref: ActorRef[CardBehavior.Command])
      extends Command

  case class PlayCard(cardId: String) extends Command

  case class FinishCard(cardId: String) extends Command

  sealed abstract class State {
    def applyCommand(cmd: Command)(
      implicit ctx: ActorContext[Command]
    ): ReplyEffect[Event, State]

    def applyEvent(evt: Event): State
  }

  object State {

    def initial: State = new Empty

    val typeKey = EntityTypeKey[Command]("CardGroupState")
  }

  class Empty extends State {

    override def applyCommand(
      cmd: Command
    )(implicit ctx: ActorContext[Command]): ReplyEffect[Event, State] =
      cmd match {
        case Initialise(gameManagerRef) =>
          Effect
            .persist(InitialisationStarted(gameManagerRef))
            .thenRun(
              (_: State) =>
                initCards.map(
                  card =>
                    ctx.spawn(CardBehavior.create(), "Card".concat(UUID.randomUUID().toString))
                      ! CardBehavior.Initialise(card.cardType, ctx.self)
                )
            )
            .thenNoReply()
      }

    override def applyEvent(evt: Event): State =
      evt match {
        case InitialisationStarted(gameManagerRef) =>
          Initialising(gameManagerRef, Map.empty)
      }
  }

  case class Initialising(
    gameManagerRef: ActorRef[GameManagerBehavior.Command], // TODO: Like this?
    cardIdToActor: Map[String, ActorRef[CardBehavior.Command]]
  ) extends State {

    override def applyCommand(
      cmd: Command
    )(implicit ctx: ActorContext[Command]): ReplyEffect[Event, State] =
      cmd match {
        case SupplyCardRef(cardId, ref) =>
          Effect
            .persist(CardRefSupplied(cardId, ref))
            .thenRun(
              (_: State) =>
                if (initCards.size >= cardIdToActor.size - 1) { // TODO: Think about this redudant logic, can it be prevented?
                  gameManagerRef ! GameManagerBehavior.SupplyCardsDeckRef(ctx.self)
                }
            )
            .thenNoReply
      }

    override def applyEvent(evt: Event): State =
      evt match {
        case CardRefSupplied(cardId, ref) =>
          if (initCards.size >= cardIdToActor.size - 1)
            Initialised(gameManagerRef, cardIdToActor + (cardId -> ref))
          else
            Initialising(gameManagerRef, cardIdToActor + (cardId -> ref))
      }
  }

  case class Initialised(
    gameManagerRef: ActorRef[GameManagerBehavior.Command],
    cardIdToActor: Map[String, ActorRef[CardBehavior.Command]]
  ) extends State {

    override def applyCommand(
      cmd: Command
    )(implicit ctx: ActorContext[Command]): ReplyEffect[Event, State] =
      cmd match {
        case PlayCard(cardId) =>
          Effect.none
            .thenReply(cardIdToActor(cardId))(
              (_: State) => CardBehavior.PlayCard(ctx.self)
            )
        case FinishCard(cardId) =>
          Effect.none
            .thenReply(gameManagerRef)(
              (_: State) =>
                GameManagerBehavior
                  .CardFinished("Super, dokonceno, karta ".concat(cardId))
            )
      }

    override def applyEvent(evt: Event): State =
      throw new IllegalStateException("No events for state Initialised")
  }

  sealed trait Event extends AggregateEvent[Event] with Serializable {
    def aggregateTag: AggregateEventTag[Event] = Event.Tag
  }

  object Event {
    val Tag: AggregateEventTag[Event] =
      AggregateEventTag[Event]
  }

  case class InitialisationStarted(
    gameManagerRef: ActorRef[GameManagerBehavior.Command]
  ) extends Event

  case class CardRefSupplied(
    cardId: String,
    ref: ActorRef[CardBehavior.Command]
  ) extends Event

  private final def initCards = Seq( // TODO: Move somewhere else, consider json config
    Card(CardType.Bang, CardSymbol.Hearts, CardValue.A),
    Card(CardType.Bang, CardSymbol.Hearts, CardValue.Q),
    Card(CardType.Bang, CardSymbol.Hearts, CardValue.K),
    Card(CardType.Bang, CardSymbol.Tiles, CardValue.A),
    Card(CardType.Bang, CardSymbol.Tiles, CardValue.Two),
    Card(CardType.Bang, CardSymbol.Tiles, CardValue.Three),
    Card(CardType.Bang, CardSymbol.Tiles, CardValue.Four),
    Card(CardType.Bang, CardSymbol.Tiles, CardValue.Five),
    Card(CardType.Bang, CardSymbol.Tiles, CardValue.Six),
    Card(CardType.Bang, CardSymbol.Tiles, CardValue.Seven),
    Card(CardType.Bang, CardSymbol.Tiles, CardValue.Eight),
    Card(CardType.Bang, CardSymbol.Tiles, CardValue.Nine),
    Card(CardType.Bang, CardSymbol.Tiles, CardValue.Ten),
    Card(CardType.Bang, CardSymbol.Tiles, CardValue.J),
    Card(CardType.Bang, CardSymbol.Tiles, CardValue.Q),
    Card(CardType.Bang, CardSymbol.Tiles, CardValue.K),
    Card(CardType.Bang, CardSymbol.Cloves, CardValue.Two),
    Card(CardType.Bang, CardSymbol.Cloves, CardValue.Three),
    Card(CardType.Bang, CardSymbol.Cloves, CardValue.Four),
    Card(CardType.Bang, CardSymbol.Cloves, CardValue.Five),
    Card(CardType.Bang, CardSymbol.Cloves, CardValue.Six),
    Card(CardType.Bang, CardSymbol.Cloves, CardValue.Seven),
    Card(CardType.Bang, CardSymbol.Cloves, CardValue.Eight),
    Card(CardType.Bang, CardSymbol.Cloves, CardValue.Nine),
    Card(CardType.Bang, CardSymbol.Pikes, CardValue.A),
    Card(CardType.Birra, CardSymbol.Hearts, CardValue.Six),
    Card(CardType.Birra, CardSymbol.Hearts, CardValue.Seven),
    Card(CardType.Birra, CardSymbol.Hearts, CardValue.Eight),
    Card(CardType.Birra, CardSymbol.Hearts, CardValue.Nine),
    Card(CardType.Birra, CardSymbol.Hearts, CardValue.Ten),
    Card(CardType.Birra, CardSymbol.Hearts, CardValue.J),
    Card(CardType.Panico, CardSymbol.Hearts, CardValue.A),
    Card(CardType.Panico, CardSymbol.Hearts, CardValue.J),
    Card(CardType.Panico, CardSymbol.Hearts, CardValue.Q),
    Card(CardType.Panico, CardSymbol.Tiles, CardValue.Eight),
    Card(CardType.Indiani, CardSymbol.Tiles, CardValue.A),
    Card(CardType.Indiani, CardSymbol.Tiles, CardValue.K),
    Card(CardType.Duello, CardSymbol.Tiles, CardValue.Q),
    Card(CardType.Duello, CardSymbol.Cloves, CardValue.Eight),
    Card(CardType.Duello, CardSymbol.Pikes, CardValue.J),
    Card(CardType.CatBalou, CardSymbol.Hearts, CardValue.K),
    Card(CardType.CatBalou, CardSymbol.Tiles, CardValue.Nine),
    Card(CardType.CatBalou, CardSymbol.Tiles, CardValue.Ten),
    Card(CardType.CatBalou, CardSymbol.Tiles, CardValue.J),
    Card(CardType.Diligenza, CardSymbol.Pikes, CardValue.J),
    Card(CardType.Diligenza, CardSymbol.Pikes, CardValue.J),
    Card(CardType.WellsFargo, CardSymbol.Hearts, CardValue.Three),
    Card(CardType.Emporio, CardSymbol.Cloves, CardValue.Nine),
    Card(CardType.Emporio, CardSymbol.Pikes, CardValue.Q),
    Card(CardType.Gatling, CardSymbol.Hearts, CardValue.Ten),
    Card(CardType.Saloon, CardSymbol.Hearts, CardValue.Five),
    Card(CardType.Prigione, CardSymbol.Hearts, CardValue.Four),
    Card(CardType.Prigione, CardSymbol.Pikes, CardValue.Ten),
    Card(CardType.Prigione, CardSymbol.Pikes, CardValue.J),
    Card(CardType.Mustang, CardSymbol.Hearts, CardValue.Eight),
    Card(CardType.Mustang, CardSymbol.Hearts, CardValue.Nine),
    Card(CardType.Barile, CardSymbol.Pikes, CardValue.Q),
    Card(CardType.Barile, CardSymbol.Pikes, CardValue.K),
    Card(CardType.Dinamite, CardSymbol.Hearts, CardValue.Two),
    Card(CardType.Appaloosa, CardSymbol.Pikes, CardValue.A),
    Card(CardType.Volcanic, CardSymbol.Cloves, CardValue.Ten),
    Card(CardType.Volcanic, CardSymbol.Pikes, CardValue.Ten),
    Card(CardType.Schofield, CardSymbol.Cloves, CardValue.J),
    Card(CardType.Schofield, CardSymbol.Cloves, CardValue.Q),
    Card(CardType.Schofield, CardSymbol.Pikes, CardValue.K),
    Card(CardType.Remington, CardSymbol.Cloves, CardValue.K),
    Card(CardType.RevCarabine, CardSymbol.Cloves, CardValue.A),
    Card(CardType.Winchester, CardSymbol.Pikes, CardValue.Eight)
  )

}
