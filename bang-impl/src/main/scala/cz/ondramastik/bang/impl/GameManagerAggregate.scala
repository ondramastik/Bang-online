package cz.ondramastik.bang.impl

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl._
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, AkkaTaggerAdapter}
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import cz.ondramastik.bang.impl.GameManagerBehavior._
import play.api.libs.json.{Format, Json}

import java.time.Duration
import java.util.UUID
import scala.collection.immutable.Seq


object GameManagerBehavior {

  implicit val timeout: Timeout = Timeout.create(Duration.ofSeconds(20))

 
  def create(entityContext: EntityContext[Command]): Behavior[Command] = Behaviors.setup { implicit ctx: ActorContext[Command] =>
    create(PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))
      .withTagger(
        AkkaTaggerAdapter.fromLagom(entityContext, GameManagerEvent.Tag)
      )

  }

  private[impl] def create(persistenceId: PersistenceId)(implicit ctx: ActorContext[Command]) =
    EventSourcedBehavior
      .withEnforcedReplies[Command, GameManagerEvent, GameManagerState](
        persistenceId = persistenceId,
        emptyState = GameManagerState.initial,
        commandHandler = (state, cmd) => state.applyCommand(cmd),
        eventHandler = (state, evt) => state.applyEvent(evt)
      )


  trait GameManagerCommandSerializable

  sealed trait Command
    extends GameManagerCommandSerializable

  case class StartGame(players: Seq[String], replyTo: ActorRef[StatusReply[String]])
    extends Command

  case class CardFinished(message: String)
    extends Command

  case class PlayCard(cardId: String, replyTo: ActorRef[StatusReply[String]])
    extends Command

  case class SupplyPlayerGroupRef(ref: ActorRef[PlayerGroupBehavior.Command])
    extends Command
}

abstract class GameManagerState {
  def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[GameManagerEvent, GameManagerState]

  def applyEvent(evt: GameManagerEvent): GameManagerState
}

object GameManagerState {

  def initial: GameManagerState = new Empty

  val typeKey = EntityTypeKey[Command]("GameManagerAggregate")

  implicit val format: Format[GameManagerState] = Json.format
}

class Empty extends GameManagerState {

  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[GameManagerEvent, GameManagerState] =
    cmd match {
      case x: StartGame => Effect
        .persist(StartGameReceived(x.replyTo))
        .thenRun((_: GameManagerState) => ctx.spawn(PlayerManagerBehavior.apply(), "PlayerManagerBehavior")
          ! PlayerManagerBehavior.RequestPlayerGroupRef(UUID.randomUUID().toString, ctx.self))
        .thenNoReply()
    }

  override def applyEvent(evt: GameManagerEvent): GameManagerState =
    evt match {
      case StartGameReceived(originRef) => Starting(originRef, None, None)
    }
}

case class Starting(
  replyTo: ActorRef[StatusReply[String]],
  playerGroupRef: Option[ActorRef[PlayerGroupBehavior.Command]],
  cardGroupRef: Option[ActorRef[CardGroupBehavior.Command]]
) extends GameManagerState {

  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[GameManagerEvent, GameManagerState] =
    cmd match {
      case SupplyPlayerGroupRef(ref) => Effect
        .persist(PlayerGroupRefReceived(ref))
        .thenNoReply()
    }

  override def applyEvent(evt: GameManagerEvent): GameManagerState = {
    this match {
      case Starting(_, None, None) =>
        evt match {
          case PlayerGroupRefReceived(ref) => Starting(this.replyTo, Some(ref), this.cardGroupRef)
          case CardGroupRefReceived(ref) => Starting(this.replyTo, this.playerGroupRef, Some(ref))
        }
      case Starting(_, None, Some(_)) =>
        evt match {
          case PlayerGroupRefReceived(ref) => Ready(ref, this.cardGroupRef.get)
        }
      case Starting(_, Some(_), None) =>
        evt match {
          case CardGroupRefReceived(ref) => Ready(this.playerGroupRef.get, ref)
        }
    }
  }
}

case class Ready(
  playerGroupRef: ActorRef[PlayerGroupBehavior.Command],
  cardGroupRef: ActorRef[CardGroupBehavior.Command]
) extends GameManagerState {
  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[GameManagerEvent, GameManagerState] =
    cmd match {
      case PlayCard(cardId, replyTo) => Effect
        .persist(CardPlayed(cardId, replyTo))
        .thenRun((_: GameManagerState) => cardGroupRef ! CardGroupBehavior.PlayCard(cardId, ctx.self))
        .thenNoReply()
    }

  override def applyEvent(evt: GameManagerEvent): GameManagerState =
    evt match {
      case CardPlayed(_, replyTo) => CardInProgress(replyTo, playerGroupRef, cardGroupRef)
    }
}

case class CardInProgress(
  replyTo: ActorRef[StatusReply[String]],
  playerGroupRef: ActorRef[PlayerGroupBehavior.Command],
  cardGroupRef: ActorRef[CardGroupBehavior.Command]
) extends GameManagerState {

  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[GameManagerEvent, GameManagerState] =
    cmd match {
      case CardFinished(message) => Effect
        .persist(CardActionFinished(message))
        .thenReply(replyTo) { _ =>
          StatusReply.success("Card has finished")
        }
    }

  override def applyEvent(evt: GameManagerEvent): GameManagerState =
    evt match {
      case CardActionFinished(_) => Ready(playerGroupRef, cardGroupRef)
    }
}

sealed trait GameManagerEvent extends AggregateEvent[GameManagerEvent] {
  def aggregateTag: AggregateEventTag[GameManagerEvent] = GameManagerEvent.Tag
}

object GameManagerEvent {
  val Tag: AggregateEventTag[GameManagerEvent] = AggregateEventTag[GameManagerEvent]
}

case class CardActionFinished(message: String) extends GameManagerEvent

object CardActionFinished {
  
  implicit val format: Format[CardActionFinished] = Json.format
}

case class PlayerGroupRefReceived(ref: ActorRef[PlayerGroupBehavior.Command]) extends GameManagerEvent

object PlayerGroupRefReceived {
  implicit val format: Format[PlayerGroupRefReceived] = Json.format
}

case class CardGroupRefReceived(ref: ActorRef[CardGroupBehavior.Command]) extends GameManagerEvent

object CardGroupRefReceived {
  implicit val format: Format[PlayerGroupRefReceived] = Json.format
}

case class CardPlayed(cardId: String, replyTo: ActorRef[StatusReply[String]]) extends GameManagerEvent

object CardPlayed {
  implicit val format: Format[CardPlayed] = Json.format
}

case class StartGameReceived(originRef: ActorRef[StatusReply[String]]) extends GameManagerEvent

object StartGameReceived {
  implicit val format: Format[StartGameReceived] = Json.format
}


object GameManagerSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    // state and events can use play-json, but commands should use jackson because of ActorRef[T] (see application.conf)
    JsonSerializer[CardActionFinished],
    JsonSerializer[GameManagerState]
  )
}
