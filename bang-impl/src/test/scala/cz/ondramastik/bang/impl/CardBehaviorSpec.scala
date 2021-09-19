package cz.ondramastik.bang.impl

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import cz.ondramastik.bang.domain.CardType
import org.scalatest.{Matchers, WordSpecLike}

import java.util.UUID

class CardBehaviorSpec
    extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID
      .randomUUID()
      .toString}"
    """)
    with WordSpecLike
    with Matchers {

  "Card behavior" should {
    "send SupplyCardRef command after Initialisation is done" in {
      val probe = createTestProbe[CardsDeckBehavior.Command]()

      val ref = spawn(CardBehavior.create())

      ref ! CardBehavior.Initialise(CardType.WellsFargo, probe.ref)

      probe.expectMessageType[CardsDeckBehavior.SupplyCardRef]
    }
  }
}
