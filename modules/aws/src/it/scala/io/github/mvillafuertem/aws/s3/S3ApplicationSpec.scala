package io.github.mvillafuertem.aws.s3

import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletionException

import com.dimafeng.testcontainers.{ DockerComposeContainer, ExposedService }
import io.github.mvillafuertem.aws.s3.S3ApplicationSpec.S3ApplicationConfigurationIT
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.testcontainers.containers
import org.testcontainers.containers.wait.strategy.Wait
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.http.HttpStatusCode
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model._
import software.amazon.awssdk.services.s3.{ S3AsyncClient, S3AsyncClientBuilder }

import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.{ ExecutionContext, Future }

final class S3ApplicationSpec extends AsyncFlatSpecLike with Matchers with BeforeAndAfterAll with S3ApplicationConfigurationIT {

  behavior of s"${this.getClass.getSimpleName}"

  it should "Create Bucket Request" in {

    // g i v e n
    val NEW_BUCKET_NAME   = "new-bucket-test"
    val headObjectRequest = CreateBucketRequest
      .builder()
      .bucket(NEW_BUCKET_NAME)
      .build()

    // w h e n
    val createBucketResponse: Future[CreateBucketResponse] = for {
      _                  <- createBucketData()
      headObjectResponse <- s3AsyncClient(endpoint = Option(URI.create(S3_ENDPOINT)))
                              .createBucket(headObjectRequest)
                              .toScala
                              .recover {
                                case e: CompletionException if e.getCause.isInstanceOf[NoSuchKeyException] =>
                                  throw e
                              }
    } yield headObjectResponse

    // t h e n
    createBucketResponse.map { actual =>
      actual.sdkHttpResponse().statusCode() shouldBe HttpStatusCode.OK
    }

  }

  it should "Head Object Request" in {

    // g i v e n
    val headObjectRequest = HeadObjectRequest
      .builder()
      .bucket(BUCKET_NAME)
      .key(KEY)
      .build()

    // w h e n
    val headObjectResponse: Future[HeadObjectResponse] = for {
      _                  <- putObjectData()
      headObjectResponse <- s3AsyncClient(endpoint = Option(URI.create(S3_ENDPOINT)))
                              .headObject(headObjectRequest)
                              .toScala
                              .recover {
                                case e: CompletionException if e.getCause.isInstanceOf[NoSuchKeyException] =>
                                  throw e
                              }
    } yield headObjectResponse

    // t h e n
    headObjectResponse.map { actual =>
      actual.sdkHttpResponse().statusCode() shouldBe HttpStatusCode.OK
    }

  }

  override protected def beforeAll(): Unit = dockerInfrastructure.start()

  override protected def afterAll(): Unit = dockerInfrastructure.stop()

}

object S3ApplicationSpec {

  trait S3ApplicationConfigurationIT {

    private val S3_PORT: Int    = 4566
    private val S3_HOST: String = "http://0.0.0.0"
    val S3_ENDPOINT: String     = s"$S3_HOST:$S3_PORT"
    val BUCKET_NAME: String     = "bucket-test"
    val KEY                     = "logback-test.xml"

    val dockerInfrastructure: containers.DockerComposeContainer[_] =
      DockerComposeContainer(
        new File(s"${System.getProperty("user.dir")}/modules/aws/src/it/resources/docker-compose.it.yml"),
        exposedServices = Seq(ExposedService("localstack", S3_PORT, 1, Wait.forLogMessage(".*Starting mock S3 service.*\\n", 1))),
        identifier = "docker_infrastructure"
      ).container

    def createBucketData()(implicit executionContext: ExecutionContext): Future[CreateBucketResponse] = { // create bucket
      val createBucketRequest = CreateBucketRequest
        .builder()
        .bucket(BUCKET_NAME)
        .build()

      s3AsyncClient(endpoint = Option(URI.create(S3_ENDPOINT)))
        .createBucket(createBucketRequest)
        .toScala
        .recover { case e: CompletionException if e.getCause.isInstanceOf[NoSuchKeyException] => throw e }
    }

    def putObjectData()(implicit executionContext: ExecutionContext): Future[PutObjectResponse] = { // put object
      val putObjectRequest = PutObjectRequest
        .builder()
        .bucket(BUCKET_NAME)
        .key(KEY)
        .acl("public-read")
        .build()
      s3AsyncClient(endpoint = Option(URI.create(S3_ENDPOINT)))
        .putObject(putObjectRequest, Path.of(this.getClass.getResource(s"/$KEY").toURI))
        .toScala
        .recover { case e: CompletionException if e.getCause.isInstanceOf[NoSuchKeyException] => throw e }
    }

    def s3AsyncClient(
      region: Option[Region] = None,
      endpoint: Option[URI] = None,
      credentialsProvider: Option[AwsCredentialsProvider] = None
    ): S3AsyncClient = {
      implicit class RichBuilder(s3ClientBuilder: S3AsyncClientBuilder) {
        def add[T](value: Option[T], builder: S3AsyncClientBuilder => T => S3AsyncClientBuilder): S3AsyncClientBuilder =
          value.fold(s3ClientBuilder)(builder(s3ClientBuilder))
      }

      S3AsyncClient
        .builder()
        .add(region, _.region)
        .add(endpoint, _.endpointOverride)
        .add(credentialsProvider, _.credentialsProvider)
        .build()
    }

  }
}