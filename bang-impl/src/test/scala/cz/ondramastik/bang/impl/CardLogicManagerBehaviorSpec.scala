package cz.ondramastik.bang.impl

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import cz.ondramastik.bang.domain.CardType
import cz.ondramastik.bang.impl.cardlogicbehaviors.WellsFargoBehavior
import org.scalatest.{Matchers, WordSpecLike}

import java.util.UUID

class CardLogicManagerBehaviorSpec
    extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID
      .randomUUID()
      .toString}"
    """)
    with WordSpecLike
    with Matchers {

  "Card logic manager behavior" should {
    "receive SupplyCardLogicRef command after RequestCardLogicRef command" in {
      val probe = createTestProbe[CardBehavior.Command]()

      val ref = spawn(CardLogicManagerBehavior.apply())

      ref ! CardLogicManagerBehavior.RequestCardLogicRef(
        CardType.WellsFargo,
        probe.ref
      )

      probe.expectMessageType[CardBehavior.SupplyCardLogicRef]
    }
  }
}
