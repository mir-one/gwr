package com.wavesplatform.it.sync.matcher

import com.typesafe.config.Config
import com.wavesplatform.it.api.SyncHttpApi._
import com.wavesplatform.it.api.SyncMatcherHttpApi._
import com.wavesplatform.it.matcher.MatcherSuiteBase
import com.wavesplatform.it.sync._
import com.wavesplatform.state.ByteStr
import com.wavesplatform.transaction.assets.exchange.{AssetPair, OrderType}
import com.wavesplatform.it.sync.matcher.config.MatcherDefaultConfig._

import scala.concurrent.duration._

class MatcherMigrationTestSuite extends MatcherSuiteBase {
  override protected def nodeConfigs: Seq[Config] = Configs

  "issue asset and run migration tool inside container" - {
    // Alice issues new asset
    val aliceAsset =
      aliceNode
        .issue(aliceAcc.address, "DisconnectCoin", "Alice's coin for disconnect tests", 1000 * someAssetAmount, 8, reissuable = false, issueFee, 2)
        .id
    nodes.waitForHeightAriseAndTxPresent(aliceAsset)

    val aliceBalance = aliceNode.accountBalances(aliceAcc.address)._2
    val t1           = aliceNode.transfer(aliceAcc.address, matcherAcc.address, aliceBalance - minFee - 250000, minFee, None, None, 2).id
    nodes.waitForHeightAriseAndTxPresent(t1)

    val aliceWavesPair = AssetPair(ByteStr.decodeBase58(aliceAsset).toOption, None)

    "place order and run migration tool" in {
      // Alice places sell order
      val aliceOrder = matcherNode
        .placeOrder(aliceAcc, aliceWavesPair, OrderType.SELL, 3000000, 3000000)
      aliceOrder.status shouldBe "OrderAccepted"
      val firstOrder = aliceOrder.message.id

      // check order status
      matcherNode.orderStatus(firstOrder, aliceWavesPair).status shouldBe "Accepted"

      // sell order should be in the aliceNode orderbook
      matcherNode.fullOrderHistory(aliceAcc).head.status shouldBe "Accepted"

      val tbBefore = matcherNode.tradableBalance(aliceAcc, aliceWavesPair)
      val rbBefore = matcherNode.reservedBalance(aliceAcc)

      // stop node, run migration tool and start node again
      docker.runMigrationToolInsideContainer(dockerNodes().head)
      Thread.sleep(60.seconds.toMillis)

      val height = nodes.map(_.height).max

      matcherNode.waitForHeight(height + 1, 40.seconds)
      matcherNode.orderStatus(firstOrder, aliceWavesPair).status shouldBe "Accepted"
      matcherNode.fullOrderHistory(aliceAcc).head.status shouldBe "Accepted"

      val tbAfter = matcherNode.tradableBalance(aliceAcc, aliceWavesPair)
      val rbAfter = matcherNode.reservedBalance(aliceAcc)

      assert(tbBefore == tbAfter)
      assert(rbBefore == rbAfter)

    }
  }
}
