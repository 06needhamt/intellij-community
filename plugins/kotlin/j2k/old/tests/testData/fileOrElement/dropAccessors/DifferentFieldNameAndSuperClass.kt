open class Base internal constructor(x: Int) {
    var x = 42
        protected set

    init {
        this.x = x
    }
}

internal class Derived internal constructor(b: Base) : Base(b.x)
