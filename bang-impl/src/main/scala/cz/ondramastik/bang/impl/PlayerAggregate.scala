package cz.ondramastik.bang.impl

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag}
import cz.ondramastik.bang.impl.characterlogicbehaviors.{Command => CharacterLogicCommand, PerformAction => CharacterLogicPerformAction}
import cz.ondramastik.bang.impl.PlayerBehavior._
import cz.ondramastik.bang.domain.CharacterType.CharacterType
import cz.ondramastik.bang.domain.Role.Role
import play.api.libs.json.{Format, Json}


object PlayerBehavior {

  def create(playerId: String): Behavior[Command] = Behaviors.setup { ctx =>
  val persistenceId: PersistenceId = PersistenceId("Player", playerId)

    create(persistenceId)(ctx)
      .withTagger(_ => Set(PlayerEvent.Tag))

  }

  private[impl] def create(persistenceId: PersistenceId)(implicit ctx: ActorContext[Command]) = EventSourcedBehavior
    .withEnforcedReplies[Command, PlayerEvent, PlayerState](
      persistenceId = persistenceId,
      emptyState = PlayerState.initial,
      commandHandler = (cart, cmd) => cart.applyCommand(cmd),
      eventHandler = (cart, evt) => cart.applyEvent(evt)
    )

  trait PlayerCommandSerializable

  sealed trait Command
    extends PlayerCommandSerializable

  case class Initialise(playerId: String, replyTo: ActorRef[PlayerGroupBehavior.Command])
    extends Command

  case class SupplyRole(role: Role)
    extends Command

  case class SupplyCharacterType(characterType: CharacterType)
    extends Command

  case class SupplyCharacterLogicRef(characterLogicRef: ActorRef[CharacterLogicCommand])
    extends Command

  case class Draw(count: Int)
    extends Command
}

abstract class PlayerState {
  def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[PlayerEvent, PlayerState]

  def applyEvent(evt: PlayerEvent): PlayerState
}

object PlayerState {

  def initial: PlayerState = new Empty

  val typeKey = EntityTypeKey[Command]("PlayerState")

  implicit val format: Format[PlayerState] = Json.format
}

class Empty extends PlayerState {

  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[PlayerEvent, PlayerState] =
    cmd match {
      case Initialise(playerId, replyTo) => Effect
        .persist(InitialisationStarted(replyTo))
        .thenReply(replyTo)((_: PlayerState) => PlayerGroupBehavior.SupplyPlayerRef(playerId, ctx.self))
    }

  override def applyEvent(evt: PlayerEvent): PlayerState =
    evt match {
      case InitialisationStarted(replyTo) => Initialising(replyTo, None, None)
    }
}

case class Initialising(
  replyTo: ActorRef[PlayerGroupBehavior.Command],
  role: Option[Role],
  characterLogicRef: Option[ActorRef[CharacterLogicCommand]]
) extends PlayerState {

  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[PlayerEvent, PlayerState] =
    cmd match {
      case SupplyRole(role) => Effect
        .persist(RoleSupplied(role))
        .thenNoReply()
      case SupplyCharacterType(characterType) => Effect
        .none
        .thenRun((_: PlayerState) => ctx.spawn(CharacterLogicManagerBehavior.apply(), "CharacterLogicManagerBehavior")
          ! CharacterLogicManagerBehavior.RequestCharacterLogicRef(characterType, ctx.self))
        .thenNoReply()
      case SupplyCharacterLogicRef(characterLogicRef) => Effect
        .persist(CharacterLogicRefSupplied(characterLogicRef))
        .thenNoReply()
    }

  override def applyEvent(evt: PlayerEvent): PlayerState = {
    this match {
      case Initialising(_, Some(_), None) => evt match {
        case CharacterLogicRefSupplied(characterLogicRef) => Initialised(this.role.get, characterLogicRef)
      }
      case Initialising(_, None, Some(_)) => evt match {
        case RoleSupplied(role) => Initialised(role, this.characterLogicRef.get)
      }
      case _ => evt match {
        case RoleSupplied(role) => Initialising(this.replyTo, Some(role), this.characterLogicRef)
        case CharacterLogicRefSupplied(characterLogicRef) => Initialising(this.replyTo, this.role, Some(characterLogicRef))
      }
    }
  }
}

case class Initialised(
  role: Role,
  characterLogicRef: ActorRef[CharacterLogicCommand]
) extends PlayerState {

  override def applyCommand(cmd: Command)(implicit ctx: ActorContext[Command]): ReplyEffect[PlayerEvent, PlayerState] =
    cmd match {
      case Initialise(_, ref) => Effect
        .persist(InitialisationStarted(ref))
        .thenNoReply()
    }

  override def applyEvent(evt: PlayerEvent): PlayerState =
    evt match {
      case InitialisationStarted(ref) => Initialised(this.role, this.characterLogicRef)
    }
}

sealed trait PlayerEvent extends AggregateEvent[PlayerEvent] {
  def aggregateTag: AggregateEventTag[PlayerEvent] = PlayerEvent.Tag
}

object PlayerEvent {
  val Tag: AggregateEventTag[PlayerEvent] = AggregateEventTag[PlayerEvent]
}

case class InitialisationStarted(replyTo: ActorRef[PlayerGroupBehavior.Command]) extends PlayerEvent

object InitialisationStarted {

  implicit val format: Format[InitialisationStarted] = Json.format
}

case class RoleSupplied(role: Role) extends PlayerEvent

object RoleSupplied {

  implicit val format: Format[RoleSupplied] = Json.format
}

case class CharacterTypeSupplied(characterType: CharacterType) extends PlayerEvent

object CharacterTypeSupplied {

  implicit val format: Format[CharacterTypeSupplied] = Json.format
}

case class CharacterLogicRefSupplied(characterLogicRef: ActorRef[CharacterLogicCommand]) extends PlayerEvent

object CharacterTypeSupplied {

  implicit val format: Format[CharacterTypeSupplied] = Json.format
}