package cz.ondramastik.bang.impl

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import cz.ondramastik.bang.domain.CharacterType
import cz.ondramastik.bang.domain.CharacterType.CharacterType
import cz.ondramastik.bang.impl.CharacterLogicManagerBehavior._
import cz.ondramastik.bang.impl.characterlogicbehaviors.{SuzyLafayetteBehavior, Command => CharacterLogicCommand}


object CharacterLogicManagerBehavior {

  def apply(): Behavior[Command] =
    Behaviors.setup(context => new CharacterLogicManagerBehavior(context))

  trait CharacterLogicCommandSerializable

  sealed trait Command
    extends CharacterLogicCommandSerializable

  case class RequestCharacterLogicRef(characterType: CharacterType, replyTo: ActorRef[PlayerBehavior.Command])
    extends Command
}

class CharacterLogicManagerBehavior(context: ActorContext[Command])
  extends AbstractBehavior[Command](context) {

  private def getCharacterLogicRef(characterType: CharacterType): ActorRef[CharacterLogicCommand] = characterType match {
    case CharacterType.SuzyLafayette => context.spawn(SuzyLafayetteBehavior.apply(), "SuzyLafayetteBehavior")
  }

  override def onMessage(msg: Command): Behavior[Command] =
    msg match {
      case RequestCharacterLogicRef(cardType, replyTo) =>
        replyTo ! PlayerBehavior.SupplyCharacterLogicRef(getCharacterLogicRef(cardType))
        this
    }

}