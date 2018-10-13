package com.wavesplatform.matcher.model

import cats.implicits._
import com.wavesplatform.account.PublicKeyAccount
import com.wavesplatform.matcher.MatcherSettings
import com.wavesplatform.matcher.api.DBUtils.indexes.active.MaxElements
import com.wavesplatform.matcher.model.OrderHistory.OrderInfoChange
import com.wavesplatform.metrics.TimerExt
import com.wavesplatform.state._
import com.wavesplatform.transaction.AssetAcc
import com.wavesplatform.transaction.ValidationError.GenericError
import com.wavesplatform.transaction.assets.exchange.Validation.booleanOperators
import com.wavesplatform.transaction.assets.exchange.{Order, Validation}
import com.wavesplatform.transaction.smart.Verifier
import com.wavesplatform.utils.NTP
import com.wavesplatform.utx.UtxPool
import com.wavesplatform.wallet.Wallet
import kamon.Kamon

trait OrderValidator {
  val orderHistory: OrderHistory
  val utxPool: UtxPool
  val settings: MatcherSettings
  val wallet: Wallet

  lazy val matcherPubKey: PublicKeyAccount = wallet.findPrivateKey(settings.account).explicitGet()
  val MinExpiration: Long                  = 60 * 1000L

  private val timer = Kamon.timer("matcher.validation")

  private def isBalanceWithOpenOrdersEnough(order: Order): Validation = {
    val lo = LimitOrder(order)

    val b: Map[Option[ByteStr], Long] = Seq(lo.spentAcc, lo.feeAcc).map(a => a.assetId -> spendableBalance(a)).toMap

    val change = OrderInfoChange(lo.order, None, OrderInfo(order.amount, 0L, None, None, order.matcherFee, Some(0L)))
    val newOrder = OrderHistory
      .diff(List(change))
      .getOrElse(order.senderPublicKey.toAddress, OpenPortfolio.empty)

    val open  = b.keySet.map(id => id -> orderHistory.openVolume(order.senderPublicKey, id)).toMap
    val needs = OpenPortfolio(open).combine(newOrder)

    val res: Boolean = b.combine(needs.orders.mapValues(-_)).forall(_._2 >= 0)

    res :| s"Not enough tradable balance: ${b.combine(open.mapValues(-_))}, needs: $newOrder"
  }

  def getTradableBalance(acc: AssetAcc): Long = timer.refine("action" -> "tradableBalance").measure {
    math.max(0l, spendableBalance(acc) - orderHistory.openVolume(acc.account, acc.assetId))
  }

  def validateNewOrder(order: Order): Either[GenericError, Order] =
    timer
      .refine("action" -> "place", "pair" -> order.assetPair.toString)
      .measure {
        val orderSignatureVerification =
          Verifier
            .verifyAsEllipticCurveSignature(order)
            .map(_ => ())
            .leftMap(_.toString)

        val v =
          (order.matcherPublicKey == matcherPubKey) :| "Incorrect matcher public key" &&
            (order.expiration > NTP.correctedTime() + MinExpiration) :| "Order expiration should be > 1 min" &&
            orderSignatureVerification &&
            order.isValid(NTP.correctedTime()) &&
            (order.matcherFee >= settings.minOrderFee) :| s"Order matcherFee should be >= ${settings.minOrderFee}" &&
            (orderHistory.orderInfo(order.id()).status == LimitOrder.NotFound) :| "Order was placed before" &&
            (orderHistory.activeOrderCount(order.senderPublicKey) < MaxElements) :| s"Limit of $MaxElements active orders has been reached" &&
            isBalanceWithOpenOrdersEnough(order)
        Either.cond(v, order, GenericError(v.messages()))
      }

  private def spendableBalance(a: AssetAcc): Long = {
    val portfolio = utxPool.portfolio(a.account)
    a.assetId match {
      case Some(x) => portfolio.assets.getOrElse(x, 0)
      case None    => portfolio.spendableBalance
    }
  }
}
