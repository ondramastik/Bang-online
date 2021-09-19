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
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import cz.ondramastik.bang.domain.CharacterType.CharacterType
import cz.ondramastik.bang.domain.Role.Role
import cz.ondramastik.bang.impl.characterlogicbehaviors.{
  Command => CharacterLogicCommand
}
import play.api.libs.json.{Format, Json}

object PlayerBehavior {

  def create(playerId: String): Behavior[Command] = Behaviors.setup { ctx =>
    val persistenceId: PersistenceId = PersistenceId("Player", playerId)

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
    playerId: String,
    replyTo: ActorRef[PlayerGroupBehavior.Command]
  ) extends Command

  case class SupplyRole(role: Role) extends Command

  case class SupplyCharacterType(characterType: CharacterType) extends Command

  case class SupplyCharacterLogicRef(
    characterLogicRef: ActorRef[CharacterLogicCommand]
  ) extends Command

  case class Draw(count: Int) extends Command

  sealed trait State {
    def applyCommand(cmd: Command)(
      implicit ctx: ActorContext[Command]
    ): ReplyEffect[Event, State]

    def applyEvent(evt: Event): State
  }

  object State {

    def initial: State = new Empty

    val typeKey = EntityTypeKey[Command]("PlayerState")
  }

  class Empty extends State {

    override def applyCommand(
      cmd: Command
    )(implicit ctx: ActorContext[Command]): ReplyEffect[Event, State] =
      cmd match {
        case Initialise(playerId, replyTo) =>
          Effect
            .persist(InitialisationStarted(replyTo))
            .thenReply(replyTo)(
              (_: State) =>
                PlayerGroupBehavior.SupplyPlayerRef(playerId, ctx.self)
            )
      }

    override def applyEvent(evt: Event): State =
      evt match {
        case InitialisationStarted(replyTo) => Initialising(replyTo, None, None)
      }
  }

  case class Initialising(
    replyTo: ActorRef[PlayerGroupBehavior.Command],
    role: Option[Role],
    characterLogicRef: Option[ActorRef[CharacterLogicCommand]]
  ) extends State {

    override def applyCommand(
      cmd: Command
    )(implicit ctx: ActorContext[Command]): ReplyEffect[Event, State] =
      cmd match {
        case SupplyRole(role) =>
          Effect
            .persist(RoleSupplied(role))
            .thenNoReply()
        case SupplyCharacterType(characterType) =>
          Effect.none
            .thenRun(
              (_: State) =>
                ctx.spawn(
                  CharacterLogicManagerBehavior.apply(),
                  "CharacterLogicManagerBehavior"
                )
                  ! CharacterLogicManagerBehavior
                    .RequestCharacterLogicRef(characterType, ctx.self)
            )
            .thenNoReply()
        case SupplyCharacterLogicRef(characterLogicRef) =>
          Effect
            .persist(CharacterLogicRefSupplied(characterLogicRef))
            .thenNoReply()
      }

    override def applyEvent(evt: Event): State = {
      this match {
        case Initialising(_, Some(_), None) =>
          evt match {
            case CharacterLogicRefSupplied(characterLogicRef) =>
              Initialised(this.role.get, characterLogicRef)
          }
        case Initialising(_, None, Some(_)) =>
          evt match {
            case RoleSupplied(role) =>
              Initialised(role, this.characterLogicRef.get)
          }
        case _ =>
          evt match {
            case RoleSupplied(role) =>
              Initialising(this.replyTo, Some(role), this.characterLogicRef)
            case CharacterLogicRefSupplied(characterLogicRef) =>
              Initialising(this.replyTo, this.role, Some(characterLogicRef))
          }
      }
    }
  }

  case class Initialised(
    role: Role,
    characterLogicRef: ActorRef[CharacterLogicCommand]
  ) extends State {

    override def applyCommand(
      cmd: Command
    )(implicit ctx: ActorContext[Command]): ReplyEffect[Event, State] =
      cmd match {
        case Initialise(_, ref) =>
          Effect
            .persist(InitialisationStarted(ref))
            .thenNoReply()
      }

    override def applyEvent(evt: Event): State =
      evt match {
        case InitialisationStarted(ref) =>
          Initialised(this.role, this.characterLogicRef)
      }
  }

  sealed trait Event extends AggregateEvent[Event] with Serializable {
    def aggregateTag: AggregateEventTag[Event] = Event.Tag
  }

  object Event {
    val Tag: AggregateEventTag[Event] = AggregateEventTag[Event]
  }

  case class InitialisationStarted(
    replyTo: ActorRef[PlayerGroupBehavior.Command]
  ) extends Event

  case class RoleSupplied(role: Role) extends Event

  case class CharacterTypeSupplied(characterType: CharacterType) extends Event

  case class CharacterLogicRefSupplied(
    characterLogicRef: ActorRef[CharacterLogicCommand]
  ) extends Event
}
