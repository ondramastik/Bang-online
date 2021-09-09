package cz.ondramastik.bang.impl.cardlogicbehaviors

import akka.actor.typed.ActorRef
import cz.ondramastik.bang.impl.CardBehavior
import cz.ondramastik.bang.impl.PlayerBehavior


trait CardImplCommandSerializable

sealed trait Command
  extends CardImplCommandSerializable

case class PerformAction(
  initiator: ActorRef[PlayerBehavior.Command],
  target: Option[ActorRef[PlayerBehavior.Command]],
  replyTo: ActorRef[CardBehavior.Command]
) extends Command

case class PerformRespondAction(replyTo: ActorRef[CardBehavior.Command]) extends Command