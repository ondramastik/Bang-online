package cz.ondramastik.bang.impl

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.{Matchers, WordSpecLike}

import java.util.UUID

class CardsDeckBehaviorSpec
    extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID
      .randomUUID()
      .toString}"
    """)
    with WordSpecLike
    with Matchers {

  "Cards deck behavior" should {
    "send SupplyCardsDeckRef command after Initialization is done" in {
      val probe = createTestProbe[GameManagerBehavior.Command]()

      val ref = spawn(CardsDeckBehavior.create("game-1"))

      ref ! CardsDeckBehavior.Initialise(probe.ref)

      probe.expectMessageType[GameManagerBehavior.SupplyCardsDeckRef]
    }
  }
}
