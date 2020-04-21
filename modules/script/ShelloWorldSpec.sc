#!/usr/bin/env amm

import $file.ShelloWorld
import $ivy.`dev.zio::zio-test-sbt:1.0.0-RC18-2`
import $ivy.`dev.zio::zio-test:1.0.0-RC18-2`
import zio._
import zio.console._
import zio.test.Assertion._
import zio.test._
import zio.test.environment._

// amm `pwd`/ShelloWorldSpec.sc
MyAppSpec.main(Array())

import HelloWorld._

object HelloWorld {
  def sayHello: ZIO[Console, Nothing, Unit] =
    console.putStrLn("Hello, World!")
}

object MyAppSpec extends DefaultRunnableSpec {

  override def spec =
    suite("HelloWorldSpec")(
      testM("sayHello correctly displays output") {
        for {
          _ <- TestConsole.feedLines("Pepe", "1")
          _ <- ShelloWorld.MyApp.myAppLogic
          _ <- sayHello
          output <- TestConsole.output
        } yield assert(output)(equalTo(Vector("Hello, World!\n")))
      }
    )

}