package circlet.settings

import circlet.utils.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import runtime.reactive.*

@State(
    name = "CircletProjectSettings",
    storages = [Storage(value = "CircletClient.xml", roamingType = RoamingType.DISABLED)]
)
class CircletProjectSettings(private val project: Project) :
    Lifetimed by LifetimedOnDisposable(project),
    PersistentStateComponent<CircletProjectSettings.State> {

    data class State(var serverUrl: String = "")

    val serverUrl: MutableProperty<String> = mutableProperty("")

    override fun getState(): State? = State(
        serverUrl = serverUrl.value
    )

    override fun loadState(state: State) {
        serverUrl.value = state.serverUrl
    }
}

val Project.settings: CircletProjectSettings get() = getService()
