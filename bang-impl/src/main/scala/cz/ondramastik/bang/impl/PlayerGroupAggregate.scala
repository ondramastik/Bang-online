package cz.ondramastik.bang.impl

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag}
import cz.ondramastik.bang.impl.PlayerGroupBehavior._
import play.api.libs.json.{Format, Json}


object PlayerGroupBehavior {

  def create(gameId: String): Behavior[Command] =  Behaviors.setup { ctx =>
  val persistenceId: PersistenceId = PersistenceId("PlayerGroup", gameId)

    create(persistenceId)(ctx)
      .withTagger(_ => Set(PlayerGroupEvent.Tag))

  }

  private[impl] def create(persistenceId: PersistenceId)(implicit ctx: ActorContext[Command]) = EventSourcedBehavior
    .withEnforcedReplies[Command, PlayerGroupEvent, PlayerGroupState](
      persistenceId = persistenceId,
      emptyState = PlayerGroupState.initial,
      commandHandler = (cart, cmd) => cart.applyCommand(cmd),
      eventHandler = (cart, evt) => cart.applyEvent(evt)
    )

  trait PlayerGroupCommandSerializable

  sealed trait Command
    extends PlayerGroupCommandSerializable

  case class Initialise(playerIds: Seq[String], replyTo: ActorRef[GameManagerBehavior.Command])
    extends Command

  case class SupplyPlayerRef(playerId: String, ref: ActorRef[PlayerBehavior.Command])
    extends Command

}


abstract class PlayerGroupState {
  def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[PlayerGroupEvent, PlayerGroupState]

  def applyEvent(evt: PlayerGroupEvent): PlayerGroupState
}

object PlayerGroupState {

  def initial: PlayerGroupState = new Empty

  val typeKey = EntityTypeKey[Command]("PlayerGroupState")

  implicit val format: Format[PlayerGroupState] = Json.format
}

class Empty extends PlayerGroupState {

  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[PlayerGroupEvent, PlayerGroupState] =
    cmd match {
      case Initialise(playerIds, replyTo) => Effect
        .persist(InitialisationStarted(playerIds, replyTo))
        .thenRun((_: PlayerGroupState) => playerIds.map(
          playerId => ctx.spawn(PlayerBehavior.create(playerId), "Player")
            ! PlayerBehavior.Initialise(playerId, ctx.self)))
        .thenNoReply()
    }

  override def applyEvent(evt: PlayerGroupEvent): PlayerGroupState =
    evt match {
      case InitialisationStarted(playerIds, replyTo) => Initialising(replyTo, playerIds, Map.empty)
    }
}

case class Initialising(
  replyTo: ActorRef[GameManagerBehavior.Command],
  playerIds: Seq[String],
  playerIdToActor: Map[String, ActorRef[PlayerBehavior.Command]]
) extends PlayerGroupState {

  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[PlayerGroupEvent, PlayerGroupState] =
    cmd match {
      case SupplyPlayerRef(playerId, ref) => Effect
        .persist(PlayerRefSupplied(playerId, ref))
        .thenRun((_: PlayerGroupState) => if(playerIds.length.equals(playerIdToActor.size + 1)) { // TODO: Think about this redudant logic, can it be prevented?
          replyTo ! GameManagerBehavior.SupplyPlayerGroupRef(ctx.self)
        })
        .thenNoReply
    }

  override def applyEvent(evt: PlayerGroupEvent): PlayerGroupState =
    evt match {
      case PlayerRefSupplied(playerId, ref) => playerIds.length match {
        case playerIdToActor.size - 1 => Initialised(playerIdToActor + (playerId -> ref))
        case _ => Initialising(replyTo, playerIds, playerIdToActor + (playerId -> ref))
      }
    }
}

case class Initialised(
  playerIdToActor: Map[String, ActorRef[PlayerBehavior.Command]]
) extends PlayerGroupState {

  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[PlayerGroupEvent, PlayerGroupState] =
    cmd match {
      case _ => Effect
        .none
        .thenNoReply
    }

  override def applyEvent(evt: PlayerGroupEvent): PlayerGroupState =
    evt match {
      case _ => Initialised(playerIdToActor)
    }
}


sealed trait PlayerGroupEvent extends AggregateEvent[PlayerGroupEvent] {
  def aggregateTag: AggregateEventTag[PlayerGroupEvent] = PlayerGroupEvent.Tag
}

object PlayerGroupEvent {
  val Tag: AggregateEventTag[PlayerGroupEvent] = AggregateEventTag[PlayerGroupEvent]
}

case class InitialisationStarted(playerIds: Seq[String], replyTo: ActorRef[GameManagerBehavior.Command]) extends PlayerGroupEvent

object InitialisationStarted {

  implicit val format: Format[InitialisationStarted] = Json.format
}

case class PlayerRefSupplied(playerId: String, ref: ActorRef[PlayerBehavior.Command]) extends PlayerGroupEvent

object PlayerRefSupplied {

  implicit val format: Format[PlayerRefSupplied] = Json.format
}