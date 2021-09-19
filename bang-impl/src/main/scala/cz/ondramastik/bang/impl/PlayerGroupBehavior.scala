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

object PlayerGroupBehavior {

  def create(gameId: String): Behavior[Command] = Behaviors.setup { ctx =>
    val persistenceId: PersistenceId = PersistenceId("PlayerGroup", gameId)

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

  case class Initialise(
    playerIds: Seq[String],
    replyTo: ActorRef[GameBehavior.Command]
  ) extends Command

  case class SupplyPlayerRef(
    playerId: String,
    ref: ActorRef[PlayerBehavior.Command]
  ) extends Command

  sealed trait State {
    def applyCommand(cmd: Command)(
      implicit ctx: ActorContext[Command]
    ): ReplyEffect[Event, State]

    def applyEvent(evt: Event): State
  }

  object State {

    def initial: State = new Empty

    val typeKey = EntityTypeKey[Command]("PlayerGroupState")
  }

  case class Empty() extends State {

    override def applyCommand(
      cmd: Command
    )(implicit ctx: ActorContext[Command]): ReplyEffect[Event, State] =
      cmd match {
        case Initialise(playerIds, replyTo) =>
          Effect
            .persist(InitialisationStarted(playerIds, replyTo))
            .thenRun(
              (_: State) =>
                playerIds.map(
                  playerId =>
                    ctx.spawn(PlayerBehavior.create(playerId), "Player")
                      ! PlayerBehavior.Initialise(playerId, ctx.self)
                )
            )
            .thenNoReply()
      }

    override def applyEvent(evt: Event): State =
      evt match {
        case InitialisationStarted(playerIds, replyTo) =>
          Initialising(replyTo, playerIds, Map.empty)
      }
  }

  case class Initialising(
                           replyTo: ActorRef[GameBehavior.Command],
                           playerIds: Seq[String],
                           playerIdToActor: Map[String, ActorRef[PlayerBehavior.Command]]
  ) extends State {

    override def applyCommand(
      cmd: Command
    )(implicit ctx: ActorContext[Command]): ReplyEffect[Event, State] =
      cmd match {
        case SupplyPlayerRef(playerId, ref) =>
          Effect
            .persist(PlayerRefSupplied(playerId, ref))
            .thenRun(
              (_: State) =>
                if (playerIds.length.equals(playerIdToActor.size + 1)) { // TODO: Think about this redudant logic, can it be prevented?
                  replyTo ! GameBehavior.SetPlayerGroupInitialized(ctx.self)
                }
            )
            .thenNoReply
      }

    override def applyEvent(evt: Event): State =
      evt match {
        case PlayerRefSupplied(playerId, ref) =>
          if (playerIds.length.equals(playerIdToActor.size - 1))
            Initialised(playerIdToActor + (playerId -> ref))
          else
            Initialising(
              replyTo,
              playerIds,
              playerIdToActor + (playerId -> ref)
            )
      }
  }

  case class Initialised(
    playerIdToActor: Map[String, ActorRef[PlayerBehavior.Command]]
  ) extends State {

    override def applyCommand(
      cmd: Command
    )(implicit ctx: ActorContext[Command]): ReplyEffect[Event, State] =
      cmd match {
        case _ => Effect.none.thenNoReply
      }

    override def applyEvent(evt: Event): State =
      evt match {
        case _ => Initialised(playerIdToActor)
      }
  }

  sealed trait Event extends AggregateEvent[Event] with Serializable {
    def aggregateTag: AggregateEventTag[Event] = Event.Tag
  }

  object Event {
    val Tag: AggregateEventTag[Event] =
      AggregateEventTag[Event]
  }

  case class InitialisationStarted(
    playerIds: Seq[String],
    replyTo: ActorRef[GameBehavior.Command]
  ) extends Event

  case class PlayerRefSupplied(
    playerId: String,
    ref: ActorRef[PlayerBehavior.Command]
  ) extends Event

}
