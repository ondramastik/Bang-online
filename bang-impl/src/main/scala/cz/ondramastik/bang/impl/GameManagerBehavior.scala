package cz.ondramastik.bang.impl

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl._
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{
  Effect,
  EventSourcedBehavior,
  ReplyEffect
}
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.persistence.{
  AggregateEvent,
  AggregateEventTag,
  AkkaTaggerAdapter
}

import java.time.Duration
import java.util.UUID
import scala.collection.immutable.Seq

object GameManagerBehavior {

  implicit val timeout: Timeout = Timeout.create(Duration.ofSeconds(20))

  def create(entityContext: EntityContext[Command]): Behavior[Command] =
    Behaviors.setup { implicit ctx: ActorContext[Command] =>
      create(
        PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)
      ).withTagger(AkkaTaggerAdapter.fromLagom(entityContext, Event.Tag))

    }

  private[impl] def create(
    persistenceId: PersistenceId
  )(implicit ctx: ActorContext[Command]) =
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        persistenceId = persistenceId,
        emptyState = State.initial,
        commandHandler = (state, cmd) => state.applyCommand(cmd),
        eventHandler = (state, evt) => state.applyEvent(evt)
      )

  trait Serializable

  sealed trait Command extends Serializable

  case class StartGame(
    players: Seq[String],
    replyTo: ActorRef[StatusReply[String]]
  ) extends Command

  case class CardFinished(message: String) extends Command

  case class PlayCard(cardId: String, replyTo: ActorRef[StatusReply[String]])
      extends Command

  case class SupplyPlayerGroupRef(ref: ActorRef[PlayerGroupBehavior.Command])
      extends Command

  case class SupplyCardsDeckRef(ref: ActorRef[CardsDeckBehavior.Command])
    extends Command

  sealed trait State {
    def applyCommand(cmd: Command)(
      implicit ctx: ActorContext[Command]
    ): ReplyEffect[Event, State]

    def applyEvent(evt: Event): State
  }

  object State {

    def initial: State = new Empty

    val typeKey = EntityTypeKey[Command]("GameManagerAggregate")
  }

  class Empty extends State {

    override def applyCommand(
      cmd: Command
    )(implicit ctx: ActorContext[Command]): ReplyEffect[Event, State] =
      cmd match {
        case x: StartGame =>
          Effect
            .persist(StartGameReceived(x.replyTo))
            .thenRun(
              (_: State) =>
                ctx
                  .spawn(PlayerManagerBehavior.apply(), "PlayerManagerBehavior")
                  ! PlayerManagerBehavior
                    .RequestPlayerGroupRef(UUID.randomUUID().toString, ctx.self)
            )
            .thenNoReply()
      }

    override def applyEvent(evt: Event): State =
      evt match {
        case StartGameReceived(originRef) => Starting(originRef, None, None)
      }
  }

  case class Starting(
    replyTo: ActorRef[StatusReply[String]],
    playerGroupRef: Option[ActorRef[PlayerGroupBehavior.Command]],
    cardGroupRef: Option[ActorRef[CardsDeckBehavior.Command]]
  ) extends State {

    override def applyCommand(
      cmd: Command
    )(implicit ctx: ActorContext[Command]): ReplyEffect[Event, State] =
      cmd match {
        case SupplyPlayerGroupRef(ref) =>
          Effect
            .persist(PlayerGroupRefReceived(ref))
            .thenNoReply()
        case SupplyCardsDeckRef(ref) =>
          Effect
            .persist(CardGroupRefReceived(ref))
            .thenNoReply()
      }

    override def applyEvent(evt: Event): State = {
      this match {
        case Starting(_, None, None) =>
          evt match {
            case PlayerGroupRefReceived(ref) =>
              Starting(this.replyTo, Some(ref), this.cardGroupRef)
            case CardGroupRefReceived(ref) =>
              Starting(this.replyTo, this.playerGroupRef, Some(ref))
          }
        case Starting(_, None, Some(_)) =>
          evt match {
            case PlayerGroupRefReceived(ref) =>
              Ready(ref, this.cardGroupRef.get)
          }
        case Starting(_, Some(_), None) =>
          evt match {
            case CardGroupRefReceived(ref) =>
              Ready(this.playerGroupRef.get, ref)
          }
      }
    }
  }

  case class Ready(
    playerGroupRef: ActorRef[PlayerGroupBehavior.Command],
    cardGroupRef: ActorRef[CardsDeckBehavior.Command]
  ) extends State {
    override def applyCommand(
      cmd: Command
    )(implicit ctx: ActorContext[Command]): ReplyEffect[Event, State] =
      cmd match {
        case PlayCard(cardId, replyTo) =>
          Effect
            .persist(CardPlayed(cardId, replyTo))
            .thenRun(
              (_: State) => cardGroupRef ! CardsDeckBehavior.PlayCard(cardId)
            )
            .thenNoReply()
      }

    override def applyEvent(evt: Event): State =
      evt match {
        case CardPlayed(_, replyTo) =>
          CardInProgress(replyTo, playerGroupRef, cardGroupRef)
      }
  }

  case class CardInProgress(
    replyTo: ActorRef[StatusReply[String]],
    playerGroupRef: ActorRef[PlayerGroupBehavior.Command],
    cardGroupRef: ActorRef[CardsDeckBehavior.Command]
  ) extends State {

    override def applyCommand(
      cmd: Command
    )(implicit ctx: ActorContext[Command]): ReplyEffect[Event, State] =
      cmd match {
        case CardFinished(message) =>
          Effect
            .persist(CardActionFinished(message))
            .thenReply(replyTo) { _ =>
              StatusReply.success("Card has finished")
            }
      }

    override def applyEvent(evt: Event): State =
      evt match {
        case CardActionFinished(_) => Ready(playerGroupRef, cardGroupRef)
      }
  }

  sealed trait Event extends AggregateEvent[Event] with Serializable {
    def aggregateTag: AggregateEventTag[Event] = Event.Tag
  }

  object Event {
    val Tag: AggregateEventTag[Event] =
      AggregateEventTag[Event]
  }

  case class CardActionFinished(message: String) extends Event

  case class PlayerGroupRefReceived(ref: ActorRef[PlayerGroupBehavior.Command])
      extends Event

  case class CardGroupRefReceived(ref: ActorRef[CardsDeckBehavior.Command])
      extends Event

  case class CardPlayed(cardId: String, replyTo: ActorRef[StatusReply[String]])
      extends Event

  case class StartGameReceived(originRef: ActorRef[StatusReply[String]])
      extends Event

}
