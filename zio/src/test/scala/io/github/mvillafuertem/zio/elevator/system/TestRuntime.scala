package io.github.mvillafuertem.zio.elevator.system

import java.util.{Timer, TimerTask}

import org.specs2.Specification
import org.specs2.execute.AsResult
import org.specs2.specification.core.{AsExecution, Execution}
import zio.{DefaultRuntime, FiberFailure, ZIO}
import zio.duration.Duration
import zio.duration._

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author Miguel Villafuerte
 */
abstract class TestRuntime extends Specification with DefaultRuntime {
  val DefaultTimeout: Duration = 60 seconds
  val timer = new Timer()
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  implicit def zioAsExecution[A: AsResult, R >: Environment, E]
  : AsExecution[ZIO[R, E, A]] =
    io =>
      Execution.withEnvAsync(_ => runToFutureWithTimeout(io, DefaultTimeout))

  protected def runToFutureWithTimeout[E, R >: Environment, A: AsResult](
                                                                          io: ZIO[R, E, A],
                                                                          timeout: Duration
                                                                        ): Future[A] = {
    val p = scala.concurrent.Promise[A]()
    val task = new TimerTask {
      override def run(): Unit =
        try {
          p.failure(new Exception("TIMEOUT: " + timeout))
          ()
        } catch {
          case _: Throwable => ()
        }
    }
    timer.schedule(task, timeout.toMillis)

    unsafeRunToFuture(io.sandbox.mapError(FiberFailure(_))).map(p.success)
    p.future
  }

}
