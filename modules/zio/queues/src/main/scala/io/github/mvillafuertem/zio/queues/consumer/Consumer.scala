package io.github.mvillafuertem.zio.queues.consumer

import io.github.mvillafuertem.zio.queues.consumer.ConsumerSettings.ZConsumerSettings
import zio.clock.{ sleep, _ }
import zio.console.{ putStrLn, Console }
import zio.duration._
import zio.random.{ Random, _ }
import zio.{ Queue, _ }

trait Consumer[T] {

  def consume(): ZIO[Console with Clock with Random, Nothing, (Queue[ConsumerRecord[T]], Fiber.Runtime[Nothing, Nothing])]

}

object Consumer {

  def make[T](consumerSettings: ConsumerSettings): UIO[Consumer[T]] =
    UIO.succeed(Live[T](consumerSettings))

  type ZConsumer[T] = Has[Consumer[T]]

  case class Live[T](consumerSettings: ConsumerSettings) extends Consumer[T] {

    private val queueM: UIO[Queue[ConsumerRecord[T]]] = Queue.bounded[ConsumerRecord[T]](consumerSettings.size)

    def consume() =
      for {
        queue <- queueM
        fiber <- loop(queue).forever.fork
      } yield (queue, fiber)

    def loop(queue: Queue[ConsumerRecord[T]]) =
      for {
        nOrder   <- queue.take
        _        <- putStrLn(s"${consumerSettings.color}[${consumerSettings.name}] worker: Starting preparing order $nOrder${scala.Console.RESET}")
        duration <- nextInt(4).map(_.seconds)
        _        <- sleep(duration)
        _        <- putStrLn(s"${consumerSettings.color}[${consumerSettings.name}] worker: Finished order $nOrder${scala.Console.RESET}")
      } yield ()

  }

  def live[A: Tagged]: ZLayer[ZConsumerSettings, Nothing, ZConsumer[A]] =
    ZLayer.fromService[ConsumerSettings, Consumer[A]](Live.apply[A])

  def makeM[A: Tagged](consumerSettings: ConsumerSettings): ZLayer[Any, Nothing, ZConsumer[A]] =
    ZLayer.succeed(consumerSettings) >>> live[A]

}