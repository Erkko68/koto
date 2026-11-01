package buildlogic.apigen.kotlin.api

import buildlogic.apigen.ApiGenConfig
import buildlogic.apigen.c.Binding
import buildlogic.apigen.c.Bound
import buildlogic.apigen.c.Bound.Role
import buildlogic.apigen.c.CApi
import buildlogic.apigen.c.CBridge
import buildlogic.apigen.c.CValue

/**
 * The objects of a C library ([buildlogic.apigen.CObjects]): what C spells `ma_engine_set_volume(engine, volume)`
 * becomes a member of the record its name starts with, taking the record first; a function taking none is static.
 * `<record>_init…`, taking it last, is a static function making one ([Role.INIT]), and `<record>_uninit` part of its
 * `close`. A function of an alias of a record is the record's, its name keeping what tells them apart (`groupInit`).
 */
internal class CFunctions(private val c: CApi, config: ApiGenConfig, records: Collection<String>) {
    private val prefix = config.kotlin!!.objects!!.prefix
    /** Every name a record's functions start with, longest first, and the record each stands for. */
    private val owners = (records.associateWith { it } + c.api.aliases.mapNotNull { (alias, target) -> target.decl?.takeIf { it in records }?.let { alias to it } })
        .entries.sortedByDescending { it.key.length }
    /** C record → the binding giving back what `init` made. */
    val uninit = HashMap<String, Binding>()
    /** The C records `init` makes. */
    val initialized = HashSet<String>()

    fun regroup(bindings: List<Binding>): List<Binding> = bindings.mapNotNull { binding ->
        val f = binding.bound!!
        if (f.role != Role.METHOD || f.owner.isNotEmpty()) return@mapNotNull binding
        val (name, record) = owners.firstOrNull { f.name.startsWith("${it.key}_") } ?: return@mapNotNull rebound(binding, "", f.name.removePrefix(prefix), f.params, Role.METHOD, true)
        val self = c.names.type(record)
        val rest = f.name.removePrefix("${name}_")
        val member = if (name == record) rest else "${name.removePrefix("${record}_")}_$rest"
        when {
            rest == "uninit" && isSelf(f.params.firstOrNull(), self) -> { uninit.putIfAbsent(self, binding); null }
            rest.startsWith("init") && isSelf(f.params.lastOrNull(), self) -> {
                initialized += self
                rebound(binding, record, member, f.params.dropLast(1), Role.INIT, true)
            }
            isSelf(f.params.firstOrNull(), self) -> rebound(binding, record, member, f.params.drop(1), Role.METHOD, false)
            else -> rebound(binding, record, member, f.params, Role.METHOD, true)
        }
    }

    private fun isSelf(param: Pair<String, CBridge>?, self: String) = (param?.second?.value as? CValue.Handle)?.c == self

    private fun rebound(binding: Binding, owner: String, name: String, params: List<Pair<String, CBridge>>, role: Role, static: Boolean) = binding.bound!!.let { f ->
        Binding(binding.c, binding.cpp, binding.gap, Bound(role, owner, name, params, f.returns, static, f.signature, doc = f.doc))
    }
}
