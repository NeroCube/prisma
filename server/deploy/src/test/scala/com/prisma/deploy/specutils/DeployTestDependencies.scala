package com.prisma.deploy.specutils

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.prisma.auth.AuthImpl
import com.prisma.errors.{BugsnagErrorReporter, ErrorReporter}
import com.prisma.deploy.{DeployDependencies, MySqlDeployPersistencePlugin}
import com.prisma.deploy.persistence.DeployPersistencePlugin
import com.prisma.deploy.server.DummyClusterAuth
import com.prisma.graphql.GraphQlClient
import com.prisma.messagebus.pubsub.inmemory.InMemoryAkkaPubSub

case class DeployTestDependencies()(implicit val system: ActorSystem, val materializer: ActorMaterializer) extends DeployDependencies {
  override implicit def self: DeployDependencies = this

  implicit val reporter: ErrorReporter = BugsnagErrorReporter(sys.env.getOrElse("BUGSNAG_API_KEY", ""))

  override lazy val migrator              = TestMigrator(migrationPersistence, deployPersistencePlugin.deployMutactionExecutor)
  override lazy val clusterAuth           = DummyClusterAuth()
  override lazy val invalidationPublisher = InMemoryAkkaPubSub[String]()

  override lazy val graphQlClient = {
    val port = sys.props.getOrElse("STUB_SERVER_PORT", sys.error("No running stub server detected! Can't instantiate GraphQlClient."))
    GraphQlClient(s"http://localhost:$port")
  }

  override def apiAuth = AuthImpl

  override def deployPersistencePlugin: DeployPersistencePlugin = {
    import slick.jdbc.MySQLProfile.api._
    val sqlInternalHost     = sys.env("SQL_CLIENT_HOST")
    val sqlInternalPort     = sys.env("SQL_CLIENT_PORT")
    val sqlInternalUser     = sys.env("SQL_CLIENT_USER")
    val sqlInternalPassword = sys.env("SQL_CLIENT_PASSWORD")
    val clientDb = Database.forURL(
      url =
        s"jdbc:mariadb://$sqlInternalHost:$sqlInternalPort?autoReconnect=true&useSSL=false&serverTimeZone=UTC&useUnicode=true&characterEncoding=UTF-8&usePipelineAuth=false",
      user = sqlInternalUser,
      password = sqlInternalPassword,
      driver = "org.mariadb.jdbc.Driver"
    )
    MySqlDeployPersistencePlugin(clientDatabase = clientDb)(system.dispatcher)
  }
}
