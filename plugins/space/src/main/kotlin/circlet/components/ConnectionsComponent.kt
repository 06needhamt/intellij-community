package circlet.components

import circlet.app.*
import circlet.client.api.*
import circlet.messages.*
import circlet.utils.*
import com.intellij.openapi.components.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import runtime.*
import runtime.reactive.*
import runtime.utils.*
import runtime.utils.LifetimedValue
import java.awt.*
import java.net.*
import java.util.concurrent.*

class ConnectionsComponent : ApplicationComponent, LifetimedComponent by SimpleLifetimedComponent() {
    private val connections = LifetimedValueCache<String, Connection>(lifetime) { url, connectionLifetime ->
        Connection(
            LoginModel(
                appLifetime = connectionLifetime,
                server = url,
                credentialsPersistence = IdeaPersistence.substorage("$url-"),
                offlinePersistence = null,
                offlinePersistenceConfiguration = CircletPersistenceConfiguration.storeNothing
            ),
            connectionLifetime
        )
    }

    fun get(url: String, urlLifetime: Lifetime): LifetimedValue<Connection> =
        connections.get(url, urlLifetime)
}

val connections: ConnectionsComponent = application.getComponent()

class Connection(val loginModel: LoginModel, connectionLifetime: Lifetime) {
    private val serverLifetimes = SequentialLifetimes(connectionLifetime)

    fun authenticate() {
        val port = selectFreePort(10000)
        val serverLifetime = serverLifetimes.next().nested()
        val server = embeddedServer(Jetty, port, "localhost") {
            routing {
                get("auth") {
                    val userId = call.parameters[USER_ID_PARAMETER]!!
                    val token = call.parameters[TOKEN_PARAMETER]!!

                    loginModel.signIn(userId, token, "")

                    call.respondRedirect("success")
                }

                get("success") {
                    call.respondText(CircletBundle.message("authorization-successful"), ContentType.Text.Html)

                    serverLifetime.terminate()
                }
            }
        }.start(wait = false)

        serverLifetime.add {
            server.stop(100, 5000, TimeUnit.MILLISECONDS)
        }

        Desktop.getDesktop().browse(URI(
            Navigator.login("http://localhost:$port/auth").absoluteHref(loginModel.server)
        ))
    }
}
